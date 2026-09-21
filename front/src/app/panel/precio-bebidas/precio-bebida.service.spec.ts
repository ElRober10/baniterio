import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PrecioBebidaService } from './precio-bebida.service';

const base = environment.apiBaseUrl;

describe('PrecioBebidaService', () => {
  let service: PrecioBebidaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PrecioBebidaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('eventos() hace GET a /precio-bebida/eventos', () => {
    service.eventos().subscribe();
    const req = http.expectOne(`${base}/precio-bebida/eventos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('tiendas() hace GET a /precio-bebida/tiendas', () => {
    service.tiendas().subscribe();
    const req = http.expectOne(`${base}/precio-bebida/tiendas`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crearTienda() hace POST con el nombre', () => {
    service.crearTienda('Eroski').subscribe();
    const req = http.expectOne(`${base}/precio-bebida/tiendas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nombre: 'Eroski' });
    req.flush({ id: 1, nombre: 'Eroski' });
  });

  it('alcohol() hace GET a /precio-bebida/eventos/:id/alcohol', () => {
    service.alcohol(7).subscribe();
    const req = http.expectOne(`${base}/precio-bebida/eventos/7/alcohol`);
    expect(req.request.method).toBe('GET');
    req.flush({ puedoEditar: false, tiendas: [], tamanos: [], porKilo: [], bebidas: [], precios: [] });
  });

  it('guardarPrecio() hace PUT con la celda', () => {
    service.guardarPrecio(7, { bebidaId: 1, tamano: '70 cl', tiendaId: 2, precio: 8.5 }).subscribe();
    const req = http.expectOne(`${base}/precio-bebida/eventos/7/alcohol/precio`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ bebidaId: 1, tamano: '70 cl', tiendaId: 2, precio: 8.5 });
    req.flush(null);
  });

  it('anadirTamano() hace POST con el tamaño', () => {
    service.anadirTamano(7, '3 L').subscribe();
    const req = http.expectOne(`${base}/precio-bebida/eventos/7/alcohol/tamanos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ tamano: '3 L' });
    req.flush(['70 cl', '1 L', '3 L']);
  });
});
