package com.ramonnnarokomo.hucha.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;

/** Límite diario de salidas (transferencias a terceros + pagos con tarjeta) de una cuenta. */
@Component
public class SpendingLimits {

    private final PostingRepository postings;
    private final HuchaProperties properties;

    public SpendingLimits(PostingRepository postings, HuchaProperties properties) {
        this.postings = postings;
        this.properties = properties;
    }

    /** Lo que aún se puede gastar hoy (día natural en hora de Madrid). Sin límite configurado, null. */
    public BigDecimal remainingToday(Account account, Instant now) {
        if (account.getDailyLimit() == null) {
            return null;
        }
        Instant startOfDay = LocalDate.ofInstant(now, properties.zone()).atStartOfDay(properties.zone()).toInstant();
        BigDecimal spent = postings.sumOutgoingSince(account.getId(), EntryType.SPENDING, startOfDay).negate();
        return account.getDailyLimit().subtract(spent).max(BigDecimal.ZERO);
    }

    public boolean allows(Account account, BigDecimal amount, Instant now) {
        BigDecimal remaining = remainingToday(account, now);
        return remaining == null || amount.compareTo(remaining) <= 0;
    }
}
