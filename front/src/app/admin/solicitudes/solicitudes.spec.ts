import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { SolicitudResumen } from '../admin.types';
import { AdminSolicitudes } from './solicitudes';

/**
 * Tests de la pantalla AdminSolicitudes con el AdminService real + HttpTestingController.
 * Comprueban render de la lista, el flujo de aprobar (recarga + mensaje), el manejo
 * de SOLICITUD_YA_RESUELTA y que el rechazo manda el motivo escrito.
 */
describe('AdminSolicitudes', () => {
  let fixture: ComponentFixture<AdminSolicitudes>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;
  const listaUrl = `${base}/admin/solicitudes?estado=PENDIENTE`;

  const solicitud: SolicitudResumen = {
    id: 1,
    nombre: 'Ada',
    apellidos: 'Lovelace',
    telefono: '600000000',
    email: 'ada@example.com',
    motivo: 'Quiero entrar en la peña',
    relacion: 'Amiga de Pepe',
    conocidos: 'Pepe, Juan',
    traeContrasena: true,
    estado: 'PENDIENTE',
    createdAt: '2026-08-29T10:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminSolicitudes],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSolicitudes);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function boton(etiqueta: string): HTMLButtonElement {
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const encontrado = botones.find((b) => (b.textContent ?? '').trim().includes(etiqueta));
    if (!encontrado) throw new Error(`No hay botón "${etiqueta}"`);
    return encontrado;
  }

  async function iniciarConLista(datos: SolicitudResumen[]): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne(listaUrl).flush(datos);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('carga las solicitudes y las pinta', async () => {
    await iniciarConLista([solicitud]);

    expect(texto()).toContain('Ada Lovelace');
    expect(texto()).toContain('600000000');
    expect(texto()).toContain('ada@example.com');
    expect(texto()).toContain('Quiero entrar en la peña');
    expect(texto()).toContain('Amiga de Pepe');
    expect(texto()).toContain('Pepe, Juan');
  });

  it('sin solicitudes muestra el vacío', async () => {
    await iniciarConLista([]);
    expect(texto()).toContain('No hay solicitudes pendientes');
  });

  it('aprobar llama al servicio, recarga y muestra el mensaje de éxito', async () => {
    await iniciarConLista([solicitud]);

    boton('Aprobar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/solicitudes/1/aprobar`);
    expect(req.request.method).toBe('POST');
    req.flush({ resultado: 'CUENTA_CREADA' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Cuenta creada y correo enviado.');
  });

  it('SOLICITUD_YA_RESUELTA al aprobar avisa y recarga la lista', async () => {
    await iniciarConLista([solicitud]);

    boton('Aprobar').click();
    fixture.detectChanges();

    httpMock
      .expectOne(`${base}/admin/solicitudes/1/aprobar`)
      .flush({ codigo: 'SOLICITUD_YA_RESUELTA' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('ya la había resuelto alguien');
  });

  it('el rechazo envía el motivo escrito en el textarea', async () => {
    await iniciarConLista([solicitud]);

    boton('Rechazar').click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const textarea = (fixture.nativeElement as HTMLElement).querySelector(
      'textarea',
    ) as HTMLTextAreaElement;
    expect(textarea).toBeTruthy();
    textarea.value = 'No te conoce nadie de la peña';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    boton('Confirmar rechazo').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/solicitudes/1/rechazar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motivo: 'No te conoce nadie de la peña' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Solicitud rechazada');
  });

  it('rechazar sin motivo manda cuerpo vacío', async () => {
    await iniciarConLista([solicitud]);

    boton('Rechazar').click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    boton('Confirmar rechazo').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/solicitudes/1/rechazar`);
    expect(req.request.body).toEqual({});
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([]);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('un error de red al cargar deja la pantalla en estado de error con reintento', async () => {
    fixture.detectChanges();
    httpMock
      .expectOne(listaUrl)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Reintentar');

    boton('Reintentar').click();
    fixture.detectChanges();
    httpMock.expectOne(listaUrl).flush([solicitud]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Ada Lovelace');
  });
});
