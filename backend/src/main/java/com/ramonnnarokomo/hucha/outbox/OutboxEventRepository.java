package com.ramonnnarokomo.hucha.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select e.id from OutboxEvent e
            where e.publishedAt is null and e.attempts < :maxAttempts
            order by e.createdAt
            """)
    List<UUID> findPendingIds(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    long countByPublishedAtIsNull();
}
