package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Fila de `GET /api/v1/logs` (ver back `LogEventoDto`). */
@Serializable
data class LogEventoDto(
    val id: Long,
    val origen: String,
    val usuarioId: Long? = null,
    val usuarioNombre: String? = null,
    val metodo: String? = null,
    val ruta: String? = null,
    val estado: Int? = null,
    val codigoError: String? = null,
    val mensaje: String? = null,
    val creadoEn: String,
)

@Serializable
data class PaginaLogsDto(
    val contenido: List<LogEventoDto>,
    val total: Long,
    val pagina: Int,
    val tamano: Int,
)
