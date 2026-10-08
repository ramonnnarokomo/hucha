package com.ramonnnarokomo.hucha.statement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatementCsvTest {

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertThat(StatementCsv.escape("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(StatementCsv.escape("+34")).isEqualTo("'+34");
        assertThat(StatementCsv.escape("@SUM")).isEqualTo("'@SUM");
    }

    @Test
    void quotesSeparatorsAndQuotes() {
        assertThat(StatementCsv.escape("Cena; copas")).isEqualTo("\"Cena; copas\"");
        assertThat(StatementCsv.escape("El \"jefe\"")).isEqualTo("\"El \"\"jefe\"\"\"");
        assertThat(StatementCsv.escape(null)).isEmpty();
        assertThat(StatementCsv.escape("Nómina")).isEqualTo("Nómina");
    }
}
