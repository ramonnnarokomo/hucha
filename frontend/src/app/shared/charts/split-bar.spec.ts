import { TestBed } from '@angular/core/testing';
import { SplitBar } from './split-bar';

describe('SplitBar', () => {
  it('reparte la barra según los importes y descarta los ceros', () => {
    const fixture = TestBed.createComponent(SplitBar);
    fixture.componentRef.setInput('data', [
      { label: 'Vivienda', value: 750, color: '#5c584f' },
      { label: 'Supermercado', value: 250, color: '#6f8a2e' },
      { label: 'Ocio', value: 0, color: '#6b5596' },
    ]);
    fixture.detectChanges();

    const parts = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.part'),
    );
    expect(parts.map((p) => p.style.flexGrow)).toEqual(['750', '250']);
    expect(parts[0]!.title.replace(/\s/g, ' ')).toBe('Vivienda: 750,00 € (75 %)');
  });
});
