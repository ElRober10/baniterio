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
 *   respuesta llega bien.
 * - `token()` devuelve el JWT crudo; `sesionActiva()` dice si sirve (existe y
 *   no ha caducado); `cerrarSesion()` lo borra.
 *
 * La "sesión" del front es exactamente ese JWT en localStorage. El guard
 * (auth.guard.ts) y el interceptor (auth.interceptor.ts) son quienes lo usan.
 */
const CLAVE_TOKEN = 'baniterio.token';

interface JwtPayload {
  sub: string;
  esSuperadmin: boolean;
  iat: number;
  exp: number;
}

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

  /** Hay token y su fecha de caducidad (`exp` del JWT) todavía no ha pasado. */
  sesionActiva(): boolean {
    const payload = this.leerPayload();
    return payload !== null && payload.exp * 1000 > Date.now();
  }

  cerrarSesion(): void {
    localStorage.removeItem(CLAVE_TOKEN);
  }

  /**
   * Decodifica el trozo del medio del JWT (`cabecera.PAYLOAD.firma`). Es JSON en
   * base64url, sin verificar la firma — eso solo lo hace el backend; aquí basta
   * para leer `exp`. Devuelve null si no hay token o está mal formado.
   */
  private leerPayload(): JwtPayload | null {
    const token = this.token();
    if (!token) {
      return null;
    }
    try {
      const parte = token.split('.')[1] ?? '';
      const base64 = parte.replaceAll('-', '+').replaceAll('_', '/');
      const relleno = base64.length % 4 ? '='.repeat(4 - (base64.length % 4)) : '';
      return JSON.parse(atob(base64 + relleno)) as JwtPayload;
    } catch {
      return null;
    }
  }
}
