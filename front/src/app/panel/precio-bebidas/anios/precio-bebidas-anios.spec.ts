import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PrecioBebidasAnios } from './precio-bebidas-anios';

const base = environment.apiBaseUrl;

function montar(): { fixture: ComponentFixture<PrecioBebidasAnios>; http: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [PrecioBebidasAnios],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  return {
    fixture: TestBed.createComponent(PrecioBebidasAnios),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('PrecioBebidasAnios', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta un botón por año, sin repetir y en orden descendente', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos`).flush([
      { id: 1, nombre: 'San Miguel 2025', fecha: '2025-09-25', fechaFin: null },
      { id: 2, nombre: 'San Miguel 2026', fecha: '2026-09-25', fechaFin: null },
      { id: 3, nombre: 'Otro de 2026', fecha: '2026-05-01', fechaFin: null },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const textos = Array.from(el.querySelectorAll('a')).map((a) => a.textContent?.trim());
    expect(textos).toEqual(['2026', '2025']);
    http.verify();
  });

  it('sin eventos, avisa de que todavía no hay ninguno', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos`).flush([]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Todavía no hay eventos');
    http.verify();
  });
});
