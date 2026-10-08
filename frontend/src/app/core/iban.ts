/** Validación de IBAN en el cliente (ISO 13616, módulo 97), para avisar antes de enviar. */
export function normalizeIban(raw: string): string {
  return (raw ?? '').replace(/[\s-]/g, '').toUpperCase();
}

export function isValidIban(raw: string): boolean {
  const iban = normalizeIban(raw);
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban)) {
    return false;
  }
  if (iban.startsWith('ES') && !/^ES\d{22}$/.test(iban)) {
    return false;
  }
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  let remainder = 0;
  for (const char of rearranged) {
    const value = char >= 'A' ? (char.charCodeAt(0) - 55).toString() : char;
    for (const digit of value) {
      remainder = (remainder * 10 + Number(digit)) % 97;
    }
  }
  return remainder === 1;
}

/** "ES9121000418450200051332" -> "ES91 2100 0418 4502 0005 1332". */
export function formatIban(raw: string | undefined | null): string {
  return normalizeIban(raw ?? '').replace(/(.{4})(?!$)/g, '$1 ');
}

/** Solo los últimos dígitos, para mostrar cuentas sin ocupar espacio. */
export function shortIban(raw: string | undefined | null): string {
  const iban = normalizeIban(raw ?? '');
  return iban ? '•••• ' + iban.slice(-4) : '';
}
