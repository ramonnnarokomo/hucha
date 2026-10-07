package com.ramonnnarokomo.hucha.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("""
            select t from RefreshToken t
            where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now
            order by t.lastUsedAt desc
            """)
    List<RefreshToken> findActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<RefreshToken> findFirstByFamilyIdOrderByCreatedAtAsc(UUID familyId);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.userId = :userId and t.revokedAt is null and t.familyId <> :keepFamilyId
            """)
    int revokeAllForUserExcept(@Param("userId") UUID userId, @Param("keepFamilyId") UUID keepFamilyId,
                               @Param("now") Instant now);
}
