package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Un artículo del inventario tal cual viaja en el JSON. */
@Serializable
data class ArticuloInventarioDto(
    val id: Long,
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
)

/** Una categoría del inventario con sus tamaños permitidos y sus artículos. */
@Serializable
data class CategoriaInventarioDto(
    val categoria: String,
    val etiqueta: String,
    val tamanos: List<String> = emptyList(),
    val articulos: List<ArticuloInventarioDto> = emptyList(),
)

/** Raíz de `GET /api/v1/inventario`. `puedoEditar` dice si quien pregunta puede tocar. */
@Serializable
data class InventarioResponse(
    val puedoEditar: Boolean = false,
    val categorias: List<CategoriaInventarioDto> = emptyList(),
)

/** Cuerpo de `PUT /api/v1/inventario/{id}`. La categoría no se puede cambiar. */
@Serializable
data class ActualizarArticuloRequest(
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
)

/** Cuerpo de `POST /api/v1/inventario`: alta de un artículo en una categoría. */
@Serializable
data class CrearArticuloRequest(
    val categoria: String,
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
)
