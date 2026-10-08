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
          <app-icon
            [name]="toast.kind === 'error' ? 'alert' : toast.kind === 'success' ? 'check' : 'bell'"
          />
          <div class="toast-text">
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
      gap: 10px;
      width: min(380px, calc(100vw - 32px));
    }
    .toast {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 14px 14px 14px 16px;
      border-radius: 14px;
      border: 1px solid var(--border);
      background: var(--surface);
      box-shadow: var(--shadow);
      animation: slide-in 0.25s ease-out;
    }
    .toast.success app-icon {
      color: var(--positive);
    }
    .toast.error app-icon {
      color: var(--negative);
    }
    .toast.info > app-icon {
      color: var(--brand-text);
    }
    .toast-text {
      flex: 1;
      display: grid;
      gap: 2px;
      font-size: 0.9rem;
    }
    .toast-text span {
      color: var(--muted);
      font-size: 0.85rem;
    }
    @keyframes slide-in {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
    }
    @media (max-width: 720px) {
      .toasts {
        right: 16px;
        bottom: 84px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly toasts = inject(ToastService);
}
