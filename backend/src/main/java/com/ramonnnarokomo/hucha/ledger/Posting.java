package com.ramonnnarokomo.hucha.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Apunte de un asiento sobre una cuenta: positivo entra dinero, negativo sale.
 * Guarda el saldo resultante para poder pintar el extracto sin recalcular.
 */
@Entity
@Table(name = "postings")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "counterparty_iban")
    private String counterpartyIban;

    private String concept;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected Posting() {
    }

    Posting(JournalEntry entry, UUID accountId, BigDecimal amount, BigDecimal balanceAfter, Category category,
            String counterpartyName, String counterpartyIban, String concept) {
        this.entryId = entry.getId();
        this.entryType = entry.getType();
        this.occurredAt = entry.getOccurredAt();
        this.accountId = accountId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.category = category;
        this.counterpartyName = counterpartyName;
        this.counterpartyIban = counterpartyIban;
        this.concept = concept;
    }

    public Long getId() {
        return id;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Category getCategory() {
        return category;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public String getCounterpartyIban() {
        return counterpartyIban;
    }

    public String getConcept() {
        return concept;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
