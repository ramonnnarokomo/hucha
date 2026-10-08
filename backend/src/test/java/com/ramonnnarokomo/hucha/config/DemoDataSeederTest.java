package com.ramonnnarokomo.hucha.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.ramonnnarokomo.hucha.account.AccountType;
import com.ramonnnarokomo.hucha.ledger.PostingRepository;
import com.ramonnnarokomo.hucha.support.IntegrationTest;
import com.ramonnnarokomo.hucha.transfer.TransferRepository;

/** Arranca con los datos de demo en una base de datos aparte y comprueba que todo cuadra. */
@TestPropertySource(properties = {
        "hucha.demo-data=true",
        "spring.datasource.url=jdbc:h2:mem:hucha-demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
class DemoDataSeederTest extends IntegrationTest {

    @Autowired
    private PostingRepository postings;

    @Autowired
    private TransferRepository transfers;

    @Test
    void seedsSixMonthsOfConsistentHistory() throws Exception {
        assertThat(users.findByEmail(DemoDataSeeder.LUCIA_EMAIL)).isPresent();
        assertThat(postings.sumOfAllPostings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(postings.count()).isGreaterThan(200);
        assertThat(transfers.count()).isGreaterThan(20);

        var lucia = users.findByEmail(DemoDataSeeder.LUCIA_EMAIL).orElseThrow();
        accounts.findByOwnerIdOrderByCreatedAtAsc(lucia.getId()).forEach(account -> {
            assertThat(account.getBalance().signum()).isPositive();
            assertThat(postings.sumForAccount(account.getId())).isEqualByComparingTo(account.getBalance());
        });
        assertThat(accounts.findByOwnerIdAndTypeOrderByCreatedAtAsc(lucia.getId(), AccountType.SAVINGS)).hasSize(2);

        // Se puede entrar con las credenciales de demo.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + DemoDataSeeder.LUCIA_EMAIL + "\", \"password\": \""
                                + DemoDataSeeder.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }
}
