import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Guard de ruta parametrizado por área del panel de administración. Se usa como
 * `areaGuard('ADMIN_SOLICITUDES')` en app.routes.ts.
 *
 * - Sin sesión activa → a /login (como el authGuard).
 * - Con sesión: pregunta el usuario (cacheado) con `asegurarYo()` y comprueba
 *   que tenga concedida esa área. Si no, a /panel (la home del panel, a la que
 *   todo miembro puede entrar).
 *
 * Devolver un `UrlTree` = "no actives esta ruta, navega a esta otra".
 */
export function areaGuard(area: string): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.sesionActiva()) {
      return router.createUrlTree(['/login']);
    }
    return auth
      .asegurarYo()
      .pipe(map((u) => (u?.areas?.includes(area) ? true : router.createUrlTree(['/panel']))));
  };
}
