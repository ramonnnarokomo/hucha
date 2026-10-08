import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ENTRY_TYPES, categoryMeta, movementTitle } from '../core/format';
import { Movement } from '../core/models';
import { IbanPipe, LongDateTimePipe, MoneyPipe } from '../core/pipes';
import { CopyButton } from './copy-button';
import { Icon } from './icon';

/** Detalle de un movimiento, para mostrarlo dentro de un modal. */
@Component({
  selector: 'app-movement-detail',
  imports: [Icon, MoneyPipe, IbanPipe, LongDateTimePipe, CopyButton],
  template: `
    @let m = movement();
    <div class="summary">
      <span class="cat" [style.color]="meta().color" [style.background]="meta().color + '1f'">
        <app-icon [name]="meta().icon" [size]="24" />
      </span>
      <strong class="amount num" [class.positive]="m.amount > 0">{{
        m.amount | money: true
      }}</strong>
      <span class="title">{{ title() }}</span>
      <span class="small muted">{{ m.occurredAt | longDateTime }}</span>
    </div>
    <dl class="definition">
      <dt>Tipo</dt>
      <dd>{{ types[m.type] }}</dd>
      @if (m.category) {
        <dt>Categoría</dt>
        <dd>{{ meta().label }}</dd>
      }
      @if (m.concept) {
        <dt>Concepto</dt>
        <dd>{{ m.concept }}</dd>
      }
      @if (m.counterpartyName) {
        <dt>
          {{ m.type === 'CARD_PAYMENT' ? 'Comercio' : m.amount > 0 ? 'Ordenante' : 'Beneficiario' }}
        </dt>
        <dd>{{ m.counterpartyName }}</dd>
      }
      @if (m.counterpartyIban) {
        <dt>IBAN</dt>
        <dd class="iban">
          <span class="num">{{ m.counterpartyIban | iban }}</span>
          <app-copy-button [value]="m.counterpartyIban" label="Copiar IBAN" />
        </dd>
      }
      <dt>Saldo después</dt>
      <dd class="num">{{ m.balanceAfter | money }}</dd>
      <dt>Referencia</dt>
      <dd class="ref num">{{ m.entryId.slice(0, 8).toUpperCase() }}</dd>
    </dl>
  `,
  styles: `
    :host {
      display: grid;
      gap: 20px;
    }
    .summary {
      display: grid;
      justify-items: center;
      gap: 6px;
      text-align: center;
    }
    .cat {
      display: grid;
      place-items: center;
      width: 52px;
      height: 52px;
      border-radius: 16px;
      margin-bottom: 4px;
    }
    .amount {
      font-size: 2rem;
      letter-spacing: -0.02em;
    }
    .title {
      font-weight: 600;
    }
    .iban {
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 4px;
    }
    .ref {
      color: var(--muted);
      font-weight: 500;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementDetail {
  readonly movement = input.required<Movement>();

  protected readonly types = ENTRY_TYPES;
  protected readonly meta = computed(() => categoryMeta(this.movement().category));
  protected readonly title = computed(() => movementTitle(this.movement()));
}
