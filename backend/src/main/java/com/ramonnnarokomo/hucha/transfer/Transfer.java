package com.ramonnnarokomo.hucha.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    @Column(name = "to_iban", nullable = false)
    private String toIban;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(nullable = false)
    private BigDecimal amount;

    private String concept;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "sca_required", nullable = false)
    private boolean scaRequired;

    @Column(name = "sca_code_hash")
    private String scaCodeHash;

    @Column(name = "sca_attempts", nullable = false)
    private int scaAttempts;

    @Column(name = "sca_expires_at")
    private Instant scaExpiresAt;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transfer() {
    }

    public Transfer(UUID createdBy, UUID fromAccountId, UUID toAccountId, String toIban, String beneficiaryName,
                    BigDecimal amount, String concept, String idempotencyKey, String requestHash, Instant now) {
        this.id = UUID.randomUUID();
        this.createdBy = createdBy;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.toIban = toIban;
        this.beneficiaryName = beneficiaryName.trim();
        this.amount = amount;
        this.concept = concept == null || concept.isBlank() ? null : concept.trim();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = TransferStatus.PENDING_SCA;
        this.createdAt = now;
    }

    public void requireSca(String codeHash, Instant expiresAt) {
        this.scaRequired = true;
        this.scaCodeHash = codeHash;
        this.scaExpiresAt = expiresAt;
    }

    public boolean isScaExpired(Instant now) {
        return scaExpiresAt != null && !now.isBefore(scaExpiresAt);
    }

    /** Suma un intento fallido de código. Devuelve los que quedan; al agotarlos la rechaza. */
    public int registerFailedScaAttempt(int maxAttempts) {
        scaAttempts++;
        int remaining = maxAttempts - scaAttempts;
        if (remaining <= 0) {
            reject(ErrorCode.SCA_TOO_MANY_ATTEMPTS);
        }
        return Math.max(remaining, 0);
    }

    public void complete(UUID journalEntryId, Instant now) {
        this.status = TransferStatus.COMPLETED;
        this.journalEntryId = journalEntryId;
        this.completedAt = now;
        this.scaCodeHash = null;
    }

    public void reject(ErrorCode reason) {
        this.status = TransferStatus.REJECTED;
        this.rejectionReason = reason.name();
        this.scaCodeHash = null;
    }

    public void expire() {
        this.status = TransferStatus.EXPIRED;
        this.scaCodeHash = null;
    }

    public void cancel() {
        this.status = TransferStatus.CANCELLED;
        this.scaCodeHash = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public String getToIban() {
        return toIban;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getConcept() {
        return concept;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public boolean isScaRequired() {
        return scaRequired;
    }

    public String getScaCodeHash() {
        return scaCodeHash;
    }

    public int getScaAttempts() {
        return scaAttempts;
    }

    public Instant getScaExpiresAt() {
        return scaExpiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
