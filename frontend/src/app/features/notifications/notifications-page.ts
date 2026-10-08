import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BankApi } from '../../core/api';
import { AppNotification, NotificationType } from '../../core/models';
import { NotificationsService } from '../../core/notifications.service';
import { TimeAgoPipe } from '../../core/pipes';
import { Icon } from '../../shared/icon';

const LOOKS: Record<NotificationType, { icon: string; tone: 'good' | 'bad' | 'warn' | '' }> = {
  WELCOME: { icon: 'gift', tone: 'good' },
  SCA_CODE: { icon: 'lock', tone: '' },
  TRANSFER_RECEIVED: { icon: 'download', tone: 'good' },
  TRANSFER_COMPLETED: { icon: 'send', tone: '' },
  CARD_DECLINED: { icon: 'card', tone: 'bad' },
  ACCOUNT_FROZEN: { icon: 'snowflake', tone: 'bad' },
  ACCOUNT_UNFROZEN: { icon: 'check', tone: 'good' },
  SECURITY: { icon: 'shield', tone: 'warn' },
};

@Component({
  selector: 'app-notifications-page',
  imports: [Icon, TimeAgoPipe],
  templateUrl: './notifications-page.html',
  styleUrl: './notifications-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsPage {
  private readonly api = inject(BankApi);
  protected readonly service = inject(NotificationsService);

  protected readonly items = signal<AppNotification[]>([]);
  protected readonly loading = signal(true);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalItems = signal(0);
  protected readonly onlyUnread = signal(false);
  protected readonly visible = computed(() =>
    this.onlyUnread() ? this.items().filter((n) => !n.read) : this.items(),
  );

  constructor() {
    this.load(0);
    // Los que llegan en vivo se ponen arriba sin recargar.
    this.service.incoming.pipe(takeUntilDestroyed()).subscribe((notification) => {
      this.items.update((list) => [notification, ...list.filter((n) => n.id !== notification.id)]);
      this.totalItems.update((count) => count + 1);
    });
  }

  protected look(notification: AppNotification) {
    return LOOKS[notification.type] ?? { icon: 'bell', tone: '' };
  }

  protected open(notification: AppNotification): void {
    if (notification.read) return;
    this.api.markRead(notification.id).subscribe(() => {
      this.items.update((list) =>
        list.map((n) => (n.id === notification.id ? { ...n, read: true } : n)),
      );
      this.service.unread.update((count) => Math.max(0, count - 1));
    });
  }

  protected markAll(): void {
    this.service.markAllRead();
    this.items.update((list) => list.map((n) => ({ ...n, read: true })));
  }

  protected loadMore(): void {
    this.load(this.page() + 1);
  }

  private load(page: number): void {
    this.loading.set(true);
    this.api.notifications(page, 20).subscribe({
      next: (result) => {
        this.items.update((list) => (page === 0 ? result.items : [...list, ...result.items]));
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalItems.set(result.totalItems);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
