package com.baniterio.app.data

import com.baniterio.app.data.dto.CrearReglaBody
import com.baniterio.app.data.dto.EventoListaCompraDto
import com.baniterio.app.data.dto.ListaCompraAdminResponse
import com.baniterio.app.data.dto.ListaCompraResponse
import com.baniterio.app.data.dto.ReglaCompraEventoDto

/** Llamadas de la lista de la compra por evento (lectura y editor de administración). */
interface ListaCompraRepository {
    suspend fun lista(eventoId: Long): ResultadoListaCompra<ListaCompraResponse>
    suspend fun adminEventos(): ResultadoListaCompra<List<EventoListaCompraDto>>
    suspend fun adminEvento(eventoId: Long): ResultadoListaCompra<ListaCompraAdminResponse>
    suspend fun ajustarRegla(
        eventoId: Long,
        reglaId: Long,
        cantidadAjustada: Double?,
        activa: Boolean,
    ): ResultadoListaCompra<Unit>
    suspend fun crearRegla(eventoId: Long, body: CrearReglaBody): ResultadoListaCompra<ReglaCompraEventoDto>
    suspend fun borrarRegla(eventoId: Long, reglaId: Long): ResultadoListaCompra<Unit>
    suspend fun marcarComprada(eventoId: Long, lineaId: Long): ResultadoListaCompra<Unit>
    suspend fun cambiarBloqueo(eventoId: Long, bloqueada: Boolean): ResultadoListaCompra<Unit>
    suspend fun ajustarLinea(
        eventoId: Long,
        lineaId: Long,
        cantidad: Double,
        tamano: String? = null,
        tienda: String? = null,
    ): ResultadoListaCompra<Unit>
    /** Quita la modificación a mano de la marca o artículo de una línea y la recalcula. */
    suspend fun restablecerLinea(eventoId: Long, lineaId: Long): ResultadoListaCompra<Unit>
    /** Quita todas las modificaciones a mano de la lista. */
    suspend fun restablecerTodo(eventoId: Long): ResultadoListaCompra<Unit>
}
