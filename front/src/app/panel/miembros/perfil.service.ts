import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AvatarResumen,
  GuardarPerfilRequest,
  PerfilResponse,
  TarjetaMiembroResponse,
} from './perfil.types';

/**
 * Origen del backend (sin el sufijo `/api/v1`), para completar las URLs de
 * imagen que llegan del backend ya con `/api/v1/media/...` delante. En
 * producción `apiBaseUrl` es relativo (`/api/v1`) y el origen sale vacío: la
 * URL de imagen queda tal cual, resuelta contra el propio dominio. En
 * desarrollo `apiBaseUrl` es absoluto (`http://localhost:8080/api/v1`) y hay
 * que anteponer `http://localhost:8080`.
 */
const ORIGEN_MEDIA = environment.apiBaseUrl.replace(/\/api\/v1$/, '');

/** Completa una `imagenUrl` del backend (p. ej. `/api/v1/media/avatares/01_chico.png`)
 *  con el origen correcto. `null` si no hay imagen. */
export function urlMedia(imagenUrl: string | null): string | null {
  return imagenUrl === null ? null : ORIGEN_MEDIA + imagenUrl;
}

/**
 * Llamadas del editor de perfil y de la sección Miembros
 * (`/api/v1/perfil*`, `/api/v1/miembros`). Un método por endpoint; no maneja
 * errores, los deja propagar para que cada pantalla decida el mensaje según
 * el `codigo` que traiga el backend (mismo patrón que `AdminService`).
 */
@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  miPerfil(): Observable<PerfilResponse> {
    return this.http.get<PerfilResponse>(`${this.base}/perfil`);
  }

  guardar(body: GuardarPerfilRequest): Observable<PerfilResponse> {
    return this.http.put<PerfilResponse>(`${this.base}/perfil`, body);
  }

  /** Sube una foto ya elegida por el usuario; el backend la recorta y la deja en 512×512. */
  subirFoto(archivo: File): Observable<{ imagenRef: string }> {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<{ imagenRef: string }>(`${this.base}/perfil/foto`, form);
  }

  avatares(): Observable<AvatarResumen[]> {
    return this.http.get<AvatarResumen[]>(`${this.base}/perfil/avatares`);
  }

  /** Confirmo el vínculo de pareja que otra persona declaró conmigo. */
  aceptarPareja(): Observable<void> {
    return this.http.post<void>(`${this.base}/perfil/pareja/aceptar`, {});
  }

  /** Rechazo el vínculo de pareja que otra persona declaró conmigo. */
  rechazarPareja(): Observable<void> {
    return this.http.post<void>(`${this.base}/perfil/pareja/rechazar`, {});
  }

  /** Deshago mi vínculo de pareja vivo (cualquiera de los dos lados puede). */
  romperPareja(): Observable<void> {
    return this.http.delete<void>(`${this.base}/perfil/pareja`);
  }

  /** Tarjetas de la sección Miembros, ya ordenadas por el backend. */
  miembros(): Observable<TarjetaMiembroResponse[]> {
    return this.http.get<TarjetaMiembroResponse[]>(`${this.base}/miembros`);
  }
}
