package com.ramonnnarokomo.hucha.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.ramonnnarokomo.hucha.shared.error.ApiException;

class AmountsTest {

    @Test
    void normalizesToTwoDecimals() {
        assertThat(Amounts.positive(new BigDecimal("12.5"))).hasToString("12.50");
        assertThat(Amounts.positive(new BigDecimal("7"))).hasToString("7.00");
        assertThat(Amounts.positive(new BigDecimal("3.100"))).hasToString("3.10");
    }

    @Test
    void rejectsZeroNegativeAndFractionsOfCents() {
        assertThatThrownBy(() -> Amounts.positive(BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Amounts.positive(new BigDecimal("-1"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Amounts.positive(new BigDecimal("0.001"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Amounts.positive(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void formatsMoneyInSpanish() {
        assertThat(Money.format(new BigDecimal("1234.5"))).isEqualTo("1.234,50 €").doesNotContain("\u00a0");
        assertThat(Money.format(new BigDecimal("12345.67"))).isEqualTo("12.345,67 €");
    }

    @Test
    void hashesAndComparesInConstantTime() {
        String hash = Hashing.sha256("hola");
        assertThat(hash).hasSize(64);
        assertThat(Hashing.matches(hash, "hola")).isTrue();
        assertThat(Hashing.matches(hash, "adios")).isFalse();
    }
}
