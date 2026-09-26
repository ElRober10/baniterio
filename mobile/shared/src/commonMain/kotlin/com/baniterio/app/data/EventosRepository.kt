package com.baniterio.app.data

import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.data.dto.GuardarEventoRequest
import com.baniterio.app.data.dto.DeclararPagoBody
import com.baniterio.app.data.dto.ListadoAsistentesDto
import com.baniterio.app.data.dto.ListaEventosResponse
import com.baniterio.app.data.dto.PagoDeclaradoPendienteDto

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
    /** "Borra" el evento ocultándolo (no lo quita de la BBDD); solo admin/superadmin. */
    suspend fun ocultar(id: Long): ResultadoEvento<Unit>
    /** Deshace [ocultar]. Devuelve el detalle ya actualizado. */
    suspend fun recuperar(id: Long): ResultadoEvento<EventoDetalle>
    /** Los eventos "borrados" (ocultos, recuperables); solo admin/superadmin. */
    suspend fun listarOcultos(): ResultadoEvento<List<EventoResumen>>
    /** Listado de asistentes de un evento de San Miguel (pieza 4). Solo lectura. */
    suspend fun asistentes(eventoId: Long): ResultadoEvento<ListadoAsistentesDto>

    /** Un administrador confirma el pago de una asistencia (pieza 5). Devuelve el listado recalculado. */
    suspend fun confirmarPago(
        eventoId: Long,
        asistenciaId: Long,
        metodo: String,
    ): ResultadoEvento<ListadoAsistentesDto>

    /** Un administrador cambia la cuota de una asistencia; `metodo` es cómo se pagó la diferencia. */
    suspend fun actualizarCuota(
        eventoId: Long,
        asistenciaId: Long,
        cuota: Double,
        metodo: String?,
    ): ResultadoEvento<ListadoAsistentesDto>

    /** Deshace la confirmación de pago de una asistencia. */
    suspend fun deshacerPago(
        eventoId: Long,
        asistenciaId: Long,
    ): ResultadoEvento<ListadoAsistentesDto>

    /** Un peñista (o admin, para sí mismo) declara que ha pagado. Devuelve el detalle recargado. */
    suspend fun declararPago(eventoId: Long, body: DeclararPagoBody): ResultadoEvento<EventoDetalle>

    /** Anula mi declaración de pago pendiente. Devuelve el detalle recargado. */
    suspend fun anularPagoDeclarado(eventoId: Long): ResultadoEvento<EventoDetalle>

    /** Cola de declaraciones de pago pendientes (solo admin/superadmin). */
    suspend fun pagosDeclaradosPendientes(): ResultadoEvento<List<PagoDeclaradoPendienteDto>>

    suspend fun confirmarPagoDeclarado(id: Long): ResultadoEvento<Unit>

    suspend fun rechazarPagoDeclarado(id: Long): ResultadoEvento<Unit>

    suspend fun solicitarCrear(mensaje: String?): ResultadoEvento<Unit>
}
