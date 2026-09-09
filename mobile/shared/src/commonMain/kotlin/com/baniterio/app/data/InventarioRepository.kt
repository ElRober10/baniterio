package com.baniterio.app.data

import com.baniterio.app.data.dto.ArticuloInventarioDto
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
}
