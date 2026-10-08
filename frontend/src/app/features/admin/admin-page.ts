import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { debounceTime, skip, switchMap } from 'rxjs';
import { AdminApi } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { describeDevice } from '../../core/device';
import { initials } from '../../core/format';
import { Account, AdminOverview, AdminUser, AuditEntry } from '../../core/models';
import { IbanPipe, MoneyPipe, ShortDatePipe, TimePipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { BarChart, BarGroup } from '../../shared/charts/bar-chart';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';

type Tab = 'overview' | 'users' | 'audit';

const ACTIONS: Record<string, { label: string; tone: 'ok' | 'bad' | 'warn' | 'brand' | '' }> = {
  USER_REGISTERED: { label: 'Alta de cliente', tone: 'brand' },
  LOGIN_SUCCEEDED: { label: 'Inicio de sesión', tone: '' },
  LOGIN_FAILED: { label: 'Login fallido', tone: 'warn' },
  LOGIN_LOCKED: { label: 'Login bloqueado', tone: 'bad' },
  REFRESH_TOKEN_REUSE: { label: 'Reutilización de sesión', tone: 'bad' },
  LOGOUT: { label: 'Cierre de sesión', tone: '' },
  PASSWORD_CHANGED: { label: 'Cambio de contraseña', tone: 'warn' },
  SESSION_REVOKED: { label: 'Sesión cerrada', tone: '' },
  TRANSFER_PENDING_SCA: { label: 'Transferencia pendiente', tone: 'warn' },
  TRANSFER_COMPLETED: { label: 'Transferencia', tone: 'ok' },
  TRANSFER_REJECTED: { label: 'Transferencia rechazada', tone: 'bad' },
  TRANSFER_CANCELLED: { label: 'Transferencia cancelada', tone: '' },
  SCA_FAILED: { label: 'Código SCA erróneo', tone: 'warn' },
  PAYEE_ADDED: { label: 'Beneficiario añadido', tone: '' },
  PAYEE_REMOVED: { label: 'Beneficiario eliminado', tone: '' },
  CARD_ISSUED: { label: 'Tarjeta emitida', tone: 'brand' },
  CARD_UPDATED: { label: 'Tarjeta modificada', tone: '' },
  CARD_CANCELLED: { label: 'Tarjeta cancelada', tone: 'warn' },
  CARD_PURCHASE_DECLINED: { label: 'Pago denegado', tone: 'bad' },
  SAVINGS_CREATED: { label: 'Hucha creada', tone: 'brand' },
  SAVINGS_CLOSED: { label: 'Hucha cerrada', tone: '' },
  STATEMENT_DOWNLOADED: { label: 'Extracto descargado', tone: '' },
  ACCOUNT_FROZEN: { label: 'Cuenta congelada', tone: 'bad' },
  ACCOUNT_UNFROZEN: { label: 'Cuenta descongelada', tone: 'ok' },
  USER_BLOCKED: { label: 'Usuario bloqueado', tone: 'bad' },
  USER_UNBLOCKED: { label: 'Usuario desbloqueado', tone: 'ok' },
};

const WEEKDAY = new Intl.DateTimeFormat('es-ES', { weekday: 'short' });

@Component({
  selector: 'app-admin-page',
  imports: [Icon, Modal, BarChart, MoneyPipe, IbanPipe, ShortDatePipe, TimePipe],
  templateUrl: './admin-page.html',
  styleUrl: './admin-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPage {
  private readonly api = inject(AdminApi);
  private readonly toasts = inject(ToastService);

  protected readonly tab = signal<Tab>('overview');

  // Resumen
  protected readonly overview = signal<AdminOverview | null>(null);
  protected readonly volumeBars = computed<BarGroup[]>(() =>
    (this.overview()?.last7Days ?? []).map((d) => ({
      label: WEEKDAY.format(new Date(d.date + 'T12:00:00')).replace('.', ''),
      values: [d.volume],
    })),
  );

  // Clientes
  protected readonly userQuery = signal('');
  protected readonly users = signal<AdminUser[]>([]);
  protected readonly usersPage = signal(0);
  protected readonly usersPages = signal(0);
  protected readonly usersTotal = signal(0);
  protected readonly detail = signal<{ user: AdminUser; accounts: Account[] } | null>(null);

  // Auditoría
  protected readonly auditActions = Object.entries(ACTIONS).map(
    ([key, value]) => [key, value.label] as const,
  );
  protected readonly auditAction = signal('');
  protected readonly audit = signal<AuditEntry[]>([]);
  protected readonly auditPage = signal(0);
  protected readonly auditPages = signal(0);
  protected readonly auditTotal = signal(0);

  protected readonly initials = initials;

  constructor() {
    this.loadUsers(0);
    this.loadAudit(0);

    toObservable(this.userQuery)
      .pipe(skip(1), debounceTime(300), takeUntilDestroyed())
      .subscribe(() => this.loadUsers(0));
    toObservable(this.auditAction)
      .pipe(skip(1), takeUntilDestroyed())
      .subscribe(() => this.loadAudit(0));

    // Al volver a una pestaña se refresca, por si algo cambió (bloqueos, nuevos eventos...).
    effect(() => {
      const tab = this.tab();
      untracked(() => {
        if (tab === 'overview')
          this.api.overview().subscribe((overview) => this.overview.set(overview));
        if (tab === 'audit') this.loadAudit(0);
      });
    });
  }

  protected actionLabel(action: string): string {
    return ACTIONS[action]?.label ?? action;
  }

  /** Los logins guardan el User-Agent completo: se resume a "Chrome en Windows" para que se lea. */
  protected detailText(entry: AuditEntry): string {
    const details = entry.details;
    if (!details) return '—';
    return entry.action.startsWith('LOGIN') && /Mozilla\/|curl\//.test(details)
      ? describeDevice(details).label
      : details;
  }

  protected actionTone(action: string): string {
    return ACTIONS[action]?.tone ?? '';
  }

  protected openUser(user: AdminUser): void {
    this.api.user(user.id).subscribe((detail) => this.detail.set(detail));
  }

  protected toggleBlocked(user: AdminUser): void {
    const block = user.status === 'ACTIVE';
    this.api.setBlocked(user.id, block).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.detail.update((d) => (d ? { ...d, user: updated } : d));
        this.toasts.success(block ? 'Usuario bloqueado' : 'Usuario desbloqueado');
      },
      error: (error) => this.toasts.error('No se ha podido cambiar', toApiError(error).message),
    });
  }

  protected toggleFrozen(account: Account): void {
    const freeze = account.status === 'ACTIVE';
    this.api.setFrozen(account.id, freeze).subscribe({
      next: (updated) => {
        this.detail.update((d) =>
          d ? { ...d, accounts: d.accounts.map((a) => (a.id === updated.id ? updated : a)) } : d,
        );
        this.toasts.success(
          freeze ? 'Cuenta congelada' : 'Cuenta descongelada',
          'El cliente recibe un aviso al momento.',
        );
      },
      error: (error) => this.toasts.error('No se ha podido cambiar', toApiError(error).message),
    });
  }

  protected moreUsers(): void {
    this.loadUsers(this.usersPage() + 1);
  }

  protected moreAudit(): void {
    this.loadAudit(this.auditPage() + 1);
  }

  private loadUsers(page: number): void {
    this.api.users(this.userQuery().trim(), page, 20).subscribe((result) => {
      this.users.update((list) => (page === 0 ? result.items : [...list, ...result.items]));
      this.usersPage.set(result.page);
      this.usersPages.set(result.totalPages);
      this.usersTotal.set(result.totalItems);
    });
  }

  private loadAudit(page: number): void {
    this.api.audit(this.auditAction(), page, 30).subscribe((result) => {
      this.audit.update((list) => (page === 0 ? result.items : [...list, ...result.items]));
      this.auditPage.set(result.page);
      this.auditPages.set(result.totalPages);
      this.auditTotal.set(result.totalItems);
    });
  }
}
