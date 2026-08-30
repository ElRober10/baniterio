import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UsuarioDto } from '../auth/auth.types';
import { Panel } from './panel';

/**
 * Tests del layout `Panel`. El nav pinta "Inicio" + las secciones "próximamente";
 * el enlace "Administración" (a `/panel/administracion`) va el último y solo si el
 * usuario tiene alguna área concedida. Las sub-secciones (Solicitudes / Permisos)
 * ya no cuelgan del nav: se eligen dentro del índice.
 */
describe('Panel · nav lateral', () => {
  const usuarioSesion = signal<UsuarioDto | null>(null);

  const authFalso: Partial<AuthService> = {
    usuarioActual: usuarioSesion,
    tieneArea: (area: string) => usuarioSesion()?.areas?.includes(area) ?? false,
    asegurarYo: () => of(null),
    sesionActiva: () => true,
    cerrarSesion: () => {},
  };

  function render(areas: string[]): string {
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
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(() => {
    usuarioSesion.set(null);
    TestBed.configureTestingModule({
      imports: [Panel],
      providers: [provideRouter([]), { provide: AuthService, useValue: authFalso }],
    });
  });

  it('pinta "Inicio" y las secciones "próximamente"', () => {
    const texto = render([]);
    expect(texto).toContain('Inicio');
    expect(texto).toContain('Miembros');
    expect(texto).toContain('Eventos');
  });

  it('usuario sin áreas: no aparece "Administración" en el nav', () => {
    expect(render([])).not.toContain('Administración');
  });

  it('usuario con áreas: aparece "Administración" pero no las sub-secciones', () => {
    const texto = render(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS']);
    expect(texto).toContain('Administración');
    expect(texto).not.toContain('Solicitudes');
    expect(texto).not.toContain('Permisos');
  });
});
