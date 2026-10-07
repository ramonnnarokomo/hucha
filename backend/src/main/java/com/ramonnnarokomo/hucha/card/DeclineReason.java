package com.ramonnnarokomo.hucha.card;

public enum DeclineReason {
    CARD_FROZEN("la tarjeta está congelada"),
    CARD_CANCELLED("la tarjeta está cancelada"),
    ONLINE_DISABLED("las compras online están desactivadas"),
    MONTHLY_LIMIT("supera el límite mensual de la tarjeta"),
    DAILY_LIMIT("supera el límite diario de la cuenta"),
    INSUFFICIENT_FUNDS("no hay saldo suficiente"),
    ACCOUNT_NOT_ACTIVE("la cuenta no está operativa");

    private final String description;

    DeclineReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
