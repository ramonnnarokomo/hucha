package com.ramonnnarokomo.hucha.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.support.IntegrationTest;

/**
 * Los mismos flujos contra PostgreSQL de verdad (Testcontainers): migraciones de Flyway, bloqueos
 * FOR UPDATE y consultas. Si no hay Docker disponible, el test se salta.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest extends IntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void fullFlowOnPostgres() throws Exception {
        Session ana = register("Ana Postgres");
        Session luis = register("Luis Postgres");
        Account from = mainAccount(ana);
        addPayee(ana, "Luis", mainAccount(luis).getIban());

        transfer(ana, "pg-1", from.getId(), mainAccount(luis).getIban(), "Luis", "99.99")
                .andExpect(status().isCreated());
        transfer(ana, "pg-1", from.getId(), mainAccount(luis).getIban(), "Luis", "99.99")
                .andExpect(status().isOk());

        assertThat(balance(from.getId())).isEqualByComparingTo("900.01");
        mvc.perform(as(ana, get("/api/accounts/" + from.getId() + "/movements").param("q", "test")))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(as(ana, get("/api/analytics/summary"))).andExpect(status().isOk());
    }
}
