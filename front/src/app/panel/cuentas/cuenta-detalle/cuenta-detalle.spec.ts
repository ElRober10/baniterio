import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { CuentaDetalle } from '../cuentas.types';
import { CuentaDetalleComponent } from './cuenta-detalle';

function detalle(over: Partial<CuentaDetalle> = {}): CuentaDetalle {
  return {
    id: 3,
    nombre: 'San Miguel',
    descripcion: null,
    saldo: 91.13,
    saldoInicial: 91.13,
    estimacion: 107.13,
    cobradoSinIngresar: null,
    puedoGestionar: false,
    precioCamiseta: null,
    precioSudadera: null,
    penistas: [],
    totalCuotas: 0,
    totalCobrado: 0,
    movimientos: [
      { id: 1, concepto: 'Saldo del año anterior', categoria: null, importe: 91.13, fecha: '2026-01-01', saldoTras: 91.13, reciboArchivo: null, manual: false, origen: 'SALDO_INICIAL', adelantadoPor: null },
    ],
    resumenGastos: [],
    ...over,
  };
}

describe('CuentaDetalleComponent', () => {
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '3' } } } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('pinta saldo, estimación y el libro', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle());
    fixture.detectChanges();
    const txt = fixture.nativeElement.textContent as string;
    expect(txt).toContain('91.13');
    expect(txt).toContain('107.13');
    expect(txt).toContain('Saldo del año anterior');
  });

  it('sin puedoGestionar no muestra "+ Gasto / Ingreso" ni casillas', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(
      detalle({
        puedoGestionar: false,
        precioCamiseta: 10,
        penistas: [
          { asistenciaId: 11, nombre: 'Ana', cuota: 16, estadoPago: 'PENDIENTE_PAGO', metodoPago: null, camisetaCantidad: 0, camisetaTalla: null, sudaderaCantidad: 0, sudaderaTalla: null, ingreso: null, saldoTras: null },
        ],
      }),
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('+ Gasto / Ingreso');
    expect(fixture.nativeElement.querySelector('input[type=checkbox]')).toBeFalsy();
  });

  it('guarda la cantidad de camiseta llamando a marcarRopa', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(
      detalle({
        puedoGestionar: true,
        penistas: [
          { asistenciaId: 11, nombre: 'Ana', cuota: 16, estadoPago: 'PENDIENTE_PAGO', metodoPago: null, camisetaCantidad: 0, camisetaTalla: null, sudaderaCantidad: 0, sudaderaTalla: null, ingreso: null, saldoTras: null },
        ],
      }),
    );
    fixture.detectChanges();

    const num = fixture.nativeElement.querySelector('input[type=number]') as HTMLInputElement;
    num.value = '2';
    num.dispatchEvent(new Event('change'));

    const req = httpMock.expectOne(`${base}/cuentas/3/asistencias/11/ropa`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ camisetaCantidad: 2 });
    req.flush(detalle());
  });
});
