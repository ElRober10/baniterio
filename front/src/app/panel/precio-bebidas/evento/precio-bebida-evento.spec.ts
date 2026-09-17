import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { PrecioBebidaEvento } from './precio-bebida-evento';

function montar(): ComponentFixture<PrecioBebidaEvento> {
  TestBed.configureTestingModule({
    imports: [PrecioBebidaEvento],
    providers: [
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['anio', '2026']]) } } },
    ],
  });
  return TestBed.createComponent(PrecioBebidaEvento);
}

describe('PrecioBebidaEvento', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('muestra el placeholder y vuelve al año de la ruta', () => {
    const fixture = montar();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Próximamente');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/panel/precio-bebidas/2026');
  });
});
