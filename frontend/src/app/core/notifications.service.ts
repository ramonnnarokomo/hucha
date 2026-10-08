import { Injectable, effect, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { BankApi } from './api';
import { AuthService } from './auth.service';
import { AppNotification } from './models';
import { ToastService } from './toast.service';

/**
 * Avisos en tiempo real por Server-Sent Events. La conexión se abre al iniciar sesión y se rehace
 * cuando se renueva el token (EventSource no permite cabeceras, el token va en la URL).
 */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly auth = inject(AuthService);
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);

  readonly unread = signal(0);
  readonly connected = signal(false);
  /** Cada aviso que llega en vivo, para que las pantallas abiertas se refresquen. */
  readonly incoming = new Subject<AppNotification>();

  private source: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const token = this.auth.accessToken();
      if (token) {
        this.connect(token);
        this.refreshUnread();
      } else {
        this.disconnect();
        this.unread.set(0);
      }
    });
  }

  refreshUnread(): void {
    this.api
      .unreadCount()
      .subscribe({ next: (result) => this.unread.set(result.count), error: () => undefined });
  }

  markAllRead(): void {
    this.api.markAllRead().subscribe(() => this.unread.set(0));
  }

  private connect(token: string): void {
    this.disconnect();
    if (typeof EventSource === 'undefined') {
      return;
    }
    const source = new EventSource(
      `/api/notifications/stream?access_token=${encodeURIComponent(token)}`,
    );
    source.addEventListener('connected', () => this.connected.set(true));
    source.addEventListener('notification', (event) => {
      const notification = JSON.parse((event as MessageEvent<string>).data) as AppNotification;
      this.unread.update((count) => count + 1);
      this.incoming.next(notification);
      this.toasts.show(
        notification.title,
        notification.body,
        notification.type === 'CARD_DECLINED' ? 'error' : 'info',
      );
    });
    source.onerror = () => {
      // El navegador reintenta solo, pero con el mismo token; se reconecta con el actual.
      this.connected.set(false);
      source.close();
      clearTimeout(this.retryTimer);
      this.retryTimer = setTimeout(() => {
        const current = this.auth.accessToken();
        if (current && this.source === source) {
          this.connect(current);
        }
      }, 5000);
    };
    this.source = source;
  }

  private disconnect(): void {
    clearTimeout(this.retryTimer);
    this.source?.close();
    this.source = null;
    this.connected.set(false);
  }
}
