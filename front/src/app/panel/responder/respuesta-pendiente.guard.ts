import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { EventosService } from '../eventos/eventos.service';

/**
 * Guard de las hijas de `/panel` (salvo `responder` y `miembros/editar`): si el
 * usuario tiene eventos con notificación sin contestar, lo manda a
 * `/panel/responder` y no puede hacer otra cosa hasta responder.
 *
 * Va DESPUÉS de `perfilCompletoGuard`: primero se completa el perfil, luego se
 * contestan las convocatorias. Si la petición falla se deja pasar (no tiene
 * sentido atrapar al usuario en un guard que no puede resolver).
 */
export const respuestaPendienteGuard: CanActivateFn = () => {
  const eventos = inject(EventosService);
  const router = inject(Router);

  return eventos.pendientesRespuesta().pipe(
    map((r) => (r.eventos.length > 0 ? router.parseUrl('/panel/responder') : true)),
    catchError(() => of(true)),
  );
};
