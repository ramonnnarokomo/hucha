import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BankApi } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { TRANSFER_STATUS, initials } from '../../core/format';
import { groupByDay } from '../../core/grouping';
import { shortIban } from '../../core/iban';
import { Transfer } from '../../core/models';
import { IbanPipe, LongDateTimePipe, MoneyPipe, TimePipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';

const PAGE_SIZE = 20;

function startOfMonth(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1);
}

const REJECTIONS: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'Saldo insuficiente',
  DAILY_LIMIT_EXCEEDED: 'Límite diario superado',
  ACCOUNT_NOT_ACTIVE: 'Cuenta no operativa',
  SCA_TOO_MANY_ATTEMPTS: 'Demasiados intentos con el código',
  SCA_CODE_EXPIRED: 'El código caducó',
};

@Component({
  selector: 'app-transfers-page',
  imports: [RouterLink, Icon, Modal, MoneyPipe, IbanPipe, TimePipe, LongDateTimePipe],
  templateUrl: './transfers-page.html',
  styleUrl: './transfers-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransfersPage {
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);

  protected readonly transfers = signal<Transfer[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalItems = signal(0);
  protected readonly loading = signal(true);
  protected readonly selected = signal<Transfer | null>(null);

  protected readonly groups = computed(() => groupByDay(this.transfers(), (t) => t.createdAt));
  protected readonly pending = computed(
    () => this.transfers().filter((t) => t.status === 'PENDING_SCA').length,
  );
  private readonly thisMonth = computed(() => {
    const start = startOfMonth();
    return this.transfers().filter(
      (t) => t.status === 'COMPLETED' && new Date(t.createdAt) >= start,
    );
  });
  protected readonly sentThisMonth = computed(() =>
    this.thisMonth().reduce((sum, t) => sum + t.amount, 0),
  );
  protected readonly countThisMonth = computed(() => this.thisMonth().length);

  protected readonly status = TRANSFER_STATUS;
  protected readonly initials = initials;
  protected readonly shortIban = shortIban;

  constructor() {
    this.load(0);
  }

  protected loadMore(): void {
    this.load(this.page() + 1);
  }

  protected rejection(reason: string): string {
    return REJECTIONS[reason] ?? reason;
  }

  protected cancel(transfer: Transfer): void {
    this.api.cancelTransfer(transfer.id).subscribe({
      next: (updated) => {
        this.transfers.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
        this.selected.set(updated);
        this.toasts.success('Transferencia cancelada');
      },
      error: (error) => this.toasts.error('No se ha podido cancelar', toApiError(error).message),
    });
  }

  private load(page: number): void {
    this.loading.set(true);
    this.api.transfers(page, PAGE_SIZE).subscribe({
      next: (result) => {
        this.transfers.update((list) => (page === 0 ? result.items : [...list, ...result.items]));
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalItems.set(result.totalItems);
        this.loading.set(false);
        // El resumen del mes necesita todas las del mes: si la última cargada aún es de este mes, se pide otra página.
        const last = result.items[result.items.length - 1];
        if (
          last &&
          new Date(last.createdAt) >= startOfMonth() &&
          result.page + 1 < result.totalPages
        ) {
          this.load(result.page + 1);
        }
      },
      error: () => this.loading.set(false),
    });
  }
}
