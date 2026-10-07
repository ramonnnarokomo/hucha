package com.ramonnnarokomo.hucha.account;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** IBAN nuevos de Hucha: entidad 9999 (ficticia), oficina 0001 y número de cuenta aleatorio. */
@Component
public class IbanGenerator {

    static final String BANK_CODE = "9999";
    static final String BRANCH_CODE = "0001";

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accounts;

    public IbanGenerator(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public String next() {
        while (true) {
            String number = String.format("%010d", 1_000_000_000L + (long) (random.nextDouble() * 8_999_999_999L));
            String iban = Iban.spanish(BANK_CODE, BRANCH_CODE, number);
            if (!accounts.existsByIban(iban)) {
                return iban;
            }
        }
    }
}
