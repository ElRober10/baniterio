import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { PanelInicio } from './inicio';

/**
 * Tests de `PanelInicio`: la tarjeta "Administración" de la rejilla solo aparece
 * si el usuario de la sesión tiene alguna área concedida. Las secciones
 * "próximamente" se pintan siempre.
 */
describe('PanelInicio · tarjeta de administración', () => {
  const usuarioSesion = signal<UsuarioDto | null>(null);

  const authFalso: Partial<AuthService> = { usuarioActual: usuarioSesion };

  function render(): string {
    const fixture = TestBed.createComponent(PanelInicio);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function usuario(areas: string[]): UsuarioDto {
    return {
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas,
    };
  }

  beforeEach(() => {
    usuarioSesion.set(null);
    TestBed.configureTestingModule({
      imports: [PanelInicio],
      providers: [provideRouter([]), { provide: AuthService, useValue: authFalso }],
    });
  });

  it('sin usuario cargado todavía: no hay tarjeta "Administración"', () => {
    expect(render()).not.toContain('Administración');
  });

  it('usuario sin áreas: no hay tarjeta "Administración"', () => {
    usuarioSesion.set(usuario([]));
    expect(render()).not.toContain('Administración');
  });

  it('usuario con un área: aparece la tarjeta "Administración"', () => {
    usuarioSesion.set(usuario(['ADMIN_SOLICITUDES']));
    const texto = render();
    expect(texto).toContain('Administración');
    expect(texto).toContain('Miembros');
  });
});
