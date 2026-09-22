package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException

/** Sin `auth()`: `/media/...` es público (ver `SecurityConfig`), no hace falta el token. */
class MediaRepositoryImpl(private val http: HttpClient) : MediaRepository {

    override suspend fun descargar(url: String): ResultadoMedia<ByteArray> =
        try {
            ResultadoMedia.Exito(http.get(url).body())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            ResultadoMedia.Error("No se pudo cargar el archivo.")
        } catch (e: Exception) {
            ResultadoMedia.Error("No se pudo conectar con el servidor.")
        }
}
