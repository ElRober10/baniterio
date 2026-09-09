import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { EventoDetalleComponent } from './evento-detalle';

/**
 * Tests del detalle de un evento: carga por id, recuento de asistencia y los
 * botones de gestión, que dependen de los permisos que devuelve el backend. La
 * parte de responder/notificar/añadir a mano vive ahora en
 * `asistencia-evento.spec.ts`.
 */
describe('EventoDetalleComponent', () => {
  let fixture: ComponentFixture<EventoDetalleComponent>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function crear(id = '5') {
    TestBed.configureTestingModule({
      imports: [EventoDetalleComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id }) } },
        },
      ],
    });
    fixture = TestBed.createComponent(EventoDetalleComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  const asistenciaBase = {
    miAsistencia: null,
    puedeNotificar: false,
    notificacionReenviableAt: null,
    notificacionMandada: false,
    apuntados: 0,
    noVoy: 0,
    enDuda: 0,
    sinContestar: 0,
    ficha: { llevaFicha: false, diasEvento: [], miFicha: null },
  };

  function responder(extra: Record<string, unknown> = {}) {
    const { asistencia, ...resto } = extra as { asistencia?: Record<string, unknown> };
    httpMock.expectOne(`${base}/eventos/5`).flush({
      id: 5,
      nombre: 'San Miguel',
      descripcion: 'La fiesta grande',
      lugar: 'La plaza',
      fecha: '2026-09-25',
      fechaFin: '2026-09-26',
      pasado: false,
      cuenta: { id: 2, nombre: 'San Miguel' },
      cuotaCubatas: null,
      cuotaCervezas: null,
      cuotaCubatas1Dia: null,
      cuotaCervezas1Dia: null,
      cuotaEmbarazada: null,
      creadoPor: null,
      puedoEditar: false,
      puedoBorrar: false,
      oculto: false,
      asistencia: { ...asistenciaBase, ...asistencia },
      ...resto,
    });
  }

  it('pinta el nombre, las fechas, el lugar, la descripción y la cuenta', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('San Miguel');
    expect(txt).toContain('2026-09-25');
    expect(txt).toContain('La plaza');
    expect(txt).toContain('La fiesta grande');
    expect(txt).toContain('Cuenta:');
    const enlace = (fixture.nativeElement as HTMLElement).querySelector('a[href="/panel/cuentas/2"]');
    expect(enlace).toBeTruthy();
  });

  it('muestra las cuotas que el evento tiene puestas, y solo esas', () => {
    crear();
    fixture.detectChanges();
    responder({ cuotaCubatas: 26, cuotaEmbarazada: 5 });
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Cuotas a pagar');
    expect(txt).toContain('Cubatas');
    expect(txt).toContain('26 €');
    expect(txt).toContain('Embarazada');
    expect(txt).toContain('5 €');
    expect(txt).not.toContain('Cervezas');
  });

  it('no muestra el bloque de cuotas si no hay ninguna puesta', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Cuotas a pagar');
  });

  it('muestra "Listado de asistentes" en un evento de San Miguel', () => {
    crear();
    fixture.detectChanges();
    responder({
      asistencia: {
        ...asistenciaBase,
        ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha: null },
      },
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Listado de asistentes');
  });

  it('"Lista de la compra" (placeholder) e "Inventario de la fiesta" (enlace)', () => {
    crear();
    fixture.detectChanges();
    responder({
      asistencia: {
        ...asistenciaBase,
        ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha: null },
      },
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Lista de la compra');

    const enlace = Array.from(el.querySelectorAll('a')).find(
      (a) => a.textContent?.trim() === 'Inventario de la fiesta',
    );
    expect(enlace?.getAttribute('href')).toContain('/inventario');
  });

  it('no muestra "Listado de asistentes" si el evento no lleva ficha', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Listado de asistentes');
  });

  it('muestra "Confirmar el pago" solo si tengo cuota', () => {
    crear();
    fixture.detectChanges();
    const miFicha = {
      alcoholBebidaId: null,
      alcohol: 'No bebo alcohol',
      refrescoBebidaId: 20,
      refresco: 'Coca-Cola',
      alternativa: 'NADA',
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
      modalidad: 'SOLO_CERVEZA',
      cuota: 16,
      cuotaPendiente: false,
      bebidaPendiente: false,
      estadoPago: 'PENDIENTE_PAGO',
    };
    responder({
      asistencia: {
        ...asistenciaBase,
        miAsistencia: 'APUNTADO',
        ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha },
      },
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Confirmar el pago');
  });

  it('con el pago confirmado muestra "Ya he pagado" y su info', () => {
    crear();
    fixture.detectChanges();
    const miFicha = {
      alcoholBebidaId: null,
      alcohol: 'No bebo alcohol',
      refrescoBebidaId: 20,
      refresco: 'Coca-Cola',
      alternativa: 'NADA',
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
      modalidad: 'SOLO_CERVEZA',
      cuota: 16,
      cuotaPendiente: false,
      bebidaPendiente: false,
      estadoPago: 'CONFIRMADO_EN_CUENTA',
      metodoPago: 'BIZUM',
      pagadoPor: 'Jefe',
      pagadoAt: '2026-09-07T10:00:00Z',
    };
    responder({
      asistencia: {
        ...asistenciaBase,
        miAsistencia: 'APUNTADO',
        ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha },
      },
    });
    fixture.detectChanges();
    const txt1 = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt1).toContain('Ya he pagado');
    expect(txt1).not.toContain('Confirmar el pago');

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Ya he pagado')!
      .click();
    fixture.detectChanges();
    const txt2 = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt2).toContain('Jefe');
    expect(txt2).toContain('Bizum');
  });

  it('con pago declarado pendiente muestra "Ver mi pago declarado" y permite anular', () => {
    crear();
    fixture.detectChanges();
    const miFicha = {
      alcoholBebidaId: null,
      alcohol: 'No bebo alcohol',
      refrescoBebidaId: 20,
      refresco: 'Coca-Cola',
      alternativa: 'NADA',
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
      modalidad: 'SOLO_CERVEZA',
      cuota: 16,
      cuotaPendiente: false,
      bebidaPendiente: false,
      estadoPago: 'DECLARADO',
      metodoPago: null,
      pagadoPor: null,
      pagadoAt: null,
      miPagoDeclarado: {
        importe: 16,
        metodoPago: 'BIZUM',
        estado: 'PENDIENTE',
        createdAt: '2026-09-07T10:00:00Z',
        cubre: [{ nombre: 'Yo', cuota: 16 }],
      },
    };
    responder({
      asistencia: {
        ...asistenciaBase,
        miAsistencia: 'APUNTADO',
        ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha },
      },
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ver mi pago declarado');
    expect(el.textContent).not.toContain('Confirmar el pago');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Ver mi pago declarado')!
      .click();
    fixture.detectChanges();
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Anular declaración')!
      .click();
    const req = httpMock.expectOne(`${base}/eventos/5/pagos-declarados/mia`);
    expect(req.request.method).toBe('DELETE');
    req.flush({
      id: 5,
      nombre: 'San Miguel',
      descripcion: null,
      lugar: null,
      fecha: '2026-09-25',
      fechaFin: null,
      pasado: false,
      cuenta: { id: 2, nombre: 'San Miguel' },
      cuotaCubatas: null,
      cuotaCervezas: null,
      cuotaCubatas1Dia: null,
      cuotaCervezas1Dia: null,
      cuotaEmbarazada: null,
      creadoPor: null,
      puedoEditar: false,
      puedoBorrar: false,
      oculto: false,
      asistencia: asistenciaBase,
    });
  });

  it('sin permisos no muestra botones de gestión', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).not.toContain('Editar');
    expect(txt).not.toContain('Borrar');
  });

  it('con puedoBorrar y oculto, se ve «Recuperar» y no «Borrar»', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true, oculto: true });
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Recuperar');
    expect(txt).toContain('Oculto (borrado)');
    expect(
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
        (b) => b.textContent?.trim() === 'Borrar',
      ),
    ).toBeUndefined();
  });

  it('al pulsar Borrar pide confirmación antes de hacer DELETE', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true });
    fixture.detectChanges();

    const botones = () =>
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    const borrar = botones().find((b) => b.textContent?.trim() === 'Borrar');
    borrar?.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    // Todavía no ha borrado nada: hace falta confirmar.
    httpMock.expectNone(`${base}/eventos/5`);
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('¿Seguro que quieres borrar');

    const confirmar = botones().find((b) => b.textContent?.trim() === 'Sí, borrar');
    confirmar?.dispatchEvent(new Event('click'));

    const del = httpMock.expectOne(`${base}/eventos/5`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('en la confirmación de borrado, Cancelar no borra nada', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true });
    fixture.detectChanges();

    const botones = () =>
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    botones()
      .find((b) => b.textContent?.trim() === 'Borrar')
      ?.dispatchEvent(new Event('click'));
    fixture.detectChanges();
    botones()
      .find((b) => b.textContent?.trim() === 'Cancelar')
      ?.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    httpMock.expectNone(`${base}/eventos/5`);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(
      '¿Seguro que quieres borrar',
    );
  });

  it('al pulsar Recuperar hace PUT /recuperar y refresca el detalle', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true, oculto: true });
    fixture.detectChanges();

    const recuperar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Recuperar');
    recuperar?.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/eventos/5/recuperar`);
    expect(put.request.method).toBe('PUT');
    put.flush({
      id: 5,
      nombre: 'San Miguel',
      descripcion: null,
      lugar: null,
      fecha: '2026-09-25',
      fechaFin: null,
      pasado: false,
      cuenta: { id: 2, nombre: 'San Miguel' },
      cuotaCubatas: null,
      cuotaCervezas: null,
      cuotaCubatas1Dia: null,
      cuotaCervezas1Dia: null,
      cuotaEmbarazada: null,
      creadoPor: null,
      puedoEditar: false,
      puedoBorrar: true,
      oculto: false,
      asistencia: asistenciaBase,
    });
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Evento recuperado');
    expect(txt).not.toContain('Oculto (borrado)');
  });

  it('muestra el recuento de asistencia cuando ya se ha mandado la convocatoria', () => {
    crear();
    fixture.detectChanges();
    responder({
      asistencia: { apuntados: 4, enDuda: 2, sinContestar: 7, notificacionMandada: true },
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      '4 apuntados · 2 en duda · 7 sin contestar',
    );
  });

  it('oculta el recuento mientras no se haya mandado la convocatoria', () => {
    crear();
    fixture.detectChanges();
    responder({ asistencia: { apuntados: 4, enDuda: 2, sinContestar: 7 } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('apuntados');
  });
});
