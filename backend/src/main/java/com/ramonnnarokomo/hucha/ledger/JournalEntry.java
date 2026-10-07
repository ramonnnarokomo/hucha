package com.ramonnnarokomo.hucha.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Asiento contable: agrupa los apuntes de una misma operación, que siempre suman cero. */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType type;

    @Column(nullable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "reference_id")
    private UUID referenceId;

    protected JournalEntry() {
    }

    JournalEntry(EntryType type, String description, Instant occurredAt, UUID referenceId) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.description = description;
        this.occurredAt = occurredAt;
        this.referenceId = referenceId;
    }

    public UUID getId() {
        return id;
    }

    public EntryType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getReferenceId() {
        return referenceId;
    }
}
