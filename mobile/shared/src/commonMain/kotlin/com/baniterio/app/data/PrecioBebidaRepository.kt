package com.baniterio.app.data

import com.baniterio.app.data.dto.EventoPrecioBebidaDto
import com.baniterio.app.data.dto.GrillaAlcoholDto
import com.baniterio.app.data.dto.GrillaArticuloDto
import com.baniterio.app.data.dto.TiendaDto

/**
 * Llamadas de "Precio compras" (`/api/v1/precio-bebida*`). Lo ve cualquier
 * peñista; solo un admin de verdad edita (guardarPrecio/crearTienda/anadirTamano).
 */
interface PrecioBebidaRepository {
    suspend fun eventos(): ResultadoPrecioBebida<List<EventoPrecioBebidaDto>>
    suspend fun tiendas(): ResultadoPrecioBebida<List<TiendaDto>>
    suspend fun crearTienda(nombre: String): ResultadoPrecioBebida<TiendaDto>
    suspend fun alcohol(eventoId: Long): ResultadoPrecioBebida<GrillaAlcoholDto>
    suspend fun guardarPrecio(
        eventoId: Long,
        bebidaId: Long,
        tamano: String,
        tiendaId: Long,
        precio: Double?,
    ): ResultadoPrecioBebida<Unit>
    suspend fun anadirTamano(eventoId: Long, tamano: String): ResultadoPrecioBebida<List<String>>

    /** Rejilla de una sección sin tamaños: `categoria` ∈ REFRESCOS/CERVEZA/LIMPIEZA/COMIDA. */
    suspend fun articulos(eventoId: Long, categoria: String): ResultadoPrecioBebida<GrillaArticuloDto>
    suspend fun guardarPrecioArticulo(
        eventoId: Long,
        categoria: String,
        nombreArticulo: String,
        tiendaId: Long,
        precio: Double?,
        cantidad: Int? = null,
    ): ResultadoPrecioBebida<Unit>
    suspend fun guardarTamanoArticulo(
        eventoId: Long,
        categoria: String,
        nombreArticulo: String,
        litros: Double,
    ): ResultadoPrecioBebida<Unit>
    suspend fun guardarProductoKilo(
        eventoId: Long,
        nombreArticulo: String,
        precioKilo: Double,
        pesoKg: Double,
    ): ResultadoPrecioBebida<Unit>
}
