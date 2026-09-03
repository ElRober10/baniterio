/**
 * Tipos del contrato con `/api/v1/eventos*`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.evento.dto`). `*Response` = lo que recibimos;
 * `*Request` = lo que enviamos.
 */

export interface EventoResumen {
  id: number;
  nombre: string;
  fecha: string; // ISO yyyy-MM-dd
  fechaFin: string | null;
  lugar: string | null;
  pasado: boolean;
}

export interface EventoDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  lugar: string | null;
  fecha: string;
  fechaFin: string | null;
  pasado: boolean;
  creadoPor: { id: number; nombre: string } | null;
  puedoEditar: boolean;
  puedoBorrar: boolean;
  borradoPendiente: boolean;
}

/** `GET /api/v1/eventos?pagina=N`. */
export interface ListaEventosResponse {
  eventos: EventoResumen[];
  pagina: number;
  totalPaginas: number;
  puedeCrear: boolean;
  puedeSolicitar: boolean;
}

/** Cuerpo de `POST` y `PUT` de un evento. `fechaFin` opcional. */
export interface GuardarEventoRequest {
  nombre: string;
  descripcion: string | null;
  lugar: string | null;
  fecha: string; // yyyy-MM-dd
  fechaFin: string | null;
}

/** Códigos de error propios de eventos (ver ApiExceptionHandler.java). */
export type CodigoErrorEvento =
  | 'EVENTO_NO_ENCONTRADO'
  | 'SIN_PERMISO_EVENTO'
  | 'SIN_CREDITO_EVENTO'
  | 'SOLICITUD_EVENTO_YA_PENDIENTE'
  | 'CREDITO_SIN_CONSUMIR'
  | 'SOLICITUD_EVENTO_NO_APLICA'
  | 'SOLICITUD_EVENTO_YA_RESUELTA'
  | 'SIN_PERMISO'
  | 'VALIDACION';
