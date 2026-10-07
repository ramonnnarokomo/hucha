package com.ramonnnarokomo.hucha.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.ramonnnarokomo.hucha.shared.HuchaProperties;

/**
 * El refresh token viaja en una cookie HttpOnly (JavaScript no puede leerla, así que un XSS no la roba),
 * SameSite=Strict y limitada a /api/auth. El access token vive solo en memoria en el front.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "hucha_refresh";
    private static final String PATH = "/api/auth";

    private final HuchaProperties properties;

    public RefreshCookie(HuchaProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(properties.security().refreshTokenTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.security().refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
