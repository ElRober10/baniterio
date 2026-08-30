import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { AdminAvisosService } from './admin-avisos.service';

/**
 * `AdminAvisosService`: `refrescar()` pide GET /admin/pendientes y publica el
 * resultado en el signal `pendientes`; `total` suma los valores. Un error del
 * backend no rompe: deja el último valor conocido.
 */
describe('AdminAvisosService', () => {
  let servicio: AdminAvisosService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/admin/pendientes`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AdminAvisosService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('empieza vacío con total 0', () => {
    expect(servicio.pendientes()).toEqual({});
    expect(servicio.total()).toBe(0);
  });

  it('refrescar() puebla el signal y total suma los valores', () => {
    servicio.refrescar();
    httpMock.expectOne(url).flush({ ADMIN_SOLICITUDES: 3 });

    expect(servicio.pendientes()).toEqual({ ADMIN_SOLICITUDES: 3 });
    expect(servicio.total()).toBe(3);
  });

  it('un error del backend deja el último valor conocido', () => {
    servicio.refrescar();
    httpMock.expectOne(url).flush({ ADMIN_SOLICITUDES: 2 });

    servicio.refrescar();
    httpMock.expectOne(url).error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

    expect(servicio.pendientes()).toEqual({ ADMIN_SOLICITUDES: 2 });
    expect(servicio.total()).toBe(2);
  });
});
