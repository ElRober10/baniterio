package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** La cuenta a la que pertenece un evento (ver sección Cuentas). */
@Serializable
data class CuentaRef(val id: Long, val nombre: String)

/** Fila del listado de eventos (`GET /eventos`). Fechas en ISO `yyyy-MM-dd`. */
@Serializable
data class EventoResumen(
    val id: Long,
    val nombre: String,
    val fecha: String,
    val fechaFin: String? = null,
    val lugar: String? = null,
    val pasado: Boolean,
    val cuenta: CuentaRef,
)

@Serializable
data class CreadoPor(val id: Long, val nombre: String)

/**
 * Bloque de asistencia dentro de [EventoDetalle] para el usuario que pregunta.
 * `miAsistencia` = "APUNTADO" | "NO_VOY" | "EN_DUDA" o `null`.
 * `notificacionReenviableAt` = ISO o `null` si nunca se ha mandado.
 */
@Serializable
data class AsistenciaDetalleDto(
    val miAsistencia: String? = null,
    val puedeNotificar: Boolean = false,
    val notificacionReenviableAt: String? = null,
    val notificacionMandada: Boolean = false,
    val apuntados: Int = 0,
    val noVoy: Int = 0,
    val enDuda: Int = 0,
    val sinContestar: Int = 0,
    val ficha: FichaBebidaBloqueDto = FichaBebidaBloqueDto(),
)

/** Sub-bloque `asistencia.ficha` de [EventoDetalle] (pieza 3b, San Miguel). */
@Serializable
data class FichaBebidaBloqueDto(
    val llevaFicha: Boolean = false,
    val diasEvento: List<String> = emptyList(),
    val miFicha: FichaBebidaMiaDto? = null,
)

@Serializable
data class FichaBebidaMiaDto(
    val alcoholBebidaId: Long? = null,
    val alcohol: String? = null,
    val refrescoBebidaId: Long? = null,
    val refresco: String? = null,
    val alternativa: String = "NADA",
    val cervezaEspecial: String? = null,
    val embarazada: Boolean = false,
    val asisteDia1: Boolean = true,
    val asisteDia2: Boolean = true,
    val modalidad: String = "COMPLETA",
    val cuota: Double? = null,
    val cuotaPendiente: Boolean = false,
    val bebidaPendiente: Boolean = false,
    val estadoPago: String? = null,
    val metodoPago: String? = null,
    val pagadoPor: String? = null,
    val pagadoAt: String? = null,
    val miPagoDeclarado: MiPagoDeclaradoDto? = null,
)

/** Una persona a la que cubre un pago: su nombre y su cuota. */
@Serializable
data class CubiertoPagoDto(val nombre: String, val cuota: Double? = null)

/** Mi declaración de pago pendiente (o rechazada) dentro de `miFicha`. `estado` = PENDIENTE | RECHAZADA. */
@Serializable
data class MiPagoDeclaradoDto(
    val importe: Double,
    val metodoPago: String,
    val estado: String,
    val createdAt: String,
    val cubre: List<CubiertoPagoDto> = emptyList(),
)

/** Cuerpo de `POST /eventos/{id}/pagos-declarados`. */
@Serializable
data class DeclararPagoBody(
    val importe: Double,
    val metodo: String,
    val cubreUsuarioIds: List<Long> = emptyList(),
    val cubreAsistenciaIds: List<Long> = emptyList(),
)

/** Una fila de la cola "Confirmar pagos" del panel de administración. */
@Serializable
data class PagoDeclaradoPendienteDto(
    val id: Long,
    val eventoId: Long,
    val eventoNombre: String,
    val declaradoPor: String,
    val importe: Double,
    val metodoPago: String,
    val createdAt: String,
    val cubre: List<CubiertoPagoDto> = emptyList(),
)

/**
 * Cuerpo de `PUT /eventos/{id}/ficha-bebida` y de la parte `ficha` del alta
 * manual. `paraUsuarioId` solo se usa en el `PUT` directo (ver
 * [ResponderAsistenciaBody]); el alta manual no lo necesita.
 */
@Serializable
data class FichaBebidaBody(
    val estado: String,
    val alcoholBebidaId: Long? = null,
    val alcoholOtra: String? = null,
    val refrescoBebidaId: Long? = null,
    val refrescoOtra: String? = null,
    val alternativa: String = "NADA",
    val cervezaEspecial: String? = null,
    val embarazada: Boolean = false,
    val asisteDia1: Boolean = true,
    val asisteDia2: Boolean = true,
    val paraUsuarioId: Long? = null,
)

@Serializable
data class FichaBebidaResponseDto(
    val modalidad: String,
    val cuota: Double? = null,
    val cuotaPendiente: Boolean = false,
)

/** Detalle de un evento (`GET /eventos/{id}`). */
@Serializable
data class EventoDetalle(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val lugar: String? = null,
    val fecha: String,
    val fechaFin: String? = null,
    val pasado: Boolean,
    val cuenta: CuentaRef,
    /** Las 5 cuotas del evento; `null` mientras un admin no las ponga. */
    val cuotaCubatas: Double? = null,
    val cuotaCervezas: Double? = null,
    val cuotaCubatas1Dia: Double? = null,
    val cuotaCervezas1Dia: Double? = null,
    val cuotaEmbarazada: Double? = null,
    val precioCamiseta: Double? = null,
    val precioSudadera: Double? = null,
    val creadoPor: CreadoPor? = null,
    val puedoEditar: Boolean,
    val puedoBorrar: Boolean,
    /** `true` si el evento está "borrado" (oculto, recuperable). */
    val oculto: Boolean,
    val asistencia: AsistenciaDetalleDto = AsistenciaDetalleDto(),
)

/** Fila devuelta al añadir un asistente a mano (`POST /eventos/{id}/asistencias`). */
@Serializable
data class AsistenciaResumenDto(
    val id: Long,
    val nombre: String,
    val telefono: String? = null,
    val estado: String,
    val esManual: Boolean,
    val cuota: Double? = null,
    val modalidad: String? = null,
)

/**
 * `paraUsuarioId` es `null` para responder por uno mismo, o la pareja/hijo con
 * cuenta propia por quien se responde (ver `VinculoFamiliarService` en el back).
 */
@Serializable
data class ResponderAsistenciaBody(val estado: String, val paraUsuarioId: Long? = null)

// --- Listado de asistentes y pago (pieza 4) ---

/** Lo que bebe un asistente en el listado. `alcohol` null = no bebe alcohol. */
@Serializable
data class BebidaFilaDto(
    val alcohol: String? = null,
    val refresco: String,
    val alternativa: String,
    val modalidad: String,
)

/** Una fila del listado de asistentes. `estado` = APUNTADO | EN_DUDA. */
@Serializable
data class AsistenteFilaDto(
    val nombre: String,
    val telefono: String? = null,
    val estado: String,
    val esManual: Boolean,
    val bebida: BebidaFilaDto? = null,
    val cuota: Double? = null,
    val estadoPago: String? = null,
    val asistenciaId: Long = 0,
    val metodoPago: String? = null,
    val pagadoPor: String? = null,
    val pagadoAt: String? = null,
    val pendienteTransferir: Double? = null,
)

/** Alguien a quien puedo incluir en mi pago. `relacion` = PAREJA | HIJO | INVITADO. */
@Serializable
data class PersonaPagableDto(
    val nombre: String,
    val cuota: Double,
    val relacion: String,
    val usuarioId: Long? = null,
    val asistenciaId: Long? = null,
)

/** `GET /eventos/{id}/asistentes` (pieza 4). */
@Serializable
data class ListadoAsistentesDto(
    val asistentes: List<AsistenteFilaDto> = emptyList(),
    val totalCuotas: Double = 0.0,
    val totalPagado: Double = 0.0,
    val puedoPagarPor: List<PersonaPagableDto> = emptyList(),
    val miCuota: Double? = null,
    val puedoConfirmarPagos: Boolean = false,
    val opcionesCuota: List<OpcionCuotaDto> = emptyList(),
)

/** Una de las cuotas del evento, para actualizar la de un asistente. */
@Serializable
data class OpcionCuotaDto(val texto: String, val importe: Double)

@Serializable
data class AnadirAsistenteBody(
    val nombre: String,
    val telefono: String? = null,
    val estado: String,
    val ficha: FichaBebidaBody? = null,
)

@Serializable
data class MandarNotificacionBody(val texto: String? = null)

/** Cuerpo de `PUT /eventos/{id}/asistencias/{asistenciaId}/pago`. */
@Serializable
data class ConfirmarPagoBody(val metodo: String)

/** Cuerpo de `PUT /eventos/{id}/asistencias/{asistenciaId}/cuota`. */
@Serializable
data class ActualizarCuotaBody(val cuota: Double, val metodo: String? = null)

/** Persona por quien es un pendiente: uno mismo, o pareja/hijo con cuenta por quien se responde. */
@Serializable
data class ParaUsuarioDto(val id: Long, val nombre: String)

/** Un evento pendiente y por quién es (ver [ParaUsuarioDto]). */
@Serializable
data class PendienteRespuestaDto(val evento: EventoResumen, val paraUsuario: ParaUsuarioDto)

/** `GET /eventos/pendientes-respuesta`. */
@Serializable
data class PendientesRespuestaDto(val eventos: List<PendienteRespuestaDto> = emptyList())

/** `GET /eventos/ocultos`: los eventos "borrados" (ocultos, recuperables). Solo admin/superadmin. */
@Serializable
data class EventosOcultosResponse(val eventos: List<EventoResumen> = emptyList())

@Serializable
data class ListaEventosResponse(
    val eventos: List<EventoResumen> = emptyList(),
    val pagina: Int,
    val totalPaginas: Int,
    val puedeCrear: Boolean,
    val puedeSolicitar: Boolean,
)

/**
 * Cuerpo de `POST /eventos` y `PUT /eventos/{id}`. Para la cuenta: o `cuentaId`
 * (una existente) o `cuentaNueva = true` (crea una con el nombre del evento).
 */
@Serializable
data class GuardarEventoRequest(
    val nombre: String,
    val descripcion: String? = null,
    val lugar: String? = null,
    val fecha: String,
    val fechaFin: String? = null,
    val cuentaId: Long? = null,
    val cuentaNueva: Boolean = false,
    /**
     * Única cuota que se pone a mano; el backend deriva las otras 4 de esta
     * (cervezas = cubatas−10 · 1 día cubatas = cubatas/2+1 · 1 día cervezas =
     * cervezas/2+1 · embarazada = 5 fijo). Solo la aplica si quien guarda es
     * admin/superadmin.
     */
    val cuotaCubatas: Double? = null,
    val precioCamiseta: Double? = null,
    val precioSudadera: Double? = null,
)

/** Fila del bloque de administración "Solicitudes de evento". */
@Serializable
data class SolicitudEventoResumen(
    val id: Long,
    val tipo: String,           // CREAR | BORRAR
    val estado: String,
    val solicitante: SolicitanteEvento,
    val evento: EventoRefResumen? = null,
    val mensaje: String? = null,
    val createdAt: String,
)

@Serializable
data class SolicitanteEvento(val id: Long, val nombre: String, val apellidos: String)

@Serializable
data class EventoRefResumen(val id: Long, val nombre: String, val fecha: String)

/** Respuesta 201 de `POST /eventos/solicitudes` y 202 de `DELETE /eventos/{id}`. */
@Serializable
data class CrearSolicitudEventoResponse(val id: Long? = null, val estado: String)
