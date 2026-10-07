package com.ramonnnarokomo.hucha.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hucha.outbox.relay-enabled", havingValue = "true")
public class OutboxScheduler {

    private final OutboxRelay relay;

    public OutboxScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${hucha.outbox.relay-interval-ms}")
    public void relay() {
        relay.publishPending();
    }
}
