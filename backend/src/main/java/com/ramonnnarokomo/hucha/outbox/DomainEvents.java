package com.ramonnnarokomo.hucha.outbox;

import java.math.BigDecimal;
import java.util.UUID;

/** Eventos que se publican a través del outbox. Se guardan como JSON. */
public final class DomainEvents {

    public static final String TRANSFER_RECEIVED = "TRANSFER_RECEIVED";
    public static final String CARD_PURCHASE_DECLINED = "CARD_PURCHASE_DECLINED";
    public static final String ACCOUNT_STATUS_CHANGED = "ACCOUNT_STATUS_CHANGED";

    private DomainEvents() {
    }

    public record TransferReceived(UUID transferId, UUID recipientUserId, BigDecimal amount,
                                   String senderName, String concept) {
    }

    public record CardPurchaseDeclined(UUID cardTransactionId, UUID userId, String merchant,
                                       BigDecimal amount, String reason) {
    }

    public record AccountStatusChanged(UUID accountId, UUID userId, String accountName, boolean frozen) {
    }
}
