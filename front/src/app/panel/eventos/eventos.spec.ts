import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Eventos } from './eventos';

/**
 * Tests del listado de eventos: carga la primera página, pinta un botón por
 * evento y muestra el botón de acción correcto según los permisos del backend.
 */
describe('Eventos (listado)', () => {
  let fixture: ComponentFixture<Eventos>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Eventos],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(Eventos);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function responder(extra: Record<string, unknown> = {}) {
    const req = httpMock.expectOne((r) => r.url === `${base}/eventos`);
    expect(req.request.params.get('pagina')).toBe('0');
    req.flush({
      eventos: [
        {
          id: 1,
          nombre: 'Migas Santas 2027',
          fecha: '2027-03-27',
          fechaFin: null,
          lugar: null,
          pasado: false,
        },
      ],
      pagina: 0,
      totalPaginas: 1,
      puedeCrear: false,
      puedeSolicitar: true,
      ...extra,
    });
  }

  it('carga la primera página y pinta un botón por evento', () => {
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Migas Santas 2027');
    expect(txt).toContain('Solicitar crear evento');
  });

  it('con puedeCrear muestra el botón "Crear evento"', () => {
    fixture.detectChanges();
    responder({ puedeCrear: true, puedeSolicitar: false });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Crear evento');
  });

  it('al pulsar "Solicitar crear evento" y enviar, hace POST a /eventos/solicitudes', () => {
    fixture.detectChanges();
    responder();
    fixture.detectChanges();

    const botones = (fixture.nativeElement as HTMLElement).querySelectorAll('button');
    const solicitar = Array.from(botones).find((b) =>
      b.textContent?.includes('Solicitar crear evento'),
    );
    solicitar?.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const enviar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Enviar');
    enviar?.dispatchEvent(new Event('click'));

    const req = httpMock.expectOne(`${base}/eventos/solicitudes`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 5, estado: 'PENDIENTE' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Solicitud enviada');
  });
});
