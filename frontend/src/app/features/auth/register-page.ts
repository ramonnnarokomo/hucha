import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiError, toApiError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { AuthHero } from './auth-hero';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, AuthHero],
  template: `
    <section class="hero"><app-auth-hero /></section>
    <section class="form-side">
      <form class="form-card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <header>
          <span class="eyebrow">Alta en un minuto</span>
          <h1>Abre tu cuenta</h1>
          <p>Recibirás un IBAN, una tarjeta virtual y 1.000 € de regalo para probarla.</p>
        </header>

        @if (error(); as error) {
          <div class="alert error" role="alert">
            <app-icon name="alert" [size]="18" /> {{ error.message }}
          </div>
        }

        <div class="field">
          <label for="fullName">Nombre y apellidos</label>
          <input
            id="fullName"
            class="input"
            formControlName="fullName"
            autocomplete="name"
            [class.invalid]="invalid('fullName')"
          />
          @if (invalid('fullName')) {
            <span class="error-text">Escribe tu nombre.</span>
          }
        </div>
        <div class="field">
          <label for="email">Email</label>
          <input
            id="email"
            class="input"
            type="email"
            formControlName="email"
            autocomplete="email"
            [class.invalid]="invalid('email')"
          />
          @if (invalid('email')) {
            <span class="error-text">Escribe un email válido.</span>
          }
        </div>
        <div class="field">
          <label for="password">Contraseña</label>
          <input
            id="password"
            class="input"
            type="password"
            formControlName="password"
            autocomplete="new-password"
            [class.invalid]="invalid('password')"
            aria-describedby="password-hint"
          />
          <span id="password-hint" [class]="invalid('password') ? 'error-text' : 'hint'">
            Mínimo 8 caracteres, con letras y números.
          </span>
        </div>

        <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
          @if (loading()) {
            <span class="spinner"></span>
          }
          Crear cuenta
        </button>
        <p class="switch-link">¿Ya tienes cuenta? <a routerLink="/login">Entra</a></p>
      </form>
    </section>
  `,
  styleUrl: './auth-layout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected readonly loading = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/)]],
  });

  protected invalid(name: 'fullName' | 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register(fullName, email, password).subscribe({
      next: () => {
        this.toasts.success('¡Cuenta abierta!', 'Ya tienes tu IBAN y tu tarjeta virtual.');
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(toApiError(error));
      },
    });
  }
}
