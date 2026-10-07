package com.ramonnnarokomo.hucha.outbox;

/** Consumidor de un tipo de evento del outbox. */
public interface OutboxHandler {

    boolean supports(String eventType);

    void handle(OutboxEvent event) throws Exception;
}
