package com.ramonnnarokomo.hucha.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CardNumbersTest {

    @Test
    void validatesKnownLuhnNumbers() {
        assertThat(CardNumbers.isValid("4111111111111111")).isTrue();
        assertThat(CardNumbers.isValid("79927398713")).isFalse(); // demasiado corto (12 mínimo)
        assertThat(CardNumbers.isValid("4111111111111112")).isFalse();
        assertThat(CardNumbers.luhnCheckDigit("7992739871")).isEqualTo(3);
    }

    @RepeatedTest(50)
    void generatesSixteenDigitNumbersThatPassLuhn() {
        String number = CardNumbers.generate();
        assertThat(number).hasSize(16).startsWith(CardNumbers.TEST_BIN);
        assertThat(CardNumbers.isValid(number)).isTrue();
    }
}
