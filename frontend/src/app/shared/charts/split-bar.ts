import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { money, percent } from '../../core/format';

export interface SplitPart {
  label: string;
  value: number;
  color: string;
}

/**
 * Reparto de un total en una sola barra horizontal (100 %). Se lee mejor que un donut: las
 * porciones se comparan por longitud, que es lo que el ojo mide bien.
 */
@Component({
  selector: 'app-split-bar',
  template: `
    <div class="bar" role="img" [attr.aria-label]="ariaLabel()">
      @for (part of parts(); track part.label) {
        <span
          class="part"
          [style.flex-grow]="part.value"
          [style.background]="part.color"
          [title]="part.title"
        ></span>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .bar {
      display: flex;
      gap: 2px;
      height: 14px;
      background: var(--rule);
    }
    .part {
      flex-basis: 0;
      min-width: 3px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SplitBar {
  readonly data = input.required<SplitPart[]>();
  readonly ariaLabel = input('Reparto');

  protected readonly parts = computed(() => {
    const parts = this.data().filter((part) => part.value > 0);
    const total = parts.reduce((sum, part) => sum + part.value, 0) || 1;
    return parts.map((part) => ({
      ...part,
      title: `${part.label}: ${money(part.value)} (${percent((part.value / total) * 100)})`,
    }));
  });
}
