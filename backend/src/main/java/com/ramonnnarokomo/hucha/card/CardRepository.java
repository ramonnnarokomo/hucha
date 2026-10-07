package com.ramonnnarokomo.hucha.card;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    long countByOwnerIdAndStatusNot(UUID ownerId, CardStatus status);
}
