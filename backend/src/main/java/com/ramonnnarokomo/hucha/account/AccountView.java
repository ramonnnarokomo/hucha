package com.ramonnnarokomo.hucha.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountView(
        UUID id,
        String iban,
        AccountType type,
        String name,
        AccountStatus status,
        BigDecimal balance,
        String currency,
        BigDecimal dailyLimit,
        BigDecimal goalAmount,
        String icon,
        Instant createdAt) {

    public static AccountView of(Account account) {
        return new AccountView(account.getId(), account.getIban(), account.getType(), account.getName(),
                account.getStatus(), account.getBalance(), account.getCurrency(), account.getDailyLimit(),
                account.getGoalAmount(), account.getIcon(), account.getCreatedAt());
    }
}
