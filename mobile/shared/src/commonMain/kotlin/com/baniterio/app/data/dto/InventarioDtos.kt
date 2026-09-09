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

/** Un evento "abierto" (no pasado, no oculto): opción del selector de "enviar a evento". */
@Serializable
data class EventoAbiertoDto(val id: Long, val nombre: String, val fecha: String)

/** Cuerpo de `POST /api/v1/inventario/{id}/enviar`. */
@Serializable
data class EnviarAEventoBody(val eventoId: Long)

/** Cuerpo de `POST /api/v1/inventario/enviar-categoria`. */
@Serializable
data class EnviarCategoriaBody(val categoria: String, val eventoId: Long)

/** Un artículo del inventario de un evento ("inventario de la fiesta"). */
@Serializable
data class ArticuloFiestaDto(val id: Long, val nombre: String, val tamano: String, val cantidad: Double)

@Serializable
data class CategoriaFiestaDto(
    val categoria: String,
    val etiqueta: String,
    val articulos: List<ArticuloFiestaDto> = emptyList(),
)

/** Raíz de `GET /api/v1/inventario/evento/{id}`. */
@Serializable
data class InventarioFiestaResponse(
    val puedoEditar: Boolean = false,
    val categorias: List<CategoriaFiestaDto> = emptyList(),
)
