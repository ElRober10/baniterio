package com.baniterio.app.data

import com.baniterio.app.data.dto.AsistenciaResumenDto
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.data.dto.FichaBebidaBody
import com.baniterio.app.data.dto.FichaBebidaResponseDto

/**
 * Asistencia a eventos (pieza 3a): responder Me apunto / No voy / En duda,
 * mandar la notificación de convocatoria, añadir/quitar gente a mano y saber qué
 * eventos tengo pendientes de contestar. La ficha de bebida y la cuota (San
 * Miguel) son la pieza 3b. Misma mecánica que [EventosRepository].
 */
interface AsistenciaRepository {
    suspend fun responder(eventoId: Long, estado: String): ResultadoAsistencia<EventoDetalle>
    suspend fun mandarNotificacion(eventoId: Long, texto: String?): ResultadoAsistencia<Unit>
    suspend fun anadir(
        eventoId: Long,
        nombre: String,
        estado: String,
        ficha: FichaBebidaBody? = null,
    ): ResultadoAsistencia<AsistenciaResumenDto>
    suspend fun quitar(eventoId: Long, asistenciaId: Long): ResultadoAsistencia<Unit>
    suspend fun pendientes(): ResultadoAsistencia<List<EventoResumen>>

    /** Guarda mi ficha de bebida para un evento de San Miguel; devuelve la cuota. */
    suspend fun guardarFicha(
        eventoId: Long,
        body: FichaBebidaBody,
    ): ResultadoAsistencia<FichaBebidaResponseDto>
}
