import { Injectable, signal } from '@angular/core';

type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(
    (document.documentElement.dataset['theme'] as Theme | undefined) ?? 'light',
  );

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    document.documentElement.dataset['theme'] = next;
    try {
      localStorage.setItem('hucha:theme', next);
    } catch {
      // Sin almacenamiento (modo privado): el tema dura lo que la pestaña.
    }
  }
}
