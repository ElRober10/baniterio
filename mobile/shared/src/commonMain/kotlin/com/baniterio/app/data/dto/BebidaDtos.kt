package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Una opción de un desplegable de la ficha de bebida. */
@Serializable
data class BebidaRefDto(val id: Long, val nombre: String)

/** `GET /api/v1/bebidas/catalogo`. */
@Serializable
data class CatalogoBebidasDto(
    val alcohol: List<BebidaRefDto> = emptyList(),
    val refresco: List<BebidaRefDto> = emptyList(),
)

/** Una bebida propuesta con "Otra…" a la espera de aprobación. */
@Serializable
data class BebidaPendienteDto(
    val id: Long,
    val tipo: String,
    val nombre: String,
    val propuestaPor: ProponenteBebidaDto? = null,
    val createdAt: String,
)

@Serializable
data class ProponenteBebidaDto(val id: Long, val nombre: String)
