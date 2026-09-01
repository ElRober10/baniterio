import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { PerfilResponse, TarjetaMiembroResponse } from './perfil.types';
import { Miembros } from './miembros';

describe('Miembros', () => {
  let fixture: ComponentFixture<Miembros>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  const usuarioSesion = signal<UsuarioDto | null>(null);
  const authFalso = { usuarioActual: usuarioSesion };

  const tarjeta = (id: number, nombre: string): TarjetaMiembroResponse => ({
    id,
    nombre,
    apellidos: 'X',
    mote: null,
    sobreMi: null,
    imagenUrl: null,
    parejaNombre: null,
    hijos: [],
  });

  const perfilSinPendiente: Partial<PerfilResponse> = { vinculoPendiente: null };

  beforeEach(() => {
    usuarioSesion.set({
      id: 1,
      nombre: 'Ada',
      apellidos: 'X',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas: [],
    });
    TestBed.configureTestingModule({
      imports: [Miembros],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    fixture = TestBed.createComponent(Miembros);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function boton(etiqueta: string): HTMLButtonElement {
    const b = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((x) => (x.textContent ?? '').trim() === etiqueta) as HTMLButtonElement | undefined;
    if (!b) throw new Error(`No hay botón "${etiqueta}"`);
    return b;
  }

  async function iniciar(
    tarjetas: TarjetaMiembroResponse[],
    perfil: Partial<PerfilResponse> = perfilSinPendiente,
  ): Promise<void> {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/miembros`).flush(tarjetas);
    httpMock.expectOne(`${base}/perfil`).flush(perfil);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('pinta las tarjetas en el orden que da el backend (no reordena)', async () => {
    await iniciar([tarjeta(9, 'Zoe'), tarjeta(1, 'Ada'), tarjeta(5, 'Marta')]);

    const nombres = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('h2'),
    ).map((h) => (h.textContent ?? '').trim());
    expect(nombres).toEqual(['Zoe X', 'Ada X', 'Marta X']);
  });

  it('la tarjeta propia lleva botón "Editar"', async () => {
    await iniciar([tarjeta(1, 'Ada'), tarjeta(5, 'Marta')]);
    const editar = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'a[href="/panel/miembros/editar"]',
    );
    expect(editar.length).toBe(1);
  });

  it('sin vínculo pendiente no hay aviso', async () => {
    await iniciar([tarjeta(1, 'Ada')]);
    expect(texto()).not.toContain('dice que sois pareja');
  });

  it('con vínculo pendiente muestra el aviso; Confirmar manda POST y recarga sin aviso', async () => {
    await iniciar([tarjeta(1, 'Ada')], {
      vinculoPendiente: { vinculoId: 3, solicitanteNombre: 'Grace Hopper' },
    });
    expect(texto()).toContain('Grace Hopper');
    expect(texto()).toContain('dice que sois pareja');

    boton('Confirmar').click();
    fixture.detectChanges();

    const aceptar = httpMock.expectOne(`${base}/perfil/pareja/aceptar`);
    expect(aceptar.request.method).toBe('POST');
    aceptar.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(`${base}/miembros`).flush([tarjeta(1, 'Ada')]);
    httpMock.expectOne(`${base}/perfil`).flush(perfilSinPendiente);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).not.toContain('dice que sois pareja');
  });

  it('Rechazar manda POST a /perfil/pareja/rechazar', async () => {
    await iniciar([tarjeta(1, 'Ada')], {
      vinculoPendiente: { vinculoId: 3, solicitanteNombre: 'Grace Hopper' },
    });

    boton('Rechazar').click();
    fixture.detectChanges();

    const req = httpMock.expectOne(`${base}/perfil/pareja/rechazar`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    await fixture.whenStable();

    httpMock.expectOne(`${base}/miembros`).flush([tarjeta(1, 'Ada')]);
    httpMock.expectOne(`${base}/perfil`).flush(perfilSinPendiente);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('un error de red al cargar deja la pantalla en estado de error con reintento', async () => {
    fixture.detectChanges();
    httpMock
      .expectOne(`${base}/miembros`)
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    // forkJoin cancela la otra petición en cuanto una falla.
    expect(httpMock.expectOne(`${base}/perfil`).cancelled).toBe(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Reintentar');

    boton('Reintentar').click();
    fixture.detectChanges();
    httpMock.expectOne(`${base}/miembros`).flush([tarjeta(1, 'Ada')]);
    httpMock.expectOne(`${base}/perfil`).flush(perfilSinPendiente);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto()).toContain('Ada X');
  });
});
