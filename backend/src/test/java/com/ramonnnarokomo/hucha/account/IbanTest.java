package com.ramonnnarokomo.hucha.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbanTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ES9121000418450200051332",
            "es91 2100 0418 4502 0005 1332",
            "DE89370400440532013000",
            "GB29NWBK60161331926819",
            "FR1420041010050500013M02606"})
    void acceptsValidIbans(String iban) {
        assertThat(Iban.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ES9121000418450200051333",
            "ES912100041845020005133",
            "ES91210004184502000513AB",
            "XX00",
            ""})
    void rejectsInvalidIbans(String iban) {
        assertThat(Iban.isValid(iban)).isFalse();
    }

    @Test
    void buildsSpanishIbansWithTheRightControlDigits() {
        // CCC de ejemplo 2100-0418-45-0200051332 (dígitos de control 45).
        assertThat(Iban.spanish("2100", "0418", "0200051332")).isEqualTo("ES9121000418450200051332");
        assertThat(Iban.spanish("9999", "0001", "0000000001")).isEqualTo("ES8099990001450000000001");
    }

    @Test
    void cccControlDigitHandlesTheSpecialCases() {
        // 11 - (suma % 11) vale 11 -> 0 y vale 10 -> 1.
        assertThat(Iban.cccControlDigit("0000000000")).isZero();
        assertThat(Iban.cccControlDigit("0021000418")).isEqualTo(4);
    }

    @Test
    void generatedIbansAreAlwaysValid() {
        for (int i = 0; i < 200; i++) {
            String number = String.format("%010d", (long) (Math.random() * 9_999_999_999L));
            assertThat(Iban.isValid(Iban.spanish("9999", "0001", number))).isTrue();
        }
    }

    @Test
    void normalizesAndFormats() {
        assertThat(Iban.normalize(" es91-2100 0418 ")).isEqualTo("ES9121000418");
        assertThat(Iban.format("ES9121000418450200051332")).isEqualTo("ES91 2100 0418 4502 0005 1332");
    }

    @Test
    void rejectsMalformedCccParts() {
        assertThatThrownBy(() -> Iban.spanish("21", "0418", "0200051332")).isInstanceOf(IllegalArgumentException.class);
    }
}
