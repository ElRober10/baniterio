package com.baniterio.app.data

import com.baniterio.app.data.dto.ArticuloInventarioDto
import com.baniterio.app.data.dto.EventoAbiertoDto
import com.baniterio.app.data.dto.InventarioFiestaResponse
import com.baniterio.app.data.dto.InventarioResponse

/**
 * Sección Inventario: consultar lo almacenado (cualquier peñista) y —con el área
 * `INVENTARIO`— ajustar o dar de alta un artículo. Misma mecánica que
 * [BebidaRepository]: nunca lanza por un 4xx esperado, devuelve [ResultadoInventario].
 */
interface InventarioRepository {
    suspend fun ver(): ResultadoInventario<InventarioResponse>
    suspend fun actualizar(
        id: Long,
        nombre: String,
        tamano: String,
        cantidad: Double,
    ): ResultadoInventario<ArticuloInventarioDto>
    suspend fun crear(
        categoria: String,
        nombre: String,
        tamano: String,
        cantidad: Double,
    ): ResultadoInventario<ArticuloInventarioDto>
    suspend fun borrar(id: Long): ResultadoInventario<Unit>

    // --- Inventario de la fiesta ---
    suspend fun eventosAbiertos(): ResultadoInventario<List<EventoAbiertoDto>>
    suspend fun enviarAEvento(articuloId: Long, eventoId: Long): ResultadoInventario<Unit>
    suspend fun enviarCategoria(categoria: String, eventoId: Long): ResultadoInventario<Unit>
    suspend fun inventarioFiesta(eventoId: Long): ResultadoInventario<InventarioFiestaResponse>
    suspend fun devolver(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit>
}
