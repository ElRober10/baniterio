package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** La opción más barata de un tamaño de botella según la rejilla de precios. Solo alcohol. */
@Serializable
data class OpcionTamanoDto(
    val tamano: String,
    val tienda: String,
    val precio: Double,
    val precioLitro: Double,
)

/**
 * Lo que cuesta una línea en una tienda según la rejilla de precios: el precio de la
 * botella/pack (con `unidades` > 1 cuando es pack) y, en bebidas, lo que sale el litro.
 */
@Serializable
data class OpcionTiendaDto(
    val tienda: String,
    val precio: Double,
    val unidades: Int = 1,
    val precioLitro: Double? = null,
)

/**
 * Contexto de una línea de bebida para ajustar la cantidad a mano: `personas` son las
 * personas que la beben (dos días = 1, un día = 0,5) y `stock` las botellas de esa marca
 * que ya hay en el inventario de la fiesta. `tamanos` son los tamaños con precio en la
 * rejilla (solo alcohol; `null` en refrescos, que no tienen botón de tamaño).
 */
@Serializable
data class InfoBebidaDto(
    val personas: Double = 0.0,
    val stock: Double = 0.0,
    val tamanos: List<OpcionTamanoDto>? = null,
)

/** Una línea de la lista de la compra de un evento. */
@Serializable
data class LineaCompraDto(
    val id: Long = 0,
    val nombre: String,
    val tamano: String,
    val tienda: String? = null,
    val precioUnitario: Double? = null,
    val cantidad: Double,
    val cantidadCalculada: Double = 0.0,
    val ajustada: Boolean = false,
    val dinamica: Boolean = false,
    val necesitaFicha: Boolean = false,
    val comprada: Boolean = false,
    /** Nota de la compra en packs, p. ej. "2 × pack de 50". */
    val detalle: String? = null,
    /** Contexto de bebida (quién la bebe y stock); solo en alcohol y refrescos dinámicos. */
    val info: InfoBebidaDto? = null,
    /** Tiendas con precio para la línea, de más barata a más cara (para cambiar dónde se compra). */
    val tiendas: List<OpcionTiendaDto>? = null,
    /** El admin la cambió a mano: se respeta aunque se desbloquee la lista, hasta restablecerla. */
    val modificada: Boolean = false,
)

@Serializable
data class CategoriaListaCompraDto(
    /** Una categoría, o "PARA_ALTERNAR" (cerveza, cervezas especiales y tinto de verano). */
    val categoria: String,
    val etiqueta: String,
    val lineas: List<LineaCompraDto> = emptyList(),
)

/** Raíz de `GET /api/v1/eventos/{id}/lista-compra`. */
@Serializable
data class ListaCompraResponse(
    val puedoEditar: Boolean = false,
    val llevaFicha: Boolean = false,
    val bloqueada: Boolean = false,
    val apuntados: Int = 0,
    val diasFiesta: Int = 0,
    val categorias: List<CategoriaListaCompraDto> = emptyList(),
    /** Saldo de la cuenta del evento en su año en curso (lo que hay para gastar). */
    val presupuesto: Double? = null,
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
    val bloqueada: Boolean = false,
    val apuntados: Int = 0,
    val diasFiesta: Int = 0,
    val reglas: List<ReglaCompraEventoDto> = emptyList(),
)

/** Cuerpo de `PUT .../reglas/{id}`. */
@Serializable
data class AjustarReglaBody(val cantidadAjustada: Double?, val activa: Boolean)

/** Cuerpo de `PUT /api/v1/eventos/{id}/lista-compra/bloqueo`. */
@Serializable
data class CambiarBloqueoBody(val bloqueada: Boolean)

/**
 * Cuerpo de `PUT /api/v1/eventos/{id}/lista-compra/lineas/{lineaId}`. `tamano` (opcional,
 * solo bebidas alcohólicas) cambia el tamaño de la botella de la línea y `tienda`
 * (opcional) cambia la tienda donde se compra.
 */
@Serializable
data class AjustarLineaBody(
    val cantidad: Double,
    val tamano: String? = null,
    val tienda: String? = null,
)

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
