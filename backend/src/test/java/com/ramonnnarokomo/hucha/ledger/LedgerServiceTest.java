package com.ramonnnarokomo.hucha.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;
import com.ramonnnarokomo.hucha.support.IntegrationTest;

class LedgerServiceTest extends IntegrationTest {

    @Autowired
    private PostingRepository postings;

    @Test
    void recordsBalancedEntriesAndKeepsTheRunningBalance() throws Exception {
        Account a = mainAccount(register("Ana Libro"));
        Account b = mainAccount(register("Luis Libro"));

        JournalEntry entry = transaction.execute(status -> ledger.record(entry(a.getId(), b.getId(), "250.00")));

        assertThat(balance(a.getId())).isEqualByComparingTo("750.00");
        assertThat(balance(b.getId())).isEqualByComparingTo("1250.00");
        List<Posting> lines = postings.findByEntryIdOrderByIdAsc(entry.getId());
        assertThat(lines).extracting(Posting::getAmount).containsExactly(new BigDecimal("-250.00"), new BigDecimal("250.00"));
        assertThat(lines).extracting(Posting::getBalanceAfter)
                .containsExactly(new BigDecimal("750.00"), new BigDecimal("1250.00"));
    }

    @Test
    void rejectsEntriesThatDoNotAddUpToZero() throws Exception {
        Account a = mainAccount(register("Ana Descuadre"));
        LedgerEntryRequest broken = LedgerEntryRequest.builder(EntryType.TRANSFER, "descuadre", clock.instant())
                .leg(a.getId(), new BigDecimal("-10.00"), null, null, null, null)
                .leg(Account.SEPA_CLEARING, new BigDecimal("9.99"), null, null, null, null)
                .build();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> ledger.record(broken)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no cuadra");
    }

    @Test
    void neverLeavesACustomerAccountNegativeAndChangesNothingOnFailure() throws Exception {
        Account a = mainAccount(register("Ana Negativo"));
        Account b = mainAccount(register("Luis Negativo"));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                ledger.record(entry(a.getId(), b.getId(), "1000.01"))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(balance(a.getId())).isEqualByComparingTo("1000.00");
        assertThat(balance(b.getId())).isEqualByComparingTo("1000.00");
    }

    @Test
    void onlyRunsInsideATransaction() throws Exception {
        Account a = mainAccount(register("Ana Sin Tx"));
        assertThatThrownBy(() -> ledger.record(entry(a.getId(), Account.SEPA_CLEARING, "1.00")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** Invariantes del libro mayor sobre toda la base de datos, después de todo lo que hayan hecho los tests. */
    @Test
    void theWholeLedgerAlwaysBalances() throws Exception {
        Account a = mainAccount(register("Ana Invariante"));
        fund(a.getId(), "10.00");

        assertThat(postings.sumOfAllPostings()).isEqualByComparingTo("0");
        Map<UUID, BigDecimal> sums = postings.findAll().stream()
                .collect(Collectors.groupingBy(Posting::getAccountId,
                        Collectors.reducing(BigDecimal.ZERO, Posting::getAmount, BigDecimal::add)));
        accounts.findAll().forEach(account ->
                assertThat(account.getBalance())
                        .as("saldo de %s", account.getName())
                        .isEqualByComparingTo(sums.getOrDefault(account.getId(), BigDecimal.ZERO)));
    }

    private LedgerEntryRequest entry(UUID from, UUID to, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return LedgerEntryRequest.builder(EntryType.TRANSFER, "prueba", clock.instant())
                .leg(from, value.negate(), Category.TRANSFER, null, null, "prueba")
                .leg(to, value, Category.TRANSFER, null, null, "prueba")
                .build();
    }
}
