package com.baniterio.app.data

import com.baniterio.app.data.dto.ConfirmarPagoBody
import com.baniterio.app.data.dto.DeclararPagoBody
import com.baniterio.app.data.dto.PagoDeclaradoPendienteDto
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.data.dto.EventosOcultosResponse
import com.baniterio.app.data.dto.GuardarEventoRequest
import com.baniterio.app.data.dto.ListadoAsistentesDto
import com.baniterio.app.data.dto.ListaEventosResponse
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class EventosRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : EventosRepository {

    /** Adjunta la cabecera Bearer con el token en memoria de [SesionHolder]. */
    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun listar(pagina: Int): ResultadoEvento<ListaEventosResponse> = peticion {
        http.get("$API_BASE_URL/eventos") {
            auth()
            parameter("pagina", pagina)
        }.body()
    }

    override suspend fun detalle(id: Long): ResultadoEvento<EventoDetalle> = peticion {
        http.get("$API_BASE_URL/eventos/$id") { auth() }.body()
    }

    override suspend fun crear(req: GuardarEventoRequest): ResultadoEvento<EventoDetalle> = peticion {
        http.post("$API_BASE_URL/eventos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun editar(id: Long, req: GuardarEventoRequest): ResultadoEvento<EventoDetalle> = peticion {
        http.put("$API_BASE_URL/eventos/$id") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun ocultar(id: Long): ResultadoEvento<Unit> = peticion {
        http.delete("$API_BASE_URL/eventos/$id") { auth() }
        Unit
    }

    override suspend fun recuperar(id: Long): ResultadoEvento<EventoDetalle> = peticion {
        http.put("$API_BASE_URL/eventos/$id/recuperar") { auth() }.body()
    }

    override suspend fun listarOcultos(): ResultadoEvento<List<EventoResumen>> = peticion {
        http.get("$API_BASE_URL/eventos/ocultos") { auth() }.body<EventosOcultosResponse>().eventos
    }

    override suspend fun asistentes(eventoId: Long): ResultadoEvento<ListadoAsistentesDto> = peticion {
        http.get("$API_BASE_URL/eventos/$eventoId/asistentes") { auth() }.body()
    }

    override suspend fun confirmarPago(
        eventoId: Long,
        asistenciaId: Long,
        metodo: String,
    ): ResultadoEvento<ListadoAsistentesDto> = peticion {
        http.put("$API_BASE_URL/eventos/$eventoId/asistencias/$asistenciaId/pago") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ConfirmarPagoBody(metodo))
        }.body()
    }

    override suspend fun deshacerPago(
        eventoId: Long,
        asistenciaId: Long,
    ): ResultadoEvento<ListadoAsistentesDto> = peticion {
        http.delete("$API_BASE_URL/eventos/$eventoId/asistencias/$asistenciaId/pago") { auth() }.body()
    }

    override suspend fun declararPago(
        eventoId: Long,
        body: DeclararPagoBody,
    ): ResultadoEvento<EventoDetalle> = peticion {
        http.post("$API_BASE_URL/eventos/$eventoId/pagos-declarados") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    override suspend fun anularPagoDeclarado(eventoId: Long): ResultadoEvento<EventoDetalle> = peticion {
        http.delete("$API_BASE_URL/eventos/$eventoId/pagos-declarados/mia") { auth() }.body()
    }

    override suspend fun pagosDeclaradosPendientes():
        ResultadoEvento<List<PagoDeclaradoPendienteDto>> = peticion {
        http.get("$API_BASE_URL/admin/pagos-declarados") { auth() }.body()
    }

    override suspend fun confirmarPagoDeclarado(id: Long): ResultadoEvento<Unit> = peticion {
        http.post("$API_BASE_URL/admin/pagos-declarados/$id/confirmar") { auth() }
        Unit
    }

    override suspend fun rechazarPagoDeclarado(id: Long): ResultadoEvento<Unit> = peticion {
        http.post("$API_BASE_URL/admin/pagos-declarados/$id/rechazar") { auth() }
        Unit
    }

    override suspend fun solicitarCrear(mensaje: String?): ResultadoEvento<Unit> = peticion {
        http.post("$API_BASE_URL/eventos/solicitudes") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("mensaje" to (mensaje ?: "")))
        }.bodyAsText().let { }
    }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoEvento.Error.
     * `ResponseException` (4xx/5xx) → se lee `codigo` del cuerpo con [ErrorResponse];
     * cualquier otra excepción (red, timeout, serialización) → SIN_CONEXION.
     * `try` acotado, nunca `runCatching` (relanza `CancellationException`).
     */
    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoEvento<T> =
        try {
            ResultadoEvento.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorEvento.deCodigoBackend(codigo)
            ResultadoEvento.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoEvento.Error(CodigoErrorEvento.SIN_CONEXION, CodigoErrorEvento.SIN_CONEXION.mensaje)
        }
}
