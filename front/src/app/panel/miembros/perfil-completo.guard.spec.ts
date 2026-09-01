import { TestBed } from '@angular/core/testing';
import { CanActivateFn, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { PerfilResponse } from './perfil.types';
import { PerfilService } from './perfil.service';
import { perfilCompletoGuard } from './perfil-completo.guard';

/**
 * Tests de perfilCompletoGuard: sin sesión -> /login; con perfil incompleto ->
 * /panel/miembros/editar; con perfil completo -> true; fallo de red -> /login
 * (mismo patrón que areaGuard.spec.ts).
 */
describe('perfilCompletoGuard', () => {
  let sesionActiva: boolean;
  let respuesta: Observable<PerfilResponse>;

  const authFalso: Partial<AuthService> = {
    sesionActiva: () => sesionActiva,
  };
  const perfilServiceFalso: Partial<PerfilService> = {
    miPerfil: () => respuesta,
  };

  function ejecutar(guard: CanActivateFn): Promise<boolean | UrlTree> {
    const resultado = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    if (isObservable(resultado)) {
      return firstValueFrom(resultado as Observable<boolean | UrlTree>);
    }
    return Promise.resolve(resultado as boolean | UrlTree);
  }

  beforeEach(() => {
    sesionActiva = true;
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authFalso },
        { provide: PerfilService, useValue: perfilServiceFalso },
      ],
    });
  });

  it('sin sesión redirige a /login', async () => {
    sesionActiva = false;
    const resultado = await ejecutar(perfilCompletoGuard);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/login');
  });

  it('con perfil incompleto redirige a /panel/miembros/editar', async () => {
    respuesta = of({ completado: false } as PerfilResponse);
    const resultado = await ejecutar(perfilCompletoGuard);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/panel/miembros/editar');
  });

  it('con perfil completo deja pasar (true)', async () => {
    respuesta = of({ completado: true } as PerfilResponse);
    const resultado = await ejecutar(perfilCompletoGuard);
    expect(resultado).toBe(true);
  });

  it('un fallo al pedir el perfil (red caída o 401) redirige a /login', async () => {
    respuesta = throwError(() => new Error('boom'));
    const resultado = await ejecutar(perfilCompletoGuard);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/login');
  });
});
