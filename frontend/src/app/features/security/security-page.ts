import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BankApi } from '../../core/api';
import { toApiError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { describeDevice } from '../../core/device';
import { initials } from '../../core/format';
import { Session } from '../../core/models';
import { ShortDatePipe, TimeAgoPipe } from '../../core/pipes';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

/** Fuerza orientativa: longitud, mezcla de tipos de carácter. No sustituye a la regla del servidor. */
export function passwordStrength(password: string): { level: number; text: string } {
  if (!password) return { level: 0, text: 'Mínimo 8 caracteres, con letras y números.' };
  if (!PASSWORD_RULE.test(password))
    return { level: 1, text: 'Aún no vale: mínimo 8 caracteres, con letras y números.' };
  let score = 2;
  if (password.length >= 12) score++;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password) && /[^A-Za-z0-9]/.test(password)) score++;
  return score >= 4
    ? { level: 4, text: 'Muy segura.' }
    : score === 3
      ? { level: 3, text: 'Segura.' }
      : { level: 2, text: 'Válida. Más larga o con símbolos sería mejor.' };
}

@Component({
  selector: 'app-security-page',
  imports: [ReactiveFormsModule, Icon, ShortDatePipe, TimeAgoPipe],
  templateUrl: './security-page.html',
  styleUrl: './security-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityPage {
  private readonly api = inject(BankApi);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly initials = computed(() => initials(this.auth.user()?.fullName));
  protected readonly sessions = signal<Session[]>([]);

  protected readonly form = new FormGroup({
    current: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    next: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(PASSWORD_RULE)],
    }),
    repeat: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  private readonly values = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });
  protected readonly strength = computed(() => passwordStrength(this.values().next ?? ''));
  protected readonly mismatch = computed(
    () => (this.values().repeat ?? '') !== (this.values().next ?? ''),
  );
  protected readonly saving = signal(false);
  protected readonly passwordError = signal<string | null>(null);

  constructor() {
    this.loadSessions();
  }

  protected device(session: Session) {
    return describeDevice(session.userAgent);
  }

  protected changePassword(): void {
    if (this.form.invalid || this.mismatch()) {
      this.form.markAllAsTouched();
      this.passwordError.set(
        this.form.controls.next.invalid
          ? 'La nueva contraseña necesita 8 caracteres o más, con letras y números.'
          : null,
      );
      return;
    }
    const { current, next } = this.form.getRawValue();
    this.saving.set(true);
    this.passwordError.set(null);
    this.api.changePassword(current, next).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.reset();
        this.toasts.success(
          'Contraseña cambiada',
          'Hemos cerrado tus sesiones en otros dispositivos.',
        );
        this.loadSessions();
      },
      error: (error) => {
        this.saving.set(false);
        this.passwordError.set(toApiError(error).message);
      },
    });
  }

  protected revoke(session: Session): void {
    this.api.revokeSession(session.id).subscribe({
      next: () => {
        this.sessions.update((list) => list.filter((s) => s.id !== session.id));
        this.toasts.success('Sesión cerrada');
      },
      error: (error) => this.toasts.error('No se ha podido cerrar', toApiError(error).message),
    });
  }

  protected revokeOthers(): void {
    this.api.revokeOtherSessions().subscribe({
      next: () => {
        this.sessions.update((list) => list.filter((s) => s.current));
        this.toasts.success('Se han cerrado las demás sesiones');
      },
      error: (error) => this.toasts.error('No se han podido cerrar', toApiError(error).message),
    });
  }

  private loadSessions(): void {
    this.api.sessions().subscribe((sessions) => this.sessions.set(sessions));
  }
}
