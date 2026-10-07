package com.ramonnnarokomo.hucha.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.ramonnnarokomo.hucha.shared.Amounts;

@Entity
@Table(name = "accounts")
public class Account {

    public static final UUID SEPA_CLEARING = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID CARD_SETTLEMENT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID BANK_FUNDS = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String iban;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    @Column(name = "daily_limit")
    private BigDecimal dailyLimit;

    @Column(name = "goal_amount")
    private BigDecimal goalAmount;

    private String icon;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Account() {
    }

    private Account(String iban, UUID ownerId, AccountType type, String name, Instant now) {
        this.id = UUID.randomUUID();
        this.iban = iban;
        this.ownerId = ownerId;
        this.type = type;
        this.name = name;
        this.status = AccountStatus.ACTIVE;
        this.balance = Amounts.ZERO;
        this.currency = "EUR";
        this.createdAt = now;
    }

    public static Account checking(String iban, UUID ownerId, BigDecimal dailyLimit, Instant now) {
        Account account = new Account(iban, ownerId, AccountType.CHECKING, "Cuenta principal", now);
        account.dailyLimit = dailyLimit;
        return account;
    }

    public static Account savings(String iban, UUID ownerId, String name, BigDecimal goalAmount, String icon, Instant now) {
        Account account = new Account(iban, ownerId, AccountType.SAVINGS, name.trim(), now);
        account.goalAmount = goalAmount;
        account.icon = icon;
        return account;
    }

    public boolean isSystem() {
        return type == AccountType.SYSTEM;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Las cuentas de clientes nunca quedan en negativo; las internas del banco sí pueden. */
    public boolean canHaveBalance(BigDecimal newBalance) {
        return isSystem() || newBalance.signum() >= 0;
    }

    /**
     * Solo la llama el libro mayor, después de comprobar que el asiento cuadra
     * (lo vigila un test de ArchUnit).
     */
    public void applyPosting(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void freeze() {
        this.status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        this.status = AccountStatus.ACTIVE;
    }

    public void close() {
        this.status = AccountStatus.CLOSED;
    }

    public void updateSavings(String name, BigDecimal goalAmount, String icon) {
        this.name = name.trim();
        this.goalAmount = goalAmount;
        this.icon = icon;
    }

    public UUID getId() {
        return id;
    }

    public String getIban() {
        return iban;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public AccountType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public BigDecimal getGoalAmount() {
        return goalAmount;
    }

    public String getIcon() {
        return icon;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
