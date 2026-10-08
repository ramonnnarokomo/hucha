import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BankApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { initials } from '../core/format';
import { AppNotification } from '../core/models';
import { NotificationsService } from '../core/notifications.service';
import { TimeAgoPipe } from '../core/pipes';
import { ThemeService } from '../core/theme.service';
import { Icon } from '../shared/icon';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact?: boolean;
  mobile?: boolean;
}

const CUSTOMER_NAV: NavItem[] = [
  { path: '/', label: 'Inicio', icon: 'home', exact: true, mobile: true },
  { path: '/transferir', label: 'Transferir', icon: 'send', mobile: true },
  { path: '/huchas', label: 'Huchas', icon: 'piggy', mobile: true },
  { path: '/tarjetas', label: 'Tarjetas', icon: 'card', mobile: true },
  { path: '/analitica', label: 'Analítica', icon: 'chart', mobile: true },
  { path: '/transferencias', label: 'Transferencias', icon: 'swap' },
  { path: '/beneficiarios', label: 'Beneficiarios', icon: 'users' },
  { path: '/avisos', label: 'Avisos', icon: 'bell' },
  { path: '/seguridad', label: 'Seguridad', icon: 'shield' },
];

const ADMIN_NAV: NavItem[] = [
  { path: '/admin', label: 'Back-office', icon: 'bank', mobile: true },
  { path: '/avisos', label: 'Avisos', icon: 'bell', mobile: true },
  { path: '/seguridad', label: 'Seguridad', icon: 'shield', mobile: true },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, TimeAgoPipe],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly notifications = inject(NotificationsService);
  private readonly api = inject(BankApi);

  protected readonly nav = computed(() => (this.auth.isAdmin() ? ADMIN_NAV : CUSTOMER_NAV));
  protected readonly mobileNav = computed(() => this.nav().filter((item) => item.mobile));
  protected readonly initials = computed(() => initials(this.auth.user()?.fullName));

  protected readonly panelOpen = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly latest = signal<AppNotification[]>([]);

  constructor() {
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.panelOpen.set(false);
        this.menuOpen.set(false);
      });
    this.notifications.incoming.pipe(takeUntilDestroyed()).subscribe((notification) => {
      this.latest.update((list) => [notification, ...list].slice(0, 6));
    });
  }

  protected togglePanel(): void {
    const open = !this.panelOpen();
    this.panelOpen.set(open);
    this.menuOpen.set(false);
    if (open) {
      this.api.notifications(0, 6).subscribe((page) => this.latest.set(page.items));
    }
  }

  protected markAllRead(): void {
    this.notifications.markAllRead();
    this.latest.update((list) => list.map((n) => ({ ...n, read: true })));
  }

  protected iconFor(notification: AppNotification): string {
    switch (notification.type) {
      case 'TRANSFER_RECEIVED':
        return 'arrow-in';
      case 'CARD_DECLINED':
        return 'card';
      case 'SCA_CODE':
        return 'lock';
      case 'ACCOUNT_FROZEN':
      case 'ACCOUNT_UNFROZEN':
        return 'snowflake';
      case 'SECURITY':
        return 'shield';
      default:
        return 'bell';
    }
  }
}
