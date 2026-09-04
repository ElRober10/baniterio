package com.baniterio.app.data

import com.baniterio.app.data.dto.BebidaPendienteDto
import com.baniterio.app.data.dto.CatalogoBebidasDto

/**
 * Catálogo de bebidas de la ficha de San Miguel (pieza 3b): el listado para los
 * desplegables y —solo admin— aceptar/rechazar las propuestas con "Otra…".
 * Misma mecánica que [EventosRepository].
 */
interface BebidaRepository {
    suspend fun catalogo(): ResultadoBebida<CatalogoBebidasDto>
    suspend fun pendientes(): ResultadoBebida<List<BebidaPendienteDto>>
    suspend fun aceptar(id: Long): ResultadoBebida<Unit>
    suspend fun rechazar(id: Long): ResultadoBebida<Unit>
}
