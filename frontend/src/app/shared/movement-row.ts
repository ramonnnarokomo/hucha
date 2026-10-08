import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ENTRY_TYPES, categoryMeta, movementTitle } from '../core/format';
import { Movement } from '../core/models';
import { MoneyPipe, TimePipe } from '../core/pipes';
import { Icon } from './icon';

/** Fila de movimiento: icono de la categoría, comercio o concepto, y el importe con signo. */
@Component({
  selector: 'app-movement-row',
  imports: [Icon, MoneyPipe, TimePipe],
  template: `
    <span class="cat" [style.color]="meta().color" [style.background]="meta().color + '1f'">
      <app-icon [name]="meta().icon" [size]="19" />
    </span>
    <div class="grow">
      <div class="title truncate">{{ title() }}</div>
      <div class="sub truncate">{{ subtitle() }} · {{ movement().occurredAt | time }}</div>
    </div>
    <div class="amounts">
      <div class="amount num" [class.positive]="movement().amount > 0">
        {{ movement().amount | money: true }}
      </div>
      @if (showBalance()) {
        <div class="sub num">{{ movement().balanceAfter | money }}</div>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 14px;
      min-width: 0;
    }
    .cat {
      display: grid;
      place-items: center;
      flex: none;
      width: 40px;
      height: 40px;
      border-radius: 12px;
    }
    .grow {
      flex: 1;
      min-width: 0;
    }
    .title {
      font-weight: 600;
    }
    .sub {
      font-size: 0.8rem;
      color: var(--muted);
    }
    .amounts {
      text-align: right;
    }
    .amount {
      font-weight: 600;
      white-space: nowrap;
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
