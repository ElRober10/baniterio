import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PerfilResponse } from '../perfil.types';
import { EditorPerfil } from './editor-perfil';

describe('EditorPerfil', () => {
  let fixture: ComponentFixture<EditorPerfil>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  const perfilVacio: PerfilResponse = {
    usuarioId: 1,
    nombre: 'Ada',
    apellidos: 'Lovelace',
    mote: null,
    sobreMi: null,
    imagenTipo: null,
    imagenRef: null,
    imagenUrl: null,
    completado: false,
    pareja: null,
    hijos: [],
    vinculoPendiente: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EditorPerfil],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Ruta ficticia para que `navigateByUrl('/panel/miembros')` tras guardar
        // resuelva sin ensuciar la salida con "Cannot match any routes".
        provideRouter([{ path: 'panel/miembros', children: [] }]),
      ],
    });
    fixture = TestBed.createComponent(EditorPerfil);
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
    const encontrado = botones.find((b) => (b.textContent ?? '').trim() === etiqueta);
    if (!encontrado) throw new Error(`No hay botón "${etiqueta}"`);
    return encontrado;
  }

  function campo(nombre: string): HTMLInputElement | HTMLTextAreaElement {
    return (fixture.nativeElement as HTMLElement).querySelector(
      `[formcontrolname="${nombre}"]`,
    ) as HTMLInputElement | HTMLTextAreaElement;
  }

  async function asentar(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Arranca el componente y responde el GET /perfil + GET /avatares iniciales. */
  async function iniciar(perfil: PerfilResponse): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/perfil`).flush(perfil);
    httpMock.expectOne(`${base}/perfil/avatares`).flush([
      { id: '01_chico', genero: 'CHICO' },
      { id: '01_chica', genero: 'CHICA' },
    ]);
    await asentar();
  }

  function rellenarDatosMinimos(): void {
    (campo('nombre') as HTMLInputElement).value = 'Ada';
    campo('nombre').dispatchEvent(new Event('input'));
    (campo('apellidos') as HTMLInputElement).value = 'Lovelace';
    campo('apellidos').dispatchEvent(new Event('input'));
  }

  it('carga el perfil y el catálogo, y precarga el formulario', async () => {
    await iniciar({ ...perfilVacio, mote: 'Condesa', sobreMi: 'Hola' });

    expect((campo('nombre') as HTMLInputElement).value).toBe('Ada');
    expect((campo('mote') as HTMLInputElement).value).toBe('Condesa');
    expect(texto()).toContain('Guardar');
  });

  it('sin imagen elegida, guardar no manda el PUT y avisa', async () => {
    await iniciar(perfilVacio);
    rellenarDatosMinimos();

    boton('Guardar').click();
    fixture.detectChanges();

    httpMock.expectNone(`${base}/perfil`);
    expect(texto()).toContain('Elige una foto o un avatar.');
  });

  it('guardar con el nombre vacío avisa y no manda el PUT', async () => {
    await iniciar(perfilVacio);
    (
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button[aria-pressed]'))[0] as HTMLButtonElement
    ).click();
    (campo('nombre') as HTMLInputElement).value = '';
    campo('nombre').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    boton('Guardar').click();
    fixture.detectChanges();

    httpMock.expectNone(`${base}/perfil`);
    expect(texto()).toContain('Revisa los campos obligatorios');
  });

  it('guardar con un avatar elegido manda PUT con imagenTipo AVATAR y su ref', async () => {
    await iniciar(perfilVacio);
    rellenarDatosMinimos();

    (
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button[aria-pressed]'))[0] as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    boton('Guardar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.imagenTipo).toBe('AVATAR');
    expect(req.request.body.imagenRef).toBe('01_chico');
    expect(req.request.body.tienePareja).toBe(false);
    req.flush({ ...perfilVacio, completado: true });
  });

  it('un perfil sin pareja nunca manda tienePareja:true sin que el usuario lo marque', async () => {
    await iniciar(perfilVacio);
    rellenarDatosMinimos();
    (
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button[aria-pressed]'))[0] as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    boton('Guardar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.body.tienePareja).toBe(false);
    expect(req.request.body.parejaNombre).toBeNull();
    expect(req.request.body.parejaTelefono).toBeNull();
    req.flush({ ...perfilVacio, completado: true });
  });

  it('con foto nueva elegida, guardar sube la foto y luego manda el PUT con esa ref', async () => {
    await iniciar(perfilVacio);
    rellenarDatosMinimos();
    boton('Subir foto').click();
    fixture.detectChanges();

    const archivo = new File(['contenido'], 'foto.png', { type: 'image/png' });
    (fixture.componentInstance as unknown as { onFotoElegida(e: Event): void }).onFotoElegida({
      target: { files: [archivo] },
    } as unknown as Event);
    fixture.detectChanges();

    boton('Guardar').click();
    fixture.detectChanges();

    const subida = httpMock.expectOne(`${base}/perfil/foto`);
    expect(subida.request.method).toBe('POST');
    subida.flush({ imagenRef: 'abc-123.jpg' });
    fixture.detectChanges();

    const guardado = httpMock.expectOne(`${base}/perfil`);
    expect(guardado.request.body.imagenTipo).toBe('FOTO');
    expect(guardado.request.body.imagenRef).toBe('abc-123.jpg');
    guardado.flush({ ...perfilVacio, completado: true });
  });

  it('con vínculo ACEPTADO, los campos de la pareja están deshabilitados y el PUT reenvía los mismos valores', async () => {
    await iniciar({
      ...perfilVacio,
      imagenTipo: 'AVATAR',
      imagenRef: '01_chico',
      pareja: { vinculoId: 5, nombre: 'Grace Hopper', telefono: '611111111', estado: 'ACEPTADO' },
    });

    expect(texto()).toContain('Tu pareja: Grace Hopper');
    expect(texto()).toContain('Romper vínculo');
    // No hay inputs editables de nombre/teléfono de pareja en este estado.
    expect((fixture.nativeElement as HTMLElement).querySelector('[formcontrolname="parejaNombre"]')).toBeNull();

    boton('Guardar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.body.tienePareja).toBe(true);
    expect(req.request.body.parejaNombre).toBe('Grace Hopper');
    expect(req.request.body.parejaTelefono).toBe('611111111');
    req.flush({ ...perfilVacio, completado: true });
  });

  it('añadir un hijo y guardar incluye ese hijo con id null', async () => {
    await iniciar({ ...perfilVacio, imagenTipo: 'AVATAR', imagenRef: '01_chico' });

    boton('+ Añadir hijo').click();
    fixture.detectChanges();
    // El primer control "nombre" es el del propio usuario; el del hijo es el segundo.
    const nombreHijo = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '[formcontrolname="nombre"]',
    )[1] as HTMLInputElement;
    nombreHijo.value = 'Lucía';
    nombreHijo.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    boton('Guardar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.body.hijos).toEqual([
      { id: null, nombre: 'Lucía', mayorDeEdad: false, telefono: null, visible: false },
    ]);
    req.flush({ ...perfilVacio, completado: true });
  });

  it('quitar un hijo existente y guardar ya no lo incluye', async () => {
    await iniciar({
      ...perfilVacio,
      imagenTipo: 'AVATAR',
      imagenRef: '01_chico',
      hijos: [
        { id: 9, nombre: 'Bruno', mayorDeEdad: false, telefono: null, visible: true, registrado: false },
      ],
    });
    const nombres = () =>
      Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('[formcontrolname="nombre"]'),
      ) as HTMLInputElement[];
    expect(nombres().length).toBe(2); // el del usuario + el del hijo
    expect(nombres()[1].value).toBe('Bruno');

    boton('Quitar').click();
    fixture.detectChanges();
    expect(nombres().length).toBe(1);

    boton('Guardar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil`);
    expect(req.request.body.hijos).toEqual([]);
    req.flush({ ...perfilVacio, completado: true });
  });

  it('un error TELEFONO_YA_EMPAREJADO al guardar enseña su mensaje', async () => {
    await iniciar({ ...perfilVacio, imagenTipo: 'AVATAR', imagenRef: '01_chico' });

    boton('Guardar').click();
    fixture.detectChanges();

    httpMock
      .expectOne(`${base}/perfil`)
      .flush({ codigo: 'TELEFONO_YA_EMPAREJADO' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(texto().toLowerCase()).toContain('ya tiene pareja');
  });

  it('"Romper vínculo" manda DELETE /perfil/pareja y recarga el perfil', async () => {
    await iniciar({
      ...perfilVacio,
      imagenTipo: 'AVATAR',
      imagenRef: '01_chico',
      pareja: { vinculoId: 5, nombre: 'Grace Hopper', telefono: '611111111', estado: 'ACEPTADO' },
    });

    boton('Romper vínculo').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil/pareja`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    httpMock.expectOne(`${base}/perfil`).flush({ ...perfilVacio, imagenTipo: 'AVATAR', imagenRef: '01_chico' });
    httpMock.expectOne(`${base}/perfil/avatares`).flush([]);
    await asentar();

    expect(texto()).not.toContain('Tu pareja:');
  });
});
