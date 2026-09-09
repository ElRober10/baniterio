import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { environment } from '../../environments/environment';
import { AdminAvisosService } from '../admin/admin-avisos.service';
import { AuthService } from '../auth/auth.service';
import { UsuarioDto } from '../auth/auth.types';
import { Panel } from './panel';

/**
 * Tests del layout `Panel`. El nav pinta "Inicio", "Miembros" (enlace real, a
 * `/panel/miembros`) y las secciones "próximamente"; el enlace "Administración"
 * (a `/panel/administracion`) va el último y solo si el usuario tiene alguna
 * área concedida, con la campanita del total de pendientes. Las sub-secciones
 * (Solicitudes / Permisos) ya no cuelgan del nav.
 */
describe('Panel · nav lateral', () => {
  const usuarioSesion = signal<UsuarioDto | null>(null);
  const totalAvisos = signal(0);
  let refrescos = 0;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  const authFalso: Partial<AuthService> = {
    usuarioActual: usuarioSesion,
    tieneArea: (area: string) => usuarioSesion()?.areas?.includes(area) ?? false,
    asegurarYo: () => of(usuarioSesion()),
    sesionActiva: () => true,
    cerrarSesion: () => {},
  };

  const avisosFalso: Partial<AdminAvisosService> = {
    total: totalAvisos.asReadonly(),
    refrescar: () => {
      refrescos++;
    },
  };

  function render(areas: string[]): HTMLElement {
    usuarioSesion.set({
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas,
    });
    const fixture = TestBed.createComponent(Panel);
    fixture.detectChanges();
    // El modal bloqueante de convocatorias pide sus pendientes nada más montarse.
    httpMock.expectOne(`${base}/eventos/pendientes-respuesta`).flush({ eventos: [] });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    usuarioSesion.set(null);
    totalAvisos.set(0);
    refrescos = 0;
    TestBed.configureTestingModule({
      imports: [Panel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authFalso },
        { provide: AdminAvisosService, useValue: avisosFalso },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('pinta "Inicio" y las secciones "próximamente"', () => {
    const texto = render([]).textContent ?? '';
    expect(texto).toContain('Inicio');
    expect(texto).toContain('Miembros');
    expect(texto).toContain('Eventos');
  });

  it('"Miembros" es un enlace real a /panel/miembros, no una sección "Pronto"', () => {
    const el = render([]);
    const enlace = el.querySelector('a[href="/panel/miembros"]');
    expect(enlace).toBeTruthy();
    expect((enlace?.textContent ?? '').trim()).toBe('Miembros');
  });

  it('"Cuentas" es un enlace real a /panel/cuentas, no una sección "Pronto"', () => {
    const el = render([]);
    const enlace = el.querySelector('a[href="/panel/cuentas"]');
    expect(enlace).toBeTruthy();
    expect((enlace?.textContent ?? '').trim()).toBe('Cuentas');
  });

  it('"Inventario" es un enlace real a /panel/inventario, no una sección "Pronto"', () => {
    const el = render([]);
    const enlace = el.querySelector('a[href="/panel/inventario"]');
    expect(enlace).toBeTruthy();
    expect((enlace?.textContent ?? '').trim()).toBe('Inventario');
  });

  it('usuario sin áreas: no aparece "Administración" ni se pide el recuento', () => {
    expect(render([]).textContent).not.toContain('Administración');
    expect(refrescos).toBe(0);
  });

  it('usuario con áreas: aparece "Administración" (sin sub-secciones) y se pide el recuento', () => {
    const texto = render(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS']).textContent ?? '';
    expect(texto).toContain('Administración');
    expect(texto).not.toContain('Solicitudes');
    expect(texto).not.toContain('Permisos');
    expect(refrescos).toBeGreaterThan(0);
  });

  it('el nav pinta la campanita con el total de pendientes', () => {
    totalAvisos.set(4);
    const el = render(['ADMIN_SOLICITUDES']);
    const badges = el.querySelectorAll('[role="status"]');
    expect(badges.length).toBeGreaterThan(0);
    expect(badges[0].textContent).toContain('4');
    expect(badges[0].getAttribute('aria-label')).toBe('4 pendientes');
  });
});
