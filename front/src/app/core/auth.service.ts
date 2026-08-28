import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginBody, LoginDto, RegistroBody, UsuarioDto } from './auth.types';

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
