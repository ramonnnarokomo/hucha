package com.ramonnnarokomo.hucha.card;

public enum CardStatus {
    ACTIVE,
    /** Congelada por el titular: se puede reactivar. */
    FROZEN,
    /** Cancelada: es definitivo. */
    CANCELLED
}
