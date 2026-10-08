import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';
import { BankApi } from '../../core/api';
import { ApiError, toApiError } from '../../core/api-error';
import { initials, parseAmount } from '../../core/format';
import { formatIban, isValidIban, normalizeIban, shortIban } from '../../core/iban';
import { Account, IbanCheck, Payee, Transfer } from '../../core/models';
import { IbanPipe, MoneyPipe } from '../../core/pipes';
import { Icon } from '../../shared/icon';

type Step = 'recipient' | 'amount' | 'review' | 'sca' | 'done';

interface Recipient {
  name: string;
  iban: string;
  /** Beneficiario guardado del que viene, si lo hay. */
  payeeId?: string;
  /** Es una cuenta propia (hucha o cuenta principal). */
  own: boolean;
  /** La cuenta es de Hucha: llega al instante. */
  internal: boolean;
  saved: boolean;
}

/** Mismos umbrales que el backend (hucha.transfers.*) para avisar antes de enviar. */
const MAX_AMOUNT = 10_000;
const SCA_THRESHOLD = 1_000;
const SCA_NEW_PAYEE_THRESHOLD = 30;

@Component({
  selector: 'app-transfer-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, MoneyPipe, IbanPipe],
  templateUrl: './transfer-page.html',
  styleUrl: './transfer-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferPage {
  private readonly api = inject(BankApi);
  private readonly destroyRef = inject(DestroyRef);

  /** ?payee=<id> para empezar con un beneficiario ya elegido (desde la pantalla de beneficiarios). */
  readonly payee = input<string>();

  protected readonly steps: { id: Step; label: string }[] = [
    { id: 'recipient', label: 'Destinatario' },
    { id: 'amount', label: 'Importe' },
    { id: 'review', label: 'Revisar' },
    { id: 'sca', label: 'Verificar' },
  ];
  protected readonly step = signal<Step>('recipient');
  protected readonly stepIndex = computed(() => {
    const index = this.steps.findIndex((s) => s.id === this.step());
    return index === -1 ? this.steps.length : index;
  });

  protected readonly loading = signal(true);
  protected readonly accounts = signal<Account[]>([]);
  protected readonly payees = signal<Payee[]>([]);
  protected readonly payeeFilter = signal('');
  protected readonly recipient = signal<Recipient | null>(null);

  protected readonly filteredPayees = computed(() => {
    const filter = this.payeeFilter().trim().toLowerCase();
    return this.payees().filter(
      (p) =>
        !filter ||
        p.name.toLowerCase().includes(filter) ||
        (p.alias ?? '').toLowerCase().includes(filter) ||
        normalizeIban(p.iban).includes(normalizeIban(filter)),
    );
  });

  protected readonly activeAccounts = computed(() =>
    this.accounts().filter((a) => a.status === 'ACTIVE'),
  );

  // ---- Paso 1: destinatario nuevo ----
  protected readonly newRecipient = new FormGroup({
    iban: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
  });
  private readonly ibanValue = toSignal(this.newRecipient.controls.iban.valueChanges, {
    initialValue: '',
  });
  protected readonly ibanLooksValid = computed(() => isValidIban(this.ibanValue()));
  protected readonly ibanCheck = signal<IbanCheck | null>(null);
  protected readonly ownIban = computed(() => {
    const iban = normalizeIban(this.ibanValue());
    return this.accounts().find((a) => a.iban === iban) ?? null;
  });

  // ---- Paso 2: importe ----
  protected readonly details = new FormGroup({
    fromAccountId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    amount: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    concept: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(140)] }),
  });
  private readonly detailsValue = toSignal(this.details.valueChanges, {
    initialValue: this.details.getRawValue(),
  });
  protected readonly amount = computed(() => parseAmount(this.detailsValue().amount ?? ''));
  protected readonly fromAccount = computed(
    () => this.accounts().find((a) => a.id === this.detailsValue().fromAccountId) ?? null,
  );
  protected readonly amountError = computed(() => {
    const amount = this.amount();
    const raw = this.detailsValue().amount ?? '';
    if (!raw) return null;
    if (amount === null) return 'Escribe un importe válido, con dos decimales como mucho.';
    if (amount <= 0) return 'El importe tiene que ser mayor que cero.';
    if (amount > MAX_AMOUNT)
      return `El máximo por transferencia es ${MAX_AMOUNT.toLocaleString('es-ES')} €.`;
    const from = this.fromAccount();
    if (from && amount > from.balance) return 'No tienes saldo suficiente en esa cuenta.';
    return null;
  });
  protected readonly canReview = computed(
    () =>
      this.amount() !== null &&
      (this.amount() ?? 0) > 0 &&
      !this.amountError() &&
      !!this.fromAccount(),
  );
  protected readonly needsSca = computed(() => {
    const recipient = this.recipient();
    const amount = this.amount() ?? 0;
    if (!recipient || recipient.own) return false;
    return amount >= SCA_THRESHOLD || (amount > SCA_NEW_PAYEE_THRESHOLD && !recipient.saved);
  });
  protected readonly quickAmounts = [10, 20, 50, 100];

  // ---- Paso 3 y 4: envío y verificación ----
  protected readonly savePayee = new FormControl(true, { nonNullable: true });
  protected readonly sending = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly transfer = signal<Transfer | null>(null);
  protected readonly code = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
  });
  protected readonly now = signal(Date.now());
  protected readonly secondsLeft = computed(() => {
    const expires = this.transfer()?.scaExpiresAt;
    return expires ? Math.max(0, Math.floor((new Date(expires).getTime() - this.now()) / 1000)) : 0;
  });
  protected readonly countdown = computed(() => {
    const seconds = this.secondsLeft();
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });
  protected readonly payeeSaved = signal(false);

  /**
   * Clave de idempotencia de este envío. Se genera al llegar a "Revisar" y se mantiene en los
   * reintentos: si la respuesta se pierde por la red y se pulsa otra vez, el backend devuelve la
   * misma transferencia en vez de mandar el dinero dos veces.
   */
  private idempotencyKey = '';
  private timer: ReturnType<typeof setInterval> | undefined;

  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');

  protected readonly initials = initials;
  protected readonly shortIban = shortIban;

  constructor() {
    forkJoin({ accounts: this.api.accounts(), payees: this.api.payees() }).subscribe({
      next: ({ accounts, payees }) => {
        this.accounts.set(accounts);
        this.payees.set(payees);
        const main =
          accounts.find((a) => a.type === 'CHECKING' && a.status === 'ACTIVE') ?? accounts[0];
        if (main) this.details.controls.fromAccountId.setValue(main.id);
        const preselected = payees.find((p) => p.id === this.payee());
        if (preselected) this.choosePayee(preselected);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    // Comprobación del IBAN en el servidor (¿es de Hucha? ¿ya está guardado?) solo cuando el formato es válido.
    toObservable(this.ibanValue)
      .pipe(
        map((value) => normalizeIban(value)),
        distinctUntilChanged(),
        debounceTime(300),
        switchMap((iban) =>
          isValidIban(iban) ? this.api.checkIban(iban).pipe(catchError(() => of(null))) : of(null),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((check) => this.ibanCheck.set(check));

    // Cualquier cambio en los datos invalida la clave: es otra transferencia distinta.
    this.details.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => (this.idempotencyKey = ''));

    effect(() => {
      if (this.step() === 'sca') {
        queueMicrotask(() => this.codeInput()?.nativeElement.focus());
      }
    });
    this.destroyRef.onDestroy(() => clearInterval(this.timer));
  }

  // ---- Navegación entre pasos ----

  protected choosePayee(payee: Payee): void {
    this.selectRecipient({
      name: payee.name,
      iban: payee.iban,
      payeeId: payee.id,
      own: false,
      internal: payee.internal,
      saved: true,
    });
  }

  protected chooseOwn(account: Account): void {
    this.selectRecipient({
      name: account.name,
      iban: account.iban,
      own: true,
      internal: true,
      saved: true,
    });
    // Si el destino es la cuenta principal, lo normal es sacar dinero de una hucha y al revés.
    if (this.details.controls.fromAccountId.value === account.id) {
      const other = this.activeAccounts().find((a) => a.id !== account.id);
      if (other) this.details.controls.fromAccountId.setValue(other.id);
    }
  }

  protected useNewRecipient(): void {
    if (this.newRecipient.invalid || !this.ibanLooksValid()) {
      this.newRecipient.markAllAsTouched();
      return;
    }
    const iban = normalizeIban(this.newRecipient.controls.iban.value);
    const own = this.ownIban();
    if (own) {
      this.chooseOwn(own);
      return;
    }
    const check = this.ibanCheck();
    const saved = this.payees().find((p) => p.iban === iban);
    this.selectRecipient({
      name: this.newRecipient.controls.name.value.trim(),
      iban,
      payeeId: saved?.id,
      own: false,
      internal: check?.internal ?? false,
      saved: !!saved,
    });
  }

  private selectRecipient(recipient: Recipient): void {
    this.recipient.set(recipient);
    this.error.set(null);
    this.idempotencyKey = '';
    this.step.set('amount');
  }

  protected formatIbanInput(): void {
    const control = this.newRecipient.controls.iban;
    const formatted = formatIban(control.value);
    if (formatted !== control.value) {
      control.setValue(formatted, { emitEvent: true });
    }
  }

  protected setQuickAmount(value: number): void {
    this.details.controls.amount.setValue(String(value));
  }

  protected review(): void {
    if (!this.canReview()) {
      this.details.markAllAsTouched();
      return;
    }
    this.details.controls.amount.setValue(this.amount()!.toFixed(2).replace('.', ','), {
      emitEvent: false,
    });
    this.idempotencyKey ||= crypto.randomUUID();
    this.error.set(null);
    this.step.set('review');
  }

  protected back(): void {
    const order: Step[] = ['recipient', 'amount', 'review'];
    const index = order.indexOf(this.step());
    if (index > 0) {
      this.error.set(null);
      this.step.set(order[index - 1]!);
    }
  }

  // ---- Envío ----

  protected send(): void {
    const recipient = this.recipient();
    const amount = this.amount();
    if (!recipient || amount === null || this.sending()) return;
    this.idempotencyKey ||= crypto.randomUUID();
    this.sending.set(true);
    this.error.set(null);
    this.api
      .createTransfer(
        {
          fromAccountId: this.details.controls.fromAccountId.value,
          toIban: recipient.iban,
          beneficiaryName: recipient.name,
          amount,
          concept: this.details.controls.concept.value.trim() || undefined,
        },
        this.idempotencyKey,
      )
      .subscribe({
        next: (transfer) => {
          this.sending.set(false);
          this.transfer.set(transfer);
          if (transfer.status === 'PENDING_SCA') {
            this.code.reset();
            this.startTimer();
            this.step.set('sca');
          } else {
            this.finish(transfer);
          }
        },
        error: (error) => {
          this.sending.set(false);
          const apiError = toApiError(error);
          this.error.set(apiError);
          // Si el servidor ha contestado con un error de negocio, esa clave ya está "gastada".
          if (apiError.status >= 400 && apiError.status < 500) this.idempotencyKey = '';
        },
      });
  }

  protected confirm(): void {
    const transfer = this.transfer();
    if (!transfer || this.code.invalid || this.sending()) {
      this.code.markAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.api.confirmTransfer(transfer.id, this.code.value).subscribe({
      next: (result) => {
        this.sending.set(false);
        this.finish(result);
      },
      error: (error) => {
        this.sending.set(false);
        const apiError = toApiError(error);
        this.error.set(apiError);
        this.code.reset();
        this.codeInput()?.nativeElement.focus();
        // Demasiados intentos o código caducado: la transferencia ya no se puede confirmar.
        if (apiError.code === 'SCA_TOO_MANY_ATTEMPTS' || apiError.code === 'SCA_CODE_EXPIRED') {
          this.transfer.update((t) =>
            t ? { ...t, status: 'REJECTED', rejectionReason: apiError.code } : t,
          );
          this.finish(this.transfer()!);
        }
      },
    });
  }

  protected cancel(): void {
    const transfer = this.transfer();
    if (!transfer) return;
    this.api.cancelTransfer(transfer.id).subscribe({
      next: (result) => this.finish(result),
      error: () => this.restart(),
    });
  }

  protected onCodeInput(): void {
    const digits = this.code.value.replace(/\D/g, '').slice(0, 6);
    if (digits !== this.code.value) this.code.setValue(digits);
    if (digits.length === 6) this.confirm();
  }

  private finish(transfer: Transfer): void {
    clearInterval(this.timer);
    this.transfer.set(transfer);
    this.idempotencyKey = '';
    this.step.set('done');
    const recipient = this.recipient();
    if (transfer.status === 'COMPLETED' && recipient && !recipient.saved && this.savePayee.value) {
      this.api.addPayee(recipient.name, recipient.iban).subscribe({
        next: () => this.payeeSaved.set(true),
        error: () => undefined,
      });
    }
  }

  protected restart(): void {
    clearInterval(this.timer);
    this.transfer.set(null);
    this.recipient.set(null);
    this.error.set(null);
    this.payeeSaved.set(false);
    this.newRecipient.reset();
    this.details.controls.amount.reset();
    this.details.controls.concept.reset();
    this.idempotencyKey = '';
    this.step.set('recipient');
    this.api.payees().subscribe((payees) => this.payees.set(payees));
    this.api.accounts().subscribe((accounts) => this.accounts.set(accounts));
  }

  private startTimer(): void {
    clearInterval(this.timer);
    this.now.set(Date.now());
    this.timer = setInterval(() => this.now.set(Date.now()), 1000);
  }
}
