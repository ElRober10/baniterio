import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginBody, LoginDto, RegistroBody, SolicitudIngresoBody, UsuarioDto } from './auth.types';

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

  /**
   * Usuario de la sesión con su `rol` y sus `areas` (permisos del panel de
   * administración). Se puebla al hacer `login()` o `yo()` y se limpia al
   * `cerrarSesion()`. El guard y las pantallas de /admin lo consultan.
   */
  private readonly _usuarioActual = signal<UsuarioDto | null>(null);
  readonly usuarioActual = this._usuarioActual.asReadonly();

  registro(body: RegistroBody): Observable<UsuarioDto> {
    return this.http.post<UsuarioDto>(`${this.base}/auth/registro`, body);
  }

  /** Solicitud de acceso (teléfono no autorizado). El backend responde 201 sin cuerpo. */
  solicitarAcceso(body: SolicitudIngresoBody): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/solicitudes`, body);
  }

  login(body: LoginBody): Observable<LoginDto> {
    return this.http
      .post<LoginDto>(`${this.base}/auth/login`, body)
      .pipe(
        tap((res) => {
          localStorage.setItem(CLAVE_TOKEN, res.token);
          this._usuarioActual.set(res.usuario);
        }),
      );
  }

  /**
   * Pide el usuario de la sesión al backend (`GET /auth/yo`) y lo cachea en el
   * signal. Lanza error (401) si el token no vale o el usuario está inactivo.
   */
  yo(): Observable<UsuarioDto> {
    return this.http
      .get<UsuarioDto>(`${this.base}/auth/yo`)
      .pipe(tap((u) => this._usuarioActual.set(u)));
  }

  /**
   * Devuelve el usuario de la sesión sin repetir la llamada si ya lo tenemos.
   * Si falla la petición, resuelve a `null` en vez de propagar el error (útil
   * para el guard, que ya redirige por su cuenta).
   */
  asegurarYo(): Observable<UsuarioDto | null> {
    const actual = this._usuarioActual();
    if (actual) {
      return of(actual);
    }
    return this.yo().pipe(catchError(() => of(null)));
  }

  /** `true` si el usuario de la sesión tiene concedida esa área del panel. */
  tieneArea(area: string): boolean {
    return this._usuarioActual()?.areas?.includes(area) ?? false;
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
    this._usuarioActual.set(null);
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
