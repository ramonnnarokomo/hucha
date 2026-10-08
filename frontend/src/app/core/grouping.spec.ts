import { groupByDay } from './grouping';

describe('groupByDay', () => {
  it('agrupa elementos consecutivos del mismo día', () => {
    const now = new Date(2026, 9, 7, 20, 0);
    const at = (day: number, hour: number) => ({
      date: new Date(2026, 9, day, hour).toISOString(),
    });
    const items = [at(7, 18), at(7, 9), at(6, 22), at(3, 10), at(3, 8)];

    const groups = groupByDay(items, (item) => item.date, now);

    expect(groups.map((g) => g.day)).toEqual(['Hoy', 'Ayer', '3 de octubre']);
    expect(groups.map((g) => g.items.length)).toEqual([2, 1, 2]);
  });

  it('devuelve una lista vacía si no hay nada', () => {
    expect(groupByDay([], () => '')).toEqual([]);
  });
});
