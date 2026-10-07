package com.ramonnnarokomo.hucha.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, Long>, JpaSpecificationExecutor<Posting> {

    List<Posting> findByEntryIdOrderByIdAsc(UUID entryId);

    List<Posting> findByAccountIdInAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(
            Collection<UUID> accountIds, Instant from);

    List<Posting> findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
            UUID accountId, Instant from, Instant to);

    Optional<Posting> findFirstByAccountIdAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(UUID accountId, Instant before);

    /** Suma (en negativo) de lo que ha salido de la cuenta desde {@code from} en los tipos indicados. */
    @Query("""
            select coalesce(sum(p.amount), 0) from Posting p
            where p.accountId = :accountId and p.amount < 0
              and p.entryType in :types and p.occurredAt >= :from
            """)
    BigDecimal sumOutgoingSince(@Param("accountId") UUID accountId,
                                @Param("types") Collection<EntryType> types,
                                @Param("from") Instant from);

    @Query("select coalesce(sum(p.amount), 0) from Posting p")
    BigDecimal sumOfAllPostings();

    @Query("select coalesce(sum(p.amount), 0) from Posting p where p.accountId = :accountId")
    BigDecimal sumForAccount(@Param("accountId") UUID accountId);
}
