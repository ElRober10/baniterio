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
    estimacion: 107.13,
    movimientos: [],
    puedoGestionar: false,
    porIngresar: null,
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

  it('pinta saldo y estimación', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle());
    fixture.detectChanges();
    const txt = fixture.nativeElement.textContent as string;
    expect(txt).toContain('91.13');
    expect(txt).toContain('107.13');
  });

  it('sin puedoGestionar no muestra el botón de transferencia', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle({ puedoGestionar: false, porIngresar: null }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('He transferido el dinero a la peña');
  });

  it('con porIngresar el botón abre el modal y "Sí" llama a marcarTransferido', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle({ puedoGestionar: true, porIngresar: 16 }));
    fixture.detectChanges();

    const btn = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('He transferido el dinero a la peña'),
    ) as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();

    const si = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'Sí',
    ) as HTMLButtonElement;
    si.click();

    const req = httpMock.expectOne(`${base}/cuentas/3/transferencia-a-pena`);
    expect(req.request.method).toBe('POST');
    req.flush(detalle({ saldo: 107.13, puedoGestionar: true, porIngresar: 0 }));
  });
});
