/**
 * Tipos del contrato con `/api/v1/eventos*`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.evento.dto`). `*Response` = lo que recibimos;
 * `*Request` = lo que enviamos.
 */

import { EstadoPagoCuota } from '../cuentas/cuentas.types';

export type { EstadoPagoCuota };

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
  /** `true` en cuanto se ha mandado la convocatoria; hasta entonces se ocultan los recuentos. */
  notificacionMandada: boolean;
  apuntados: number;
  noVoy: number;
  enDuda: number;
  sinContestar: number;
  ficha: FichaBebidaBloque;
}

/** Ficha de bebida (pieza 3b, solo eventos de San Miguel). */
export type Alternativa = 'CERVEZA' | 'TINTO_VERANO' | 'NADA' | 'CERVEZA_ESPECIAL';
export type Modalidad = 'COMPLETA' | 'SOLO_CERVEZA' | 'UN_DIA' | 'EMBARAZADA';

/** Una opción de un desplegable de la ficha. */
export interface BebidaRef {
  id: number;
  nombre: string;
}

export interface CatalogoBebidas {
  alcohol: BebidaRef[];
  refresco: BebidaRef[];
}

/** Mi ficha ya rellenada, como la devuelve el detalle del evento. */
export interface FichaBebidaMia {
  alcoholBebidaId: number | null;
  alcohol: string | null;
  refrescoBebidaId: number | null;
  refresco: string | null;
  alternativa: Alternativa;
  cervezaEspecial: string | null;
  embarazada: boolean;
  asisteDia1: boolean;
  asisteDia2: boolean;
  modalidad: Modalidad;
  cuota: number | null;
  cuotaPendiente: boolean;
  bebidaPendiente: boolean;
  estadoPago: EstadoPagoCuota | null;
  metodoPago: MetodoPago | null;
  pagadoPor: string | null;
  pagadoAt: string | null;
  /** Mi declaración de pago si está PENDIENTE o RECHAZADA (si está confirmada lo dice `estadoPago`). */
  miPagoDeclarado: MiPagoDeclarado | null;
}

/** Una persona a la que cubre un pago: su nombre y su cuota. */
export interface CubiertoPago {
  nombre: string;
  cuota: number | null;
}

/** Mi declaración de pago pendiente (o rechazada) para un evento. */
export interface MiPagoDeclarado {
  importe: number;
  metodoPago: MetodoPago;
  estado: 'PENDIENTE' | 'RECHAZADA';
  createdAt: string;
  cubre: CubiertoPago[];
}

/** Sub-bloque `asistencia.ficha` de `EventoDetalle`. */
export interface FichaBebidaBloque {
  llevaFicha: boolean;
  diasEvento: string[];
  miFicha: FichaBebidaMia | null;
}

/** Cuerpo de `PUT /eventos/{id}/ficha-bebida` y de la parte `ficha` del alta manual. */
export interface FichaBebidaBody {
  estado: 'APUNTADO' | 'EN_DUDA';
  alcoholBebidaId: number | null;
  alcoholOtra: string | null;
  refrescoBebidaId: number | null;
  refrescoOtra: string | null;
  alternativa: Alternativa;
  cervezaEspecial: string | null;
  embarazada: boolean;
  asisteDia1: boolean;
  asisteDia2: boolean;
  /** Solo en `PUT /ficha-bebida` directo: `null` para la propia ficha, o la pareja/hijo por quien se responde. */
  paraUsuarioId?: number | null;
}

export interface FichaBebidaResponse {
  modalidad: Modalidad;
  cuota: number | null;
  cuotaPendiente: boolean;
}

/** Bebida propuesta con "Otra…" a la espera de aprobación. */
export interface BebidaPendiente {
  id: number;
  tipo: 'ALCOHOL' | 'REFRESCO';
  nombre: string;
  propuestaPor: { id: number; nombre: string } | null;
  createdAt: string;
}

/** Fila devuelta al añadir un asistente a mano (`POST /eventos/{id}/asistencias`). */
export interface AsistenciaResumen {
  id: number;
  nombre: string;
  telefono: string | null;
  estado: EstadoAsistencia;
  esManual: boolean;
  cuota: number | null;
  modalidad: Modalidad | null;
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
  /** Las 5 cuotas del evento; `null` mientras un admin no las ponga. */
  cuotaCubatas: number | null;
  cuotaCervezas: number | null;
  cuotaCubatas1Dia: number | null;
  cuotaCervezas1Dia: number | null;
  cuotaEmbarazada: number | null;
  precioCamiseta: number | null;
  precioSudadera: number | null;
  creadoPor: { id: number; nombre: string } | null;
  puedoEditar: boolean;
  puedoBorrar: boolean;
  /** `true` si el evento está "borrado" (oculto, recuperable). */
  oculto: boolean;
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

/** Persona por quien es un pendiente de respuesta: uno mismo, o pareja/hijo con cuenta. */
export interface ParaUsuario {
  id: number;
  nombre: string;
}

/**
 * Un evento pendiente y por quién es. `paraUsuario` es uno mismo salvo que se
 * pueda responder por otro (pareja con vínculo aceptado, hijo con cuenta
 * propia) y esa persona aún no haya contestado.
 */
export interface PendienteRespuesta {
  evento: EventoResumen;
  paraUsuario: ParaUsuario;
}

/** `GET /api/v1/eventos/pendientes-respuesta`. */
export interface PendientesRespuestaResponse {
  eventos: PendienteRespuesta[];
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
  /**
   * Única cuota que se pone a mano; el backend deriva las otras 4 de esta
   * (cervezas = cubatas−10 · 1 día cubatas = cubatas/2+1 · 1 día cervezas =
   * cervezas/2+1 · embarazada = 5 fijo). Solo la aplica si quien guarda es
   * admin/superadmin. `null` = sin poner / sin cambio.
   */
  cuotaCubatas: number | null;
  /** Precio de la camiseta / sudadera de la peña; solo admin. `null` = sin poner / sin cambio. */
  precioCamiseta: number | null;
  precioSudadera: number | null;
}

// --- Listado de asistentes y pago (pieza 4) ---

/** Método de un pago declarado. */
export type MetodoPago = 'TRANSFERENCIA' | 'BIZUM' | 'EFECTIVO';

/** Relación de una persona a la que puedo incluir en mi pago. */
export type RelacionPago = 'PAREJA' | 'HIJO' | 'INVITADO';

/** Lo que bebe un asistente en el listado. `alcohol`/`refresco` null = no bebe / no quiere ninguno. */
export interface BebidaFila {
  alcohol: string | null;
  refresco: string | null;
  alternativa: Alternativa;
  modalidad: Modalidad;
}

/** Una fila del listado de asistentes. `estado` es APUNTADO o EN_DUDA. */
export interface AsistenteFila {
  nombre: string;
  telefono: string | null;
  estado: 'APUNTADO' | 'EN_DUDA';
  esManual: boolean;
  bebida: BebidaFila | null;
  cuota: number | null;
  estadoPago: EstadoPagoCuota | null;
  asistenciaId: number;
  metodoPago: MetodoPago | null;
  pagadoPor: string | null;
  pagadoAt: string | null;
}

/** Una fila de la cola "Confirmar pagos" del panel de administración. */
export interface PagoDeclaradoPendiente {
  id: number;
  eventoId: number;
  eventoNombre: string;
  declaradoPor: string;
  importe: number;
  metodoPago: MetodoPago;
  createdAt: string;
  cubre: CubiertoPago[];
}

/** Alguien a quien puedo incluir en mi pago (pareja, hijo mayor con cuenta, invitado propio). */
export interface PersonaPagable {
  nombre: string;
  cuota: number;
  relacion: RelacionPago;
  usuarioId: number | null;
  asistenciaId: number | null;
}

/** `GET /api/v1/eventos/{id}/asistentes`. */
export interface ListadoAsistentes {
  asistentes: AsistenteFila[];
  totalCuotas: number;
  totalPagado: number;
  puedoPagarPor: PersonaPagable[];
  miCuota: number | null;
  puedoConfirmarPagos: boolean;
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
  | 'EVENTO_SIN_FICHA'
  | 'FICHA_SIN_CUOTA'
  | 'PAGO_DECLARADO_YA_PENDIENTE'
  | 'PAGO_DECLARADO_NO_ENCONTRADO'
  | 'PAGO_DECLARADO_YA_RESUELTO'
  | 'BEBIDA_NO_ENCONTRADA'
  | 'SIN_PERMISO'
  | 'VALIDACION';
