import {
  dayLabel,
  initials,
  money,
  movementTitle,
  parseAmount,
  percent,
  signedMoney,
  timeAgo,
} from './format';

/** Intl usa espacios duros (U+00A0 / U+202F); para comparar se pasan a espacios normales. */
const plain = (text: string) => text.replace(/[  ]/g, ' ');

describe('format', () => {
  it('formatea euros agrupando también los miles de 4 cifras, como el backend', () => {
    expect(plain(money(6624.62))).toBe('6.624,62 €');
    expect(plain(money(1234567.5))).toBe('1.234.567,50 €');
    expect(plain(money(null))).toBe('0,00 €');
  });

  it('pone signo a los importes salvo al cero', () => {
    expect(plain(signedMoney(2150))).toBe('+2.150,00 €');
    expect(plain(signedMoney(-39.16))).toBe('\u221239,16 €');
    expect(plain(money(-5))).toBe('\u22125,00 €');
    expect(plain(signedMoney(0))).toBe('0,00 €');
  });

  it('entiende los importes como los escribe la gente', () => {
    expect(parseAmount('45,50')).toBe(45.5);
    expect(parseAmount('1.234,56')).toBe(1234.56);
    expect(parseAmount('1234.56')).toBe(1234.56);
    expect(parseAmount('1.500')).toBe(1500);
    expect(parseAmount(' 20 € ')).toBe(20);
    expect(parseAmount('10,999')).toBeNull();
    expect(parseAmount('abc')).toBeNull();
    expect(parseAmount('')).toBeNull();
    expect(parseAmount(12.345)).toBe(12.35);
  });

  it('etiqueta los días como "Hoy", "Ayer" o la fecha', () => {
    const now = new Date(2026, 9, 7, 18, 0);
    expect(dayLabel(new Date(2026, 9, 7, 9, 0).toISOString(), now)).toBe('Hoy');
    expect(dayLabel(new Date(2026, 9, 6, 23, 59).toISOString(), now)).toBe('Ayer');
    expect(dayLabel(new Date(2026, 9, 3, 12, 0).toISOString(), now)).toBe('3 de octubre');
  });

  it('dice hace cuánto pasó algo', () => {
    const now = new Date('2026-10-07T12:00:00Z');
    expect(timeAgo('2026-10-07T11:59:30Z', now)).toBe('ahora');
    expect(timeAgo('2026-10-07T11:55:00Z', now)).toBe('hace 5 min');
    expect(timeAgo('2026-10-07T09:00:00Z', now)).toBe('hace 3 h');
    expect(timeAgo('2026-10-06T12:00:00Z', now)).toBe('ayer');
    expect(timeAgo('2026-10-02T12:00:00Z', now)).toBe('hace 5 días');
  });

  it('saca las iniciales de un nombre', () => {
    expect(initials('Lucía Fernández')).toBe('LF');
    expect(initials('  marcos  ruiz  pérez ')).toBe('MR');
    expect(initials(undefined)).toBe('');
  });

  it('elige el texto principal de un movimiento', () => {
    expect(
      movementTitle({ type: 'CARD_PAYMENT', counterpartyName: 'Bar Casa Paco', concept: 'x' }),
    ).toBe('Bar Casa Paco');
    expect(
      movementTitle({ type: 'TRANSFER', concept: 'Alquiler', counterpartyName: 'Inmobiliaria' }),
    ).toBe('Alquiler');
    expect(movementTitle({ type: 'INCOMING_TRANSFER', counterpartyName: 'Marcos' })).toBe('Marcos');
    expect(movementTitle({ type: 'WELCOME_GIFT' })).toBe('Regalo de bienvenida');
  });

  it('formatea porcentajes a la española', () => {
    expect(plain(percent(74.5))).toBe('74,5 %');
    expect(plain(percent(100))).toBe('100 %');
  });
});
