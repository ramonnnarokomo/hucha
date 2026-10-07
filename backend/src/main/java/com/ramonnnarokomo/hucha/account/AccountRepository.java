package com.ramonnnarokomo.hucha.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    List<Account> findByOwnerIdAndTypeOrderByCreatedAtAsc(UUID ownerId, AccountType type);

    Optional<Account> findByIban(String iban);

    boolean existsByIban(String iban);

    long countByType(AccountType type);

    long countByOwnerIdAndTypeAndStatusNot(UUID ownerId, AccountType type, AccountStatus status);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.type <> :excluded")
    BigDecimal totalBalanceExcluding(@Param("excluded") AccountType excluded);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.ownerId = :ownerId and a.status <> :excluded")
    BigDecimal totalBalanceOfOwner(@Param("ownerId") UUID ownerId, @Param("excluded") AccountStatus excluded);
}
