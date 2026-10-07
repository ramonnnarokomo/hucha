package com.ramonnnarokomo.hucha.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ramonnnarokomo.hucha.ledger.Category;

/** Intento de pago con tarjeta: aprobado (con su asiento contable) o denegado (con el motivo). */
@Entity
@Table(name = "card_transactions")
public class CardTransaction {

    @Id
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(nullable = false)
    private String merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean online;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardTransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "decline_reason")
    private DeclineReason declineReason;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CardTransaction() {
    }

    private CardTransaction(UUID cardId, String merchant, Category category, BigDecimal amount, boolean online,
                            Instant now) {
        this.id = UUID.randomUUID();
        this.cardId = cardId;
        this.merchant = merchant;
        this.category = category;
        this.amount = amount;
        this.online = online;
        this.createdAt = now;
    }

    public static CardTransaction approved(UUID cardId, String merchant, Category category, BigDecimal amount,
                                           boolean online, UUID journalEntryId, Instant now) {
        CardTransaction tx = new CardTransaction(cardId, merchant, category, amount, online, now);
        tx.status = CardTransactionStatus.APPROVED;
        tx.journalEntryId = journalEntryId;
        return tx;
    }

    public static CardTransaction declined(UUID cardId, String merchant, Category category, BigDecimal amount,
                                           boolean online, DeclineReason reason, Instant now) {
        CardTransaction tx = new CardTransaction(cardId, merchant, category, amount, online, now);
        tx.status = CardTransactionStatus.DECLINED;
        tx.declineReason = reason;
        return tx;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public String getMerchant() {
        return merchant;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isOnline() {
        return online;
    }

    public CardTransactionStatus getStatus() {
        return status;
    }

    public DeclineReason getDeclineReason() {
        return declineReason;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
