package com.ramonnnarokomo.hucha.payee;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Beneficiario guardado. Las transferencias a beneficiarios de confianza piden menos verificaciones. */
@Entity
@Table(name = "payees")
public class Payee {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String iban;

    private String alias;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payee() {
    }

    public Payee(UUID ownerId, String name, String iban, String alias, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name.trim();
        this.iban = iban;
        this.alias = alias == null || alias.isBlank() ? null : alias.trim();
        this.createdAt = now;
    }

    public void rename(String name, String alias) {
        this.name = name.trim();
        this.alias = alias == null || alias.isBlank() ? null : alias.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getIban() {
        return iban;
    }

    public String getAlias() {
        return alias;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
