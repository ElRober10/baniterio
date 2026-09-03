package com.baniterio.app.data

import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.GuardarEventoRequest
import com.baniterio.app.data.dto.ListaEventosResponse

/**
 * Endpoints de la sección Eventos (`/api/v1/eventos*`). Misma mecánica que
 * [PerfilRepository]: Bearer de [SesionHolder], nunca lanza por errores HTTP
 * esperados, relanza `CancellationException`.
 */
interface EventosRepository {
    suspend fun listar(pagina: Int): ResultadoEvento<ListaEventosResponse>
    suspend fun detalle(id: Long): ResultadoEvento<EventoDetalle>
    suspend fun crear(req: GuardarEventoRequest): ResultadoEvento<EventoDetalle>
    suspend fun editar(id: Long, req: GuardarEventoRequest): ResultadoEvento<EventoDetalle>
    suspend fun borrar(id: Long): ResultadoEvento<BorradoEvento>
    suspend fun solicitarCrear(mensaje: String?): ResultadoEvento<Unit>
}
