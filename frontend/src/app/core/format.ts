import { Category, DeclineReason, EntryType, SavingsIcon, TransferStatus } from './models';

// En es-ES Intl no agrupa los miles con 4 cifras ("6624,62 €"); 'always' da "6.624,62 €", igual que el backend.
const GROUPING = { useGrouping: 'always' } as unknown as Intl.NumberFormatOptions;
const MONEY = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR', ...GROUPING });
const MONEY_SHORT = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
  ...GROUPING,
});
const SIGNED = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  signDisplay: 'exceptZero',
  ...GROUPING,
});
const DATE = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
const DAY_MONTH = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'long' });
const TIME = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' });
const DATE_TIME = new Intl.DateTimeFormat('es-ES', { dateStyle: 'long', timeStyle: 'short' });
const MONTH = new Intl.DateTimeFormat('es-ES', { month: 'short' });
const MONTH_LONG = new Intl.DateTimeFormat('es-ES', { month: 'long', year: 'numeric' });

export function money(value: number | null | undefined): string {
  return MONEY.format(value ?? 0);
}

export function moneyShort(value: number | null | undefined): string {
  return MONEY_SHORT.format(value ?? 0);
}

export function signedMoney(value: number | null | undefined): string {
  return SIGNED.format(value ?? 0);
}

export function shortDate(iso: string): string {
  return DATE.format(new Date(iso));
}

/** "7 de octubre de 2026, 13:51". */
export function longDateTime(iso: string): string {
  return DATE_TIME.format(new Date(iso));
}

export function time(iso: string): string {
  return TIME.format(new Date(iso));
}

/** "Hoy", "Ayer" o "3 de octubre". */
export function dayLabel(iso: string, now: Date = new Date()): string {
  const date = new Date(iso);
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(now) - startOf(date)) / 86_400_000);
  if (days === 0) return 'Hoy';
  if (days === 1) return 'Ayer';
  return DAY_MONTH.format(date);
}

/** "hace 5 min", "hace 3 h", "hace 2 días". */
export function timeAgo(iso: string, now: Date = new Date()): string {
  const seconds = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return 'ahora';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  const days = Math.round(hours / 24);
  return days === 1 ? 'ayer' : `hace ${days} días`;
}

/** "2026-10" -> "oct". */
export function monthShort(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  return MONTH.format(new Date(year, month - 1, 1)).replace('.', '');
}

/** "2026-10" -> "octubre de 2026". */
export function monthLong(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  return MONTH_LONG.format(new Date(year, month - 1, 1));
}

/** Iniciales para los avatares: "Lucía Fernández" -> "LF". */
export function initials(name: string | undefined | null): string {
  return (name ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

export interface CategoryMeta {
  label: string;
  color: string;
  icon: string;
}

export const CATEGORIES: Record<Category, CategoryMeta> = {
  SALARY: { label: 'Nómina', color: '#059669', icon: 'briefcase' },
  TRANSFER: { label: 'Transferencias', color: '#6366f1', icon: 'send' },
  SAVINGS: { label: 'Ahorro', color: '#0d9488', icon: 'piggy' },
  GROCERIES: { label: 'Supermercado', color: '#16a34a', icon: 'cart' },
  RESTAURANTS: { label: 'Restaurantes', color: '#f97316', icon: 'utensils' },
  TRANSPORT: { label: 'Transporte', color: '#0ea5e9', icon: 'car' },
  SHOPPING: { label: 'Compras', color: '#ec4899', icon: 'bag' },
  LEISURE: { label: 'Ocio', color: '#a855f7', icon: 'ticket' },
  BILLS: { label: 'Recibos', color: '#eab308', icon: 'bolt' },
  HOUSING: { label: 'Vivienda', color: '#64748b', icon: 'home' },
  HEALTH: { label: 'Salud', color: '#ef4444', icon: 'heart' },
  TRAVEL: { label: 'Viajes', color: '#14b8a6', icon: 'plane' },
  GIFT: { label: 'Regalos', color: '#d946ef', icon: 'gift' },
  OTHER: { label: 'Otros', color: '#94a3b8', icon: 'dots' },
};

export const SPENDING_CATEGORIES: Category[] = [
  'GROCERIES',
  'RESTAURANTS',
  'TRANSPORT',
  'SHOPPING',
  'LEISURE',
  'BILLS',
  'HEALTH',
  'TRAVEL',
  'OTHER',
];

export function categoryMeta(category: Category | undefined | null): CategoryMeta {
  return CATEGORIES[category ?? 'OTHER'];
}

export const ENTRY_TYPES: Record<EntryType, string> = {
  TRANSFER: 'Transferencia',
  OWN_TRANSFER: 'Entre tus cuentas',
  INCOMING_TRANSFER: 'Transferencia recibida',
  CARD_PAYMENT: 'Pago con tarjeta',
  SAVINGS_MOVE: 'Hucha',
  WELCOME_GIFT: 'Regalo de bienvenida',
};

export const SAVINGS_ICONS: { value: SavingsIcon; label: string; color: string }[] = [
  { value: 'piggy', label: 'Hucha', color: '#0d9488' },
  { value: 'plane', label: 'Viaje', color: '#0ea5e9' },
  { value: 'home', label: 'Casa', color: '#64748b' },
  { value: 'car', label: 'Coche', color: '#6366f1' },
  { value: 'gift', label: 'Regalo', color: '#d946ef' },
  { value: 'heart', label: 'Salud', color: '#ef4444' },
  { value: 'study', label: 'Estudios', color: '#8b5cf6' },
  { value: 'umbrella', label: 'Imprevistos', color: '#f59e0b' },
  { value: 'star', label: 'Capricho', color: '#eab308' },
];

export function savingsColor(icon: SavingsIcon | undefined | null): string {
  return SAVINGS_ICONS.find((item) => item.value === icon)?.color ?? SAVINGS_ICONS[0]!.color;
}

/** Texto principal de un movimiento: comercio en pagos con tarjeta, concepto o contraparte en el resto. */
export function movementTitle(movement: {
  type: EntryType;
  concept?: string;
  counterpartyName?: string;
}): string {
  if (movement.type === 'CARD_PAYMENT') {
    return movement.counterpartyName ?? 'Pago con tarjeta';
  }
  return movement.concept || movement.counterpartyName || ENTRY_TYPES[movement.type];
}

/**
 * Convierte lo que escribe el usuario en un importe: acepta "1.234,56", "1234,56" y "1234.56".
 * Devuelve null si no es un número con como mucho dos decimales.
 */
export function parseAmount(raw: string | number | null | undefined): number | null {
  if (typeof raw === 'number') {
    return Number.isFinite(raw) ? Math.round(raw * 100) / 100 : null;
  }
  let text = (raw ?? '').replace(/[\s€]/g, '');
  if (!text) {
    return null;
  }
  if (text.includes(',')) {
    // Formato español: el punto agrupa miles y la coma separa decimales.
    text = text.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(\.\d{3})+$/.test(text)) {
    // "1.500" sin coma: son miles, no decimales.
    text = text.replace(/\./g, '');
  }
  if (!/^\d+(\.\d{1,2})?$/.test(text)) {
    return null;
  }
  return Number(text);
}

export const TRANSFER_STATUS: Record<
  TransferStatus,
  { label: string; tone: 'ok' | 'bad' | 'warn' | '' }
> = {
  COMPLETED: { label: 'Completada', tone: 'ok' },
  PENDING_SCA: { label: 'Pendiente de código', tone: 'warn' },
  REJECTED: { label: 'Rechazada', tone: 'bad' },
  EXPIRED: { label: 'Caducada', tone: '' },
  CANCELLED: { label: 'Cancelada', tone: '' },
};

export const DECLINE_REASONS: Record<DeclineReason, string> = {
  CARD_FROZEN: 'Tarjeta congelada',
  CARD_CANCELLED: 'Tarjeta cancelada',
  ONLINE_DISABLED: 'Compras online desactivadas',
  MONTHLY_LIMIT: 'Límite mensual superado',
  DAILY_LIMIT: 'Límite diario superado',
  INSUFFICIENT_FUNDS: 'Saldo insuficiente',
  ACCOUNT_NOT_ACTIVE: 'Cuenta no operativa',
};

const PERCENT = new Intl.NumberFormat('es-ES', { style: 'percent', maximumFractionDigits: 1 });

/** 74.5 -> "74,5 %" (recibe el porcentaje ya en escala 0-100, como lo da la API). */
export function percent(value: number | null | undefined): string {
  return PERCENT.format((value ?? 0) / 100);
}
