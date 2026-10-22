import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ENTRY_TYPES, categoryMeta, movementTitle } from '../core/format';
import { Movement } from '../core/models';
import { MoneyPipe, TimePipe } from '../core/pipes';

/**
 * Línea de extracto: hora, concepto con su categoría y el importe en su columna. La categoría se
 * marca con una muestra de color pequeña, no con un icono en un cuadrado.
 */
@Component({
  selector: 'app-movement-row',
  imports: [MoneyPipe, TimePipe],
  template: `
    <span class="time num">{{ movement().occurredAt | time }}</span>
    <div class="grow">
      <div class="title truncate">{{ title() }}</div>
      <div class="sub truncate">
        <i class="swatch" [style.background]="meta().color"></i>{{ subtitle() }}
      </div>
    </div>
    <div class="amounts">
      <div class="amount num" [class.positive]="movement().amount > 0">
        {{ movement().amount | money: true }}
      </div>
      @if (showBalance()) {
        <div class="balance num">{{ movement().balanceAfter | money }}</div>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      align-items: baseline;
      gap: 14px;
      min-width: 0;
    }
    .time {
      flex: none;
      width: 3.2em;
      font-size: 0.78rem;
      color: var(--muted);
    }
    .grow {
      flex: 1;
      min-width: 0;
    }
    .title {
      font-weight: 500;
    }
    .sub {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-top: 1px;
      font-size: 0.8rem;
      color: var(--muted);
    }
    .swatch {
      flex: none;
      width: 7px;
      height: 7px;
    }
    .amounts {
      text-align: right;
    }
    .amount {
      font-size: 0.92rem;
      font-weight: 500;
      white-space: nowrap;
    }
    .balance {
      margin-top: 1px;
      font-size: 0.75rem;
      color: var(--muted);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementRow {
  readonly movement = input.required<Movement>();
  readonly showBalance = input(false);

  protected readonly meta = computed(() => categoryMeta(this.movement().category));
  protected readonly title = computed(() => movementTitle(this.movement()));
  protected readonly subtitle = computed(() => {
    const movement = this.movement();
    return movement.category ? categoryMeta(movement.category).label : ENTRY_TYPES[movement.type];
  });
}
