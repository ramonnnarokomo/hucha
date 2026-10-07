package com.ramonnnarokomo.hucha.notification;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.error.ApiException;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final SseHub hub;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, SseHub hub, Clock clock) {
        this.repository = repository;
        this.hub = hub;
        this.clock = clock;
    }

    /** Guarda el aviso y lo empuja por SSE solo cuando la transacción se confirma. */
    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String body) {
        Notification notification = repository.save(new Notification(userId, type, title, body, clock.instant()));
        NotificationView view = NotificationView.of(notification);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    hub.push(userId, view);
                }
            });
        } else {
            hub.push(userId, view);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(UUID userId, int page, int size) {
        return PageResponse.of(
                repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 50))),
                NotificationView::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notificación no encontrada."))
                .markRead(clock.instant());
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, clock.instant());
    }
}
