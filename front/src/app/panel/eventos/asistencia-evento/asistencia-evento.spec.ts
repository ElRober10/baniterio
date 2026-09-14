import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AsistenciaEventoComponent } from './asistencia-evento';

/**
 * Tests de la parte de asistencia de un evento (aparcada mientras se rediseña):
 * responder Me apunto / No voy / En duda, ficha de bebida, mandar la
 * convocatoria y añadir asistentes a mano.
 */
describe('AsistenciaEventoComponent', () => {
  let fixture: ComponentFixture<AsistenciaEventoComponent>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function crear(id = '5') {
    TestBed.configureTestingModule({
      imports: [AsistenciaEventoComponent],
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
    fixture = TestBed.createComponent(AsistenciaEventoComponent);
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
    expect(put.request.body).toEqual({ estado: 'NO_VOY', paraUsuarioId: null });
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
      puedoBorrar: false,
      oculto: false,
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
    expect(post.request.body).toEqual({ nombre: 'Primo de Juan', telefono: null, estado: 'APUNTADO' });
    post.flush({ id: 1, nombre: 'Primo de Juan', telefono: null, estado: 'APUNTADO', esManual: true });
    responder({ puedoEditar: true });
    fixture.detectChanges();
  });

  const fichaSanMiguel = {
    llevaFicha: true,
    diasEvento: ['2026-09-25', '2026-09-26'],
    miFicha: null,
  };

  function flushCatalogo() {
    httpMock
      .expectOne(`${base}/bebidas/catalogo`)
      .flush({ alcohol: [{ id: 1, nombre: 'Barceló' }], refresco: [{ id: 10, nombre: 'Coca-Cola' }] });
  }

  it('evento de San Miguel: tras "Me apunto" aparece la ficha; evento normal: no', () => {
    crear();
    fixture.detectChanges();
    responder({ asistencia: { miAsistencia: 'APUNTADO', ficha: fichaSanMiguel } });
    flushCatalogo();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-ficha-bebida')).toBeTruthy();
  });

  it('evento normal no pide catálogo ni pinta la ficha', () => {
    crear();
    fixture.detectChanges();
    responder({ asistencia: { miAsistencia: 'APUNTADO' } });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-ficha-bebida')).toBeNull();
  });

  it('al guardar la ficha hace PUT /eventos/5/ficha-bebida y muestra la cuota', () => {
    crear();
    fixture.detectChanges();
    responder({ asistencia: { miAsistencia: 'APUNTADO', ficha: fichaSanMiguel } });
    flushCatalogo();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const refresco = el.querySelector('#fb-refresco') as HTMLSelectElement;
    refresco.value = '10';
    refresco.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (el.querySelector('app-ficha-bebida form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const put = httpMock.expectOne(`${base}/eventos/5/ficha-bebida`);
    expect(put.request.method).toBe('PUT');
    put.flush({ modalidad: 'COMPLETA', cuota: 26, cuotaPendiente: false });
    responder({ asistencia: { miAsistencia: 'APUNTADO', ficha: fichaSanMiguel } });
    fixture.detectChanges();
    expect(el.textContent).toContain('Tu cuota: 26 €');
  });
});
