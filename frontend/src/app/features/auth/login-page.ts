import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiError, toApiError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { Icon } from '../../shared/icon';
import { AuthHero } from './auth-hero';

const DEMO_PASSWORD = 'Demo1234';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, AuthHero],
  template: `
    <section class="hero"><app-auth-hero /></section>
    <section class="form-side">
      <form class="form-card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <header>
          <h1>Entrar</h1>
          <p>Accede a tu banca digital.</p>
        </header>

        @if (expired()) {
          <div class="alert warning">
            <app-icon name="lock" [size]="18" /> Tu sesión ha caducado. Vuelve a entrar.
          </div>
        }
        @if (error(); as error) {
          <div class="alert error" role="alert">
            <app-icon name="alert" [size]="18" /> {{ error.message }}
          </div>
        }

        <div class="field">
          <label for="email">Email</label>
          <input
            id="email"
            class="input"
            type="email"
            formControlName="email"
            autocomplete="email"
          />
        </div>
        <div class="field">
          <label for="password">Contraseña</label>
          <input
            id="password"
            class="input"
            type="password"
            formControlName="password"
            autocomplete="current-password"
          />
        </div>

        <button type="submit" class="btn btn-primary btn-block" [disabled]="loading()">
          @if (loading()) {
            <span class="spinner"></span>
          }
          Entrar
        </button>

        <div class="demo">
          <span class="small muted"
            >¿Solo quieres verla? Entra con un usuario de demostración:</span
          >
          <div class="demo-buttons">
            <button type="button" class="btn btn-sm" (click)="demo('lucia@hucha.dev')">
              Lucía (cliente)
            </button>
            <button type="button" class="btn btn-sm" (click)="demo('marcos@hucha.dev')">
              Marcos (cliente)
            </button>
            <button type="button" class="btn btn-sm" (click)="demo('admin@hucha.dev')">
              Administrador
            </button>
          </div>
        </div>

        <p class="switch-link">
          ¿Aún no tienes cuenta? <a routerLink="/registro">Ábrela en un minuto</a>
        </p>
      </form>
    </section>
  `,
  styleUrl: './auth-layout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query params enlazados como inputs (withComponentInputBinding). */
  readonly redirect = input<string>();
  readonly expired = input<string>();

  protected readonly loading = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected demo(email: string): void {
    this.form.setValue({ email, password: DEMO_PASSWORD });
    this.submit();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set({
        status: 400,
        message: 'Escribe tu email y tu contraseña.',
        fieldErrors: {},
      });
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (response) => {
        const target = this.redirect() ?? (response.user.role === 'ADMIN' ? '/admin' : '/');
        void this.router.navigateByUrl(target);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(toApiError(error));
      },
    });
  }
}
