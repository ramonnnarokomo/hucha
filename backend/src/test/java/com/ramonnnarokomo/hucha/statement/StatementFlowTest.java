package com.ramonnnarokomo.hucha.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.support.IntegrationTest;

class StatementFlowTest extends IntegrationTest {

    @Test
    void movementsCanBeFilteredAndExported() throws Exception {
        Session ana = register("Ana Extracto");
        Account main = mainAccount(ana);
        String card = read(mvc.perform(as(ana, get("/api/cards"))).andReturn()).get(0).get("id").asText();
        for (String[] purchase : new String[][] {{"Frutería Martín", "GROCERIES", "12.40"}, {"Sushi Kenzo", "RESTAURANTS", "38.00"}}) {
            mvc.perform(as(ana, post("/api/cards/" + card + "/simulate-purchase"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"merchant": "%s", "category": "%s", "amount": %s, "online": false}
                                    """.formatted((Object[]) purchase)))
                    .andExpect(status().isOk());
        }

        mvc.perform(as(ana, get("/api/accounts/" + main.getId() + "/movements")))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].counterpartyName").value("Sushi Kenzo"))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(949.60));
        mvc.perform(as(ana, get("/api/accounts/" + main.getId() + "/movements").param("q", "frutería")))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(as(ana, get("/api/accounts/" + main.getId() + "/movements").param("direction", "IN")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].type").value("WELCOME_GIFT"));
        mvc.perform(as(ana, get("/api/accounts/" + main.getId() + "/movements").param("category", "RESTAURANTS")))
                .andExpect(jsonPath("$.items[0].amount").value(-38.00));

        byte[] csv = mvc.perform(as(ana, get("/api/accounts/" + main.getId() + "/statement"))
                        .param("month", "2026-10").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"extracto-2026-10.csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Fecha;Concepto;Contraparte").contains("Sushi Kenzo").contains("-38,00");

        byte[] pdf = mvc.perform(as(ana, get("/api/accounts/" + main.getId() + "/statement")).param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void anotherUserCannotReadMyMovements() throws Exception {
        Session ana = register("Ana Privada");
        Session spy = register("Espía");
        mvc.perform(as(spy, get("/api/accounts/" + mainAccount(ana).getId() + "/movements")))
                .andExpect(status().isNotFound());
    }
}
