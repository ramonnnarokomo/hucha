package com.ramonnnarokomo.hucha.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Test
    void locksLoginAfterTooManyFailures() {
        User user = new User(" Ana@Test.dev ", "Ana", "hash", Role.CUSTOMER, NOW);
        assertThat(user.getEmail()).isEqualTo("ana@test.dev");

        for (int i = 0; i < 4; i++) {
            user.registerFailedLogin(5, Duration.ofMinutes(15), NOW);
        }
        assertThat(user.isLoginLocked(NOW)).isFalse();

        user.registerFailedLogin(5, Duration.ofMinutes(15), NOW);
        assertThat(user.isLoginLocked(NOW)).isTrue();
        assertThat(user.isLoginLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        User user = new User("ana@test.dev", "Ana", "hash", Role.CUSTOMER, NOW);
        user.registerFailedLogin(5, Duration.ofMinutes(15), NOW);
        user.registerSuccessfulLogin();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }
}
