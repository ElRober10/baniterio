package com.baniterio.app.data

import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.GuardarPerfilRequest
import com.baniterio.app.data.dto.PerfilResponse
import com.baniterio.app.data.dto.SubirFotoResponse
import com.baniterio.app.data.dto.TarjetaMiembroResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class PerfilRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : PerfilRepository {

    /** Adjunta la cabecera Bearer con el token en memoria de [SesionHolder]. */
    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun miPerfil(): ResultadoPerfil<PerfilResponse> = peticion {
        http.get("$API_BASE_URL/perfil") { auth() }.body()
    }

    override suspend fun guardar(req: GuardarPerfilRequest): ResultadoPerfil<PerfilResponse> = peticion {
        http.put("$API_BASE_URL/perfil") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun subirFoto(foto: FotoElegida): ResultadoPerfil<String> = peticion {
        http.submitFormWithBinaryData(
            url = "$API_BASE_URL/perfil/foto",
            formData = formData {
                append(
                    "archivo",
                    foto.bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, foto.tipoMime)
                        append(HttpHeaders.ContentDisposition, "filename=\"${foto.nombre}\"")
                    },
                )
            },
        ) { auth() }.body<SubirFotoResponse>().imagenRef
    }

    override suspend fun avatares(): ResultadoPerfil<List<AvatarResumen>> = peticion {
        http.get("$API_BASE_URL/perfil/avatares") { auth() }.body()
    }

    override suspend fun aceptarPareja(): ResultadoPerfil<Unit> = peticion {
        http.post("$API_BASE_URL/perfil/pareja/aceptar") { auth() }.bodyAsText().let { }
    }

    override suspend fun rechazarPareja(): ResultadoPerfil<Unit> = peticion {
        http.post("$API_BASE_URL/perfil/pareja/rechazar") { auth() }.bodyAsText().let { }
    }

    override suspend fun romperPareja(): ResultadoPerfil<Unit> = peticion {
        http.delete("$API_BASE_URL/perfil/pareja") { auth() }.bodyAsText().let { }
    }

    override suspend fun miembros(): ResultadoPerfil<List<TarjetaMiembroResponse>> = peticion {
        http.get("$API_BASE_URL/miembros") { auth() }.body()
    }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoPerfil.Error.
     * `ResponseException` (4xx/5xx) → se lee `codigo` del cuerpo; cualquier otra
     * excepción (red, timeout, serialización) → SIN_CONEXION. `try` acotado,
     * nunca `runCatching` (que capturaría también `CancellationException`).
     */
    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoPerfil<T> =
        try {
            ResultadoPerfil.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorPerfil.deCodigoBackend(codigo)
            ResultadoPerfil.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoPerfil.Error(CodigoErrorPerfil.SIN_CONEXION, CodigoErrorPerfil.SIN_CONEXION.mensaje)
        }
}
