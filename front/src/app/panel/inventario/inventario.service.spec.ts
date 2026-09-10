import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { InventarioService } from './inventario.service';

describe('InventarioService', () => {
  let service: InventarioService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventarioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('ver() pega a GET /inventario', () => {
    service.ver().subscribe();
    const req = httpMock.expectOne(`${base}/inventario`);
    expect(req.request.method).toBe('GET');
    req.flush({ puedoEditar: false, categorias: [] });
  });

  it('actualizar() pega a PUT /inventario/:id con el cambio', () => {
    service.actualizar(7, { nombre: 'Mixta', tamano: 'lata', cantidad: 9 }).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ nombre: 'Mixta', tamano: 'lata', cantidad: 9 });
    req.flush({ id: 7, nombre: 'Mixta', tamano: 'lata', cantidad: 9 });
  });

  it('borrar() pega a DELETE /inventario/:id', () => {
    service.borrar(7).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('eventosAbiertos() pega a GET /eventos/abiertos', () => {
    service.eventosAbiertos().subscribe();
    const req = httpMock.expectOne(`${base}/eventos/abiertos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('enviarAEvento() hace POST /inventario/:id/enviar con { eventoId }', () => {
    service.enviarAEvento(4, 9).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/4/enviar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ eventoId: 9 });
    req.flush(null);
  });

  it('enviarCategoria() hace POST /inventario/enviar-categoria', () => {
    service.enviarCategoria('ALCOHOL', 9).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/enviar-categoria`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ categoria: 'ALCOHOL', eventoId: 9 });
    req.flush(null);
  });

  it('inventarioFiesta() pega a GET /inventario/evento/:id', () => {
    service.inventarioFiesta(7).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/evento/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ puedoEditar: false, categorias: [] });
  });

  it('devolverAInventario() hace POST /inventario/evento/:e/:a/devolver', () => {
    service.devolverAInventario(7, 3).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/evento/7/3/devolver`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('devolverALista() hace POST /inventario/evento/:e/:a/devolver-a-lista', () => {
    service.devolverALista(7, 4).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/evento/7/4/devolver-a-lista`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
