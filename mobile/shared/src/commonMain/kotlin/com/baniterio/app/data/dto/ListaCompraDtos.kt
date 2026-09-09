package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Una línea de la lista de la compra de un evento. */
@Serializable
data class LineaCompraDto(
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
    val cantidadCalculada: Double = 0.0,
    val ajustada: Boolean = false,
    val dinamica: Boolean = false,
    val necesitaFicha: Boolean = false,
)

@Serializable
data class CategoriaListaCompraDto(
    val categoria: String,
    val etiqueta: String,
    val lineas: List<LineaCompraDto> = emptyList(),
)

/** Raíz de `GET /api/v1/eventos/{id}/lista-compra`. */
@Serializable
data class ListaCompraResponse(
    val puedoEditar: Boolean = false,
    val llevaFicha: Boolean = false,
    val apuntados: Int = 0,
    val diasFiesta: Int = 0,
    val categorias: List<CategoriaListaCompraDto> = emptyList(),
)

/** Ficha corta de un evento para "Cantidades para eventos". */
@Serializable
data class EventoListaCompraDto(
    val id: Long,
    val nombre: String,
    val fecha: String,
    val fechaFin: String? = null,
)

/** Una regla de la lista de la compra de un evento, en el editor de admin. */
@Serializable
data class ReglaCompraEventoDto(
    val id: Long,
    val categoria: String,
    val etiqueta: String,
    val nombre: String,
    val tamano: String,
    val tipoFormula: String,
    val factor: Double,
    val porCada: Int? = null,
    val origen: String,
    val cantidadCalculada: Double,
    val cantidadAjustada: Double? = null,
    val cantidadFinal: Double,
    val activa: Boolean,
)

/** Raíz de `GET /api/v1/admin/lista-compra/eventos/{id}`. */
@Serializable
data class ListaCompraAdminResponse(
    val evento: EventoListaCompraDto,
    val apuntados: Int = 0,
    val diasFiesta: Int = 0,
    val reglas: List<ReglaCompraEventoDto> = emptyList(),
)

/** Cuerpo de `PUT .../reglas/{id}`. */
@Serializable
data class AjustarReglaBody(val cantidadAjustada: Double?, val activa: Boolean)

/** Cuerpo de `POST .../reglas`. */
@Serializable
data class CrearReglaBody(
    val categoria: String,
    val nombre: String,
    val tamano: String,
    val tipoFormula: String,
    val factor: Double,
    val porCada: Int? = null,
)
