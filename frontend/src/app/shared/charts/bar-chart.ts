import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { moneyShort } from '../../core/format';
import { observeWidth } from './observe-width';
import { niceTicks } from './scale';

export interface BarGroup {
  label: string;
  values: number[];
}

/** Barras agrupadas (por ejemplo ingresos y gastos por mes). */
@Component({
  selector: 'app-bar-chart',
  template: `
    <figure class="chart" [attr.aria-label]="ariaLabel()">
      <svg
        [attr.viewBox]="'0 0 ' + width() + ' ' + height"
        role="img"
        preserveAspectRatio="xMidYMid meet"
      >
        @for (tick of axis(); track tick.value) {
          <g>
            <line
              [attr.x1]="padLeft"
              [attr.x2]="width()"
              [attr.y1]="tick.y"
              [attr.y2]="tick.y"
              class="grid"
            />
            <text [attr.x]="padLeft - 8" [attr.y]="tick.y + 4" class="axis" text-anchor="end">
              {{ tick.label }}
            </text>
          </g>
        }
        @for (group of bars(); track group.label) {
          <g>
            @for (bar of group.bars; track $index) {
              <rect
                [attr.x]="bar.x"
                [attr.y]="bar.y"
                [attr.width]="bar.width"
                [attr.height]="bar.height"
                rx="0"
                [attr.fill]="colors()[$index]"
              >
                <title>{{ seriesLabels()[$index] }} {{ group.label }}: {{ bar.text }}</title>
              </rect>
            }
            <text [attr.x]="group.center" [attr.y]="height - 6" class="axis" text-anchor="middle">
              {{ group.label }}
            </text>
          </g>
        }
      </svg>
      <figcaption class="legend">
        @for (label of seriesLabels(); track label) {
          <span><i [style.background]="colors()[$index]"></i>{{ label }}</span>
        }
      </figcaption>
    </figure>
  `,
  styleUrl: './chart.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarChart {
  readonly data = input.required<BarGroup[]>();
  readonly seriesLabels = input<string[]>([]);
  readonly colors = input<string[]>(['var(--chart-1)', 'var(--chart-2)']);
  readonly ariaLabel = input('Gráfico de barras');

  protected readonly width = observeWidth(560);
  protected readonly height = 220;
  protected readonly padLeft = 52;
  private readonly padBottom = 26;
  private readonly padTop = 10;

  private readonly scale = computed(() =>
    niceTicks(Math.max(0, ...this.data().flatMap((g) => g.values)), 4),
  );

  protected readonly axis = computed(() => {
    const { max, values } = this.scale();
    const plot = this.height - this.padBottom - this.padTop;
    return values.map((value) => ({
      value,
      y: this.padTop + plot - (value / max) * plot,
      label: moneyShort(value),
    }));
  });

  protected readonly bars = computed(() => {
    const groups = this.data();
    const max = this.scale().max;
    const plot = this.height - this.padBottom - this.padTop;
    const slot = (this.width() - this.padLeft) / Math.max(groups.length, 1);
    const series = Math.max(1, ...groups.map((g) => g.values.length));
    const barWidth = Math.min(22, (slot * 0.5) / series);
    return groups.map((group, i) => {
      const center = this.padLeft + slot * i + slot / 2;
      const start = center - (barWidth * series + 2 * (series - 1)) / 2;
      return {
        label: group.label,
        center,
        bars: group.values.map((value, s) => {
          const height = Math.max(value > 0 ? 2 : 0, (value / max) * plot);
          return {
            x: start + s * (barWidth + 2),
            y: this.padTop + plot - height,
            width: barWidth,
            height,
            text: moneyShort(value),
          };
        }),
      };
    });
  });
}
