/** Utilidades de escala para los gráficos SVG (sin librerías de gráficos). */

/** Redondea hacia arriba a un valor "bonito" para el eje: 1, 2, 2.5 o 5 por potencia de 10. */
export function niceMax(value: number): number {
  if (value <= 0) return 1;
  const exponent = Math.floor(Math.log10(value));
  const base = 10 ** exponent;
  for (const step of [1, 2, 2.5, 5, 10]) {
    if (value <= step * base) return step * base;
  }
  return 10 * base;
}

/**
 * Eje Y "bonito": un paso redondo (1, 2, 2.5 o 5 por potencia de 10) y un máximo que es múltiplo
 * de ese paso. Para 6.579 y 3 tramos da 0, 2.500, 5.000 y 7.500 en vez de 0, 3.333, 6.667...
 */
export function niceTicks(maxValue: number, count = 4): { max: number; values: number[] } {
  const top = Math.max(maxValue, 1);
  const step = niceMax(top / count);
  const max = Math.ceil(top / step - 1e-9) * step;
  const values: number[] = [];
  for (let value = 0; value <= max + 1e-9; value += step) {
    values.push(Math.round(value * 100) / 100);
  }
  return { max, values };
}

/** Escala lineal de un dominio [d0, d1] a un rango [r0, r1]. */
export function linear(d0: number, d1: number, r0: number, r1: number): (value: number) => number {
  const span = d1 - d0 || 1;
  return (value) => r0 + ((value - d0) / span) * (r1 - r0);
}

/** Trazo de una línea suave (curva monótona sencilla) que pasa por los puntos. */
export function smoothPath(points: { x: number; y: number }[]): string {
  if (points.length === 0) return '';
  if (points.length === 1) return `M${points[0]!.x},${points[0]!.y}`;
  let d = `M${points[0]!.x.toFixed(2)},${points[0]!.y.toFixed(2)}`;
  for (let i = 1; i < points.length; i++) {
    const prev = points[i - 1]!;
    const curr = points[i]!;
    const midX = (prev.x + curr.x) / 2;
    d += ` C${midX.toFixed(2)},${prev.y.toFixed(2)} ${midX.toFixed(2)},${curr.y.toFixed(2)} ${curr.x.toFixed(2)},${curr.y.toFixed(2)}`;
  }
  return d;
}
