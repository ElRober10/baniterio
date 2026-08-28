import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginBody, LoginDto, RegistroBody, UsuarioDto } from './auth.types';

/**
 * Servicio de autenticación del front. Único sitio que habla con los endpoints
 * /api/v1/auth/* y único sitio que toca el token guardado.
 *
 * - `registro()` / `login()` devuelven un Observable (RxJS): no hacen la
 *   petición hasta que alguien se suscribe (lo hacen los componentes).
 * - `login()` además, con `tap(...)`, guarda el JWT en localStorage cuando la
 *   respuesta llega bien. `tap` es un "efecto de lado" que no altera el flujo.
 * - `token()` / `cerrarSesion()` leen y borran ese JWT.
 *
 * `@Injectable({ providedIn: 'root' })` = hay una sola instancia para toda la
 * app y cualquier componente puede pedirla con `inject(AuthService)`.
 *
 * Pendiente (diferido en el plan): un interceptor que añada
 * `Authorization: Bearer <token>` automáticamente a las peticiones protegidas.
 */
const CLAVE_TOKEN = 'baniterio.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  registro(body: RegistroBody): Observable<UsuarioDto> {
    return this.http.post<UsuarioDto>(`${this.base}/auth/registro`, body);
  }

  login(body: LoginBody): Observable<LoginDto> {
    return this.http
      .post<LoginDto>(`${this.base}/auth/login`, body)
      .pipe(tap((res) => localStorage.setItem(CLAVE_TOKEN, res.token)));
  }

  token(): string | null {
    return localStorage.getItem(CLAVE_TOKEN);
  }

  cerrarSesion(): void {
    localStorage.removeItem(CLAVE_TOKEN);
  }
}
