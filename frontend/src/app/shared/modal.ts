import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import { Icon } from './icon';

/** Diálogo modal sobre el elemento <dialog> nativo: foco atrapado, Escape y fondo gratis. */
@Component({
  selector: 'app-modal',
  imports: [Icon],
  template: `
    <dialog
      #dialog
      class="modal"
      [attr.aria-labelledby]="titleId"
      (close)="closed.emit()"
      (click)="onBackdrop($event)"
    >
      <div class="modal-body">
        <div class="row between">
          <h2 [id]="titleId">{{ title() }}</h2>
          <button
            type="button"
            class="btn btn-ghost btn-icon"
            aria-label="Cerrar"
            (click)="close()"
          >
            <app-icon name="x" />
          </button>
        </div>
        <ng-content />
      </div>
    </dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Modal {
  private static counter = 0;

  readonly title = input.required<string>();
  readonly open = input(false);
  readonly closed = output<void>();

  protected readonly titleId = `modal-title-${++Modal.counter}`;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        element.showModal();
      } else if (!this.open() && element.open) {
        element.close();
      }
    });
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) {
      this.close();
    }
  }
}
