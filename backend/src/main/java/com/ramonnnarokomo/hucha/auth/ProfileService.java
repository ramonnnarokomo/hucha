package com.ramonnnarokomo.hucha.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.auth.AuthDtos.ChangePasswordRequest;
import com.ramonnnarokomo.hucha.auth.AuthDtos.SessionView;
import com.ramonnnarokomo.hucha.notification.NotificationService;
import com.ramonnnarokomo.hucha.notification.NotificationType;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;
import com.ramonnnarokomo.hucha.user.User;
import com.ramonnnarokomo.hucha.user.UserRepository;
import com.ramonnnarokomo.hucha.user.UserSummary;

/** Perfil, cambio de contraseña y sesiones abiertas del usuario. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ProfileService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                          NotificationService notifications, AuditService audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserSummary me(UUID userId) {
        return UserSummary.of(user(userId));
    }

    /** Cambia la contraseña y cierra el resto de sesiones abiertas. */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, UUID currentSession, RequestInfo info) {
        User user = user(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unprocessable(ErrorCode.WRONG_PASSWORD, "La contraseña actual no es correcta.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        Instant now = clock.instant();
        if (currentSession != null) {
            refreshTokens.revokeAllForUserExcept(userId, currentSession, now);
        } else {
            refreshTokens.revokeAllForUser(userId, now);
        }
        audit.record(new AuditService.Actor(userId, user.getEmail()), AuditAction.PASSWORD_CHANGED, "USER", userId, info,
                null);
        notifications.notify(userId, NotificationType.SECURITY, "Contraseña cambiada",
                "Has cambiado tu contraseña y hemos cerrado el resto de sesiones abiertas.");
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(UUID userId, UUID currentSession) {
        Instant now = clock.instant();
        return refreshTokens.findActiveByUser(userId, now).stream()
                .map(token -> new SessionView(
                        token.getFamilyId(),
                        refreshTokens.findFirstByFamilyIdOrderByCreatedAtAsc(token.getFamilyId())
                                .map(RefreshToken::getCreatedAt).orElse(token.getCreatedAt()),
                        token.getLastUsedAt(),
                        token.getUserAgent(),
                        token.getIpAddress(),
                        token.getFamilyId().equals(currentSession)))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, RequestInfo info) {
        boolean owned = refreshTokens.findActiveByUser(userId, clock.instant()).stream()
                .anyMatch(token -> token.getFamilyId().equals(sessionId));
        if (!owned) {
            throw ApiException.notFound("Sesión no encontrada.");
        }
        refreshTokens.revokeFamily(sessionId, clock.instant());
        audit.record(new AuditService.Actor(userId, user(userId).getEmail()), AuditAction.SESSION_REVOKED, "SESSION",
                sessionId, info, null);
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, UUID currentSession, RequestInfo info) {
        if (currentSession == null) {
            refreshTokens.revokeAllForUser(userId, clock.instant());
        } else {
            refreshTokens.revokeAllForUserExcept(userId, currentSession, clock.instant());
        }
        audit.record(new AuditService.Actor(userId, user(userId).getEmail()), AuditAction.SESSION_REVOKED, "SESSION",
                "todas", info, "Cierre del resto de sesiones");
    }

    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
    }
}
