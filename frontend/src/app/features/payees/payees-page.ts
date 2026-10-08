import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BankApi } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { initials } from '../../core/format';
import { isValidIban, normalizeIban } from '../../core/iban';
import { Payee } from '../../core/models';
import { IbanPipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';

@Component({
  selector: 'app-payees-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, Modal, IbanPipe],
  templateUrl: './payees-page.html',
  styleUrl: './payees-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayeesPage {
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);

  protected readonly payees = signal<Payee[]>([]);
  protected readonly loading = signal(true);
  protected readonly filter = signal('');
  protected readonly filtered = computed(() => {
    const filter = this.filter().trim().toLowerCase();
    const compact = normalizeIban(filter);
    return this.payees().filter(
      (p) =>
        !filter ||
        p.name.toLowerCase().includes(filter) ||
        (p.alias ?? '').toLowerCase().includes(filter) ||
        (compact.length > 2 && p.iban.includes(compact)),
    );
  });

  protected readonly formOpen = signal(false);
  protected readonly editing = signal<Payee | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly toDelete = signal<Payee | null>(null);

  protected readonly form = new FormGroup({
    iban: new FormControl('', { nonNullable: true }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    alias: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(60)] }),
  });
  private readonly ibanValue = toSignal(this.form.controls.iban.valueChanges, { initialValue: '' });
  protected readonly ibanValid = computed(() => isValidIban(this.ibanValue()));

  protected readonly initials = initials;

  constructor() {
    this.api.payees().subscribe({
      next: (payees) => {
        this.payees.set(payees);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset();
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(payee: Payee): void {
    this.editing.set(payee);
    this.form.reset({ iban: payee.iban, name: payee.name, alias: payee.alias ?? '' });
    this.formError.set(null);
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.formOpen.set(false);
  }

  protected save(): void {
    const editing = this.editing();
    if (this.form.invalid || (!editing && !this.ibanValid())) {
      this.form.markAllAsTouched();
      return;
    }
    const { iban, name, alias } = this.form.getRawValue();
    const request = editing
      ? this.api.updatePayee(editing.id, name.trim(), alias.trim() || undefined)
      : this.api.addPayee(name.trim(), normalizeIban(iban), alias.trim() || undefined);
    this.saving.set(true);
    this.formError.set(null);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.payees.update((list) =>
          editing
            ? list.map((p) => (p.id === saved.id ? saved : p))
            : [...list, saved].sort(byName),
        );
        this.formOpen.set(false);
        this.toasts.success(editing ? 'Beneficiario actualizado' : 'Beneficiario añadido');
      },
      error: (error) => {
        this.saving.set(false);
        this.formError.set(toApiError(error).message);
      },
    });
  }

  protected remove(payee: Payee): void {
    this.api.deletePayee(payee.id).subscribe({
      next: () => {
        this.payees.update((list) => list.filter((p) => p.id !== payee.id));
        this.toDelete.set(null);
        this.toasts.success('Beneficiario eliminado');
      },
      error: (error) => this.toasts.error('No se ha podido eliminar', toApiError(error).message),
    });
  }
}

function byName(a: Payee, b: Payee): number {
  return (a.alias || a.name).localeCompare(b.alias || b.name, 'es');
}
