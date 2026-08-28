import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Guards de ruta (funciones que el router ejecuta antes de activar una ruta).
 *
 * - `authGuard`: protege rutas privadas (p. ej. /panel). Si no hay sesión
 *   activa, redirige a /login en vez de dejar entrar.
 * - `invitadoGuard`: para rutas que solo tienen sentido SIN sesión (/, /login,
 *   /registro). Si ya hay sesión, redirige a /panel.
 *
 * Devolver un `UrlTree` = "no actives esta ruta, navega a esta otra".
 */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).sesionActiva() ? true : router.createUrlTree(['/login']);
};

export const invitadoGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).sesionActiva() ? router.createUrlTree(['/panel']) : true;
};
