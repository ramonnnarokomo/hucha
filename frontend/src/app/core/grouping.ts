import { dayLabel } from './format';

export interface DayGroup<T> {
  day: string;
  items: T[];
}

/** Agrupa elementos ya ordenados por fecha en bloques "Hoy", "Ayer", "3 de octubre"... */
export function groupByDay<T>(
  items: readonly T[],
  dateOf: (item: T) => string,
  now: Date = new Date(),
): DayGroup<T>[] {
  const groups: DayGroup<T>[] = [];
  for (const item of items) {
    const day = dayLabel(dateOf(item), now);
    const last = groups[groups.length - 1];
    if (last && last.day === day) {
      last.items.push(item);
    } else {
      groups.push({ day, items: [item] });
    }
  }
  return groups;
}
