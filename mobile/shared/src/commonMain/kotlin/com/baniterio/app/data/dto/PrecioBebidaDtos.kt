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
