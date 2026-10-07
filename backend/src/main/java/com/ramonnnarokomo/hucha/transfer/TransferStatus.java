package com.ramonnnarokomo.hucha.transfer;

public enum TransferStatus {
    /** Esperando el código de verificación (autenticación reforzada, PSD2). */
    PENDING_SCA,
    COMPLETED,
    REJECTED,
    EXPIRED,
    CANCELLED
}
