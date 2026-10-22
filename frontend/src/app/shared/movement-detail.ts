import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ENTRY_TYPES, categoryMeta, movementTitle } from '../core/format';
import { Movement } from '../core/models';
import { IbanPipe, LongDateTimePipe, MoneyPipe } from '../core/pipes';
import { CopyButton } from './copy-button';

/** Detalle de un movimiento, para mostrarlo dentro de un modal. */
@Component({
  selector: 'app-movement-detail',
  imports: [MoneyPipe, IbanPipe, LongDateTimePipe, CopyButton],
  template: `
    @let m = movement();
    <div class="summary">
      <span class="eyebrow">{{ types[m.type] }}</span>
      <strong class="amount num" [class.positive]="m.amount > 0">{{
        m.amount | money: true
      }}</strong>
      <span class="title">{{ title() }}</span>
      <span class="small muted">{{ m.occurredAt | longDateTime }}</span>
    </div>
    <dl class="definition">
      @if (m.category) {
        <dt>Categoría</dt>
        <dd class="category"><i [style.background]="meta().color"></i>{{ meta().label }}</dd>
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
      gap: 18px;
    }
    .summary {
      display: grid;
      gap: 4px;
      padding-bottom: 16px;
      border-bottom: 3px double var(--border-strong);
    }
    .amount {
      font-size: 2rem;
      font-weight: 500;
      letter-spacing: -0.03em;
    }
    .title {
      font-weight: 600;
    }
    .category {
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
    }
    .category i {
      width: 8px;
      height: 8px;
    }
    .iban {
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 4px;
    }
    .ref {
      color: var(--muted);
      font-weight: 400;
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
