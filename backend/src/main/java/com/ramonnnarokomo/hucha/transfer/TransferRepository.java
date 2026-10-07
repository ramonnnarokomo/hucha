package com.ramonnnarokomo.hucha.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByCreatedByAndIdempotencyKey(UUID createdBy, String idempotencyKey);

    Optional<Transfer> findByIdAndCreatedBy(UUID id, UUID createdBy);

    Page<Transfer> findByCreatedByOrderByCreatedAtDesc(UUID createdBy, Pageable pageable);

    long countByStatusAndCompletedAtGreaterThanEqual(TransferStatus status, Instant from);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t
            where t.status = :status and t.completedAt >= :from and t.completedAt < :to
            """)
    BigDecimal volumeBetween(@Param("status") TransferStatus status, @Param("from") Instant from,
                             @Param("to") Instant to);
}
