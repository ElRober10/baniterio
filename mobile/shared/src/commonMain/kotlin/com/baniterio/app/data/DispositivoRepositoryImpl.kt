package com.baniterio.app.data

import com.baniterio.app.data.dto.RegistrarDispositivoRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class DispositivoRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : DispositivoRepository {

    /**
     * Adjunta la cabecera Bearer. Usa [bearer] si se pasa (JWT capturado antes
     * de `logout()`); si no, el token en memoria de [SesionHolder].
     */
    private fun HttpRequestBuilder.auth(bearer: String? = null) {
        (bearer ?: sesion.token)?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun registrar(token: String, plataforma: String): Boolean = intentar {
        http.post("$API_BASE_URL/dispositivos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(RegistrarDispositivoRequest(token, plataforma))
        }
    }

    override suspend fun eliminar(token: String, bearer: String?): Boolean = intentar {
        http.delete("$API_BASE_URL/dispositivos/$token") { auth(bearer) }
    }

    /**
     * `true` si [bloque] no lanza; `false` ante cualquier error (4xx/5xx vía
     * [ResponseException], red, timeout, serialización). Relanza la
     * [CancellationException] para no romper la concurrencia estructurada;
     * `try` acotado, nunca `runCatching` (que capturaría también la cancelación).
     */
    private suspend fun intentar(bloque: suspend () -> Unit): Boolean =
        try {
            bloque()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            false
        } catch (e: Exception) {
            false
        }
}
