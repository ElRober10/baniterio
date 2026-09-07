package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Ficha de una cuenta (`GET /cuentas`). El detalle usa [CuentaDetalleDto]. */
@Serializable
data class CuentaResumen(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
)

/** Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla. */
@Serializable
data class MovimientoFilaDto(
    val id: Long = 0,
    val concepto: String,
    val categoria: String? = null,
    val importe: Double,
    val fecha: String,
    val saldoTras: Double,
    val reciboArchivo: String? = null,
    val manual: Boolean = false,
    val adelantadoPor: String? = null,
)

/** Una fila de la tabla "Peñistas" del detalle de una cuenta. */
@Serializable
data class PenistaCuotaDto(
    val asistenciaId: Long,
    val nombre: String,
    val cuota: Double,
    val estadoPago: String? = null,
    val metodoPago: String? = null,
    val camisetaPagada: Boolean = false,
    val sudaderaPagada: Boolean = false,
)

@Serializable
data class ResumenGastoDto(val categoria: String, val total: Double)

/**
 * Detalle de una cuenta (`GET /cuentas/{id}`): la hoja completa. `cobradoSinIngresar`
 * es `null` si quien mira no es administrador.
 */
@Serializable
data class CuentaDetalleDto(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val saldo: Double = 0.0,
    val estimacion: Double = 0.0,
    val cobradoSinIngresar: Double? = null,
    val puedoGestionar: Boolean = false,
    val precioCamiseta: Double? = null,
    val precioSudadera: Double? = null,
    val penistas: List<PenistaCuotaDto> = emptyList(),
    val totalCuotas: Double = 0.0,
    val totalCobrado: Double = 0.0,
    val movimientos: List<MovimientoFilaDto> = emptyList(),
    val resumenGastos: List<ResumenGastoDto> = emptyList(),
)
