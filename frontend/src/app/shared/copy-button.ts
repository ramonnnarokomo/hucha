import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { ToastService } from '../core/toast.service';
import { Icon } from './icon';

@Component({
  selector: 'app-copy-button',
  imports: [Icon],
  template: `
    <button
      type="button"
      class="btn btn-ghost btn-icon btn-sm"
      [attr.aria-label]="label()"
      [title]="label()"
      (click)="copy()"
    >
      <app-icon [name]="copied() ? 'check' : 'copy'" [size]="16" />
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CopyButton {
  private readonly toasts = inject(ToastService);

  readonly value = input.required<string>();
  readonly label = input('Copiar');
  protected readonly copied = signal(false);

  protected copy(): void {
    navigator.clipboard?.writeText(this.value()).then(
      () => {
        this.copied.set(true);
        this.toasts.success('Copiado al portapapeles');
        setTimeout(() => this.copied.set(false), 1500);
      },
      () => this.toasts.error('No se ha podido copiar'),
    );
  }
}
