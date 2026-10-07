package com.ramonnnarokomo.hucha.shared;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Formato de importes en español para los textos que genera el backend (avisos, PDF). */
public final class Money {

    private static final Locale SPAIN = Locale.forLanguageTag("es-ES");

    private Money() {
    }

    public static String format(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(SPAIN);
        return format.format(amount).replace(' ', ' ');
    }
}
