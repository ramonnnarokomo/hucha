package com.ramonnnarokomo.hucha.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Guarda el evento en la misma transacción que el cambio de negocio (patrón transactional outbox). */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String eventType, UUID aggregateId, Object payload) {
        try {
            repository.save(new OutboxEvent(eventType, aggregateId, objectMapper.writeValueAsString(payload), clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + eventType, e);
        }
    }
}
