import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Inventario } from './inventario';

describe('Inventario (portada)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Inventario],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('pinta un botón por categoría, cada uno a su ruta', () => {
    const fixture = TestBed.createComponent(Inventario);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const enlaces = Array.from(el.querySelectorAll('a[href^="/panel/inventario/"]'));
    expect(enlaces.map((a) => (a.textContent ?? '').trim())).toEqual([
      'Alcohol',
      'Cerveza',
      'Refrescos',
      'Limpieza',
      'Comida',
    ]);
    expect(enlaces.map((a) => a.getAttribute('href'))).toEqual([
      '/panel/inventario/alcohol',
      '/panel/inventario/cerveza',
      '/panel/inventario/refrescos',
      '/panel/inventario/limpieza',
      '/panel/inventario/comida',
    ]);
  });
});
