package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Ficha de una cuenta (`GET /cuentas`). El detalle usa [CuentaDetalleDto]. */
@Serializable
data class CuentaResumen(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
)

/**
 * Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla.
 * `importe` positivo = entra, negativo = sale. `manual` = lo apuntó un admin
 * (`GASTO`/`INGRESO`), se puede borrar. `origen` = el tipo de movimiento
 * (`SALDO_INICIAL`, `CUOTA`, `CAMISETA`, `SUDADERA`, `GASTO`, `INGRESO`…).
 */
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
    val origen: String = "",
    val adelantadoPor: String? = null,
)

/**
 * Una fila de la tabla "Peñistas" del detalle de una cuenta. `ingreso` y
 * `saldoTras` van a `null` mientras la cuota no esté cobrada; cuando lo está,
 * son el importe que entró y el saldo de la cuenta justo después.
 */
@Serializable
data class PenistaCuotaDto(
    val asistenciaId: Long,
    val nombre: String,
    val anio: Int = 0,
    val cuota: Double,
    val estadoPago: String? = null,
    val metodoPago: String? = null,
    val camisetaCantidad: Int = 0,
    val camisetaTalla: String? = null,
    val camisetaConfirmada: Boolean = false,
    val sudaderaCantidad: Int = 0,
    val sudaderaTalla: String? = null,
    val sudaderaConfirmada: Boolean = false,
    val ingreso: Double? = null,
    val saldoTras: Double? = null,
)

@Serializable
data class ResumenGastoDto(val categoria: String, val total: Double)

/**
 * Detalle de una cuenta (`GET /cuentas/{id}`): la hoja completa de un año
 * contable. `cobradoSinIngresar` es `null` si quien mira no es administrador.
 * `anio` es el año que muestra la hoja; `anios` son todos los que tienen datos
 * (de más nuevo a más viejo); `esAnioActual` = el único que se puede editar o
 * cerrar.
 */
@Serializable
data class CuentaDetalleDto(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val anio: Int = 0,
    val anios: List<Int> = emptyList(),
    val esAnioActual: Boolean = false,
    val saldo: Double = 0.0,
    val saldoInicial: Double = 0.0,
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

/**
 * Cambio de la ropa de un peñista (`PUT …/asistencias/{id}/ropa`). Cada campo
 * `null` = "no tocar": el backend solo aplica los que no son nulos, así que da
 * igual que el JSON los lleve como `null` o los omita.
 */
@Serializable
data class MarcarRopaInput(
    val camisetaCantidad: Int? = null,
    val camisetaTalla: String? = null,
    val camisetaConfirmada: Boolean? = null,
    val sudaderaCantidad: Int? = null,
    val sudaderaTalla: String? = null,
    val sudaderaConfirmada: Boolean? = null,
)
