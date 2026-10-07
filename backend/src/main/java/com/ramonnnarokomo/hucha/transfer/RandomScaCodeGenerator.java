package com.ramonnnarokomo.hucha.transfer;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class RandomScaCodeGenerator implements ScaCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
