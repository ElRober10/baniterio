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
    val apuntados: Int = 0,
    val noVoy: Int = 0,
    val enDuda: Int = 0,
    val sinContestar: Int = 0,
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
    val cuotaMaxima: Double? = null,
    val creadoPor: CreadoPor? = null,
    val puedoEditar: Boolean,
    val puedoBorrar: Boolean,
    val borradoPendiente: Boolean,
    val asistencia: AsistenciaDetalleDto = AsistenciaDetalleDto(),
)

/** Fila devuelta al añadir un asistente a mano (`POST /eventos/{id}/asistencias`). */
@Serializable
data class AsistenciaResumenDto(
    val id: Long,
    val nombre: String,
    val estado: String,
    val esManual: Boolean,
)

@Serializable
data class ResponderAsistenciaBody(val estado: String)

@Serializable
data class AnadirAsistenteBody(val nombre: String, val estado: String)

@Serializable
data class MandarNotificacionBody(val texto: String? = null)

/** `GET /eventos/pendientes-respuesta`. */
@Serializable
data class PendientesRespuestaDto(val eventos: List<EventoResumen> = emptyList())

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
    /** Solo la aplica el backend si quien guarda es admin/superadmin. */
    val cuotaMaxima: Double? = null,
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
