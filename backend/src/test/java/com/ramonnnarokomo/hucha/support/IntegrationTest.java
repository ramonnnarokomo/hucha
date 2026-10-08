package com.ramonnnarokomo.hucha.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.auth.RefreshCookie;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.LedgerEntryRequest;
import com.ramonnnarokomo.hucha.ledger.LedgerService;
import com.ramonnnarokomo.hucha.outbox.OutboxRelay;
import com.ramonnnarokomo.hucha.user.Role;
import com.ramonnnarokomo.hucha.user.User;
import com.ramonnnarokomo.hucha.user.UserRepository;

/**
 * Base de los tests de integración: la aplicación completa con H2, MockMvc y un reloj controlable.
 * Cada test crea sus propios usuarios con emails únicos, así que no hace falta limpiar la base de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Clave1234";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected AccountRepository accounts;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected LedgerService ledger;

    @Autowired
    protected OutboxRelay outboxRelay;

    @Autowired
    protected TransactionTemplate transaction;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetClock() {
        clock.set(TestBeans.NOW);
    }

    protected Session register(String name) throws Exception {
        String email = name.toLowerCase(Locale.ROOT).replace(' ', '.') + "-" + UUID.randomUUID() + "@test.dev";
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"fullName": "%s", "email": "%s", "password": "%s"}
                                """.formatted(name, email, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        // El reloj de los tests está parado: se adelanta un segundo para que lo que venga después
        // (avisos, movimientos) quede ordenado después del alta.
        clock.advance(java.time.Duration.ofSeconds(1));
        return session(email, result);
    }

    protected Session login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return session(email, result);
    }

    /** Administrador creado directamente en la base de datos (no hay registro público de admins). */
    protected Session admin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@test.dev";
        users.save(new User(email, "Admin de pruebas", passwordEncoder.encode(PASSWORD), Role.ADMIN, clock.instant()));
        return login(email);
    }

    protected MockHttpServletRequestBuilder as(Session session, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken());
    }

    protected ResultActions transfer(Session session, String idempotencyKey, UUID from, String toIban,
                                     String beneficiary, String amount) throws Exception {
        return mvc.perform(as(session, post("/api/transfers"))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("""
                        {"fromAccountId": "%s", "toIban": "%s", "beneficiaryName": "%s", "amount": %s, "concept": "Test"}
                        """.formatted(from, toIban, beneficiary, amount))));
    }

    protected void addPayee(Session session, String name, String iban) throws Exception {
        mvc.perform(as(session, post("/api/payees"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"name": "%s", "iban": "%s"}
                                """.formatted(name, iban))))
                .andExpect(status().isCreated());
    }

    protected Account mainAccount(Session session) {
        return accounts.findByOwnerIdAndTypeOrderByCreatedAtAsc(session.userId(),
                com.ramonnnarokomo.hucha.account.AccountType.CHECKING).get(0);
    }

    protected BigDecimal balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().getBalance();
    }

    /** Ingresa dinero desde los fondos del banco, pasando por el libro mayor como cualquier movimiento. */
    protected void fund(UUID accountId, String amount) {
        BigDecimal value = new BigDecimal(amount);
        transaction.executeWithoutResult(status -> ledger.record(LedgerEntryRequest
                .builder(EntryType.INCOMING_TRANSFER, "Ingreso de prueba", clock.instant())
                .leg(Account.BANK_FUNDS, value.negate(), null, null, null, "Ingreso de prueba")
                .leg(accountId, value, null, "Banco de pruebas", null, "Ingreso de prueba")
                .build()));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static String body(String text) {
        return text.strip();
    }

    private Session session(String email, MvcResult result) throws Exception {
        JsonNode response = read(result);
        Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
        return new Session(UUID.fromString(response.at("/user/id").asText()), email,
                response.get("accessToken").asText(), cookie != null ? cookie.getValue() : null);
    }

    public record Session(UUID userId, String email, String accessToken, String refreshToken) {

        public Cookie refreshCookie() {
            return new Cookie(RefreshCookie.NAME, refreshToken);
        }
    }
}
