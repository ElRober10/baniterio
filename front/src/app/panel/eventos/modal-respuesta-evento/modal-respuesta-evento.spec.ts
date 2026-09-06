import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../auth/auth.service';
import { UsuarioDto } from '../../../auth/auth.types';
import { ModalRespuestaEvento } from './modal-respuesta-evento';

/**
 * Tests del modal bloqueante de asistencia: se planta encima de todo mientras
 * haya convocatorias sin contestar (las propias y las de quien se pueda
 * responder por él: pareja/hijo con cuenta) y desaparece solo cuando ya no
 * queda ninguna.
 */
describe('ModalRespuestaEvento', () => {
  let fixture: ComponentFixture<ModalRespuestaEvento>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;
  const usuarioSesion = signal<UsuarioDto | null>(null);

  function crear() {
    usuarioSesion.set({
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas: [],
    });
    TestBed.configureTestingModule({
      imports: [ModalRespuestaEvento],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { usuarioActual: usuarioSesion } },
      ],
    });
    fixture = TestBed.createComponent(ModalRespuestaEvento);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  const evResumen = { id: 5, nombre: 'San Miguel', fecha: '2026-09-25', fechaFin: '2026-09-26', lugar: 'La plaza' };

  /** Cada pendiente es `{evento, paraUsuario}`; por defecto es "de uno mismo" (id 1, como `usuarioSesion`). */
  function pendiente(evento: object, paraUsuario = { id: 1, nombre: 'Ada' }) {
    return { evento, paraUsuario };
  }

  function flushPendientes(eventos: unknown[]) {
    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos });
  }

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

  function eventoDetalle(extra: Record<string, unknown> = {}) {
    const { asistencia, ...resto } = extra as { asistencia?: Record<string, unknown> };
    return {
      id: 5,
      nombre: 'San Miguel',
      descripcion: null,
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
    };
  }

  it('sin pendientes no pinta nada', () => {
    crear();
    fixture.detectChanges();
    flushPendientes([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('con una pendiente propia, pinta el evento y los 3 botones sin rótulo "Respondiendo por"', () => {
    crear();
    fixture.detectChanges();
    flushPendientes([pendiente(evResumen)]);
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('San Miguel');
    expect(txt).toContain('Me apunto');
    expect(txt).toContain('No voy');
    expect(txt).toContain('En duda');
    expect(txt).not.toContain('Respondiendo por');
  });

  it('con una pendiente de la pareja, pinta "Respondiendo por" su nombre', () => {
    crear();
    fixture.detectChanges();
    flushPendientes([pendiente(evResumen, { id: 2, nombre: 'Beto' })]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Respondiendo por: Beto');
  });

  it('al pulsar "No voy" manda el paraUsuarioId del pendiente y, sin quedar más, el modal desaparece', () => {
    crear();
    fixture.detectChanges();
    flushPendientes([pendiente({ ...evResumen, fechaFin: null, lugar: null }, { id: 2, nombre: 'Beto' })]);
    fixture.detectChanges();

    const noVoy = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'No voy');
    noVoy?.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/eventos/5/asistencia`);
    expect(put.request.body).toEqual({ estado: 'NO_VOY', paraUsuarioId: 2 });
    put.flush(eventoDetalle({ asistencia: { miAsistencia: 'NO_VOY' } }));
    flushPendientes([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('al responder "Me apunto" en un evento con ficha, pinta app-ficha-bebida', () => {
    crear();
    fixture.detectChanges();
    flushPendientes([pendiente(evResumen)]);
    fixture.detectChanges();

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Me apunto')
      ?.dispatchEvent(new Event('click'));

    httpMock.expectOne(`${base}/eventos/5/asistencia`).flush(
      eventoDetalle({
        asistencia: {
          miAsistencia: 'APUNTADO',
          ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha: null },
        },
      }),
    );
    httpMock
      .expectOne(`${base}/bebidas/catalogo`)
      .flush({ alcohol: [{ id: 1, nombre: 'Barceló' }], refresco: [{ id: 10, nombre: 'Coca-Cola' }] });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-ficha-bebida')).toBeTruthy();
  });

  it('al guardar la ficha hace PUT /ficha-bebida con el paraUsuarioId y recarga pendientes', () => {
    crear();
    fixture.detectChanges();
    flushPendientes([pendiente(evResumen, { id: 2, nombre: 'Beto' })]);
    fixture.detectChanges();

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Me apunto')
      ?.dispatchEvent(new Event('click'));
    const put1 = httpMock.expectOne(`${base}/eventos/5/asistencia`);
    expect(put1.request.body).toEqual({ estado: 'APUNTADO', paraUsuarioId: 2 });
    put1.flush(
      eventoDetalle({
        asistencia: {
          miAsistencia: 'APUNTADO',
          ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha: null },
        },
      }),
    );
    httpMock
      .expectOne(`${base}/bebidas/catalogo`)
      .flush({ alcohol: [{ id: 1, nombre: 'Barceló' }], refresco: [{ id: 10, nombre: 'Coca-Cola' }] });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const refresco = el.querySelector('#fb-refresco') as HTMLSelectElement;
    refresco.value = '10';
    refresco.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (el.querySelector('app-ficha-bebida form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const put2 = httpMock.expectOne(`${base}/eventos/5/ficha-bebida`);
    expect(put2.request.method).toBe('PUT');
    expect(put2.request.body.paraUsuarioId).toBe(2);
    put2.flush({ modalidad: 'COMPLETA', cuota: 26, cuotaPendiente: false });
    flushPendientes([]);
    fixture.detectChanges();
    expect(el.querySelector('[role="alertdialog"]')).toBeNull();
  });
});
