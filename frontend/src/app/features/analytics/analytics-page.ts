import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import { BankApi } from '../../core/api';
import { CATEGORIES, money, monthLong, monthShort, percent, signedMoney } from '../../core/format';
import { AnalyticsSummary } from '../../core/models';
import { MoneyPipe, PercentPipe } from '../../core/pipes';
import { BarChart, BarGroup } from '../../shared/charts/bar-chart';
import { DonutChart, DonutSlice } from '../../shared/charts/donut-chart';
import { LineChart, LinePoint } from '../../shared/charts/line-chart';
import { Icon } from '../../shared/icon';

interface Insight {
  icon: string;
  tone: 'good' | 'warn' | '';
  text: string;
}

@Component({
  selector: 'app-analytics-page',
  imports: [Icon, MoneyPipe, PercentPipe, BarChart, DonutChart, LineChart],
  templateUrl: './analytics-page.html',
  styleUrl: './analytics-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsPage {
  private readonly api = inject(BankApi);

  protected readonly periods = [3, 6, 12];
  protected readonly months = signal(6);
  protected readonly summary = signal<AnalyticsSummary | null>(null);
  protected readonly meta = CATEGORIES;
  protected readonly currentMonth = (() => {
    const now = new Date();
    const label = monthLong(`${now.getFullYear()}-${now.getMonth() + 1}`);
    return label.charAt(0).toUpperCase() + label.slice(1);
  })();

  /** Meses ya cerrados (sin el actual, que aún está a medias), para las medias. */
  private readonly closedMonths = computed(() => (this.summary()?.months ?? []).slice(0, -1));

  protected readonly avgIncome = computed(() => average(this.closedMonths().map((m) => m.income)));
  protected readonly avgExpenses = computed(() =>
    average(this.closedMonths().map((m) => m.expenses)),
  );

  protected readonly monthBars = computed<BarGroup[]>(() =>
    (this.summary()?.months ?? []).map((m) => ({
      label: monthShort(m.month),
      values: [m.income, m.expenses],
    })),
  );

  protected readonly slices = computed<DonutSlice[]>(() =>
    (this.summary()?.categories ?? []).map((c) => ({
      label: CATEGORIES[c.category].label,
      value: c.amount,
      color: CATEGORIES[c.category].color,
    })),
  );

  protected readonly history = computed<LinePoint[]>(() =>
    (this.summary()?.balanceHistory ?? []).map((p) => ({ date: p.date, value: p.balance })),
  );

  protected readonly historyChange = computed(() => {
    const history = this.history();
    return history.length > 1 ? history[history.length - 1]!.value - history[0]!.value : 0;
  });

  protected readonly insights = computed<Insight[]>(() => {
    const summary = this.summary();
    if (!summary) return [];
    const insights: Insight[] = [];

    const top = summary.categories[0];
    if (top) {
      insights.push({
        icon: CATEGORIES[top.category].icon,
        tone: top.share >= 50 ? 'warn' : '',
        text: `Lo que más pesa este mes es ${CATEGORIES[top.category].label.toLowerCase()}: ${money(top.amount)}, el ${percent(top.share)} de lo que has gastado.`,
      });
    }

    const closed = this.closedMonths();
    const best = closed.reduce<{ month: string; net: number } | null>((acc, m) => {
      const net = m.income - m.expenses;
      return !acc || net > acc.net ? { month: m.month, net } : acc;
    }, null);
    if (best && best.net > 0) {
      insights.push({
        icon: 'star',
        tone: 'good',
        text: `Tu mejor mes fue ${monthLong(best.month).replace(/ de \d+$/, '')}: te sobraron ${signedMoney(best.net)} entre ingresos y gastos.`,
      });
    }

    const { totalBalance, savingsBalance } = summary.thisMonth;
    if (savingsBalance > 0 && totalBalance > 0) {
      insights.push({
        icon: 'piggy',
        tone: 'good',
        text: `Tienes ${money(savingsBalance)} apartados en huchas, el ${percent(Math.round((savingsBalance / totalBalance) * 100))} de tu dinero.`,
      });
    } else {
      insights.push({
        icon: 'piggy',
        tone: '',
        text: 'Aún no tienes nada en huchas. Apartar un poco cada mes ayuda a tener un colchón para imprevistos.',
      });
    }

    if (closed.length > 0) {
      insights.push({
        icon: 'chart',
        tone: '',
        text: `En los últimos ${closed.length} meses cerrados has gastado de media ${money(this.avgExpenses())} al mes.`,
      });
    }
    return insights;
  });

  constructor() {
    toObservable(this.months)
      .pipe(
        switchMap((months) => this.api.analytics(months).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((summary) => this.summary.set(summary));
  }
}

function average(values: number[]): number {
  return values.length ? values.reduce((sum, v) => sum + v, 0) / values.length : 0;
}
