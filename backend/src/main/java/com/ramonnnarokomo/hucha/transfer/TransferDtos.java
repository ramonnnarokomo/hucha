package com.ramonnnarokomo.hucha.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record CreateTransferRequest(
            @NotNull UUID fromAccountId,
            @NotBlank @Size(max = 42) String toIban,
            @NotBlank @Size(max = 120) String beneficiaryName,
            @NotNull BigDecimal amount,
            @Size(max = 140) String concept) {
    }

    public record ConfirmTransferRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "Son 6 dígitos") String code) {
    }

    public record TransferView(
            UUID id,
            UUID fromAccountId,
            String toIban,
            String beneficiaryName,
            BigDecimal amount,
            String concept,
            TransferStatus status,
            boolean internal,
            boolean scaRequired,
            Instant scaExpiresAt,
            String rejectionReason,
            Instant createdAt,
            Instant completedAt) {

        public static TransferView of(Transfer transfer) {
            return new TransferView(transfer.getId(), transfer.getFromAccountId(), transfer.getToIban(),
                    transfer.getBeneficiaryName(), transfer.getAmount(), transfer.getConcept(), transfer.getStatus(),
                    transfer.getToAccountId() != null, transfer.isScaRequired(),
                    transfer.getStatus() == TransferStatus.PENDING_SCA ? transfer.getScaExpiresAt() : null,
                    transfer.getRejectionReason(), transfer.getCreatedAt(), transfer.getCompletedAt());
        }
    }
}
