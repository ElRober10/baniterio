package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

expect fun crearHttpClient(): HttpClient

/** Cuerpo de `POST /api/v1/logs/cliente` (ver back `LogClienteRequest`). */
@Serializable
private data class LogClienteBody(val origen: String, val pantalla: String, val mensaje: String)

private val scopeReporte = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Cliente propio para el reporte. Su `configComun` no puede reportarse a sí mismo: ver la guarda por ruta. */
private val httpReporte: HttpClient by lazy { crearHttpClient() }

/**
 * Manda el error a `POST /logs/cliente` en una corrutina aparte, sin esperar su
 * resultado: si el propio reporte falla, se ignora. Es el valor por defecto de
 * [configComun]; los tests lo sustituyen por un espía.
 */
fun reportarErrorCliente(pantalla: String, mensaje: String) {
    scopeReporte.launch {
        try {
            httpReporte.post("$API_BASE_URL/logs/cliente") {
                contentType(ContentType.Application.Json)
                setBody(LogClienteBody("MOBILE", pantalla, mensaje))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Si el registro del error también falla, no hay nada más que hacer.
        }
    }
}

/**
 * Config común de Ktor: JSON tolerante, timeouts, y expectSuccess para que un
 * 4xx/5xx lance ResponseException (lo aprovecha AuthRepositoryImpl). Además
 * reporta a `/logs/cliente` las peticiones que NUNCA llegan a tener respuesta
 * (sin red, timeout...). Un [ResponseException] no cuenta —eso ya lo registra
 * el backend— ni la propia petición de log, para no entrar en bucle.
 */
fun HttpClientConfig<*>.configComun(
    reportarError: (pantalla: String, mensaje: String) -> Unit = ::reportarErrorCliente,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
    }
    expectSuccess = true
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, request ->
            if (exception !is ResponseException &&
                exception !is CancellationException &&
                !request.url.encodedPath.endsWith("/logs/cliente")
            ) {
                reportarError(request.url.encodedPath, exception::class.simpleName ?: "Error")
            }
        }
    }
}
