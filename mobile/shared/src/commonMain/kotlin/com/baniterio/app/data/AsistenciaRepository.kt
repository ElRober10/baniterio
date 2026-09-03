package com.baniterio.app.data

import com.baniterio.app.data.dto.AsistenciaResumenDto
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.EventoResumen

/**
 * Asistencia a eventos (pieza 3a): responder Me apunto / No voy / En duda,
 * mandar la notificación de convocatoria, añadir/quitar gente a mano y saber qué
 * eventos tengo pendientes de contestar. Misma mecánica que [EventosRepository].
 */
interface AsistenciaRepository {
    suspend fun responder(eventoId: Long, estado: String): ResultadoAsistencia<EventoDetalle>
    suspend fun mandarNotificacion(eventoId: Long, texto: String?): ResultadoAsistencia<Unit>
    suspend fun anadir(eventoId: Long, nombre: String, estado: String): ResultadoAsistencia<AsistenciaResumenDto>
    suspend fun quitar(eventoId: Long, asistenciaId: Long): ResultadoAsistencia<Unit>
    suspend fun pendientes(): ResultadoAsistencia<List<EventoResumen>>
}
