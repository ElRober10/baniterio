import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { MiembroResumen } from '../admin.types';
import { AdminPermisos } from './permisos';

/**
 * Tests de la pantalla AdminPermisos con el AdminService real + HttpTestingController.
 * Comprueban el render del listado de miembros y los tres flujos de edición
 * (rol / activo / áreas): cada uno manda el PUT correcto y recarga la lista, y
 * un error del backend (ULTIMO_ADMIN) enseña el mensaje y vuelve a cargar.
 */
describe('AdminPermisos', () => {
  let fixture: ComponentFixture<AdminPermisos>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;
  const listaUrl = `${base}/admin/miembros`;

  const miembro: MiembroResumen = {
    id: 7,
    nombre: 'Ada',
    apellidos: 'Lovelace',
    mote: 'Condesa',
    telefono: '600000000',
    rol: 'MIEMBRO',
    activo: true,
    esSuperadmin: false,
    areas: ['ADMIN_SOLICITUDES'],
  };

  // AuthService falso: la pantalla lo usa para (a) deshabilitar la fila del propio
  // usuario y (b) refrescar la sesión tras un cambio. `yo()` NO pega al backend —
  // así los tests no tienen que contar una petición extra a /auth/yo.
  const usuarioSesion = signal<UsuarioDto | null>(null);
  const authFalso = {
    usuarioActual: usuarioSesion,
    yo: () => of(usuarioSesion()),
  };

  beforeEach(async () => {
    usuarioSesion.set(null);
    await TestBed.configureTestingModule({
      imports: [AdminPermisos],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authFalso },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminPermisos);
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

  function checkbox(etiqueta: string): HTMLInputElement {
    const labels = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('label'),
    ) as HTMLLabelElement[];
    const label = labels.find((l) => (l.textContent ?? '').trim().includes(etiqueta));
    if (!label) throw new Error(`No hay label "${etiqueta}"`);
    const input = label.querySelector('input[type="checkbox"]') as HTMLInputElement | null;
    if (!input) throw new Error(`El label "${etiqueta}" no tiene checkbox`);
    return input;
  }

  async function iniciarConLista(datos: MiembroResumen[]): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne(listaUrl).flush(datos);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('carga los miembros y los pinta', async () => {
    await iniciarConLista([miembro]);

    expect(texto()).toContain('Ada Lovelace');
    expect(texto()).toContain('Condesa');
    expect(texto()).toContain('600000000');
  });

  it('ponerRol manda PUT {rol} y recarga la lista', async () => {
    await iniciarConLista([miembro]);

    boton('Admin').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/miembros/7/rol`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ rol: 'ADMIN' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([{ ...miembro, rol: 'ADMIN' }]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Ada Lovelace');
  });

  it('activar manda PUT {activo} y recarga la lista', async () => {
    await iniciarConLista([miembro]);

    boton('Activo').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/miembros/7/activo`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ activo: false });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([{ ...miembro, activo: false }]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Inactivo');
  });

  it('alternarArea añade el área al array y manda PUT {areas}', async () => {
    await iniciarConLista([miembro]);

    const cb = checkbox('Permisos');
    expect(cb.checked).toBe(false);
    cb.checked = true;
    cb.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/miembros/7/areas`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ areas: ['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS'] });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([miembro]);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('alternarArea quita el área del array y manda PUT {areas}', async () => {
    await iniciarConLista([miembro]);

    const cb = checkbox('Solicitudes');
    expect(cb.checked).toBe(true);
    cb.checked = false;
    cb.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/admin/miembros/7/areas`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ areas: [] });
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([miembro]);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('un error ULTIMO_ADMIN enseña el mensaje y recarga la lista', async () => {
    await iniciarConLista([{ ...miembro, rol: 'ADMIN' }]);

    boton('Miembro').click();
    fixture.detectChanges();

    httpMock
      .expectOne(`${base}/admin/miembros/7/rol`)
      .flush({ codigo: 'ULTIMO_ADMIN' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([{ ...miembro, rol: 'ADMIN' }]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('sin ningún administrador');
  });

  it('un cambio correcto confirma con "Cambio guardado." y el aviso sobrevive a la recarga', async () => {
    await iniciarConLista([miembro]);

    boton('Admin').click();
    fixture.detectChanges();

    httpMock
      .expectOne(`${base}/admin/miembros/7/rol`)
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(listaUrl).flush([{ ...miembro, rol: 'ADMIN' }]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Cambio guardado.');
  });

  it('la fila del propio usuario sale con todos los controles deshabilitados', async () => {
    usuarioSesion.set({
      id: 7,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas: [],
    });
    await iniciarConLista([miembro]);

    expect(texto()).toContain('No puedes cambiar tus propios permisos aquí.');
    expect(boton('Admin').disabled).toBe(true);
    expect(boton('Activo').disabled).toBe(true);
    expect(checkbox('Solicitudes').disabled).toBe(true);
  });

  it('el superádmin sale con los controles deshabilitados', async () => {
    await iniciarConLista([{ ...miembro, esSuperadmin: true, rol: 'ADMIN', areas: [] }]);

    expect(texto()).toContain('Fundadora');
    expect(boton('Admin').disabled).toBe(true);
    expect(boton('Activo').disabled).toBe(true);
    expect(checkbox('Solicitudes').disabled).toBe(true);
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
    httpMock.expectOne(listaUrl).flush([miembro]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Ada Lovelace');
  });
});
