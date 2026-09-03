package com.baniterio.app.data

import com.baniterio.app.data.dto.CuentaResumen

/**
 * Endpoints de la sección Cuentas (`/api/v1/cuentas*`). Misma mecánica que
 * [EventosRepository]: Bearer de [SesionHolder], nunca lanza por errores HTTP
 * esperados, relanza `CancellationException`.
 */
interface CuentasRepository {
    suspend fun listar(): ResultadoCuenta<List<CuentaResumen>>
    suspend fun detalle(id: Long): ResultadoCuenta<CuentaResumen>
}
