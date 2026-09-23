import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Interceptor de HTTP: envuelve toda petición que sale por HttpClient.
 *
 * - Si la petición va a la API y NO es login/registro (que son públicas), le
 *   añade la cabecera `Authorization: Bearer <token>`.
 * - Si la respuesta de una petición protegida vuelve 401 (token caducado,
 *   inválido o usuario revocado), cierra la sesión y manda a
 *   /login?expirada=1.
 *
 * Se registra en app.config.ts con `withInterceptors([authInterceptor])`.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const http = inject(HttpClient);

  const esApi = req.url.startsWith(environment.apiBaseUrl);
  const esPublica =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/registro') ||
    req.url.includes('/auth/solicitudes');
  const esLogCliente = req.url.endsWith('/logs/cliente');
  const token = auth.token();
  const protegida = esApi && !esPublica;

  const peticion =
    protegida && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(peticion).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && protegida) {
        auth.cerrarSesion();
        router.navigate(['/login'], { queryParams: { expirada: 1 } });
      }
      // status 0: la petición nunca llegó a tener respuesta (sin red, CORS, servidor
      // caído). Se reporta sin esperar ni propagar el resultado: si esto también
      // falla, no hay nada más que hacer (y esLogCliente evita el bucle).
      if (err.status === 0 && esApi && !esLogCliente) {
        http
          .post(`${environment.apiBaseUrl}/logs/cliente`, {
            origen: 'WEB',
            pantalla: req.url,
            mensaje: 'Sin conexión',
          })
          .subscribe({ error: () => {} });
      }
      return throwError(() => err);
    }),
  );
};
