package com.ramonnnarokomo.hucha.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.ramonnnarokomo.hucha.support.IntegrationTest;

class CardFlowTest extends IntegrationTest {

    @Test
    void approvedPurchasesChargeTheAccount() throws Exception {
        Session ana = register("Ana Tarjeta");
        String card = cardId(ana);

        purchase(ana, card, "Supermercado", "GROCERIES", "42.30", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(balance(mainAccount(ana).getId())).isEqualByComparingTo("957.70");
        mvc.perform(as(ana, get("/api/cards")))
                .andExpect(jsonPath("$[0].spentThisMonth").value(42.30));
        mvc.perform(as(ana, get("/api/cards/" + card + "/transactions")))
                .andExpect(jsonPath("$.items[0].merchant").value("Supermercado"));
    }

    @Test
    void frozenCardsAreDeclinedAndTheOwnerIsNotified() throws Exception {
        Session ana = register("Ana Congelada");
        String card = cardId(ana);
        mvc.perform(as(ana, patch("/api/cards/" + card)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frozen\": true}"))
                .andExpect(jsonPath("$.status").value("FROZEN"));

        purchase(ana, card, "Bar", "RESTAURANTS", "10", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason").value("CARD_FROZEN"));
        assertThat(balance(mainAccount(ana).getId())).isEqualByComparingTo("1000.00");

        outboxRelay.publishPending();
        mvc.perform(as(ana, get("/api/notifications")))
                .andExpect(jsonPath("$.items[0].type").value("CARD_DECLINED"));

        mvc.perform(as(ana, patch("/api/cards/" + card)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frozen\": false}"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        purchase(ana, card, "Bar", "RESTAURANTS", "10", false).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void appliesOnlineSwitchMonthlyLimitAndBalance() throws Exception {
        Session ana = register("Ana Reglas");
        String card = cardId(ana);
        mvc.perform(as(ana, patch("/api/cards/" + card)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineEnabled\": false, \"monthlyLimit\": 100}"))
                .andExpect(status().isOk());

        purchase(ana, card, "Tienda online", "SHOPPING", "5", true)
                .andExpect(jsonPath("$.declineReason").value("ONLINE_DISABLED"));
        purchase(ana, card, "Tienda", "SHOPPING", "80", false).andExpect(jsonPath("$.status").value("APPROVED"));
        purchase(ana, card, "Tienda", "SHOPPING", "20.01", false)
                .andExpect(jsonPath("$.declineReason").value("MONTHLY_LIMIT"));

        mvc.perform(as(ana, patch("/api/cards/" + card)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthlyLimit\": 5000}"))
                .andExpect(status().isOk());
        purchase(ana, card, "Joyería", "SHOPPING", "950", false)
                .andExpect(jsonPath("$.declineReason").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void atMostTwoLiveCards() throws Exception {
        Session ana = register("Ana Dos Tarjetas");
        mvc.perform(as(ana, post("/api/cards"))).andExpect(status().isCreated());
        mvc.perform(as(ana, post("/api/cards")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CARD_LIMIT_REACHED"));
        mvc.perform(as(ana, post("/api/cards/" + cardId(ana) + "/cancel")))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(as(ana, post("/api/cards"))).andExpect(status().isCreated());
    }

    @Test
    void cannotUseSomeoneElsesCard() throws Exception {
        Session ana = register("Ana Dueña");
        Session thief = register("Ladrón Tarjeta");
        purchase(thief, cardId(ana), "Tienda", "SHOPPING", "10", false).andExpect(status().isNotFound());
    }

    private String cardId(Session session) throws Exception {
        return read(mvc.perform(as(session, get("/api/cards"))).andReturn()).get(0).get("id").asText();
    }

    private ResultActions purchase(Session session, String card, String merchant, String category, String amount,
                                   boolean online) throws Exception {
        return mvc.perform(as(session, post("/api/cards/" + card + "/simulate-purchase"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"merchant": "%s", "category": "%s", "amount": %s, "online": %s}
                        """.formatted(merchant, category, amount, online)));
    }
}
