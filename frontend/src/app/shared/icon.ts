import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ICONS } from './icons';

/** Icono SVG en línea. Los trazos son constantes del propio código, por eso es seguro marcarlos como confiables. */
@Component({
  selector: 'app-icon',
  template: `<span class="icon" [innerHTML]="svg()" aria-hidden="true"></span>`,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
    .icon {
      display: inline-flex;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  private readonly sanitizer = inject(DomSanitizer);

  readonly name = input.required<string>();
  readonly size = input(20);
  readonly stroke = input(1.8);

  protected readonly svg = computed<SafeHtml>(() => {
    const body = ICONS[this.name()] ?? ICONS['dots'];
    const size = this.size();
    return this.sanitizer.bypassSecurityTrustHtml(
      `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" ` +
        `stroke="currentColor" stroke-width="${this.stroke()}" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`,
    );
  });
}
