package com.ramonnnarokomo.hucha.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.auth.AuthDtos.LoginRequest;
import com.ramonnnarokomo.hucha.auth.AuthDtos.RegisterRequest;
import com.ramonnnarokomo.hucha.onboarding.OnboardingService;
import com.ramonnnarokomo.hucha.security.TokenService;
import com.ramonnnarokomo.hucha.shared.Hashing;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;
import com.ramonnnarokomo.hucha.user.Role;
import com.ramonnnarokomo.hucha.user.User;
import com.ramonnnarokomo.hucha.user.UserRepository;
import com.ramonnnarokomo.hucha.user.UserStatus;
import com.ramonnnarokomo.hucha.user.UserSummary;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registro, login y refresco de sesión.
 * Access token JWT de 15 minutos + refresh token opaco de 30 días que rota en cada uso.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OnboardingService onboarding;
    private final AuditService audit;
    private final HuchaProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;
    /** Hash de relleno: con un email inexistente se hace el mismo trabajo y no se nota en el tiempo de respuesta. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, OnboardingService onboarding, AuditService audit,
                       HuchaProperties properties, MeterRegistry metrics, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.onboarding = onboarding;
        this.audit = audit;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult register(RegisterRequest request, RequestInfo info) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_REGISTERED, "Ya hay una cuenta con ese email.");
        }
        Instant now = clock.instant();
        User user = users.save(new User(email, request.fullName(), passwordEncoder.encode(request.password()),
                Role.CUSTOMER, now));
        onboarding.onboard(user, now, info);
        audit.record(actor(user), AuditAction.USER_REGISTERED, "USER", user.getId(), info, null);
        return startSession(user, info, now);
    }

    /** Los intentos fallidos se guardan aunque se lance la excepción (noRollbackFor). */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(LoginRequest request, RequestInfo info) {
        Instant now = clock.instant();
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            metrics.counter("hucha.logins", "result", "failed").increment();
            throw invalidCredentials();
        }
        if (user.isLoginLocked(now)) {
            throw ApiException.unauthorized(ErrorCode.LOGIN_TEMPORARILY_LOCKED,
                    "Demasiados intentos fallidos. Prueba de nuevo en unos minutos.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(properties.security().maxFailedLogins(), properties.security().lockDuration(), now);
            boolean locked = user.isLoginLocked(now);
            audit.record(actor(user), locked ? AuditAction.LOGIN_LOCKED : AuditAction.LOGIN_FAILED, "USER",
                    user.getId(), info, null);
            metrics.counter("hucha.logins", "result", "failed").increment();
            throw invalidCredentials();
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw ApiException.forbidden(ErrorCode.USER_BLOCKED, "Tu usuario está bloqueado. Contacta con soporte.");
        }
        user.registerSuccessfulLogin();
        audit.record(actor(user), AuditAction.LOGIN_SUCCEEDED, "USER", user.getId(), info, info.userAgent());
        metrics.counter("hucha.logins", "result", "succeeded").increment();
        return startSession(user, info, now);
    }

    /**
     * Rota el refresh token. Si llega uno que ya se había rotado, alguien lo está reutilizando
     * (robo de token): se cierra toda la sesión, también para el usuario legítimo.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String rawToken, RequestInfo info) {
        Instant now = clock.instant();
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefresh();
        }
        RefreshToken current = refreshTokens.findByTokenHash(Hashing.sha256(rawToken)).orElseThrow(this::invalidRefresh);
        if (current.wasRotated()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            User owner = users.findById(current.getUserId()).orElse(null);
            audit.record(owner != null ? actor(owner) : null, AuditAction.REFRESH_TOKEN_REUSE, "SESSION",
                    current.getFamilyId(), info, "Se ha cerrado la sesión por reutilización del token de refresco");
            throw invalidRefresh();
        }
        if (!current.isActive(now)) {
            throw invalidRefresh();
        }
        User user = users.findById(current.getUserId()).orElseThrow(this::invalidRefresh);
        if (user.getStatus() == UserStatus.BLOCKED) {
            refreshTokens.revokeAllForUser(user.getId(), now);
            throw ApiException.forbidden(ErrorCode.USER_BLOCKED, "Tu usuario está bloqueado. Contacta con soporte.");
        }
        String newRaw = newRawToken();
        RefreshToken next = new RefreshToken(user.getId(), current.getFamilyId(), Hashing.sha256(newRaw), now,
                current.getExpiresAt(), info.userAgent(), info.ipAddress());
        refreshTokens.save(next);
        current.rotateTo(next.getId(), now);
        return result(user, newRaw, next.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken, RequestInfo info) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Hashing.sha256(rawToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            users.findById(token.getUserId()).ifPresent(user ->
                    audit.record(actor(user), AuditAction.LOGOUT, "SESSION", token.getFamilyId(), info, null));
        });
    }

    /** Familia (sesión) a la que pertenece un refresh token, para marcar la sesión actual. */
    @Transactional(readOnly = true)
    public UUID familyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return refreshTokens.findByTokenHash(Hashing.sha256(rawToken)).map(RefreshToken::getFamilyId).orElse(null);
    }

    private LoginResult startSession(User user, RequestInfo info, Instant now) {
        String raw = newRawToken();
        RefreshToken token = refreshTokens.save(new RefreshToken(user.getId(), UUID.randomUUID(), Hashing.sha256(raw),
                now, now.plus(properties.security().refreshTokenTtl()), info.userAgent(), info.ipAddress()));
        return result(user, raw, token.getFamilyId());
    }

    private LoginResult result(User user, String rawRefreshToken, UUID familyId) {
        TokenService.AccessToken access = tokenService.issueAccessToken(user);
        return new LoginResult(access.value(), access.expiresInSeconds(), rawRefreshToken, familyId, UserSummary.of(user));
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static AuditService.Actor actor(User user) {
        return new AuditService.Actor(user.getId(), user.getEmail());
    }

    private ApiException invalidCredentials() {
        return ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Email o contraseña incorrectos.");
    }

    private ApiException invalidRefresh() {
        return ApiException.unauthorized(ErrorCode.INVALID_REFRESH_TOKEN, "La sesión ha caducado. Vuelve a entrar.");
    }

    public record LoginResult(String accessToken, long expiresIn, String refreshToken, UUID sessionId, UserSummary user) {
    }
}
