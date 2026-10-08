import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { donutSegments } from './scale';

export interface DonutSlice {
  label: string;
  value: number;
  color: string;
}

/** Donut con el total en el centro. */
@Component({
  selector: 'app-donut-chart',
  template: `
    <svg
      viewBox="0 0 120 120"
      [attr.width]="size()"
      [attr.height]="size()"
      role="img"
      [attr.aria-label]="ariaLabel()"
    >
      <circle
        cx="60"
        cy="60"
        [attr.r]="radius"
        fill="none"
        stroke="var(--surface-hover)"
        stroke-width="14"
      />
      @for (segment of segments(); track $index) {
        <circle
          cx="60"
          cy="60"
          [attr.r]="radius"
          fill="none"
          [attr.stroke]="segment.color"
          stroke-width="14"
          [attr.stroke-dasharray]="segment.length + ' ' + circumference"
          [attr.stroke-dashoffset]="segment.offset"
          transform="rotate(-90 60 60)"
        >
          <title>{{ segment.label }}</title>
        </circle>
      }
      <text x="60" y="56" text-anchor="middle" class="donut-value">{{ centerValue() }}</text>
      <text x="60" y="72" text-anchor="middle" class="donut-label">{{ centerLabel() }}</text>
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
    .donut-value {
      font-size: 13px;
      font-weight: 700;
      fill: var(--text);
    }
    .donut-label {
      font-size: 8px;
      fill: var(--muted);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DonutChart {
  readonly slices = input.required<DonutSlice[]>();
  readonly centerValue = input('');
  readonly centerLabel = input('');
  readonly size = input(180);
  readonly ariaLabel = input('Gráfico circular');

  protected readonly radius = 46;
  protected readonly circumference = 2 * Math.PI * 46;

  protected readonly segments = computed(() => {
    const slices = this.slices();
    return donutSegments(
      slices.map((s) => s.value),
      this.circumference,
    ).map((segment, i) => ({ ...segment, color: slices[i]!.color, label: slices[i]!.label }));
  });
}
