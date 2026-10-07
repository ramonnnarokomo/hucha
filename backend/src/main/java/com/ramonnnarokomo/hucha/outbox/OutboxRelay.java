package com.ramonnnarokomo.hucha.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Lee los eventos pendientes y los entrega a sus consumidores. Hoy los consumidores están en el
 * mismo proceso (notificaciones); cambiar el destino a Kafka o RabbitMQ no tocaría el código de negocio.
 */
@Component
public class OutboxRelay {

    static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository repository;
    private final OutboxProcessor processor;
    private final Counter published;

    public OutboxRelay(OutboxEventRepository repository, OutboxProcessor processor, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.processor = processor;
        this.published = Counter.builder("hucha.outbox.processed").description("Eventos del outbox procesados")
                .register(meterRegistry);
    }

    /** Devuelve cuántos eventos ha procesado. */
    public int publishPending() {
        List<UUID> ids = repository.findPendingIds(MAX_ATTEMPTS, PageRequest.of(0, 100));
        for (UUID id : ids) {
            try {
                processor.process(id);
                published.increment();
            } catch (Exception e) {
                processor.recordFailure(id, e);
            }
        }
        return ids.size();
    }

    public long pendingCount() {
        return repository.countByPublishedAtIsNull();
    }
}
