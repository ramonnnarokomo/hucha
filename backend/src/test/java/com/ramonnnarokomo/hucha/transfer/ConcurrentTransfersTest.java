package com.ramonnnarokomo.hucha.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.ledger.PostingRepository;
import com.ramonnnarokomo.hucha.support.IntegrationTest;

/**
 * Diez transferencias de 100 € a la vez desde una cuenta con 500 €: exactamente cinco tienen que
 * salir bien y la cuenta no puede quedar en negativo. Es lo que garantiza el bloqueo de la cuenta.
 */
class ConcurrentTransfersTest extends IntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private PostingRepository postings;

    @Test
    void parallelTransfersNeverOverdrawTheAccount() throws Exception {
        Session ana = register("Ana Concurrente");
        Session luis = register("Luis Concurrente");
        Account from = mainAccount(ana);
        Account to = mainAccount(luis);
        addPayee(ana, "Luis", to.getIban());
        // Deja la cuenta en 500 €: 1000 de regalo menos 500 a Luis.
        transfer(ana, "setup", from.getId(), to.getIban(), "Luis", "500");

        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String key = "paralela-" + i;
            Callable<Integer> call = () -> {
                start.await();
                return transfer(ana, key, from.getId(), to.getIban(), "Luis", "100").andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(code -> code == 201).hasSize(5);
        assertThat(statuses).filteredOn(code -> code == 422).hasSize(5);
        assertThat(balance(from.getId())).isEqualByComparingTo("0.00");
        assertThat(balance(to.getId())).isEqualByComparingTo("2000.00");
        assertThat(postings.sumForAccount(from.getId())).isEqualByComparingTo("0.00");
    }
}
