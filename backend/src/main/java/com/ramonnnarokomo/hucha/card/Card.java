package com.ramonnnarokomo.hucha.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tarjeta de débito virtual. Del número solo se guardan los cuatro últimos dígitos. */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(nullable = false)
    private String last4;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled;

    @Column(name = "monthly_limit", nullable = false)
    private BigDecimal monthlyLimit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Card() {
    }

    public Card(UUID accountId, UUID ownerId, String holderName, String cardNumber, BigDecimal monthlyLimit, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.ownerId = ownerId;
        this.holderName = holderName.toUpperCase(Locale.ROOT);
        this.last4 = cardNumber.substring(cardNumber.length() - 4);
        YearMonth expiry = YearMonth.from(now.atOffset(ZoneOffset.UTC)).plusYears(4);
        this.expiryMonth = expiry.getMonthValue();
        this.expiryYear = expiry.getYear();
        this.status = CardStatus.ACTIVE;
        this.onlineEnabled = true;
        this.monthlyLimit = monthlyLimit;
        this.createdAt = now;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public void freeze() {
        if (status == CardStatus.ACTIVE) {
            status = CardStatus.FROZEN;
        }
    }

    public void unfreeze() {
        if (status == CardStatus.FROZEN) {
            status = CardStatus.ACTIVE;
        }
    }

    public void cancel() {
        status = CardStatus.CANCELLED;
    }

    public void setOnlineEnabled(boolean onlineEnabled) {
        this.onlineEnabled = onlineEnabled;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getLast4() {
        return last4;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public CardStatus getStatus() {
        return status;
    }

    public boolean isOnlineEnabled() {
        return onlineEnabled;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
