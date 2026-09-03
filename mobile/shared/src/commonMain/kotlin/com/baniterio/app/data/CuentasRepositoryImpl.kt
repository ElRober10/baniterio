package com.baniterio.app.data

import com.baniterio.app.data.dto.CuentaResumen
import com.baniterio.app.data.dto.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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

    override suspend fun detalle(id: Long): ResultadoCuenta<CuentaResumen> = peticion {
        http.get("$API_BASE_URL/cuentas/$id") { auth() }.body()
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
