import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BankApi } from '../../core/api';
import { AuthService } from '../../core/auth.service';
import { CATEGORIES, monthShort } from '../../core/format';
import { groupByDay } from '../../core/grouping';
import { Account, AnalyticsSummary, Movement } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { IbanPipe, MoneyPipe } from '../../core/pipes';
import { BarChart, BarGroup } from '../../shared/charts/bar-chart';
import { DonutChart, DonutSlice } from '../../shared/charts/donut-chart';
import { LineChart } from '../../shared/charts/line-chart';
import { CopyButton } from '../../shared/copy-button';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { MovementDetail } from '../../shared/movement-detail';
import { MovementRow } from '../../shared/movement-row';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    RouterLink,
    Icon,
    MoneyPipe,
    IbanPipe,
    MovementRow,
    MovementDetail,
    Modal,
    DonutChart,
    BarChart,
    LineChart,
    CopyButton,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly api = inject(BankApi);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly movements = signal<Movement[]>([]);
  protected readonly analytics = signal<AnalyticsSummary | null>(null);
  protected readonly selected = signal<Movement | null>(null);

  protected readonly checking = computed(
    () => this.accounts().find((a) => a.type === 'CHECKING') ?? null,
  );
  protected readonly savings = computed(() => this.accounts().filter((a) => a.type === 'SAVINGS'));
  protected readonly total = computed(() => this.accounts().reduce((sum, a) => sum + a.balance, 0));
  protected readonly savingsTotal = computed(() =>
    this.savings().reduce((sum, a) => sum + a.balance, 0),
  );

  protected readonly greeting = (() => {
    const hour = new Date().getHours();
    return hour < 14 ? 'Buenos días' : hour < 21 ? 'Buenas tardes' : 'Buenas noches';
  })();

  /** Movimientos agrupados por día ("Hoy", "Ayer", "3 de octubre"). */
  protected readonly groupedMovements = computed(() =>
    groupByDay(this.movements(), (m) => m.occurredAt),
  );

  protected readonly categorySlices = computed<DonutSlice[]>(() =>
    (this.analytics()?.categories ?? []).map((c) => ({
      label: CATEGORIES[c.category].label,
      value: c.amount,
      color: CATEGORIES[c.category].color,
    })),
  );

  protected readonly topCategories = computed(() =>
    (this.analytics()?.categories ?? []).slice(0, 4),
  );

  protected readonly monthBars = computed<BarGroup[]>(() =>
    (this.analytics()?.months ?? []).map((m) => ({
      label: monthShort(m.month),
      values: [m.income, m.expenses],
    })),
  );

  protected readonly balanceTrend = computed(() =>
    (this.analytics()?.balanceHistory ?? [])
      .slice(-30)
      .map((p) => ({ date: p.date, value: p.balance })),
  );

  protected readonly categoryMeta = CATEGORIES;

  constructor() {
    this.load();
    // Si llega dinero mientras se mira el inicio, se refresca solo.
    inject(NotificationsService)
      .incoming.pipe(takeUntilDestroyed())
      .subscribe((n) => {
        if (
          n.type === 'TRANSFER_RECEIVED' ||
          n.type === 'ACCOUNT_FROZEN' ||
          n.type === 'ACCOUNT_UNFROZEN'
        ) {
          this.load();
        }
      });
  }

  protected progress(account: Account): number {
    return account.goalAmount
      ? Math.min(100, Math.round((account.balance / account.goalAmount) * 100))
      : 0;
  }

  private load(): void {
    forkJoin({ accounts: this.api.accounts(), analytics: this.api.analytics(6) }).subscribe({
      next: ({ accounts, analytics }) => {
        this.accounts.set(accounts);
        this.analytics.set(analytics);
        const main = accounts.find((a) => a.type === 'CHECKING');
        if (main) {
          this.api.movements(main.id, { size: 8 }).subscribe((page) => {
            this.movements.set(page.items);
            this.loading.set(false);
          });
        } else {
          this.loading.set(false);
        }
      },
      error: () => this.loading.set(false),
    });
  }
}
