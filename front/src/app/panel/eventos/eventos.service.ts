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
  ListadoAsistentes,
  ListaEventosResponse,
  MetodoPago,
  PendientesRespuestaResponse,
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

  /** "Borra" el evento ocultándolo (no lo quita de la BBDD); solo admin/superadmin. */
  ocultar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/eventos/${id}`);
  }

  /** Deshace {@link ocultar}. Devuelve el detalle ya actualizado. */
  recuperar(id: number): Observable<EventoDetalle> {
    return this.http.put<EventoDetalle>(`${this.base}/eventos/${id}/recuperar`, {});
  }

  /** Los eventos "borrados" (ocultos, recuperables); solo admin/superadmin. */
  listarOcultos(): Observable<{ eventos: EventoResumen[] }> {
    return this.http.get<{ eventos: EventoResumen[] }>(`${this.base}/eventos/ocultos`);
  }

  /** Quién va a un evento de San Miguel, qué bebe, su cuota y su estado de pago (pieza 4). */
  asistentesEvento(id: number): Observable<ListadoAsistentes> {
    return this.http.get<ListadoAsistentes>(`${this.base}/eventos/${id}/asistentes`);
  }

  /** Un administrador confirma el pago de una asistencia (pieza 5). Devuelve el listado recalculado. */
  confirmarPago(
    eventoId: number,
    asistenciaId: number,
    metodo: MetodoPago,
  ): Observable<ListadoAsistentes> {
    return this.http.put<ListadoAsistentes>(
      `${this.base}/eventos/${eventoId}/asistencias/${asistenciaId}/pago`,
      { metodo },
    );
  }

  /** Deshace la confirmación de pago de una asistencia. */
  deshacerPago(eventoId: number, asistenciaId: number): Observable<ListadoAsistentes> {
    return this.http.delete<ListadoAsistentes>(
      `${this.base}/eventos/${eventoId}/asistencias/${asistenciaId}/pago`,
    );
  }

  solicitarCrear(mensaje?: string): Observable<{ id: number; estado: string }> {
    return this.http.post<{ id: number; estado: string }>(
      `${this.base}/eventos/solicitudes`,
      mensaje ? { mensaje } : {},
    );
  }

  // --- Asistencia (pieza 3a) ---

  /**
   * Respuesta a la convocatoria: la propia, o —si hay vínculo (pareja aceptada,
   * hijo con cuenta propia)— en nombre de `paraUsuarioId`. Devuelve el detalle
   * ya actualizado, desde la perspectiva de esa persona.
   */
  responder(id: number, estado: EstadoAsistencia, paraUsuarioId?: number): Observable<EventoDetalle> {
    return this.http.put<EventoDetalle>(`${this.base}/eventos/${id}/asistencia`, {
      estado,
      paraUsuarioId: paraUsuarioId ?? null,
    });
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

  /**
   * Eventos con notificación sin contestar: los propios y los de quien pueda
   * responder en su nombre (para el modal bloqueante).
   */
  pendientesRespuesta(): Observable<PendientesRespuestaResponse> {
    return this.http.get<PendientesRespuestaResponse>(
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
