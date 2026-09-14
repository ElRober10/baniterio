package com.baniterio.app.data

import com.baniterio.app.data.dto.AsistenciaResumenDto
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.FichaBebidaBody
import com.baniterio.app.data.dto.FichaBebidaResponseDto
import com.baniterio.app.data.dto.PendienteRespuestaDto

/**
 * Asistencia a eventos (pieza 3a): responder Me apunto / No voy / En duda,
 * mandar la notificación de convocatoria, añadir/quitar gente a mano y saber qué
 * eventos tengo pendientes de contestar. La ficha de bebida y la cuota (San
 * Miguel) son la pieza 3b. Misma mecánica que [EventosRepository].
 */
interface AsistenciaRepository {
    /**
     * Respuesta a un evento: la propia, o —si `paraUsuarioId` es la pareja
     * (vínculo aceptado) o un hijo con cuenta propia— en su nombre.
     */
    suspend fun responder(
        eventoId: Long,
        estado: String,
        paraUsuarioId: Long? = null,
    ): ResultadoAsistencia<EventoDetalle>
    suspend fun mandarNotificacion(eventoId: Long, texto: String?): ResultadoAsistencia<Unit>
    suspend fun anadir(
        eventoId: Long,
        nombre: String,
        estado: String,
        telefono: String? = null,
        ficha: FichaBebidaBody? = null,
    ): ResultadoAsistencia<AsistenciaResumenDto>
    suspend fun quitar(eventoId: Long, asistenciaId: Long): ResultadoAsistencia<Unit>

    /** Pendientes propios y los de quien se pueda responder en su nombre (pareja/hijos con cuenta). */
    suspend fun pendientes(): ResultadoAsistencia<List<PendienteRespuestaDto>>

    /** Guarda una ficha de bebida (la propia o la de `body.paraUsuarioId`); devuelve la cuota. */
    suspend fun guardarFicha(
        eventoId: Long,
        body: FichaBebidaBody,
    ): ResultadoAsistencia<FichaBebidaResponseDto>
}
