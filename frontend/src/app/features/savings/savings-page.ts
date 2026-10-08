import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, switchMap } from 'rxjs';
import { BankApi } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { SAVINGS_ICONS, parseAmount, savingsColor } from '../../core/format';
import { Account, SavingsIcon } from '../../core/models';
import { MoneyPipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';

type Direction = 'deposit' | 'withdraw';

@Component({
  selector: 'app-savings-page',
  imports: [ReactiveFormsModule, Icon, Modal, MoneyPipe],
  templateUrl: './savings-page.html',
  styleUrl: './savings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SavingsPage {
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly pots = signal<Account[]>([]);
  protected readonly checking = signal<Account | null>(null);

  protected readonly totalSaved = computed(() =>
    this.pots().reduce((sum, p) => sum + p.balance, 0),
  );
  protected readonly totalGoals = computed(() =>
    this.pots().reduce((sum, p) => sum + (p.goalAmount ?? 0), 0),
  );
  protected readonly overallProgress = computed(() => {
    const withGoal = this.pots().filter((p) => p.goalAmount);
    const goals = withGoal.reduce((sum, p) => sum + p.goalAmount!, 0);
    const saved = withGoal.reduce((sum, p) => sum + Math.min(p.balance, p.goalAmount!), 0);
    return goals ? Math.round((saved / goals) * 100) : 0;
  });

  // Crear / editar
  protected readonly icons = SAVINGS_ICONS;
  protected readonly formOpen = signal(false);
  protected readonly editing = signal<Account | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(40)],
    }),
    goal: new FormControl('', { nonNullable: true }),
    icon: new FormControl<SavingsIcon>('piggy', { nonNullable: true }),
  });
  private readonly goalValue = toSignal(this.form.controls.goal.valueChanges, { initialValue: '' });
  protected readonly goalError = computed(() => {
    const raw = this.goalValue();
    if (!raw) return null;
    const goal = parseAmount(raw);
    if (goal === null) return 'Escribe una cantidad válida.';
    if (goal < 1) return 'El objetivo tiene que ser de al menos 1 €.';
    if (goal > 1_000_000) return 'Como mucho 1.000.000 €.';
    return null;
  });

  // Meter / sacar
  protected readonly moving = signal<{ pot: Account; direction: Direction } | null>(null);
  protected readonly moveAmount = new FormControl('', { nonNullable: true });
  private readonly moveValue = toSignal(this.moveAmount.valueChanges, { initialValue: '' });
  protected readonly moveParsed = computed(() => parseAmount(this.moveValue()));
  protected readonly moveError = computed(() => {
    const moving = this.moving();
    const raw = this.moveValue();
    if (!moving || !raw) return null;
    const amount = this.moveParsed();
    if (amount === null || amount <= 0) return 'Escribe un importe válido.';
    const available =
      moving.direction === 'deposit' ? (this.checking()?.balance ?? 0) : moving.pot.balance;
    if (amount > available) {
      return moving.direction === 'deposit'
        ? 'No tienes tanto saldo en tu cuenta.'
        : 'La hucha no tiene tanto dinero.';
    }
    return null;
  });
  protected readonly moveValid = computed(() => (this.moveParsed() ?? 0) > 0 && !this.moveError());
  protected readonly moveTitle = computed(() => {
    const moving = this.moving();
    if (!moving) return '';
    return moving.direction === 'deposit'
      ? `Meter en «${moving.pot.name}»`
      : `Sacar de «${moving.pot.name}»`;
  });

  // Cerrar
  protected readonly closing = signal<Account | null>(null);

  constructor() {
    this.load();
  }

  protected color(pot: Account): string {
    return savingsColor(pot.icon);
  }

  protected progress(pot: Account): number {
    return pot.goalAmount ? Math.min(100, Math.floor((pot.balance / pot.goalAmount) * 100)) : 0;
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset({ name: '', goal: '', icon: 'piggy' });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(pot: Account): void {
    this.editing.set(pot);
    this.form.reset({
      name: pot.name,
      goal: pot.goalAmount ? String(pot.goalAmount).replace('.', ',') : '',
      icon: pot.icon ?? 'piggy',
    });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid || this.goalError()) {
      this.form.markAllAsTouched();
      return;
    }
    const { name, goal, icon } = this.form.getRawValue();
    const goalAmount = goal ? parseAmount(goal) : null;
    const editing = this.editing();
    const request = editing
      ? this.api.updateSavings(editing.id, name.trim(), goalAmount, icon)
      : this.api.createSavings(name.trim(), goalAmount, icon);
    this.saving.set(true);
    this.formError.set(null);
    request.subscribe({
      next: (pot) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.pots.update((list) =>
          editing ? list.map((p) => (p.id === pot.id ? pot : p)) : [...list, pot],
        );
        this.toasts.success(editing ? 'Hucha actualizada' : `Hucha «${pot.name}» creada`);
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(toApiError(error).message);
      },
    });
  }

  protected openMove(pot: Account, direction: Direction): void {
    this.moveAmount.reset('');
    this.moving.set({ pot, direction });
  }

  protected move(): void {
    const moving = this.moving();
    const checking = this.checking();
    const amount = this.moveParsed();
    if (!moving || !checking || amount === null || !this.moveValid()) return;
    this.saving.set(true);
    this.api.moveSavings(moving.pot.id, moving.direction, checking.id, amount).subscribe({
      next: () => {
        this.saving.set(false);
        this.moving.set(null);
        this.toasts.success(
          moving.direction === 'deposit'
            ? 'Dinero guardado en la hucha'
            : 'Dinero devuelto a tu cuenta',
        );
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.toasts.error('No se ha podido mover el dinero', toApiError(error).message);
      },
    });
  }

  protected askClose(pot: Account): void {
    this.formOpen.set(false);
    this.closing.set(pot);
  }

  /** Si la hucha tiene dinero, primero se devuelve a la cuenta y después se cierra. */
  protected closePot(pot: Account): void {
    const checking = this.checking();
    if (!checking) return;
    this.saving.set(true);
    const close$ =
      pot.balance > 0
        ? this.api
            .moveSavings(pot.id, 'withdraw', checking.id, pot.balance)
            .pipe(switchMap(() => this.api.closeSavings(pot.id)))
        : this.api.closeSavings(pot.id);
    close$.subscribe({
      next: () => {
        this.saving.set(false);
        this.closing.set(null);
        this.toasts.success(`Hucha «${pot.name}» cerrada`);
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.toasts.error('No se ha podido cerrar', toApiError(error).message);
        this.load();
      },
    });
  }

  private load(): void {
    forkJoin({ pots: this.api.savings(), accounts: this.api.accounts() }).subscribe({
      next: ({ pots, accounts }) => {
        this.pots.set(pots);
        this.checking.set(accounts.find((a) => a.type === 'CHECKING') ?? null);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
