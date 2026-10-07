package com.ramonnnarokomo.hucha.account;

public enum AccountStatus {
    ACTIVE,
    /** Congelada por un administrador: no puede enviar ni recibir dinero. */
    FROZEN,
    CLOSED
}
