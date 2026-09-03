import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { EventoDetalleComponent } from './evento-detalle';

/**
 * Tests del detalle de un evento: carga por id, y los botones de gestión
 * dependen de los permisos que devuelve el backend.
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
    apuntados: 0,
    noVoy: 0,
    enDuda: 0,
    sinContestar: 0,
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
      cuotaMaxima: null,
      creadoPor: null,
      puedoEditar: false,
      puedoBorrar: false,
      borradoPendiente: false,
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

  it('muestra la cuota máxima si el evento la tiene', () => {
    crear();
    fixture.detectChanges();
    responder({ cuotaMaxima: 26 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Cuota máxima: 26 €');
  });

  it('sin permisos no muestra botones de gestión', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).not.toContain('Gestionar');
    expect(txt).not.toContain('Borrar');
  });

  it('con puedoBorrar y borradoPendiente, el botón está deshabilitado', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true, borradoPendiente: true });
    fixture.detectChanges();
    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Borrado pendiente'));
    expect(boton).toBeTruthy();
    expect((boton as HTMLButtonElement).disabled).toBe(true);
  });

  it('al borrar, si el backend responde 202 muestra el aviso de solicitud', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoBorrar: true, creadoPor: { id: 9, nombre: 'Ana' } });
    fixture.detectChanges();

    const borrar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Solicitar borrado'));
    borrar?.dispatchEvent(new Event('click'));

    const del = httpMock.expectOne(`${base}/eventos/5`);
    expect(del.request.method).toBe('DELETE');
    del.flush({ estado: 'PENDIENTE' }, { status: 202, statusText: 'Accepted' });
    // recarga tras el aviso
    responder({ puedoBorrar: true, borradoPendiente: true, creadoPor: { id: 9, nombre: 'Ana' } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Solicitud de borrado enviada');
  });

  it('pinta los 3 botones de asistencia y marca el actual', () => {
    crear();
    fixture.detectChanges();
    responder({ asistencia: { miAsistencia: 'EN_DUDA' } });
    fixture.detectChanges();
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    );
    const apunto = botones.find((b) => b.textContent?.trim() === 'Me apunto');
    const enDuda = botones.find((b) => b.textContent?.trim() === 'En duda');
    expect(apunto).toBeTruthy();
    expect(enDuda?.className).toContain('bg-brand');
    expect(apunto?.className).not.toContain('bg-brand');
  });

  it('al pulsar "No voy" hace PUT /eventos/5/asistencia y refresca', () => {
    crear();
    fixture.detectChanges();
    responder();
    fixture.detectChanges();

    const noVoy = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'No voy');
    noVoy?.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/eventos/5/asistencia`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ estado: 'NO_VOY' });
    put.flush({
      id: 5,
      nombre: 'San Miguel',
      descripcion: null,
      lugar: null,
      fecha: '2026-09-25',
      fechaFin: null,
      pasado: false,
      cuenta: { id: 2, nombre: 'San Miguel' },
      cuotaMaxima: null,
      creadoPor: null,
      puedoEditar: false,
      puedoBorrar: false,
      borradoPendiente: false,
      asistencia: { ...asistenciaBase, miAsistencia: 'NO_VOY', noVoy: 1 },
    });
  });

  it('con puedeNotificar aparece "Mandar notificación" y al enviar hace POST', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoEditar: true, asistencia: { puedeNotificar: true } });
    fixture.detectChanges();

    const abrir = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Mandar notificación'));
    expect(abrir).toBeTruthy();
    abrir?.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const enviar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Enviar');
    enviar?.dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${base}/eventos/5/notificacion`);
    expect(post.request.method).toBe('POST');
    post.flush(null, { status: 204, statusText: 'No Content' });
    responder({ puedoEditar: true, asistencia: { puedeNotificar: true } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Notificación enviada');
  });

  it('con notificacionReenviableAt en el futuro el botón está deshabilitado', () => {
    crear();
    fixture.detectChanges();
    const futuro = new Date(Date.now() + 3_600_000).toISOString();
    responder({
      puedoEditar: true,
      asistencia: { puedeNotificar: true, notificacionReenviableAt: futuro },
    });
    fixture.detectChanges();
    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Mandar notificación'));
    expect((boton as HTMLButtonElement).disabled).toBe(true);
  });

  it('el bloque "Añadir a mano" hace POST /eventos/5/asistencias', () => {
    crear();
    fixture.detectChanges();
    responder({ puedoEditar: true });
    fixture.detectChanges();

    const input = (fixture.nativeElement as HTMLElement).querySelector(
      'input[aria-labelledby="anadir-mano"]',
    ) as HTMLInputElement;
    input.value = 'Primo de Juan';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const anadir = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Añadir');
    anadir?.dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${base}/eventos/5/asistencias`);
    expect(post.request.body).toEqual({ nombre: 'Primo de Juan', estado: 'APUNTADO' });
    post.flush({ id: 1, nombre: 'Primo de Juan', estado: 'APUNTADO', esManual: true });
    responder({ puedoEditar: true });
    fixture.detectChanges();
  });

  it('muestra el recuento de asistencia', () => {
    crear();
    fixture.detectChanges();
    responder({ asistencia: { apuntados: 4, enDuda: 2, sinContestar: 7 } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      '4 apuntados · 2 en duda · 7 sin contestar',
    );
  });
});
