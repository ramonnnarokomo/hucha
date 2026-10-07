package com.ramonnnarokomo.hucha.card;

import java.security.SecureRandom;

/** Números de tarjeta con dígito de control Luhn (ISO/IEC 7812). */
public final class CardNumbers {

    /** Prefijo de prueba: empieza por 4 como las Visa, pero no pertenece a ningún emisor real. */
    static final String TEST_BIN = "499999";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumbers() {
    }

    public static String generate() {
        StringBuilder number = new StringBuilder(TEST_BIN);
        while (number.length() < 15) {
            number.append(RANDOM.nextInt(10));
        }
        return number.append(luhnCheckDigit(number.toString())).toString();
    }

    public static boolean isValid(String number) {
        if (number == null || !number.matches("\\d{12,19}")) {
            return false;
        }
        String payload = number.substring(0, number.length() - 1);
        return luhnCheckDigit(payload) == Character.digit(number.charAt(number.length() - 1), 10);
    }

    /** Dígito que hace que el número completo pase el algoritmo de Luhn. */
    static int luhnCheckDigit(String payload) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = Character.digit(payload.charAt(i), 10);
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }
}
