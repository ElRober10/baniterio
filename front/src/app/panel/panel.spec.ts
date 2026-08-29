import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UsuarioDto } from '../auth/auth.types';
import { Panel } from './panel';

/**
 * Tests del layout `Panel`: el grupo "Administración" del nav solo aparece si el
 * usuario de la sesión tiene áreas, y cada enlace solo si tiene esa área concreta.
 */
describe('Panel · visibilidad del nav de administración', () => {
  const usuarioSesion = signal<UsuarioDto | null>(null);

  const authFalso: Partial<AuthService> = {
    usuarioActual: usuarioSesion,
    tieneArea: (area: string) => usuarioSesion()?.areas?.includes(area) ?? false,
    asegurarYo: () => of(null),
    sesionActiva: () => true,
    cerrarSesion: () => {},
  };

  function usuario(areas: string[]): UsuarioDto {
    return { id: 1, nombre: 'Ada', apellidos: 'Lovelace', mote: null, esSuperadmin: false, rol: 'MIEMBRO', areas };
  }

  function render(areas: string[]): string {
    usuarioSesion.set(usuario(areas));
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

  it('usuario sin áreas: no se pinta el grupo "Administración"', () => {
    const texto = render([]);
    expect(texto).not.toContain('Administración');
  });

  it('usuario con ADMIN_SOLICITUDES: enlace "Solicitudes" presente, "Permisos" ausente', () => {
    const texto = render(['ADMIN_SOLICITUDES']);
    expect(texto).toContain('Administración');
    expect(texto).toContain('Solicitudes');
    expect(texto).not.toContain('Permisos');
  });

  it('usuario con las dos áreas: los dos enlaces de administración presentes', () => {
    const texto = render(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS']);
    expect(texto).toContain('Solicitudes');
    expect(texto).toContain('Permisos');
  });
});
