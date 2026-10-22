import { TestBed } from '@angular/core/testing';
import { Movement } from '../core/models';
import { MovementRow } from './movement-row';

describe('MovementRow', () => {
  const movement: Movement = {
    id: 1,
    entryId: 'e1',
    occurredAt: '2026-10-07T11:51:00Z',
    type: 'CARD_PAYMENT',
    amount: -39.16,
    balanceAfter: 3624.62,
    category: 'GROCERIES',
    counterpartyName: 'Supermercado La Plaza',
  };

  function render(value: Movement, showBalance = false): HTMLElement {
    const fixture = TestBed.createComponent(MovementRow);
    fixture.componentRef.setInput('movement', value);
    fixture.componentRef.setInput('showBalance', showBalance);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el comercio, la categoría y el importe con signo', () => {
    const element = render(movement);
    expect(element.querySelector('.title')?.textContent).toContain('Supermercado La Plaza');
    expect(element.querySelector('.sub')?.textContent).toContain('Supermercado');
    const amount = element.querySelector('.amount')!;
    expect(amount.textContent?.replace(/\s/g, ' ')).toContain('\u221239,16');
    expect(amount.classList.contains('positive')).toBe(false);
  });

  it('marca los ingresos en verde y puede enseñar el saldo después', () => {
    const element = render(
      { ...movement, type: 'INCOMING_TRANSFER', amount: 48, counterpartyName: 'Marcos' },
      true,
    );
    expect(element.querySelector('.amount')!.classList.contains('positive')).toBe(true);
    expect(element.querySelectorAll('.amounts .balance').length).toBe(1);
  });
});
