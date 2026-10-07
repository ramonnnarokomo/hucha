package com.ramonnnarokomo.hucha.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(UUID id, NotificationType type, String title, String body, boolean read, Instant createdAt) {

    public static NotificationView of(Notification notification) {
        return new NotificationView(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getBody(), notification.getReadAt() != null, notification.getCreatedAt());
    }
}
