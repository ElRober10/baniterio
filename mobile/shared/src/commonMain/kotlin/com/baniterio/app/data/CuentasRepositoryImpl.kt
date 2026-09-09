package com.baniterio.app.data

import com.baniterio.app.data.dto.CuentaDetalleDto
import com.baniterio.app.data.dto.CuentaResumen
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.MarcarRopaInput
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class CuentasRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : CuentasRepository {

    /** Adjunta la cabecera Bearer con el token en memoria de [SesionHolder]. */
    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun listar(): ResultadoCuenta<List<CuentaResumen>> = peticion {
        http.get("$API_BASE_URL/cuentas") { auth() }.body()
    }

    override suspend fun detalle(id: Long, anio: Int?): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.get("$API_BASE_URL/cuentas/$id") {
            auth()
            if (anio != null) parameter("anio", anio)
        }.body()
    }

    override suspend fun marcarTransferido(id: Long): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.post("$API_BASE_URL/cuentas/$id/transferencia-a-pena") { auth() }.body()
    }

    override suspend fun cerrarAnio(id: Long): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.post("$API_BASE_URL/cuentas/$id/cerrar-anio") { auth() }.body()
    }

    override suspend fun borrarMovimiento(movId: Long): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.delete("$API_BASE_URL/cuentas/movimientos/$movId") { auth() }.body()
    }

    override suspend fun marcarRopa(
        cuentaId: Long,
        asistenciaId: Long,
        cambio: MarcarRopaInput,
    ): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.put("$API_BASE_URL/cuentas/$cuentaId/asistencias/$asistenciaId/ropa") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(cambio)
        }.body()
    }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoCuenta.Error.
     * `ResponseException` (4xx/5xx) → se lee `codigo` del cuerpo con [ErrorResponse];
     * cualquier otra excepción (red, timeout, serialización) → SIN_CONEXION.
     * `try` acotado, nunca `runCatching` (relanza `CancellationException`).
     */
    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoCuenta<T> =
        try {
            ResultadoCuenta.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorCuenta.deCodigoBackend(codigo)
            ResultadoCuenta.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoCuenta.Error(CodigoErrorCuenta.SIN_CONEXION, CodigoErrorCuenta.SIN_CONEXION.mensaje)
        }
}
