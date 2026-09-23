import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { token: () => null, cerrarSesion: () => {} } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
  });

  afterEach(() => httpMock.verify());

  it('reporta a /logs/cliente cuando una petición a la API falla sin respuesta (error de red)', () => {
    http.get(`${base}/cuentas/1`).subscribe({ error: () => {} });

    httpMock
      .expectOne(`${base}/cuentas/1`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    const reporte = httpMock.expectOne(`${base}/logs/cliente`);
    expect(reporte.request.method).toBe('POST');
    expect(reporte.request.body.origen).toBe('WEB');
    expect(reporte.request.body.pantalla).toBe(`${base}/cuentas/1`);
    reporte.flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('no reporta un error con respuesta del servidor (eso ya lo registra el backend)', () => {
    http.get(`${base}/cuentas/1`).subscribe({ error: () => {} });

    httpMock.expectOne(`${base}/cuentas/1`).flush(null, { status: 500, statusText: 'Server Error' });

    httpMock.expectNone(`${base}/logs/cliente`);
  });

  it('no entra en bucle si el propio reporte falla sin respuesta', () => {
    http.post(`${base}/logs/cliente`, {}).subscribe({ error: () => {} });

    httpMock
      .expectOne(`${base}/logs/cliente`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    httpMock.expectNone(`${base}/logs/cliente`);
  });
});
