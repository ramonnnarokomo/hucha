package com.ramonnnarokomo.hucha.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Asiento que se quiere registrar. Se construye con {@link #builder}. */
public record LedgerEntryRequest(
        EntryType type,
        String description,
        Instant occurredAt,
        UUID referenceId,
        List<Leg> legs) {

    public record Leg(UUID accountId, BigDecimal amount, Category category,
                      String counterpartyName, String counterpartyIban, String concept) {
    }

    public static Builder builder(EntryType type, String description, Instant occurredAt) {
        return new Builder(type, description, occurredAt);
    }

    public static final class Builder {
        private final EntryType type;
        private final String description;
        private final Instant occurredAt;
        private UUID referenceId;
        private final List<Leg> legs = new ArrayList<>();

        private Builder(EntryType type, String description, Instant occurredAt) {
            this.type = type;
            this.description = description;
            this.occurredAt = occurredAt;
        }

        public Builder reference(UUID referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder leg(UUID accountId, BigDecimal amount, Category category,
                           String counterpartyName, String counterpartyIban, String concept) {
            legs.add(new Leg(accountId, amount, category, counterpartyName, counterpartyIban, concept));
            return this;
        }

        public LedgerEntryRequest build() {
            return new LedgerEntryRequest(type, description, occurredAt, referenceId, List.copyOf(legs));
        }
    }
}
