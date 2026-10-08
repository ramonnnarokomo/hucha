package com.ramonnnarokomo.hucha.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.support.IntegrationTest;

class AdminFlowTest extends IntegrationTest {

    @Test
    void customersCannotReachTheBackOffice() throws Exception {
        Session ana = register("Ana Curiosa");
        mvc.perform(as(ana, get("/api/admin/overview"))).andExpect(status().isForbidden());
        mvc.perform(as(ana, get("/actuator/prometheus"))).andExpect(status().isForbidden());
    }

    @Test
    void freezingAnAccountStopsItsMoneyAndNotifiesTheOwner() throws Exception {
        Session admin = admin();
        Session ana = register("Ana Congelada Admin");
        Session luis = register("Luis Destino Admin");
        Account account = mainAccount(ana);
        addPayee(ana, "Luis", mainAccount(luis).getIban());

        mvc.perform(as(admin, post("/api/admin/accounts/" + account.getId() + "/freeze")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        transfer(ana, "k-frozen", account.getId(), mainAccount(luis).getIban(), "Luis", "10")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        // Tampoco puede recibir.
        addPayee(luis, "Ana", account.getIban());
        transfer(luis, "k-to-frozen", mainAccount(luis).getId(), account.getIban(), "Ana", "10")
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

        outboxRelay.publishPending();
        mvc.perform(as(ana, get("/api/notifications")))
                .andExpect(jsonPath("$.items[0].type").value("ACCOUNT_FROZEN"));

        mvc.perform(as(admin, post("/api/admin/accounts/" + account.getId() + "/unfreeze")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        transfer(ana, "k-unfrozen", account.getId(), mainAccount(luis).getIban(), "Luis", "10")
                .andExpect(status().isCreated());
    }

    @Test
    void blockingAUserClosesTheirSessions() throws Exception {
        Session admin = admin();
        Session ana = register("Ana Bloqueada");
        mvc.perform(as(admin, post("/api/admin/users/" + ana.userId() + "/block")))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mvc.perform(post("/api/auth/refresh").cookie(ana.refreshCookie())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(ana.email(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    void overviewUsersAndAuditTrail() throws Exception {
        Session admin = admin();
        Session ana = register("Ana Auditada");

        mvc.perform(as(admin, get("/api/admin/overview")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers").isNumber())
                .andExpect(jsonPath("$.last7Days.length()").value(7));
        mvc.perform(as(admin, get("/api/admin/users").param("q", "auditada")))
                .andExpect(jsonPath("$.items[0].email").value(ana.email()))
                .andExpect(jsonPath("$.items[0].totalBalance").value(1000.00));
        mvc.perform(as(admin, get("/api/admin/users/" + ana.userId())))
                .andExpect(jsonPath("$.accounts.length()").value(1));
        mvc.perform(as(admin, get("/api/admin/audit").param("action", "USER_REGISTERED")))
                .andExpect(jsonPath("$.items[0].action").value("USER_REGISTERED"));
    }
}
