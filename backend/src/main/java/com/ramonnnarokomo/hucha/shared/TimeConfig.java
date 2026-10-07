package com.ramonnnarokomo.hucha.shared;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Hora actual inyectable: los tests pueden sustituirla por un reloj fijo. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
