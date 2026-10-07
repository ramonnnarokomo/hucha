package com.ramonnnarokomo.hucha.notification;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conexiones Server-Sent Events abiertas por usuario. Cada pestaña del navegador es un emisor.
 * Con varias instancias del backend haría falta un bus (Redis pub/sub, Kafka...) para repartir los avisos.
 */
@Component
public class SseHub {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> userEmitters = emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        userEmitters.add(emitter);
        Runnable remove = () -> userEmitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        send(emitter, SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    public void push(UUID userId, NotificationView notification) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, List.of())) {
            send(emitter, SseEmitter.event()
                    .name("notification")
                    .id(notification.id().toString())
                    .data(notification, MediaType.APPLICATION_JSON));
        }
    }

    public int connectedClients() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    /** Comentario cada 25 s para que proxies y balanceadores no corten la conexión por inactividad. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.values().forEach(list -> list.forEach(emitter -> send(emitter, SseEmitter.event().comment("ping"))));
    }

    private static void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
