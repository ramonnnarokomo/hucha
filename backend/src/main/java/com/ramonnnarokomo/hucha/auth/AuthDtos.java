package com.ramonnnarokomo.hucha.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.ramonnnarokomo.hucha.user.UserSummary;

public final class AuthDtos {

    /** Mínimo 8 caracteres con al menos una letra y un número. */
    static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    static final String PASSWORD_MESSAGE = "Mínimo 8 caracteres, con letras y números";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
    }

    /**
     * Respuesta de login/refresco. El token de refresco no va aquí: viaja en una cookie HttpOnly.
     * {@code sessionId} identifica la sesión para marcarla como "esta" en la lista de sesiones.
     */
    public record AuthResponse(String accessToken, long expiresIn, UUID sessionId, UserSummary user) {
    }

    public record SessionView(UUID id, Instant startedAt, Instant lastUsedAt, String userAgent,
                              String ipAddress, boolean current) {
    }
}
