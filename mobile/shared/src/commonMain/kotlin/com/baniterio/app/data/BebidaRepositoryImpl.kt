package com.baniterio.app.data

import com.baniterio.app.data.dto.BebidaPendienteDto
import com.baniterio.app.data.dto.CatalogoBebidasDto
import com.baniterio.app.data.dto.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

class BebidaRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : BebidaRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun catalogo(): ResultadoBebida<CatalogoBebidasDto> = peticion {
        http.get("$API_BASE_URL/bebidas/catalogo") { auth() }.body()
    }

    override suspend fun pendientes(): ResultadoBebida<List<BebidaPendienteDto>> = peticion {
        http.get("$API_BASE_URL/bebidas") {
            auth()
            parameter("estado", "PENDIENTE")
        }.body()
    }

    override suspend fun aceptar(id: Long): ResultadoBebida<Unit> = peticion {
        http.post("$API_BASE_URL/bebidas/$id/aceptar") { auth() }.bodyAsText().let { }
    }

    override suspend fun rechazar(id: Long): ResultadoBebida<Unit> = peticion {
        http.post("$API_BASE_URL/bebidas/$id/rechazar") { auth() }.bodyAsText().let { }
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoBebida<T> =
        try {
            ResultadoBebida.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorBebida.deCodigoBackend(codigo)
            ResultadoBebida.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoBebida.Error(CodigoErrorBebida.SIN_CONEXION, CodigoErrorBebida.SIN_CONEXION.mensaje)
        }
}
