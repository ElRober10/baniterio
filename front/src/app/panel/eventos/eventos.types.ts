/**
 * Tipos del contrato con `/api/v1/eventos*`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.evento.dto`). `*Response` = lo que recibimos;
 * `*Request` = lo que enviamos.
 */

/** La cuenta a la que pertenece un evento (ver sección Cuentas). */
export interface CuentaRef {
  id: number;
  nombre: string;
}

export interface EventoResumen {
  id: number;
  nombre: string;
  fecha: string; // ISO yyyy-MM-dd
  fechaFin: string | null;
  lugar: string | null;
  pasado: boolean;
  cuenta: CuentaRef;
}

/** Me apunto / No voy / En duda. */
export type EstadoAsistencia = 'APUNTADO' | 'NO_VOY' | 'EN_DUDA';

/**
 * Bloque de asistencia dentro de `EventoDetalle` para el usuario que pregunta.
 * `miAsistencia` = su respuesta o `null`. `notificacionReenviableAt` = ISO o
 * `null` si nunca se ha mandado.
 */
export interface AsistenciaDetalle {
  miAsistencia: EstadoAsistencia | null;
  puedeNotificar: boolean;
  notificacionReenviableAt: string | null;
  apuntados: number;
  noVoy: number;
  enDuda: number;
  sinContestar: number;
}

/** Fila devuelta al añadir un asistente a mano (`POST /eventos/{id}/asistencias`). */
export interface AsistenciaResumen {
  id: number;
  nombre: string;
  estado: EstadoAsistencia;
  esManual: boolean;
}

export interface EventoDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  lugar: string | null;
  fecha: string;
  fechaFin: string | null;
  pasado: boolean;
  cuenta: CuentaRef;
  cuotaMaxima: number | null;
  creadoPor: { id: number; nombre: string } | null;
  puedoEditar: boolean;
  puedoBorrar: boolean;
  borradoPendiente: boolean;
  asistencia: AsistenciaDetalle;
}

/** `GET /api/v1/eventos?pagina=N`. */
export interface ListaEventosResponse {
  eventos: EventoResumen[];
  pagina: number;
  totalPaginas: number;
  puedeCrear: boolean;
  puedeSolicitar: boolean;
}

/**
 * Cuerpo de `POST` y `PUT` de un evento. `fechaFin` opcional. Para la cuenta:
 * o `cuentaId` (una existente) o `cuentaNueva: true` (crea una con el nombre del
 * evento), exactamente uno.
 */
export interface GuardarEventoRequest {
  nombre: string;
  descripcion: string | null;
  lugar: string | null;
  fecha: string; // yyyy-MM-dd
  fechaFin: string | null;
  cuentaId: number | null;
  cuentaNueva: boolean;
  /** Solo la aplica el backend si quien guarda es admin/superadmin. `null` = sin cuota / sin cambio. */
  cuotaMaxima: number | null;
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
  | 'CUENTA_YA_EXISTE'
  | 'CUENTA_NO_ENCONTRADA'
  | 'EVENTO_YA_PASADO'
  | 'NOTIFICACION_REENVIO_PRONTO'
  | 'ASISTENCIA_NO_ENCONTRADA'
  | 'ASISTENCIA_NO_MANUAL'
  | 'SIN_PERMISO'
  | 'VALIDACION';
