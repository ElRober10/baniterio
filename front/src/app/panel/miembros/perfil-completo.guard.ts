import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { PerfilService } from './perfil.service';

/**
 * Guard de las rutas de `/panel` (salvo `/panel/miembros/editar`): si el
 * perfil del usuario no está completo, lo manda al editor antes de dejarle
 * usar cualquier otra sección — el primer login obliga a rellenarlo (foto o
 * avatar + responder si tiene pareja).
 *
 * - Sin sesión activa → `/login` (como `authGuard`).
 * - Con sesión: `GET /perfil`. `completado === false` → al editor.
 *   `completado === true` → deja pasar.
 * - Si la petición falla (red caída, o 401 porque el usuario quedó
 *   desactivado) se deja pasar a `/login`, igual que `areaGuard` hace con
 *   `asegurarYo()`: no tiene sentido atrapar al usuario en un guard que no
 *   puede resolver.
 */
export const perfilCompletoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const perfilService = inject(PerfilService);
  const router = inject(Router);

  if (!auth.sesionActiva()) {
    return router.createUrlTree(['/login']);
  }

  return perfilService.miPerfil().pipe(
    map((p) => (p.completado ? true : router.createUrlTree(['/panel/miembros/editar']))),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
