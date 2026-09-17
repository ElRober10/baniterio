import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PrecioBebidasEventos } from './precio-bebidas-eventos';

const base = environment.apiBaseUrl;

function montar(): { fixture: ComponentFixture<PrecioBebidasEventos>; http: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [PrecioBebidasEventos],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['anio', '2026']]) } } },
    ],
  });
  return {
    fixture: TestBed.createComponent(PrecioBebidasEventos),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('PrecioBebidasEventos', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta solo los eventos del año de la ruta, ordenados por fecha', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos`).flush([
      { id: 1, nombre: 'San Fermín 2025', fecha: '2025-07-06', fechaFin: null },
      { id: 2, nombre: 'San Miguel 2026', fecha: '2026-09-25', fechaFin: null },
      { id: 3, nombre: 'Fiesta de primavera 2026', fecha: '2026-04-10', fechaFin: null },
    ]);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const textos = Array.from(el.querySelectorAll('a')).map((a) => a.textContent?.trim());
    expect(textos).toEqual(['← Precio bebidas', 'Fiesta de primavera 2026', 'San Miguel 2026']);
    http.verify();
  });

  it('sin eventos ese año, lo dice', () => {
    const { fixture, http } = montar();
    fixture.detectChanges();
    http.expectOne(`${base}/precio-bebida/eventos`).flush([]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No hay eventos de 2026');
    http.verify();
  });
});
