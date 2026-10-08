import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BankApi } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { DECLINE_REASONS, SPENDING_CATEGORIES, categoryMeta, parseAmount } from '../../core/format';
import { Card, CardTransaction, Category, DeclineReason } from '../../core/models';
import { MoneyPipe, ShortDatePipe, TimePipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';

const SAMPLE_MERCHANTS: Record<string, Category> = {
  'Supermercado La Plaza': 'GROCERIES',
  'Bar Casa Paco': 'RESTAURANTS',
  Cabify: 'TRANSPORT',
  'Tienda Moda Urbana': 'SHOPPING',
  'Cine Lumière': 'LEISURE',
};

@Component({
  selector: 'app-cards-page',
  imports: [ReactiveFormsModule, Icon, Modal, MoneyPipe, ShortDatePipe, TimePipe],
  templateUrl: './cards-page.html',
  styleUrl: './cards-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardsPage {
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly cards = signal<Card[]>([]);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly liveCards = computed(() =>
    this.cards().filter((c) => c.status !== 'CANCELLED'),
  );
  protected readonly card = computed(
    () => this.cards().find((c) => c.id === this.selectedId()) ?? this.liveCards()[0] ?? null,
  );
  protected readonly spentPercent = computed(() => {
    const card = this.card();
    return card ? Math.min(100, Math.round((card.spentThisMonth / card.monthlyLimit) * 100)) : 0;
  });

  protected readonly updating = signal(false);
  protected readonly issuing = signal(false);
  protected readonly limitDraft = signal(0);
  protected readonly confirmCancel = signal(false);

  protected readonly transactions = signal<CardTransaction[]>([]);
  protected readonly txPage = signal(0);
  protected readonly txTotalPages = signal(0);

  protected readonly categories = SPENDING_CATEGORIES;
  protected readonly purchase = new FormGroup({
    merchant: new FormControl('Supermercado La Plaza', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    category: new FormControl<Category>('GROCERIES', { nonNullable: true }),
    amount: new FormControl('24,90', { nonNullable: true, validators: [Validators.required] }),
    online: new FormControl(false, { nonNullable: true }),
  });
  protected readonly simulating = signal(false);
  protected readonly lastResult = signal<CardTransaction | null>(null);

  constructor() {
    this.api.cards().subscribe({
      next: (cards) => {
        this.cards.set(cards);
        this.loading.set(false);
        const card = this.card();
        if (card) this.select(card.id);
      },
      error: () => this.loading.set(false),
    });
    // Al escribir un comercio conocido se propone su categoría.
    this.purchase.controls.merchant.valueChanges.subscribe((merchant) => {
      const category = SAMPLE_MERCHANTS[merchant.trim()];
      if (category) this.purchase.controls.category.setValue(category);
    });
  }

  protected select(id: string): void {
    this.selectedId.set(id);
    const card = this.card();
    this.limitDraft.set(card?.monthlyLimit ?? 0);
    this.lastResult.set(null);
    this.loadTransactions(id, 0);
  }

  protected toggleFrozen(card: Card, event: Event): void {
    const message = card.status === 'FROZEN' ? 'Tarjeta reactivada' : 'Tarjeta congelada';
    this.update(
      card,
      { frozen: card.status !== 'FROZEN' },
      message,
      event.target as HTMLInputElement,
    );
  }

  protected toggleOnline(card: Card, event: Event): void {
    const message = card.onlineEnabled ? 'Compras online desactivadas' : 'Compras online activadas';
    this.update(
      card,
      { onlineEnabled: !card.onlineEnabled },
      message,
      event.target as HTMLInputElement,
    );
  }

  protected saveLimit(card: Card): void {
    this.update(card, { monthlyLimit: this.limitDraft() }, 'Límite mensual actualizado');
  }

  protected cancelCard(): void {
    const card = this.card();
    if (!card) return;
    this.api.cancelCard(card.id).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.confirmCancel.set(false);
        this.toasts.success('Tarjeta cancelada');
        const next = this.liveCards()[0];
        if (next) this.select(next.id);
      },
      error: (error) => this.toasts.error('No se ha podido cancelar', toApiError(error).message),
    });
  }

  protected issue(): void {
    this.issuing.set(true);
    this.api.issueCard().subscribe({
      next: (card) => {
        this.issuing.set(false);
        this.cards.update((list) => [...list, card]);
        this.select(card.id);
        this.toasts.success(
          'Nueva tarjeta virtual lista',
          `Termina en ${card.last4}. Ya puedes usarla.`,
        );
      },
      error: (error) => {
        this.issuing.set(false);
        this.toasts.error('No se ha podido emitir la tarjeta', toApiError(error).message);
      },
    });
  }

  protected simulate(card: Card): void {
    const { merchant, category, amount, online } = this.purchase.getRawValue();
    const parsed = parseAmount(amount);
    if (!merchant.trim() || parsed === null || parsed <= 0) {
      this.toasts.error('Revisa el comercio y el importe');
      return;
    }
    this.simulating.set(true);
    this.api
      .simulatePurchase(card.id, { merchant: merchant.trim(), category, amount: parsed, online })
      .subscribe({
        next: (tx) => {
          this.simulating.set(false);
          this.lastResult.set(tx);
          this.transactions.update((list) => [tx, ...list]);
          if (tx.status === 'APPROVED') {
            this.cards.update((list) =>
              list.map((c) =>
                c.id === card.id ? { ...c, spentThisMonth: c.spentThisMonth + tx.amount } : c,
              ),
            );
          }
        },
        error: (error) => {
          this.simulating.set(false);
          this.toasts.error('No se ha podido simular el pago', toApiError(error).message);
        },
      });
  }

  protected moreTransactions(card: Card): void {
    this.loadTransactions(card.id, this.txPage() + 1);
  }

  protected pad(month: number): string {
    return String(month).padStart(2, '0');
  }

  protected statusLabel(card: Card): string {
    return card.status === 'ACTIVE'
      ? 'activa'
      : card.status === 'FROZEN'
        ? 'congelada'
        : 'cancelada';
  }

  protected declineLabel(reason: DeclineReason | undefined): string {
    return reason ? DECLINE_REASONS[reason] : 'Denegada';
  }

  /** El backend explica el motivo en minúscula ("la tarjeta está congelada"); aquí va al principio de frase. */
  protected declineText(tx: CardTransaction): string {
    const message = tx.declineMessage ?? this.declineLabel(tx.declineReason);
    return message.charAt(0).toUpperCase() + message.slice(1) + (message.endsWith('.') ? '' : '.');
  }

  protected categoryLabel(category: Category): string {
    return categoryMeta(category).label;
  }

  protected categoryColor(category: Category): string {
    return categoryMeta(category).color;
  }

  protected categoryIcon(category: Category): string {
    return categoryMeta(category).icon;
  }

  private update(
    card: Card,
    change: Parameters<BankApi['updateCard']>[1],
    message: string,
    toggle?: HTMLInputElement,
  ): void {
    this.updating.set(true);
    this.api.updateCard(card.id, change).subscribe({
      next: (updated) => {
        this.updating.set(false);
        this.replace(updated);
        this.limitDraft.set(updated.monthlyLimit);
        this.toasts.success(message);
      },
      error: (error) => {
        this.updating.set(false);
        // El interruptor ya cambió en el DOM: se devuelve a su sitio para que no se quede mintiendo.
        if (toggle) toggle.checked = !toggle.checked;
        this.toasts.error('No se ha podido guardar', toApiError(error).message);
      },
    });
  }

  private replace(updated: Card): void {
    this.cards.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
  }

  private loadTransactions(cardId: string, page: number): void {
    this.api.cardTransactions(cardId, page, 15).subscribe((result) => {
      this.transactions.update((list) => (page === 0 ? result.items : [...list, ...result.items]));
      this.txPage.set(result.page);
      this.txTotalPages.set(result.totalPages);
    });
  }
}
