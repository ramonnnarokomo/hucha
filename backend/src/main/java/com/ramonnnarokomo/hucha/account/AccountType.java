package com.ramonnnarokomo.hucha.account;

public enum AccountType {
    /** Cuenta corriente: recibe la nómina, tiene tarjeta y hace transferencias. */
    CHECKING,
    /** Hucha de ahorro con objetivo opcional. */
    SAVINGS,
    /** Cuenta interna del banco (contrapartida SEPA, tarjetas, fondos propios). */
    SYSTEM
}
