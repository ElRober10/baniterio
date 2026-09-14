import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ListaCompraService } from './lista-compra.service';

describe('ListaCompraService', () => {
  let service: ListaCompraService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ListaCompraService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista() hace GET /eventos/:id/lista-compra', () => {
    service.lista(7).subscribe();
    const req = http.expectOne(`${base}/eventos/7/lista-compra`);
    expect(req.request.method).toBe('GET');
    req.flush({ puedoEditar: false, llevaFicha: false, apuntados: 0, diasFiesta: 1, categorias: [] });
  });

  it('adminEventos() hace GET /admin/lista-compra/eventos', () => {
    service.adminEventos().subscribe();
    const req = http.expectOne(`${base}/admin/lista-compra/eventos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('adminEvento() hace GET /admin/lista-compra/eventos/:id', () => {
    service.adminEvento(7).subscribe();
    const req = http.expectOne(`${base}/admin/lista-compra/eventos/7`);
    expect(req.request.method).toBe('GET');
    req.flush({
      evento: { id: 7, nombre: 'x', fecha: '2026-09-25', fechaFin: null },
      apuntados: 0,
      diasFiesta: 1,
      reglas: [],
    });
  });

  it('ajustarRegla() hace PUT con body', () => {
    service.ajustarRegla(7, 3, { cantidadAjustada: 12, activa: true, factor: 3, porCada: null }).subscribe();
    const req = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas/3`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ cantidadAjustada: 12, activa: true, factor: 3, porCada: null });
    req.flush(null);
  });

  it('crearRegla() hace POST con body', () => {
    const body = {
      categoria: 'COMIDA',
      nombre: 'Servilletas',
      tamano: 'paquete',
      tipoFormula: 'POR_EVENTO',
      factor: 2,
    } as const;
    service.crearRegla(7, body).subscribe();
    const req = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('borrarRegla() hace DELETE', () => {
    service.borrarRegla(7, 3).subscribe();
    const req = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas/3`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('comprado() hace POST a la ruta de la línea', () => {
    service.comprado(7, 3).subscribe();
    const req = http.expectOne(`${base}/eventos/7/lista-compra/lineas/3/comprado`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('bloqueo() hace PUT con { bloqueada }', () => {
    service.bloqueo(7, true).subscribe();
    const req = http.expectOne(`${base}/eventos/7/lista-compra/bloqueo`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ bloqueada: true });
    req.flush(null);
  });
});
