package com.ramonnnarokomo.hucha.statement;

import java.time.LocalDate;

import com.ramonnnarokomo.hucha.ledger.Category;

/** Filtros opcionales del extracto. {@code direction}: IN (entradas) u OUT (salidas). */
public record MovementFilter(LocalDate from, LocalDate to, String q, Direction direction, Category category) {

    public enum Direction {
        IN,
        OUT
    }
}
