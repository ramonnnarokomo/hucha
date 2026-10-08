import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account,
  AdminOverview,
  AdminUser,
  AnalyticsSummary,
  AppNotification,
  AuditEntry,
  Card,
  CardTransaction,
  Category,
  CreateTransfer,
  IbanCheck,
  Movement,
  Page,
  Payee,
  SavingsIcon,
  Session,
  Transfer,
  UserSummary,
} from './models';

export interface MovementFilters {
  q?: string;
  direction?: 'IN' | 'OUT';
  category?: Category;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

function params(values: object): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

@Injectable({ providedIn: 'root' })
export class BankApi {
  private readonly http = inject(HttpClient);

  // Cuentas y movimientos
  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  account(id: string): Observable<Account> {
    return this.http.get<Account>(`/api/accounts/${id}`);
  }

  movements(accountId: string, filters: MovementFilters): Observable<Page<Movement>> {
    return this.http.get<Page<Movement>>(`/api/accounts/${accountId}/movements`, {
      params: params(filters),
    });
  }

  statement(accountId: string, month: string, format: 'pdf' | 'csv'): Observable<Blob> {
    return this.http.get(`/api/accounts/${accountId}/statement`, {
      params: params({ month, format }),
      responseType: 'blob',
    });
  }

  // Transferencias
  createTransfer(request: CreateTransfer, idempotencyKey: string): Observable<Transfer> {
    return this.http.post<Transfer>('/api/transfers', request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  confirmTransfer(id: string, code: string): Observable<Transfer> {
    return this.http.post<Transfer>(`/api/transfers/${id}/confirm`, { code });
  }

  cancelTransfer(id: string): Observable<Transfer> {
    return this.http.post<Transfer>(`/api/transfers/${id}/cancel`, {});
  }

  transfers(page = 0, size = 20): Observable<Page<Transfer>> {
    return this.http.get<Page<Transfer>>('/api/transfers', { params: params({ page, size }) });
  }

  // Beneficiarios
  payees(): Observable<Payee[]> {
    return this.http.get<Payee[]>('/api/payees');
  }

  addPayee(name: string, iban: string, alias?: string): Observable<Payee> {
    return this.http.post<Payee>('/api/payees', { name, iban, alias });
  }

  updatePayee(id: string, name: string, alias?: string): Observable<Payee> {
    return this.http.put<Payee>(`/api/payees/${id}`, { name, alias });
  }

  deletePayee(id: string): Observable<void> {
    return this.http.delete<void>(`/api/payees/${id}`);
  }

  checkIban(iban: string): Observable<IbanCheck> {
    return this.http.get<IbanCheck>('/api/payees/check-iban', { params: params({ iban }) });
  }

  // Tarjetas
  cards(): Observable<Card[]> {
    return this.http.get<Card[]>('/api/cards');
  }

  issueCard(): Observable<Card> {
    return this.http.post<Card>('/api/cards', {});
  }

  updateCard(
    id: string,
    change: { frozen?: boolean; onlineEnabled?: boolean; monthlyLimit?: number },
  ): Observable<Card> {
    return this.http.patch<Card>(`/api/cards/${id}`, change);
  }

  cancelCard(id: string): Observable<Card> {
    return this.http.post<Card>(`/api/cards/${id}/cancel`, {});
  }

  simulatePurchase(
    id: string,
    purchase: { merchant: string; category: Category; amount: number; online: boolean },
  ): Observable<CardTransaction> {
    return this.http.post<CardTransaction>(`/api/cards/${id}/simulate-purchase`, purchase);
  }

  cardTransactions(id: string, page = 0, size = 20): Observable<Page<CardTransaction>> {
    return this.http.get<Page<CardTransaction>>(`/api/cards/${id}/transactions`, {
      params: params({ page, size }),
    });
  }

  // Huchas
  savings(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/savings');
  }

  createSavings(name: string, goalAmount: number | null, icon: SavingsIcon): Observable<Account> {
    return this.http.post<Account>('/api/savings', { name, goalAmount, icon });
  }

  updateSavings(
    id: string,
    name: string,
    goalAmount: number | null,
    icon: SavingsIcon,
  ): Observable<Account> {
    return this.http.put<Account>(`/api/savings/${id}`, { name, goalAmount, icon });
  }

  moveSavings(
    id: string,
    action: 'deposit' | 'withdraw',
    accountId: string,
    amount: number,
  ): Observable<Account> {
    return this.http.post<Account>(`/api/savings/${id}/${action}`, { accountId, amount });
  }

  closeSavings(id: string): Observable<void> {
    return this.http.delete<void>(`/api/savings/${id}`);
  }

  // Analítica
  analytics(months = 6): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>('/api/analytics/summary', {
      params: params({ months }),
    });
  }

  // Notificaciones
  notifications(page = 0, size = 20): Observable<Page<AppNotification>> {
    return this.http.get<Page<AppNotification>>('/api/notifications', {
      params: params({ page, size }),
    });
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>('/api/notifications/unread-count');
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`/api/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>('/api/notifications/read-all', {});
  }

  // Perfil y seguridad
  me(): Observable<UserSummary> {
    return this.http.get<UserSummary>('/api/me');
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>('/api/me/password', { currentPassword, newPassword });
  }

  sessions(): Observable<Session[]> {
    return this.http.get<Session[]>('/api/me/sessions');
  }

  revokeSession(id: string): Observable<void> {
    return this.http.delete<void>(`/api/me/sessions/${id}`);
  }

  revokeOtherSessions(): Observable<void> {
    return this.http.delete<void>('/api/me/sessions');
  }
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  overview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>('/api/admin/overview');
  }

  users(q: string, page = 0, size = 20): Observable<Page<AdminUser>> {
    return this.http.get<Page<AdminUser>>('/api/admin/users', {
      params: params({ q, page, size }),
    });
  }

  user(id: string): Observable<{ user: AdminUser; accounts: Account[] }> {
    return this.http.get<{ user: AdminUser; accounts: Account[] }>(`/api/admin/users/${id}`);
  }

  setBlocked(id: string, blocked: boolean): Observable<AdminUser> {
    return this.http.post<AdminUser>(`/api/admin/users/${id}/${blocked ? 'block' : 'unblock'}`, {});
  }

  setFrozen(accountId: string, frozen: boolean): Observable<Account> {
    return this.http.post<Account>(
      `/api/admin/accounts/${accountId}/${frozen ? 'freeze' : 'unfreeze'}`,
      {},
    );
  }

  audit(action: string, page = 0, size = 30): Observable<Page<AuditEntry>> {
    return this.http.get<Page<AuditEntry>>('/api/admin/audit', {
      params: params({ action, page, size }),
    });
  }
}
