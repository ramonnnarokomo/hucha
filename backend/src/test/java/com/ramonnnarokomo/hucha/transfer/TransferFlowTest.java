package com.ramonnnarokomo.hucha.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.support.IntegrationTest;
import com.ramonnnarokomo.hucha.support.TestBeans;

class TransferFlowTest extends IntegrationTest {

    /** IBAN de otro banco (no existe en Hucha). */
    private static final String EXTERNAL_IBAN = "ES9121000418450200051332";

    @Autowired
    private TransferRepository transfers;

    @Test
    void transferToASavedPayeeInHuchaArrivesInstantly() throws Exception {
        Session ana = register("Ana Envía");
        Session luis = register("Luis Recibe");
        Account from = mainAccount(ana);
        Account to = mainAccount(luis);
        addPayee(ana, "Luis", to.getIban());

        transfer(ana, "k-1", from.getId(), to.getIban(), "Luis", "150.25")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.internal").value(true));

        assertThat(balance(from.getId())).isEqualByComparingTo("849.75");
        assertThat(balance(to.getId())).isEqualByComparingTo("1150.25");

        // El aviso al receptor sale por el outbox.
        outboxRelay.publishPending();
        mvc.perform(as(luis, get("/api/notifications")))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER_RECEIVED"))
                .andExpect(jsonPath("$.items[0].title").value("Has recibido 150,25 €"));
    }

    @Test
    void theSameIdempotencyKeyNeverChargesTwice() throws Exception {
        Session ana = register("Ana Idempotente");
        Account from = mainAccount(ana);
        addPayee(ana, "Casero", EXTERNAL_IBAN);

        String id = read(transfer(ana, "pago-alquiler", from.getId(), EXTERNAL_IBAN, "Casero", "400")
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        transfer(ana, "pago-alquiler", from.getId(), EXTERNAL_IBAN, "Casero", "400.00")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(id));

        assertThat(balance(from.getId())).isEqualByComparingTo("600.00");

        transfer(ana, "pago-alquiler", from.getId(), EXTERNAL_IBAN, "Casero", "401")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void validatesTheRequest() throws Exception {
        Session ana = register("Ana Valida");
        Account from = mainAccount(ana);

        mvc.perform(as(ana, post("/api/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": "%s", "toIban": "%s", "beneficiaryName": "X", "amount": 10}
                                """.formatted(from.getId(), EXTERNAL_IBAN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));
        transfer(ana, "k-iban", from.getId(), "ES0000000000000000000000", "X", "10")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_IBAN"));
        transfer(ana, "k-same", from.getId(), from.getIban(), "Yo", "10")
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
        transfer(ana, "k-funds", from.getId(), EXTERNAL_IBAN, "X", "2000")
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        transfer(ana, "k-max", from.getId(), EXTERNAL_IBAN, "X", "10000.01")
                .andExpect(jsonPath("$.code").value("TRANSFER_LIMIT_EXCEEDED"));
        transfer(ana, "k-cents", from.getId(), EXTERNAL_IBAN, "X", "1.001")
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void cannotSpendFromSomeoneElsesAccount() throws Exception {
        Session ana = register("Ana Ajena");
        Session thief = register("Ladrón");
        transfer(thief, "k-steal", mainAccount(ana).getId(), mainAccount(thief).getIban(), "Yo", "10")
                .andExpect(status().isNotFound());
        assertThat(balance(mainAccount(ana).getId())).isEqualByComparingTo("1000.00");
    }

    @Test
    void newPayeesNeedTheVerificationCode() throws Exception {
        Session ana = register("Ana Sca");
        Account from = mainAccount(ana);

        String id = read(transfer(ana, "k-sca", from.getId(), EXTERNAL_IBAN, "Desconocido", "120.50")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_SCA"))
                .andExpect(jsonPath("$.scaExpiresAt").isNotEmpty())
                .andReturn()).get("id").asText();
        assertThat(balance(from.getId())).isEqualByComparingTo("1000.00");
        mvc.perform(as(ana, get("/api/notifications")))
                .andExpect(jsonPath("$.items[0].type").value("SCA_CODE"))
                .andExpect(jsonPath("$.items[0].body").value(org.hamcrest.Matchers.containsString(TestBeans.SCA_CODE)));

        confirm(ana, id, "000000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCA_CODE_INVALID"))
                .andExpect(jsonPath("$.detail").value("Código incorrecto. Te quedan 2 intentos."));
        confirm(ana, id, TestBeans.SCA_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(balance(from.getId())).isEqualByComparingTo("879.50");

        confirm(ana, id, TestBeans.SCA_CODE).andExpect(jsonPath("$.code").value("TRANSFER_NOT_PENDING"));
    }

    @Test
    void smallAmountsToNewPayeesAndOwnAccountsSkipTheCode() throws Exception {
        Session ana = register("Ana Exenta");
        Account from = mainAccount(ana);
        transfer(ana, "k-small", from.getId(), EXTERNAL_IBAN, "Café", "30.00")
                .andExpect(status().isCreated());
    }

    @Test
    void largeAmountsNeedTheCodeEvenForSavedPayees() throws Exception {
        Session ana = register("Ana Grande");
        Account from = mainAccount(ana);
        fund(from.getId(), "2000.00");
        addPayee(ana, "Casero", EXTERNAL_IBAN);
        transfer(ana, "k-big", from.getId(), EXTERNAL_IBAN, "Casero", "1000")
                .andExpect(status().isAccepted());
    }

    @Test
    void threeWrongCodesRejectTheTransfer() throws Exception {
        Session ana = register("Ana Fallos");
        String id = read(transfer(ana, "k-wrong", mainAccount(ana).getId(), EXTERNAL_IBAN, "X", "50")
                .andReturn()).get("id").asText();
        confirm(ana, id, "111111").andExpect(jsonPath("$.code").value("SCA_CODE_INVALID"));
        confirm(ana, id, "222222").andExpect(jsonPath("$.detail").value("Código incorrecto. Te queda 1 intento."));
        confirm(ana, id, "333333").andExpect(jsonPath("$.code").value("SCA_TOO_MANY_ATTEMPTS"));

        assertThat(transfers.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(TransferStatus.REJECTED);
        confirm(ana, id, TestBeans.SCA_CODE).andExpect(jsonPath("$.code").value("TRANSFER_NOT_PENDING"));
        assertThat(balance(mainAccount(ana).getId())).isEqualByComparingTo("1000.00");
    }

    @Test
    void theCodeExpiresAfterFiveMinutes() throws Exception {
        Session ana = register("Ana Caduca");
        String id = read(transfer(ana, "k-exp", mainAccount(ana).getId(), EXTERNAL_IBAN, "X", "50")
                .andReturn()).get("id").asText();
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        confirm(ana, id, TestBeans.SCA_CODE).andExpect(jsonPath("$.code").value("SCA_CODE_EXPIRED"));
        assertThat(transfers.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(TransferStatus.EXPIRED);
    }

    @Test
    void pendingTransfersCanBeCancelled() throws Exception {
        Session ana = register("Ana Cancela");
        String id = read(transfer(ana, "k-cancel", mainAccount(ana).getId(), EXTERNAL_IBAN, "X", "50")
                .andReturn()).get("id").asText();
        mvc.perform(as(ana, post("/api/transfers/" + id + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void dailyLimitCountsEverythingSentTodayAndResetsTomorrow() throws Exception {
        Session ana = register("Ana Límite");
        Account from = mainAccount(ana);
        fund(from.getId(), "5000.00");
        addPayee(ana, "Casero", EXTERNAL_IBAN);

        transfer(ana, "d-1", from.getId(), EXTERNAL_IBAN, "Casero", "900").andExpect(status().isCreated());
        transfer(ana, "d-2", from.getId(), EXTERNAL_IBAN, "Casero", "900").andExpect(status().isCreated());
        transfer(ana, "d-3", from.getId(), EXTERNAL_IBAN, "Casero", "900").andExpect(status().isCreated());
        transfer(ana, "d-4", from.getId(), EXTERNAL_IBAN, "Casero", "300.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("300,00 €")));

        clock.advance(Duration.ofDays(1));
        Session tomorrow = login(ana.email());
        transfer(tomorrow, "d-5", from.getId(), EXTERNAL_IBAN, "Casero", "300.01").andExpect(status().isCreated());
    }

    @Test
    void movingMoneyBetweenOwnAccountsHasNoLimitsOrCode() throws Exception {
        Session ana = register("Ana Propias");
        Account main = mainAccount(ana);
        fund(main.getId(), "9000.00");
        String savingsIban = read(mvc.perform(as(ana, post("/api/savings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Coche", "goalAmount": 8000, "icon": "car"}
                                """))
                .andExpect(status().isCreated()).andReturn()).get("iban").asText();

        transfer(ana, "own-1", main.getId(), savingsIban, "Coche", "6000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.beneficiaryName").value("Coche"));
        assertThat(balance(main.getId())).isEqualByComparingTo("4000.00");
    }

    private org.springframework.test.web.servlet.ResultActions confirm(Session session, String transferId, String code)
            throws Exception {
        return mvc.perform(as(session, post("/api/transfers/" + transferId + "/confirm"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s"}
                        """.formatted(code)));
    }
}
