import { formatIban, isValidIban, normalizeIban, shortIban } from './iban';

describe('iban', () => {
  it('acepta IBAN válidos con o sin espacios', () => {
    expect(isValidIban('ES9121000418450200051332')).toBe(true);
    expect(isValidIban('es91 2100 0418 4502 0005 1332')).toBe(true);
    expect(isValidIban('DE89370400440532013000')).toBe(true);
  });

  it('rechaza dígitos de control incorrectos y formatos raros', () => {
    expect(isValidIban('ES9221000418450200051332')).toBe(false);
    expect(isValidIban('ES91210004184502000513')).toBe(false); // España exige 24 caracteres
    expect(isValidIban('1234')).toBe(false);
    expect(isValidIban('')).toBe(false);
  });

  it('normaliza, formatea en grupos de 4 y acorta', () => {
    expect(normalizeIban(' es91-2100 0418 ')).toBe('ES9121000418');
    expect(formatIban('ES9121000418450200051332')).toBe('ES91 2100 0418 4502 0005 1332');
    expect(shortIban('ES9121000418450200051332')).toBe('•••• 1332');
    expect(shortIban(null)).toBe('');
  });
});
