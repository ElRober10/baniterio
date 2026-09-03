import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventoDetalle, GuardarEventoRequest, ListaEventosResponse } from './eventos.types';

/**
 * Llamadas de la sección Eventos (`/api/v1/eventos*`). Un método por endpoint;
 * no maneja errores, los deja propagar para que cada pantalla traduzca el
 * `codigo` del backend (mismo patrón que `PerfilService` y `AdminService`).
 */
@Injectable({ providedIn: 'root' })
export class EventosService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listar(pagina: number): Observable<ListaEventosResponse> {
    return this.http.get<ListaEventosResponse>(`${this.base}/eventos`, {
      params: { pagina: String(pagina) },
    });
  }

  detalle(id: number): Observable<EventoDetalle> {
    return this.http.get<EventoDetalle>(`${this.base}/eventos/${id}`);
  }

  crear(body: GuardarEventoRequest): Observable<EventoDetalle> {
    return this.http.post<EventoDetalle>(`${this.base}/eventos`, body);
  }

  editar(id: number, body: GuardarEventoRequest): Observable<EventoDetalle> {
    return this.http.put<EventoDetalle>(`${this.base}/eventos/${id}`, body);
  }

  /** 204 si lo borra un admin; 202 `{ estado: 'PENDIENTE' }` si genera solicitud. */
  borrar(id: number): Observable<{ estado: string } | null> {
    return this.http.delete<{ estado: string } | null>(`${this.base}/eventos/${id}`);
  }

  solicitarCrear(mensaje?: string): Observable<{ id: number; estado: string }> {
    return this.http.post<{ id: number; estado: string }>(
      `${this.base}/eventos/solicitudes`,
      mensaje ? { mensaje } : {},
    );
  }
}
