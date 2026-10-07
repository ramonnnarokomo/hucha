package com.ramonnnarokomo.hucha.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditView(UUID id, Instant occurredAt, String actorEmail, AuditAction action,
                        String targetType, String targetId, String ipAddress, String details) {

    public static AuditView of(AuditEvent event) {
        return new AuditView(event.getId(), event.getOccurredAt(), event.getActorEmail(), event.getAction(),
                event.getTargetType(), event.getTargetId(), event.getIpAddress(), event.getDetails());
    }
}
