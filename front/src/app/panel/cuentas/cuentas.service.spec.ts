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

  const detalleVacio = {
    id: 3,
    nombre: 'San Miguel',
    descripcion: null,
    anio: 2026,
    anios: [2026],
    esAnioActual: true,
    saldo: 91.13,
    saldoInicial: 91.13,
    estimacion: 91.13,
    cobradoSinIngresar: null,
    puedoGestionar: false,
    precioCamiseta: null,
    precioSudadera: null,
    penistas: [],
    totalCuotas: 0,
    totalCobrado: 0,
    movimientos: [],
    resumenGastos: [],
  };

  it('detalle() hace GET a /cuentas/:id', () => {
    service.detalle(3).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3`);
    expect(req.request.method).toBe('GET');
    req.flush(detalleVacio);
  });

  it('detalle(id, anio) añade ?anio=', () => {
    service.detalle(3, 2025).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3?anio=2025`);
    expect(req.request.method).toBe('GET');
    req.flush(detalleVacio);
  });

  it('marcarTransferido() hace POST a /cuentas/:id/transferencia-a-pena', () => {
    service.marcarTransferido(3).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3/transferencia-a-pena`);
    expect(req.request.method).toBe('POST');
    req.flush(detalleVacio);
  });

  it('cerrarAnio() hace POST a /cuentas/:id/cerrar-anio', () => {
    service.cerrarAnio(3).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3/cerrar-anio`);
    expect(req.request.method).toBe('POST');
    req.flush(detalleVacio);
  });

  it('crearMovimiento() hace POST multipart a /cuentas/:id/movimientos', () => {
    const fd = new FormData();
    fd.set('tipo', 'GASTO');
    service.crearMovimiento(3, fd).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3/movimientos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(detalleVacio);
  });

  it('borrarMovimiento() hace DELETE a /cuentas/movimientos/:id', () => {
    service.borrarMovimiento(9).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/movimientos/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush(detalleVacio);
  });

  it('marcarRopa() hace PUT a /cuentas/:id/asistencias/:asisId/ropa', () => {
    service.marcarRopa(3, 11, { camisetaCantidad: 2, camisetaTalla: 'M chico' }).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3/asistencias/11/ropa`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ camisetaCantidad: 2, camisetaTalla: 'M chico' });
    req.flush(detalleVacio);
  });
});
