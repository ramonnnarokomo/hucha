package com.ramonnnarokomo.hucha.statement;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ramonnnarokomo.hucha.ledger.Posting;

/** Consulta dinámica con Criteria API: cada filtro se añade solo si viene informado. */
final class MovementSpecifications {

    private MovementSpecifications() {
    }

    static Specification<Posting> of(UUID accountId, MovementFilter filter, ZoneId zone) {
        Specification<Posting> spec = (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from().atStartOfDay(zone).toInstant()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThan(root.get("occurredAt"), filter.to().plusDays(1).atStartOfDay(zone).toInstant()));
        }
        if (filter.direction() == MovementFilter.Direction.IN) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("amount"), BigDecimal.ZERO));
        } else if (filter.direction() == MovementFilter.Direction.OUT) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("amount"), BigDecimal.ZERO));
        }
        if (filter.category() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), filter.category()));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String pattern = "%" + filter.q().trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("concept")), pattern, '\\'),
                    cb.like(cb.lower(root.get("counterpartyName")), pattern, '\\')));
        }
        return spec;
    }
}
