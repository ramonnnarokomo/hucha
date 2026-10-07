package com.ramonnnarokomo.hucha.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.shared.RequestInfo;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Se guarda en la misma transacción que la operación: si la operación falla, no queda rastro falso. */
    @Transactional
    public void record(Actor actor, AuditAction action, String targetType, Object targetId, RequestInfo request, String details) {
        repository.save(new AuditEvent(
                clock.instant(),
                actor != null ? actor.id() : null,
                actor != null ? actor.email() : null,
                action,
                targetType,
                targetId != null ? targetId.toString() : null,
                request != null ? request.ipAddress() : null,
                details));
    }

    public record Actor(UUID id, String email) {
    }
}
