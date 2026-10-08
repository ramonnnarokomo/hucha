import { donutSegments, linear, niceMax, niceTicks, smoothPath } from './scale';

describe('scale', () => {
  it('redondea a valores bonitos', () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(730)).toBe(1000);
    expect(niceMax(1250)).toBe(2000);
    expect(niceMax(2208)).toBe(2500);
    expect(niceMax(4100)).toBe(5000);
  });

  it('elige marcas de eje redondas que cubren el máximo', () => {
    expect(niceTicks(2900, 4)).toEqual({ max: 3000, values: [0, 1000, 2000, 3000] });
    expect(niceTicks(6579, 3)).toEqual({ max: 7500, values: [0, 2500, 5000, 7500] });
    expect(niceTicks(0, 4).max).toBeGreaterThan(0);
  });

  it('interpola linealmente', () => {
    const y = linear(0, 100, 200, 0);
    expect(y(0)).toBe(200);
    expect(y(50)).toBe(100);
    expect(y(100)).toBe(0);
  });

  it('reparte el donut según los valores', () => {
    const segments = donutSegments([75, 25], 100, 0);
    expect(segments).toEqual([
      { length: 75, offset: -0 },
      { length: 25, offset: -75 },
    ]);
    expect(donutSegments([0, 0], 100)).toEqual([]);
  });

  it('dibuja una curva que pasa por todos los puntos', () => {
    expect(smoothPath([])).toBe('');
    const path = smoothPath([
      { x: 0, y: 10 },
      { x: 10, y: 0 },
    ]);
    expect(path.startsWith('M0.00,10.00')).toBe(true);
    expect(path.endsWith('10.00,0.00')).toBe(true);
  });
});
