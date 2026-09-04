import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AsistenciaResumen,
  BebidaPendiente,
  CatalogoBebidas,
  EstadoAsistencia,
  EventoDetalle,
  EventoResumen,
  FichaBebidaBody,
  FichaBebidaResponse,
  GuardarEventoRequest,
  ListaEventosResponse,
} from './eventos.types';

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

  // --- Asistencia (pieza 3a) ---

  /** Mi respuesta a la convocatoria. Devuelve el detalle ya actualizado. */
  responder(id: number, estado: EstadoAsistencia): Observable<EventoDetalle> {
    return this.http.put<EventoDetalle>(`${this.base}/eventos/${id}/asistencia`, { estado });
  }

  /** Manda (o reenvía) la notificación de convocatoria. Texto libre opcional. */
  mandarNotificacion(id: number, texto?: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/eventos/${id}/notificacion`,
      texto ? { texto } : {},
    );
  }

  /** Añade a mano a alguien sin app (invitado). `ficha` obligatoria en San Miguel. */
  anadirAsistente(
    id: number,
    nombre: string,
    estado: EstadoAsistencia,
    ficha?: FichaBebidaBody,
  ): Observable<AsistenciaResumen> {
    return this.http.post<AsistenciaResumen>(`${this.base}/eventos/${id}/asistencias`, {
      nombre,
      estado,
      ...(ficha ? { ficha } : {}),
    });
  }

  quitarAsistente(id: number, asistenciaId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/eventos/${id}/asistencias/${asistenciaId}`);
  }

  /** Eventos con notificación que aún no he contestado (para la pantalla bloqueante). */
  pendientesRespuesta(): Observable<{ eventos: EventoResumen[] }> {
    return this.http.get<{ eventos: EventoResumen[] }>(
      `${this.base}/eventos/pendientes-respuesta`,
    );
  }

  // --- Ficha de bebida y catálogo (pieza 3b) ---

  /** Las dos listas de bebidas aceptadas para los desplegables de la ficha. */
  catalogoBebidas(): Observable<CatalogoBebidas> {
    return this.http.get<CatalogoBebidas>(`${this.base}/bebidas/catalogo`);
  }

  /** Guarda mi ficha de bebida para un evento de San Miguel; devuelve la cuota. */
  guardarFichaBebida(id: number, body: FichaBebidaBody): Observable<FichaBebidaResponse> {
    return this.http.put<FichaBebidaResponse>(`${this.base}/eventos/${id}/ficha-bebida`, body);
  }

  /** Bebidas propuestas con "Otra…" a la espera de aprobación (solo admin). */
  bebidasPendientes(): Observable<BebidaPendiente[]> {
    return this.http.get<BebidaPendiente[]>(`${this.base}/bebidas`, {
      params: { estado: 'PENDIENTE' },
    });
  }

  aceptarBebida(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/bebidas/${id}/aceptar`, {});
  }

  rechazarBebida(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/bebidas/${id}/rechazar`, {});
  }
}
