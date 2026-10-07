package com.ramonnnarokomo.hucha.account;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * IBAN: validación ISO 13616 (módulo 97) y cálculo de IBAN españoles a partir del CCC
 * (entidad, oficina, dígitos de control y número de cuenta).
 */
public final class Iban {

    private static final Pattern SHAPE = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");
    private static final int[] CCC_WEIGHTS = {1, 2, 4, 8, 5, 10, 9, 7, 3, 6};
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Quita espacios y guiones y pasa a mayúsculas. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String raw) {
        String iban = normalize(raw);
        if (!SHAPE.matcher(iban).matches()) {
            return false;
        }
        if (iban.startsWith("ES") && (iban.length() != 24 || !iban.substring(4).chars().allMatch(Character::isDigit))) {
            return false;
        }
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
    }

    /** "ES9121000418450200051332" -> "ES91 2100 0418 4502 0005 1332". */
    public static String format(String raw) {
        String iban = normalize(raw);
        return iban.replaceAll("(.{4})(?!$)", "$1 ");
    }

    /** Construye un IBAN español: calcula los dos dígitos de control del CCC y los del IBAN. */
    public static String spanish(String bank, String branch, String accountNumber) {
        if (!bank.matches("\\d{4}") || !branch.matches("\\d{4}") || !accountNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("Entidad y oficina de 4 dígitos y cuenta de 10 dígitos");
        }
        int firstDigit = cccControlDigit("00" + bank + branch);
        int secondDigit = cccControlDigit(accountNumber);
        String bban = bank + branch + firstDigit + secondDigit + accountNumber;
        int check = 98 - mod97(bban + "ES00");
        return String.format("ES%02d%s", check, bban);
    }

    static int cccControlDigit(String tenDigits) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.digit(tenDigits.charAt(i), 10) * CCC_WEIGHTS[i];
        }
        int digit = 11 - (sum % 11);
        if (digit == 11) {
            return 0;
        }
        return digit == 10 ? 1 : digit;
    }

    /** Resto módulo 97 sustituyendo cada letra por su valor (A=10 ... Z=35). */
    private static int mod97(String value) {
        StringBuilder digits = new StringBuilder(value.length() * 2);
        for (char c : value.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue();
    }
}
