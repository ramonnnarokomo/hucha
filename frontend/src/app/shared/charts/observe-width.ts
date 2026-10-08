import { DestroyRef, ElementRef, Signal, afterNextRender, inject, signal } from '@angular/core';

/**
 * Ancho real del componente en píxeles, actualizado con ResizeObserver. Los gráficos lo usan como
 * ancho del viewBox: así el texto de los ejes mide siempre lo mismo, sea el gráfico ancho o estrecho.
 */
export function observeWidth(fallback: number): Signal<number> {
  const host = inject<ElementRef<HTMLElement>>(ElementRef);
  const destroyRef = inject(DestroyRef);
  const width = signal(fallback);
  afterNextRender(() => {
    const element = host.nativeElement;
    const update = () => {
      const measured = Math.round(element.clientWidth);
      if (measured > 0) width.set(measured);
    };
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(element);
    destroyRef.onDestroy(() => observer.disconnect());
  });
  return width.asReadonly();
}
