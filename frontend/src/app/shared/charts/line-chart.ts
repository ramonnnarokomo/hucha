import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { money, moneyShort, shortDate } from '../../core/format';
import { observeWidth } from './observe-width';
import { linear, niceMax, niceTicks, smoothPath } from './scale';

export interface LinePoint {
  date: string;
  value: number;
}

/** Área con línea suave y cursor que muestra el valor del día bajo el ratón. */
@Component({
  selector: 'app-line-chart',
  template: `
    <figure class="chart">
      <svg
        [attr.viewBox]="'0 0 ' + width() + ' ' + height()"
        role="img"
        [attr.aria-label]="ariaLabel()"
        (mousemove)="onMove($event)"
        (mouseleave)="hover.set(null)"
      >
        @if (showAxis()) {
          @for (tick of axis(); track tick.value) {
            <line
              [attr.x1]="padLeft()"
              [attr.x2]="width()"
              [attr.y1]="tick.y"
              [attr.y2]="tick.y"
              class="grid"
            />
            <text [attr.x]="padLeft() - 8" [attr.y]="tick.y + 4" class="axis" text-anchor="end">
              {{ tick.label }}
            </text>
          }
        }
        <path
          [attr.d]="line()"
          fill="none"
          [attr.stroke]="color()"
          stroke-width="1.75"
          stroke-linejoin="round"
        />
        @if (hoverPoint(); as point) {
          <line
            [attr.x1]="point.x"
            [attr.x2]="point.x"
            [attr.y1]="padTop"
            [attr.y2]="height() - padBottom()"
            class="cursor"
          />
          <circle
            [attr.cx]="point.x"
            [attr.cy]="point.y"
            r="3.5"
            fill="var(--surface)"
            [attr.stroke]="color()"
            stroke-width="1.75"
          />
        }
      </svg>
      @if (hoverPoint(); as point) {
        <div class="tooltip" [style.left.%]="(point.x / width()) * 100">
          <strong>{{ point.valueText }}</strong>
          <span>{{ point.dateText }}</span>
        </div>
      }
    </figure>
  `,
  styleUrl: './chart.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineChart {
  readonly points = input.required<LinePoint[]>();
  readonly height = input(200);
  readonly showAxis = input(true);
  readonly ariaLabel = input('Evolución');
  readonly color = input('var(--chart-1)');

  protected readonly width = observeWidth(600);
  protected readonly padTop = 12;
  protected readonly hover = signal<number | null>(null);
  protected readonly padLeft = computed(() => (this.showAxis() ? 56 : 0));
  protected readonly padBottom = computed(() => (this.showAxis() ? 8 : 2));

  private readonly bounds = computed(() => {
    const values = this.points().map((p) => p.value);
    const minValue = Math.min(...values, 0);
    const scale = niceTicks(Math.max(1, ...values), this.height() >= 200 ? 4 : 3);
    return { min: minValue < 0 ? -niceMax(-minValue) : 0, max: scale.max, ticks: scale.values };
  });

  private readonly coords = computed(() => {
    const points = this.points();
    const { min, max } = this.bounds();
    const x = linear(0, Math.max(points.length - 1, 1), this.padLeft(), this.width());
    const y = linear(min, max, this.height() - this.padBottom(), this.padTop);
    return points.map((p, i) => ({ x: x(i), y: y(p.value), point: p }));
  });

  protected readonly line = computed(() => smoothPath(this.coords()));

  protected readonly axis = computed(() => {
    const { min, max, ticks } = this.bounds();
    const y = linear(min, max, this.height() - this.padBottom(), this.padTop);
    return ticks
      .filter((value) => value >= min)
      .map((value) => ({ value, y: y(value), label: moneyShort(value) }));
  });

  protected readonly hoverPoint = computed(() => {
    const index = this.hover();
    const coords = this.coords();
    if (index === null || !coords[index]) return null;
    const c = coords[index]!;
    return { x: c.x, y: c.y, valueText: money(c.point.value), dateText: shortDate(c.point.date) };
  });

  protected onMove(event: MouseEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const rect = svg.getBoundingClientRect();
    const relative = ((event.clientX - rect.left) / rect.width) * this.width();
    const coords = this.coords();
    if (coords.length === 0) return;
    let nearest = 0;
    for (let i = 1; i < coords.length; i++) {
      if (Math.abs(coords[i]!.x - relative) < Math.abs(coords[nearest]!.x - relative)) nearest = i;
    }
    this.hover.set(nearest);
  }
}
