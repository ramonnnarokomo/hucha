package com.ramonnnarokomo.hucha.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.ramonnnarokomo.hucha.audit.AuditService;

/** Atajos para leer el usuario autenticado del JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static AuditService.Actor actor(Jwt jwt) {
        return new AuditService.Actor(id(jwt), jwt.getClaimAsString("email"));
    }
}
