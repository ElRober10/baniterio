package com.baniterio.app.data

import com.baniterio.app.data.dto.AnadirAsistenteBody
import com.baniterio.app.data.dto.AsistenciaResumenDto
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.data.dto.FichaBebidaBody
import com.baniterio.app.data.dto.FichaBebidaResponseDto
import com.baniterio.app.data.dto.MandarNotificacionBody
import com.baniterio.app.data.dto.PendientesRespuestaDto
import com.baniterio.app.data.dto.ResponderAsistenciaBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class AsistenciaRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : AsistenciaRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun responder(eventoId: Long, estado: String): ResultadoAsistencia<EventoDetalle> = peticion {
        http.put("$API_BASE_URL/eventos/$eventoId/asistencia") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ResponderAsistenciaBody(estado))
        }.body()
    }

    override suspend fun mandarNotificacion(eventoId: Long, texto: String?): ResultadoAsistencia<Unit> = peticion {
        http.post("$API_BASE_URL/eventos/$eventoId/notificacion") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(MandarNotificacionBody(texto?.takeIf { it.isNotBlank() }))
        }.bodyAsText().let { }
    }

    override suspend fun anadir(
        eventoId: Long,
        nombre: String,
        estado: String,
        ficha: FichaBebidaBody?,
    ): ResultadoAsistencia<AsistenciaResumenDto> = peticion {
        http.post("$API_BASE_URL/eventos/$eventoId/asistencias") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AnadirAsistenteBody(nombre, estado, ficha))
        }.body()
    }

    override suspend fun quitar(eventoId: Long, asistenciaId: Long): ResultadoAsistencia<Unit> = peticion {
        http.delete("$API_BASE_URL/eventos/$eventoId/asistencias/$asistenciaId") { auth() }
            .bodyAsText().let { }
    }

    override suspend fun guardarFicha(
        eventoId: Long,
        body: FichaBebidaBody,
    ): ResultadoAsistencia<FichaBebidaResponseDto> = peticion {
        http.put("$API_BASE_URL/eventos/$eventoId/ficha-bebida") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    override suspend fun pendientes(): ResultadoAsistencia<List<EventoResumen>> = peticion {
        http.get("$API_BASE_URL/eventos/pendientes-respuesta") { auth() }
            .body<PendientesRespuestaDto>().eventos
    }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoAsistencia.Error.
     * Mismo patrón que [EventosRepositoryImpl.peticion] (nunca `runCatching`; relanza
     * `CancellationException`).
     */
    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoAsistencia<T> =
        try {
            ResultadoAsistencia.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorAsistencia.deCodigoBackend(codigo)
            ResultadoAsistencia.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAsistencia.Error(
                CodigoErrorAsistencia.SIN_CONEXION,
                CodigoErrorAsistencia.SIN_CONEXION.mensaje,
            )
        }
}
