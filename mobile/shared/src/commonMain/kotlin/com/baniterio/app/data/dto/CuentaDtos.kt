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

/** Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla. */
@Serializable
data class MovimientoFilaDto(
    val concepto: String,
    val importe: Double,
    val fecha: String,
    val saldoTras: Double,
)

/**
 * Detalle de una cuenta (`GET /cuentas/{id}`): saldo, estimación y libro de
 * movimientos. `porIngresar` es `null` si quien mira no es administrador.
 */
@Serializable
data class CuentaDetalleDto(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val saldo: Double = 0.0,
    val estimacion: Double = 0.0,
    val movimientos: List<MovimientoFilaDto> = emptyList(),
    val puedoGestionar: Boolean = false,
    val porIngresar: Double? = null,
)
