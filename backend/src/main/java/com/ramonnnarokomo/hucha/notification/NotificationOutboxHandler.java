package com.ramonnnarokomo.hucha.notification;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramonnnarokomo.hucha.outbox.DomainEvents;
import com.ramonnnarokomo.hucha.outbox.OutboxEvent;
import com.ramonnnarokomo.hucha.outbox.OutboxHandler;
import com.ramonnnarokomo.hucha.shared.Money;

/** Convierte eventos del outbox en avisos para el usuario. */
@Component
public class NotificationOutboxHandler implements OutboxHandler {

    private static final Set<String> SUPPORTED = Set.of(
            DomainEvents.TRANSFER_RECEIVED,
            DomainEvents.CARD_PURCHASE_DECLINED,
            DomainEvents.ACCOUNT_STATUS_CHANGED);

    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    public NotificationOutboxHandler(NotificationService notifications, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        switch (event.getEventType()) {
            case DomainEvents.TRANSFER_RECEIVED -> {
                var received = objectMapper.readValue(event.getPayload(), DomainEvents.TransferReceived.class);
                String body = received.concept() == null || received.concept().isBlank()
                        ? received.senderName() + " te ha enviado dinero."
                        : received.senderName() + ": «" + received.concept() + "»";
                notifications.notify(received.recipientUserId(), NotificationType.TRANSFER_RECEIVED,
                        "Has recibido " + Money.format(received.amount()), body);
            }
            case DomainEvents.CARD_PURCHASE_DECLINED -> {
                var declined = objectMapper.readValue(event.getPayload(), DomainEvents.CardPurchaseDeclined.class);
                notifications.notify(declined.userId(), NotificationType.CARD_DECLINED,
                        "Pago con tarjeta denegado",
                        Money.format(declined.amount()) + " en " + declined.merchant() + ": " + declined.reason());
            }
            case DomainEvents.ACCOUNT_STATUS_CHANGED -> {
                var changed = objectMapper.readValue(event.getPayload(), DomainEvents.AccountStatusChanged.class);
                if (changed.frozen()) {
                    notifications.notify(changed.userId(), NotificationType.ACCOUNT_FROZEN,
                            "Cuenta congelada",
                            "Hemos congelado «" + changed.accountName() + "». Escríbenos si no reconoces este cambio.");
                } else {
                    notifications.notify(changed.userId(), NotificationType.ACCOUNT_UNFROZEN,
                            "Cuenta reactivada", "«" + changed.accountName() + "» vuelve a estar operativa.");
                }
            }
            default -> throw new IllegalArgumentException("Evento no soportado: " + event.getEventType());
        }
    }
}
