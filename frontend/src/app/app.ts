import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastService } from './core/toast.service';
import { Icon } from './shared/icon';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Icon],
  template: `
    <router-outlet />
    <section class="toasts" aria-live="polite" aria-label="Avisos">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class]="'toast ' + toast.kind" role="status">
          <div class="toast-text">
            <span class="kind">{{
              toast.kind === 'error' ? 'Error' : toast.kind === 'success' ? 'Hecho' : 'Aviso'
            }}</span>
            <strong>{{ toast.title }}</strong>
            @if (toast.message) {
              <span>{{ toast.message }}</span>
            }
          </div>
          <button
            type="button"
            class="btn btn-ghost btn-icon btn-sm"
            aria-label="Cerrar aviso"
            (click)="toasts.dismiss(toast.id)"
          >
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
    </section>
  `,
  styles: `
    .toasts {
      position: fixed;
      right: 20px;
      bottom: 20px;
      z-index: 50;
      display: grid;
      gap: 8px;
      width: min(380px, calc(100vw - 32px));
    }
    .toast {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 10px 12px 14px;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
      background: var(--surface);
      box-shadow: var(--shadow);
      animation: slide-in 0.2s ease-out;
    }
    .toast-text {
      flex: 1;
      display: grid;
      gap: 2px;
      font-size: 0.9rem;
    }
    .kind {
      font-family: var(--mono);
      font-size: 0.64rem;
      font-weight: 500;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--muted);
    }
    .toast.success .kind {
      color: var(--positive);
    }
    .toast.error .kind {
      color: var(--negative);
    }
    .toast-text strong {
      font-weight: 600;
    }
    .toast-text > span:last-child:not(.kind) {
      color: var(--text-2);
      font-size: 0.85rem;
    }
    @keyframes slide-in {
      from {
        opacity: 0;
        transform: translateY(6px);
      }
    }
    @media (max-width: 1080px) {
      .toasts {
        right: 16px;
        bottom: 80px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly toasts = inject(ToastService);
}
