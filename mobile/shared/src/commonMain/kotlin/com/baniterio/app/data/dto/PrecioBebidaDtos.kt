package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Ficha corta de un evento para "Precio compras" (`GET /precio-bebida/eventos`). */
@Serializable
data class EventoPrecioBebidaDto(
    val id: Long,
    val nombre: String,
    val fecha: String,
    val fechaFin: String? = null,
)

@Serializable
data class TiendaDto(val id: Long, val nombre: String)

/** Una celda de la rejilla: precio de una marca, en un tamaño, en una tienda. */
@Serializable
data class PrecioCeldaDto(
    val bebidaId: Long,
    val tamano: String,
    val tiendaId: Long,
    val precio: Double? = null,
)

/** Raíz de `GET /precio-bebida/eventos/{id}/alcohol`. */
@Serializable
data class GrillaAlcoholDto(
    val puedoEditar: Boolean = false,
    val tiendas: List<TiendaDto> = emptyList(),
    val tamanos: List<String> = emptyList(),
    val bebidas: List<BebidaRefDto> = emptyList(),
    val precios: List<PrecioCeldaDto> = emptyList(),
)

/** Cuerpo de `POST /precio-bebida/tiendas`. */
@Serializable
data class CrearTiendaBody(val nombre: String)

/** Cuerpo de `PUT /precio-bebida/eventos/{id}/alcohol/precio`. `precio` null borra la celda. */
@Serializable
data class GuardarPrecioBody(
    val bebidaId: Long,
    val tamano: String,
    val tiendaId: Long,
    val precio: Double?,
)

/** Cuerpo de `POST /precio-bebida/eventos/{id}/alcohol/tamanos`. */
@Serializable
data class AnadirTamanoBody(val tamano: String)

/** El precio de un artículo (de un pack de `cantidad` unidades), en una tienda. */
@Serializable
data class PrecioArticuloCeldaDto(
    val nombreArticulo: String,
    val tiendaId: Long,
    val precio: Double? = null,
    val cantidad: Int = 1,
)

/** Tamaño de botella (litros) apuntado para un artículo de refrescos/cerveza. */
@Serializable
data class TamanoArticuloDto(val nombreArticulo: String, val litros: Double)

/** Precio por kilo y peso estimado de un embutido de Jamones Duriber (comida). */
@Serializable
data class ProductoKiloDto(
    val nombreArticulo: String,
    val precioKilo: Double? = null,
    val pesoKg: Double? = null,
)

/** Raíz de `GET /precio-bebida/eventos/{id}/articulos/{categoria}`. */
@Serializable
data class GrillaArticuloDto(
    val puedoEditar: Boolean = false,
    val tiendas: List<TiendaDto> = emptyList(),
    val articulos: List<String> = emptyList(),
    val precios: List<PrecioArticuloCeldaDto> = emptyList(),
    val tamanos: List<TamanoArticuloDto> = emptyList(),
    val porKilo: List<ProductoKiloDto> = emptyList(),
)

/** Cuerpo de `PUT /precio-bebida/eventos/{id}/articulos/{categoria}/precio`. `precio` null borra la celda. */
@Serializable
data class GuardarPrecioArticuloBody(
    val nombreArticulo: String,
    val tiendaId: Long,
    val precio: Double?,
    val cantidad: Int? = null,
)

/** Cuerpo de `PUT /precio-bebida/eventos/{id}/articulos/{categoria}/tamano`. */
@Serializable
data class GuardarTamanoArticuloBody(val nombreArticulo: String, val litros: Double)

/** Cuerpo de `PUT /precio-bebida/eventos/{id}/articulos/COMIDA/kilo`. */
@Serializable
data class GuardarProductoKiloBody(val nombreArticulo: String, val precioKilo: Double, val pesoKg: Double)
