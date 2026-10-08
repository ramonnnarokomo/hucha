package com.ramonnnarokomo.hucha.statement;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * CSV pensado para abrirse en Excel en español: separador ';', coma decimal y BOM UTF-8.
 * Los campos que empiezan por = + - @ se escapan para evitar inyección de fórmulas.
 */
@Component
public class StatementCsv {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] render(StatementService.Statement statement, ZoneId zone) {
        List<String> rows = new ArrayList<>();
        rows.add(String.join(";", "Fecha", "Concepto", "Contraparte", "IBAN contraparte", "Categoría", "Importe", "Saldo"));
        for (MovementView movement : statement.movements()) {
            rows.add(String.join(";",
                    escape(DATE.format(movement.occurredAt().atZone(zone))),
                    escape(movement.concept()),
                    escape(movement.counterpartyName()),
                    escape(movement.counterpartyIban()),
                    escape(movement.category() != null ? movement.category().name() : ""),
                    decimal(movement.amount()),
                    decimal(movement.balanceAfter())));
        }
        return ("﻿" + String.join("\r\n", rows) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.matches("^[=+\\-@\\t\\r].*") ? "'" + value : value;
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String decimal(BigDecimal amount) {
        return amount.toPlainString().replace('.', ',');
    }
}
