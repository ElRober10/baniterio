import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { UsuarioDto } from '../auth/auth.types';
import { areaGuard } from './area.guard';

/**
 * Tests de areaGuard: sin sesion -> UrlTree a /login; con sesion pero sin el
 * area concedida -> UrlTree a /panel; con el area -> true.
 */
describe('areaGuard', () => {
  let sesionActiva: boolean;
  let usuario: UsuarioDto | null;

  const authFalso: Partial<AuthService> = {
    sesionActiva: () => sesionActiva,
    asegurarYo: () => of(usuario),
  };

  function ejecutar(guard: CanActivateFn): boolean | UrlTree | Promise<boolean | UrlTree> {
    const resultado = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    if (isObservable(resultado)) {
      return firstValueFrom(resultado as Observable<boolean | UrlTree>);
    }
    return resultado as boolean | UrlTree;
  }

  beforeEach(() => {
    sesionActiva = true;
    usuario = null;
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authFalso }],
    });
  });

  it('sin sesion redirige a /login', async () => {
    sesionActiva = false;
    const resultado = await ejecutar(areaGuard('ADMIN_SOLICITUDES'));
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/login');
  });

  it('con sesion pero sin usuario (asegurarYo -> null: 401 de /yo o red caida) redirige a /login', async () => {
    usuario = null;
    const resultado = await ejecutar(areaGuard('ADMIN_SOLICITUDES'));
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/login');
  });

  it('con sesion pero sin el area redirige a /panel', async () => {
    usuario = { id: 1, nombre: 'A', apellidos: 'B', mote: null, esSuperadmin: false, rol: 'MIEMBRO', areas: [] };
    const resultado = await ejecutar(areaGuard('ADMIN_SOLICITUDES'));
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/panel');
  });

  it('con sesion y el area concedida deja pasar (true)', async () => {
    usuario = {
      id: 1,
      nombre: 'A',
      apellidos: 'B',
      mote: null,
      esSuperadmin: false,
      rol: 'ADMIN',
      areas: ['ADMIN_SOLICITUDES'],
    };
    const resultado = await ejecutar(areaGuard('ADMIN_SOLICITUDES'));
    expect(resultado).toBe(true);
  });

  it('usa el Router real para construir el UrlTree', () => {
    const router = TestBed.inject(Router);
    expect(router).toBeTruthy();
  });
});
