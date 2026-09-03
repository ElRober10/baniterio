import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { EventosService } from './eventos.service';
import { GuardarEventoRequest } from './eventos.types';

/**
 * Un caso por método: comprueba que cada llamada de EventosService pega al
 * endpoint correcto con el método/cuerpo/params correctos. Mismo patrón que
 * PerfilService.spec.ts.
 */
describe('EventosService', () => {
  let service: EventosService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EventosService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() hace GET a /eventos con el parámetro pagina', () => {
    service.listar(2).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/eventos`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('pagina')).toBe('2');
    req.flush({ eventos: [], pagina: 2, totalPaginas: 3, puedeCrear: false, puedeSolicitar: true });
  });

  it('detalle() hace GET a /eventos/:id', () => {
    service.detalle(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('crear() hace POST a /eventos con el cuerpo', () => {
    const body: GuardarEventoRequest = {
      nombre: 'Cena',
      descripcion: null,
      lugar: null,
      fecha: '2027-12-01',
      fechaFin: null,
      cuentaId: 2,
      cuentaNueva: false,
      cuotaMaxima: null,
    };
    service.crear(body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('editar() hace PUT a /eventos/:id', () => {
    const body: GuardarEventoRequest = {
      nombre: 'Cena 2',
      descripcion: null,
      lugar: null,
      fecha: '2027-12-02',
      fechaFin: null,
      cuentaId: 2,
      cuentaNueva: false,
      cuotaMaxima: null,
    };
    service.editar(7, body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('borrar() hace DELETE a /eventos/:id', () => {
    service.borrar(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('solicitarCrear() hace POST a /eventos/solicitudes con el mensaje', () => {
    service.solicitarCrear('quiero organizar la cena').subscribe();
    const req = httpMock.expectOne(`${base}/eventos/solicitudes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ mensaje: 'quiero organizar la cena' });
    req.flush({ id: 1, estado: 'PENDIENTE' });
  });
});
