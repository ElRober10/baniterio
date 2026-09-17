import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { PrecioBebidaEvento } from './precio-bebida-evento';

function montar(): ComponentFixture<PrecioBebidaEvento> {
  TestBed.configureTestingModule({
    imports: [PrecioBebidaEvento],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: new Map([['anio', '2026'], ['id', '9']]) } },
      },
    ],
  });
  return TestBed.createComponent(PrecioBebidaEvento);
}

describe('PrecioBebidaEvento', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('vuelve al año de la ruta y enlaza a "Bebidas alcohólicas"', () => {
    const fixture = montar();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const enlaces = Array.from(el.querySelectorAll('a'));
    expect(enlaces[0].getAttribute('href')).toBe('/panel/precio-bebidas/2026');
    const alcohol = enlaces.find((a) => a.textContent?.includes('Bebidas alcohólicas'));
    expect(alcohol?.getAttribute('href')).toBe('/panel/precio-bebidas/2026/9/alcohol');
  });
});
