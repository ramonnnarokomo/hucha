package com.ramonnnarokomo.hucha.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.support.IntegrationTest;

class SavingsFlowTest extends IntegrationTest {

    @Test
    void createFillEmptyAndCloseASavingsPot() throws Exception {
        Session ana = register("Ana Ahorra");
        Account main = mainAccount(ana);
        String id = read(mvc.perform(as(ana, post("/api/savings")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Viaje a Japón", "goalAmount": 3000, "icon": "plane"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.goalAmount").value(3000.00))
                .andReturn()).get("id").asText();

        move(ana, id, "deposit", main, "300").andExpect(jsonPath("$.balance").value(300.00));
        assertThat(balance(main.getId())).isEqualByComparingTo("700.00");

        mvc.perform(as(ana, delete("/api/savings/" + id)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAVINGS_NOT_EMPTY"));

        move(ana, id, "withdraw", main, "300").andExpect(jsonPath("$.balance").value(0.00));
        mvc.perform(as(ana, delete("/api/savings/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(ana, get("/api/savings"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cannotTakeOutMoreThanThereIs() throws Exception {
        Session ana = register("Ana Sin Ahorros");
        String id = read(mvc.perform(as(ana, post("/api/savings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hucha\"}")).andReturn()).get("id").asText();
        move(ana, id, "withdraw", mainAccount(ana), "1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void savingsMovesAreNotIncomeOrExpenses() throws Exception {
        Session ana = register("Ana Analítica");
        String id = read(mvc.perform(as(ana, post("/api/savings")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Colchón\"}")).andReturn()).get("id").asText();
        move(ana, id, "deposit", mainAccount(ana), "250");

        mvc.perform(as(ana, get("/api/analytics/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thisMonth.expenses").value(0.00))
                .andExpect(jsonPath("$.thisMonth.income").value(1000.00))
                .andExpect(jsonPath("$.thisMonth.savingsBalance").value(250.00))
                .andExpect(jsonPath("$.thisMonth.totalBalance").value(1000.00));
    }

    private org.springframework.test.web.servlet.ResultActions move(Session session, String savingsId, String action,
                                                                      Account account, String amount) throws Exception {
        return mvc.perform(as(session, post("/api/savings/" + savingsId + "/" + action))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountId": "%s", "amount": %s}
                        """.formatted(account.getId(), amount)));
    }
}
