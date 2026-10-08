package com.ramonnnarokomo.hucha.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.Posting;

/** Un movimiento del extracto: un apunte del libro mayor visto desde la cuenta. */
public record MovementView(
        Long id,
        UUID entryId,
        Instant occurredAt,
        EntryType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Category category,
        String counterpartyName,
        String counterpartyIban,
        String concept) {

    public static MovementView of(Posting posting) {
        return new MovementView(posting.getId(), posting.getEntryId(), posting.getOccurredAt(), posting.getEntryType(),
                posting.getAmount(), posting.getBalanceAfter(), posting.getCategory(), posting.getCounterpartyName(),
                posting.getCounterpartyIban(), posting.getConcept());
    }
}
