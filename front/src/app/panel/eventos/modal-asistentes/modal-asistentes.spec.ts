import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ModalAsistentes } from './modal-asistentes';

describe('ModalAsistentes', () => {
  let fixture: ComponentFixture<ModalAsistentes>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ModalAsistentes],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ModalAsistentes);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 3);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  const filaAna = {
    nombre: 'Ana',
    estado: 'APUNTADO',
    esManual: false,
    bebida: { alcohol: 'Barceló', refresco: 'Coca-Cola', alternativa: 'NADA', modalidad: 'COMPLETA' },
    cuota: 45,
    estadoPago: 'PENDIENTE_PAGO',
    asistenciaId: 11,
    metodoPago: null,
    pagadoPor: null,
    pagadoAt: null,
  };

  function flushListado(extra: Record<string, unknown> = {}) {
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [filaAna],
      totalCuotas: 45,
      totalPagado: 0,
      puedoPagarPor: [],
      miCuota: 45,
      puedoConfirmarPagos: true,
      ...extra,
    });
    fixture.detectChanges();
  }

  function boton(texto: string): HTMLButtonElement | undefined {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === texto,
    );
  }

  function opcion(texto: string): HTMLButtonElement | undefined {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
      b.textContent?.trim().startsWith(texto),
    );
  }

  it('carga el listado y pinta una fila por asistente con su cuota', () => {
    flushListado({ puedoConfirmarPagos: false });
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ana');
    expect(texto).toContain('45 €');
    expect(texto).toContain('Total cuotas');
  });

  it('emite (cerrar) al pulsar Cerrar', () => {
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [],
      totalCuotas: 0,
      totalPagado: 0,
      puedoPagarPor: [],
      miCuota: null,
      puedoConfirmarPagos: false,
    });
    fixture.detectChanges();
    let cerrado = false;
    fixture.componentInstance.cerrar.subscribe(() => (cerrado = true));
    boton('Cerrar')?.click();
    expect(cerrado).toBe(true);
  });

  it('muestra "Confirmar el pago" solo si puedoConfirmarPagos y la fila no está pagada', () => {
    flushListado();
    expect(boton('Confirmar el pago')).toBeTruthy();
  });

  it('no muestra "Confirmar el pago" si no puedoConfirmarPagos', () => {
    flushListado({ puedoConfirmarPagos: false });
    expect(boton('Confirmar el pago')).toBeFalsy();
  });

  it('confirma el pago: abre el método, elige Bizum y llama al servicio', () => {
    flushListado();
    boton('Confirmar el pago')!.click();
    fixture.detectChanges();
    boton('Bizum')!.click();
    fixture.detectChanges();
    boton('Confirmar')!.click();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/11/pago`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ metodo: 'BIZUM' });
    req.flush({
      asistentes: [
        {
          ...filaAna,
          estadoPago: 'CONFIRMADO_PENDIENTE_ENVIO',
          metodoPago: 'BIZUM',
          pagadoPor: 'Jefe',
          pagadoAt: '2026-09-07T10:00:00Z',
        },
      ],
      totalCuotas: 45,
      totalPagado: 45,
      puedoPagarPor: [],
      miCuota: 45,
      puedoConfirmarPagos: true,
    });
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Confirmado, pendiente de ingresar en la cuenta');
    expect(texto).toContain('Bizum');
    expect(boton('Confirmar el pago')).toBeFalsy();
  });

  it('deshacer llama al DELETE', () => {
    flushListado({
      asistentes: [
        {
          ...filaAna,
          estadoPago: 'CONFIRMADO_PENDIENTE_ENVIO',
          metodoPago: 'EFECTIVO',
          pagadoPor: 'Jefe',
          pagadoAt: '2026-09-07T10:00:00Z',
        },
      ],
    });
    boton('deshacer')!.click();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/11/pago`);
    expect(req.request.method).toBe('DELETE');
    req.flush({
      asistentes: [],
      totalCuotas: 0,
      totalPagado: 0,
      puedoPagarPor: [],
      miCuota: null,
      puedoConfirmarPagos: true,
    });
  });

  const opcionesCuota = [
    { texto: 'Cubatas', importe: 26 },
    { texto: 'Cervezas', importe: 16 },
  ];
  const listadoVacio = {
    asistentes: [],
    totalCuotas: 0,
    totalPagado: 0,
    puedoPagarPor: [],
    miCuota: null,
    puedoConfirmarPagos: true,
    opcionesCuota,
  };

  it('actualizar la cuota de un pago confirmado pide el método y manda cuota + método', () => {
    flushListado({
      asistentes: [
        { ...filaAna, cuota: 16, estadoPago: 'CONFIRMADO_EN_CUENTA', metodoPago: 'TRANSFERENCIA' },
      ],
      opcionesCuota,
    });
    boton('Actualizar cuota')!.click();
    fixture.detectChanges();
    opcion('Cubatas')!.click();
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('+10 €');
    boton('Bizum')!.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Quedarán 10 € pendientes de transferir',
    );
    boton('Actualizar')!.click();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/11/cuota`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ cuota: 26, metodo: 'BIZUM' });
    req.flush(listadoVacio);
  });

  it('sin pago confirmado no pide método', () => {
    flushListado({ asistentes: [{ ...filaAna, cuota: 16 }], opcionesCuota });
    boton('Actualizar cuota')!.click();
    fixture.detectChanges();
    opcion('Cubatas')!.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(
      '¿Cómo ha pagado la diferencia?',
    );
    boton('Actualizar')!.click();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/11/cuota`);
    expect(req.request.body).toEqual({ cuota: 26, metodo: null });
    req.flush(listadoVacio);
  });

  it('muestra lo pendiente de transferir de la fila', () => {
    flushListado({
      asistentes: [
        {
          ...filaAna,
          cuota: 26,
          estadoPago: 'CONFIRMADO_PENDIENTE_ENVIO',
          metodoPago: 'BIZUM',
          pendienteTransferir: 10,
        },
      ],
    });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      '10 € de la cuota pendientes de transferir',
    );
  });
});
