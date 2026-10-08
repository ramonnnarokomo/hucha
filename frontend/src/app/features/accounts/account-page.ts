import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, debounceTime, switchMap, tap } from 'rxjs';
import { BankApi, MovementFilters } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { recentMonths, saveBlob } from '../../core/download';
import { CATEGORIES, monthLong } from '../../core/format';
import { groupByDay } from '../../core/grouping';
import { Account, Category, Movement } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { IbanPipe, MoneyPipe, ShortDatePipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { CopyButton } from '../../shared/copy-button';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';
import { MovementDetail } from '../../shared/movement-detail';
import { MovementRow } from '../../shared/movement-row';

const PAGE_SIZE = 25;

@Component({
  selector: 'app-account-page',
  imports: [
    RouterLink,
    Icon,
    MoneyPipe,
    IbanPipe,
    ShortDatePipe,
    CopyButton,
    MovementRow,
    MovementDetail,
    Modal,
  ],
  templateUrl: './account-page.html',
  styleUrl: './account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountPage {
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);

  /** Id de la cuenta, viene del parámetro de ruta :id. */
  readonly id = input.required<string>();

  protected readonly account = signal<Account | null>(null);
  protected readonly notFound = signal(false);
  protected readonly movements = signal<Movement[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly loadingMore = signal(false);
  protected readonly selected = signal<Movement | null>(null);

  // Filtros
  protected readonly q = signal('');
  protected readonly direction = signal<'IN' | 'OUT' | null>(null);
  protected readonly category = signal<Category | ''>('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  private readonly reload = signal(0);

  protected readonly hasFilters = computed(
    () => !!(this.q().trim() || this.direction() || this.category() || this.from() || this.to()),
  );

  // Extracto
  protected readonly months = recentMonths(12);
  protected readonly month = signal(this.months[0]!);
  protected readonly downloading = signal<'pdf' | 'csv' | null>(null);

  protected readonly categories = Object.entries(CATEGORIES) as [
    Category,
    (typeof CATEGORIES)[Category],
  ][];
  protected readonly monthLong = monthLong;
  protected readonly groups = computed(() => groupByDay(this.movements(), (m) => m.occurredAt));

  private readonly filters = computed<MovementFilters>(() => ({
    q: this.q().trim() || undefined,
    direction: this.direction() ?? undefined,
    category: this.category() || undefined,
    from: this.from() || undefined,
    to: this.to() || undefined,
  }));

  private readonly query = computed(() => ({
    id: this.id(),
    filters: this.filters(),
    reload: this.reload(),
  }));

  constructor() {
    toObservable(this.id)
      .pipe(
        tap(() => this.notFound.set(false)),
        switchMap((id) =>
          this.api.account(id).pipe(
            catchError(() => {
              this.notFound.set(true);
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((account) => this.account.set(account));

    // Cada cambio de filtro relanza la búsqueda; el debounce evita una petición por tecla.
    toObservable(this.query)
      .pipe(
        debounceTime(250),
        tap(() => this.loading.set(true)),
        switchMap(({ id, filters }) =>
          this.api.movements(id, { ...filters, page: 0, size: PAGE_SIZE }).pipe(
            catchError(() => {
              this.loading.set(false);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.movements.set(page.items);
        this.page.set(page.page);
        this.totalPages.set(page.totalPages);
        this.totalItems.set(page.totalItems);
        this.loading.set(false);
      });

    // Si entra dinero mientras se mira la cuenta, se refresca saldo y lista.
    inject(NotificationsService)
      .incoming.pipe(takeUntilDestroyed())
      .subscribe((n) => {
        if (
          n.type === 'TRANSFER_RECEIVED' ||
          n.type === 'ACCOUNT_FROZEN' ||
          n.type === 'ACCOUNT_UNFROZEN'
        ) {
          this.api.account(this.id()).subscribe((account) => this.account.set(account));
          this.reload.update((value) => value + 1);
        }
      });
  }

  protected loadMore(): void {
    if (this.loadingMore() || this.page() + 1 >= this.totalPages()) {
      return;
    }
    this.loadingMore.set(true);
    this.api
      .movements(this.id(), { ...this.filters(), page: this.page() + 1, size: PAGE_SIZE })
      .subscribe({
        next: (page) => {
          this.movements.update((list) => [...list, ...page.items]);
          this.page.set(page.page);
          this.loadingMore.set(false);
        },
        error: () => this.loadingMore.set(false),
      });
  }

  protected clearFilters(): void {
    this.q.set('');
    this.direction.set(null);
    this.category.set('');
    this.from.set('');
    this.to.set('');
  }

  protected download(format: 'pdf' | 'csv'): void {
    const month = this.month();
    this.downloading.set(format);
    this.api.statement(this.id(), month, format).subscribe({
      next: (blob) => {
        saveBlob(blob, `hucha-extracto-${month}.${format}`);
        this.downloading.set(null);
      },
      error: (error) => {
        this.downloading.set(null);
        this.toasts.error('No se ha podido generar el extracto', toApiError(error).message);
      },
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
