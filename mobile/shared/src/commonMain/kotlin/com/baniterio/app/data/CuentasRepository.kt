package com.baniterio.app.data

import com.baniterio.app.data.dto.CuentaDetalleDto
import com.baniterio.app.data.dto.CuentaResumen
import com.baniterio.app.data.dto.MarcarRopaInput

/**
 * Endpoints de la sección Cuentas (`/api/v1/cuentas*`). Misma mecánica que
 * [EventosRepository]: Bearer de [SesionHolder], nunca lanza por errores HTTP
 * esperados, relanza `CancellationException`.
 */
interface CuentasRepository {
    suspend fun listar(): ResultadoCuenta<List<CuentaResumen>>

    /** La hoja de la cuenta. `anio` `null` = año en curso; con valor = ese año pasado (solo lectura). */
    suspend fun detalle(id: Long, anio: Int? = null): ResultadoCuenta<CuentaDetalleDto>

    /** El admin marca que ha ingresado en la cuenta de la peña lo cobrado por bizum/efectivo. */
    suspend fun marcarTransferido(id: Long): ResultadoCuenta<CuentaDetalleDto>

    /** El admin cierra el año en curso: el saldo pasa como saldo de partida al año siguiente. */
    suspend fun cerrarAnio(id: Long): ResultadoCuenta<CuentaDetalleDto>

    /** El admin borra un gasto/ingreso manual. */
    suspend fun borrarMovimiento(movId: Long): ResultadoCuenta<CuentaDetalleDto>

    /** El admin apunta la ropa de un peñista (cantidad, talla, € confirmado). */
    suspend fun marcarRopa(
        cuentaId: Long,
        asistenciaId: Long,
        cambio: MarcarRopaInput,
    ): ResultadoCuenta<CuentaDetalleDto>
}
