package com.baniterio.app.data

import com.baniterio.app.data.dto.CuentaDetalleDto
import com.baniterio.app.data.dto.CuentaResumen

/**
 * Endpoints de la sección Cuentas (`/api/v1/cuentas*`). Misma mecánica que
 * [EventosRepository]: Bearer de [SesionHolder], nunca lanza por errores HTTP
 * esperados, relanza `CancellationException`.
 */
interface CuentasRepository {
    suspend fun listar(): ResultadoCuenta<List<CuentaResumen>>
    suspend fun detalle(id: Long): ResultadoCuenta<CuentaDetalleDto>

    /** El admin marca que ha ingresado en la cuenta de la peña lo cobrado por bizum/efectivo. */
    suspend fun marcarTransferido(id: Long): ResultadoCuenta<CuentaDetalleDto>
}
