import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CuentasService } from './cuentas.service';

/**
 * Un caso por método: comprueba que cada llamada de CuentasService pega al
 * endpoint correcto con el método correcto. Mismo patrón que EventosService.spec.ts.
 */
describe('CuentasService', () => {
  let service: CuentasService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CuentasService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() hace GET a /cuentas', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne(`${base}/cuentas`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('detalle() hace GET a /cuentas/:id', () => {
    service.detalle(3).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 3, nombre: 'San Miguel', descripcion: null });
  });
});
