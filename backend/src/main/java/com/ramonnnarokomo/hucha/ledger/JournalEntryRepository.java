package com.ramonnnarokomo.hucha.ledger;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
}
