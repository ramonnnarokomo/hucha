package com.ramonnnarokomo.hucha.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

/** Importes en euros: siempre BigDecimal con dos decimales, nunca double. */
public final class Amounts {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Amounts() {
    }

    /** Importe positivo con como mucho dos decimales, devuelto con escala 2. */
    public static BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.unprocessable(ErrorCode.INVALID_AMOUNT, "El importe tiene que ser mayor que cero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.unprocessable(ErrorCode.INVALID_AMOUNT, "El importe no puede tener más de dos decimales.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN);
    }
}
