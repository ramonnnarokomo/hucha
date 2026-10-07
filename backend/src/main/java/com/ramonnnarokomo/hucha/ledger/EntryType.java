package com.ramonnnarokomo.hucha.ledger;

import java.util.EnumSet;
import java.util.Set;

public enum EntryType {
    TRANSFER,
    /** Transferencia entre cuentas del mismo cliente. */
    OWN_TRANSFER,
    INCOMING_TRANSFER,
    CARD_PAYMENT,
    SAVINGS_MOVE,
    WELCOME_GIFT;

    /** Movimientos que cuentan para el límite diario de salidas. */
    public static final Set<EntryType> SPENDING = EnumSet.of(TRANSFER, CARD_PAYMENT);

    /** Movimientos de dinero entre cuentas propias: no son ni ingresos ni gastos. */
    public static final Set<EntryType> INTERNAL = EnumSet.of(OWN_TRANSFER, SAVINGS_MOVE);
}
