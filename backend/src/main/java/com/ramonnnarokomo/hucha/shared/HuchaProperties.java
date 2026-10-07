package com.ramonnnarokomo.hucha.shared;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia de la aplicación (prefijo {@code hucha} en application.yml). */
@ConfigurationProperties(prefix = "hucha")
public record HuchaProperties(
        ZoneId zone,
        Security security,
        Transfers transfers,
        Accounts accounts,
        Outbox outbox,
        boolean demoData) {

    public record Security(
            String jwtSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            int maxFailedLogins,
            Duration lockDuration,
            boolean refreshCookieSecure) {
    }

    public record Transfers(
            BigDecimal maxAmount,
            BigDecimal scaThreshold,
            BigDecimal scaNewPayeeThreshold,
            Duration scaCodeTtl,
            int scaMaxAttempts) {
    }

    public record Accounts(BigDecimal defaultDailyLimit, BigDecimal welcomeGift) {
    }

    public record Outbox(boolean relayEnabled, long relayIntervalMs) {
    }
}
