import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Responder } from './responder';

/**
 * Tests de la pantalla bloqueante: pinta el primer evento pendiente; al
 * responder hace PUT y recarga; sin pendientes navega a /panel.
 */
describe('Responder', () => {
  let fixture: ComponentFixture<Responder>;
  let httpMock: HttpTestingController;
  let navSpy: ReturnType<typeof vi.spyOn>;
  const base = environment.apiBaseUrl;

  function crear() {
    TestBed.configureTestingModule({
      imports: [Responder],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(Responder);
    httpMock = TestBed.inject(HttpTestingController);
    navSpy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  }

  afterEach(() => httpMock.verify());

  const evento = (id: number, nombre: string) => ({
    id,
    nombre,
    fecha: '2026-09-25',
    fechaFin: null,
    lugar: null,
    pasado: false,
    cuenta: { id: 2, nombre: 'San Miguel' },
  });

  it('pinta el primer evento pendiente', () => {
    crear();
    fixture.detectChanges();
    httpMock
      .expectOne(`${base}/eventos/pendientes-respuesta`)
      .flush({ eventos: [evento(1, 'San Miguel'), evento(2, 'Migas')] });
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('San Miguel');
    expect(txt).toContain('Te quedan 2 convocatorias');
  });

  it('al pulsar "Me apunto" hace PUT y recarga', () => {
    crear();
    fixture.detectChanges();
    httpMock
      .expectOne(`${base}/eventos/pendientes-respuesta`)
      .flush({ eventos: [evento(1, 'San Miguel')] });
    fixture.detectChanges();

    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Me apunto');
    boton?.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/eventos/1/asistencia`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ estado: 'APUNTADO' });
    put.flush({});

    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
  });

  it('sin pendientes navega a /panel', () => {
    crear();
    fixture.detectChanges();
    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
    expect(navSpy).toHaveBeenCalledWith('/panel');
  });
});
