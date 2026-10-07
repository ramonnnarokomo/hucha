package com.ramonnnarokomo.hucha.payee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayeeRepository extends JpaRepository<Payee, UUID> {

    List<Payee> findByOwnerIdOrderByNameAsc(UUID ownerId);

    Optional<Payee> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndIban(UUID ownerId, String iban);
}
