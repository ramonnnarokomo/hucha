package com.ramonnnarokomo.hucha.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ramonnnarokomo.hucha.ledger.Category;

public final class CardDtos {

    private CardDtos() {
    }

    public record CardView(UUID id, UUID accountId, String holderName, String last4, int expiryMonth, int expiryYear,
                           CardStatus status, boolean onlineEnabled, BigDecimal monthlyLimit,
                           BigDecimal spentThisMonth, Instant createdAt) {
    }

    /** Cambios parciales: solo se aplican los campos que llegan. */
    public record UpdateCardRequest(
            Boolean frozen,
            Boolean onlineEnabled,
            @DecimalMin("50.00") @DecimalMax("10000.00") BigDecimal monthlyLimit) {
    }

    /** Pago de prueba, para ver la autorización en acción sin una pasarela real. */
    public record SimulatePurchaseRequest(
            @NotBlank @Size(max = 120) String merchant,
            @NotNull Category category,
            @NotNull BigDecimal amount,
            boolean online) {
    }

    public record CardTransactionView(UUID id, String merchant, Category category, BigDecimal amount, boolean online,
                                      CardTransactionStatus status, DeclineReason declineReason,
                                      String declineMessage, Instant createdAt) {

        public static CardTransactionView of(CardTransaction tx) {
            return new CardTransactionView(tx.getId(), tx.getMerchant(), tx.getCategory(), tx.getAmount(), tx.isOnline(),
                    tx.getStatus(), tx.getDeclineReason(),
                    tx.getDeclineReason() != null ? tx.getDeclineReason().description() : null,
                    tx.getCreatedAt());
        }
    }
}
