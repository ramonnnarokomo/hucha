package com.ramonnnarokomo.hucha.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ramonnnarokomo.hucha.ledger.Category;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Summary(
            Totals thisMonth,
            List<MonthFlow> months,
            List<CategorySpend> categories,
            List<BalancePoint> balanceHistory) {
    }

    /** {@code savingsRate}: porcentaje de los ingresos del mes que no se ha gastado. */
    public record Totals(BigDecimal income, BigDecimal expenses, BigDecimal net, BigDecimal savingsRate,
                         BigDecimal totalBalance, BigDecimal savingsBalance) {
    }

    public record MonthFlow(String month, BigDecimal income, BigDecimal expenses) {
    }

    public record CategorySpend(Category category, BigDecimal amount, BigDecimal share) {
    }

    public record BalancePoint(LocalDate date, BigDecimal balance) {
    }
}
