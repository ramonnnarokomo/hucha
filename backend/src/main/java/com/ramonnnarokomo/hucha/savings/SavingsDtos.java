package com.ramonnnarokomo.hucha.savings;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SavingsDtos {

    /** Iconos que el front sabe pintar. */
    static final String ICONS = "piggy|plane|home|car|gift|heart|study|umbrella|star";

    private SavingsDtos() {
    }

    public record SavingsRequest(
            @NotBlank @Size(max = 40) String name,
            @DecimalMin("1.00") @DecimalMax("1000000.00") BigDecimal goalAmount,
            @Pattern(regexp = ICONS) String icon) {
    }

    public record MoveMoneyRequest(@NotNull UUID accountId, @NotNull BigDecimal amount) {
    }
}
