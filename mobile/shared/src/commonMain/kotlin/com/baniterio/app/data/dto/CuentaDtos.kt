package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/**
 * Ficha de una cuenta (`GET /cuentas` y `GET /cuentas/{id}`). De momento la
 * sección solo lista y ve el detalle; los movimientos llegan después.
 */
@Serializable
data class CuentaResumen(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
)
