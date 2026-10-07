package com.ramonnnarokomo.hucha.outbox;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Procesa cada evento en su propia transacción: si uno falla se deshace solo ese,
 * se apunta el error y los demás siguen su curso.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository repository;
    private final List<OutboxHandler> handlers;
    private final Clock clock;

    public OutboxProcessor(OutboxEventRepository repository, List<OutboxHandler> handlers, Clock clock) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void process(UUID eventId) throws Exception {
        OutboxEvent event = repository.findById(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null) {
            return;
        }
        for (OutboxHandler handler : handlers) {
            if (handler.supports(event.getEventType())) {
                handler.handle(event);
            }
        }
        event.markPublished(clock.instant());
    }

    /** Se llama en otra transacción, después de que la del evento se haya deshecho. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID eventId, Exception error) {
        log.warn("Fallo al publicar el evento {}: {}", eventId, error.getMessage());
        repository.findById(eventId).ifPresent(event -> event.markFailed(error.getMessage()));
    }
}
