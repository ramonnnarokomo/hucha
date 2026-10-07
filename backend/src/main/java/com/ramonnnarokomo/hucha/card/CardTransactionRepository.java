package com.ramonnnarokomo.hucha.card;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID> {

    Page<CardTransaction> findByCardIdOrderByCreatedAtDesc(UUID cardId, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amount), 0) from CardTransaction t
            where t.cardId = :cardId and t.status = :status and t.createdAt >= :from
            """)
    BigDecimal sumSince(@Param("cardId") UUID cardId, @Param("status") CardTransactionStatus status,
                        @Param("from") Instant from);

    long countByStatusAndCreatedAtGreaterThanEqual(CardTransactionStatus status, Instant from);
}
