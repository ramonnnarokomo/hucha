package com.ramonnnarokomo.hucha.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.ramonnnarokomo.hucha.transfer.ScaCodeGenerator;

@TestConfiguration
public class TestBeans {

    /** Miércoles 7 de octubre de 2026, 12:00 en Madrid. */
    public static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    public static final String SCA_CODE = "123456";

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(NOW);
    }

    @Bean
    @Primary
    public ScaCodeGenerator fixedScaCode() {
        return () -> SCA_CODE;
    }
}
