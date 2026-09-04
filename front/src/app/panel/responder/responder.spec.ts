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

  const detalle = (llevaFicha: boolean, miAsistencia = 'APUNTADO') => ({
    id: 1,
    asistencia: {
      miAsistencia,
      ficha: {
        llevaFicha,
        diasEvento: llevaFicha ? ['2026-09-25', '2026-09-26'] : [],
        miFicha: null,
      },
    },
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
    put.flush(detalle(false));

    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
  });

  it('sin pendientes navega a /panel', () => {
    crear();
    fixture.detectChanges();
    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
    expect(navSpy).toHaveBeenCalledWith('/panel');
  });

  it('evento de San Miguel: tras "Me apunto" muestra la ficha antes de avanzar', () => {
    crear();
    fixture.detectChanges();
    httpMock
      .expectOne(`${base}/eventos/pendientes-respuesta`)
      .flush({ eventos: [evento(1, 'San Miguel')] });
    fixture.detectChanges();

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Me apunto')
      ?.dispatchEvent(new Event('click'));

    httpMock.expectOne(`${base}/eventos/1/asistencia`).flush(detalle(true));
    httpMock
      .expectOne(`${base}/bebidas/catalogo`)
      .flush({ alcohol: [], refresco: [{ id: 10, nombre: 'Coca-Cola' }] });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-ficha-bebida')).toBeTruthy();

    const refresco = el.querySelector('#fb-refresco') as HTMLSelectElement;
    refresco.value = '10';
    refresco.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (el.querySelector('app-ficha-bebida form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    httpMock.expectOne(`${base}/eventos/1/ficha-bebida`).flush({
      modalidad: 'COMPLETA',
      cuota: 26,
      cuotaPendiente: false,
    });
    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
  });

  it('"No voy" avanza sin pedir ficha', () => {
    crear();
    fixture.detectChanges();
    httpMock
      .expectOne(`${base}/eventos/pendientes-respuesta`)
      .flush({ eventos: [evento(1, 'San Miguel')] });
    fixture.detectChanges();

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'No voy')
      ?.dispatchEvent(new Event('click'));

    httpMock.expectOne(`${base}/eventos/1/asistencia`).flush(detalle(true));
    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
  });
});
