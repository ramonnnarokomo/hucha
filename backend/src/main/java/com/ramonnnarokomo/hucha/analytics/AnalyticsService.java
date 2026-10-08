package com.ramonnnarokomo.hucha.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.AccountStatus;
import com.ramonnnarokomo.hucha.account.AccountType;
import com.ramonnnarokomo.hucha.analytics.AnalyticsDtos.BalancePoint;
import com.ramonnnarokomo.hucha.analytics.AnalyticsDtos.CategorySpend;
import com.ramonnnarokomo.hucha.analytics.AnalyticsDtos.MonthFlow;
import com.ramonnnarokomo.hucha.analytics.AnalyticsDtos.Summary;
import com.ramonnnarokomo.hucha.analytics.AnalyticsDtos.Totals;
import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.Posting;
import com.ramonnnarokomo.hucha.ledger.PostingRepository;
import com.ramonnnarokomo.hucha.shared.Amounts;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;

/**
 * Ingresos y gastos por mes, gasto por categoría y evolución del saldo.
 * Los movimientos entre cuentas propias (huchas) no cuentan ni como ingreso ni como gasto.
 */
@Service
public class AnalyticsService {

    static final int BALANCE_HISTORY_DAYS = 90;

    private final AccountRepository accounts;
    private final PostingRepository postings;
    private final HuchaProperties properties;
    private final Clock clock;

    public AnalyticsService(AccountRepository accounts, PostingRepository postings, HuchaProperties properties,
                            Clock clock) {
        this.accounts = accounts;
        this.postings = postings;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId, int monthsBack) {
        int months = Math.max(1, Math.min(monthsBack, 12));
        ZoneId zone = properties.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);

        List<Account> owned = accounts.findByOwnerIdOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .toList();
        Set<UUID> checkingIds = owned.stream().filter(a -> a.getType() == AccountType.CHECKING)
                .map(Account::getId).collect(Collectors.toSet());
        Set<UUID> allIds = owned.stream().map(Account::getId).collect(Collectors.toSet());

        LocalDate historyStart = today.minusDays(BALANCE_HISTORY_DAYS - 1L);
        LocalDate earliest = firstMonth.atDay(1).isBefore(historyStart) ? firstMonth.atDay(1) : historyStart;
        Instant from = earliest.atStartOfDay(zone).toInstant();
        List<Posting> recent = allIds.isEmpty()
                ? List.of()
                : postings.findByAccountIdInAndOccurredAtGreaterThanEqualOrderByOccurredAtAscIdAsc(allIds, from);

        Map<YearMonth, BigDecimal[]> flows = new LinkedHashMap<>();
        for (YearMonth m = firstMonth; !m.isAfter(currentMonth); m = m.plusMonths(1)) {
            flows.put(m, new BigDecimal[] {Amounts.ZERO, Amounts.ZERO});
        }
        Map<Category, BigDecimal> byCategory = new EnumMap<>(Category.class);
        for (Posting posting : recent) {
            if (!checkingIds.contains(posting.getAccountId()) || EntryType.INTERNAL.contains(posting.getEntryType())) {
                continue;
            }
            YearMonth month = YearMonth.from(posting.getOccurredAt().atZone(zone));
            BigDecimal[] flow = flows.get(month);
            if (flow == null) {
                continue;
            }
            if (posting.getAmount().signum() > 0) {
                flow[0] = flow[0].add(posting.getAmount());
            } else {
                flow[1] = flow[1].add(posting.getAmount().negate());
                if (month.equals(currentMonth)) {
                    Category category = posting.getCategory() != null ? posting.getCategory() : Category.OTHER;
                    byCategory.merge(category, posting.getAmount().negate(), BigDecimal::add);
                }
            }
        }

        List<MonthFlow> monthFlows = flows.entrySet().stream()
                .map(e -> new MonthFlow(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();

        BigDecimal monthExpenses = flows.get(currentMonth)[1];
        BigDecimal monthIncome = flows.get(currentMonth)[0];
        List<CategorySpend> categories = byCategory.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .map(e -> new CategorySpend(e.getKey(), e.getValue(), percentage(e.getValue(), monthExpenses)))
                .toList();

        BigDecimal totalBalance = owned.stream().map(Account::getBalance).reduce(Amounts.ZERO, BigDecimal::add);
        BigDecimal savingsBalance = owned.stream().filter(a -> a.getType() == AccountType.SAVINGS)
                .map(Account::getBalance).reduce(Amounts.ZERO, BigDecimal::add);
        BigDecimal net = monthIncome.subtract(monthExpenses);
        BigDecimal savingsRate = monthIncome.signum() > 0 ? percentage(net.max(BigDecimal.ZERO), monthIncome) : null;

        return new Summary(
                new Totals(monthIncome, monthExpenses, net, savingsRate, totalBalance, savingsBalance),
                monthFlows,
                categories,
                balanceHistory(recent, totalBalance, today, historyStart, zone));
    }

    /**
     * Saldo total al cierre de cada día: se parte del saldo actual y se va hacia atrás
     * restando lo que se movió cada día.
     */
    private static List<BalancePoint> balanceHistory(List<Posting> postings, BigDecimal currentTotal, LocalDate today,
                                                     LocalDate start, ZoneId zone) {
        Map<LocalDate, BigDecimal> netByDay = new TreeMap<>();
        for (Posting posting : postings) {
            netByDay.merge(LocalDate.ofInstant(posting.getOccurredAt(), zone), posting.getAmount(), BigDecimal::add);
        }
        List<BalancePoint> points = new ArrayList<>();
        BigDecimal closing = currentTotal;
        for (LocalDate day = today; !day.isBefore(start); day = day.minusDays(1)) {
            points.add(new BalancePoint(day, closing));
            closing = closing.subtract(netByDay.getOrDefault(day, BigDecimal.ZERO));
        }
        java.util.Collections.reverse(points);
        return points;
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
