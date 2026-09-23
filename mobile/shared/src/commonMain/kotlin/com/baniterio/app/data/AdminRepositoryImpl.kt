package com.baniterio.app.data

import com.baniterio.app.data.dto.ActivoRequest
import com.baniterio.app.data.dto.AprobarResponse
import com.baniterio.app.data.dto.AreasRequest
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.MiembroResumen
import com.baniterio.app.data.dto.PaginaLogsDto
import com.baniterio.app.data.dto.RechazoRequest
import com.baniterio.app.data.dto.RolRequest
import com.baniterio.app.data.dto.SolicitudEventoResumen
import com.baniterio.app.data.dto.SolicitudResumen
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class AdminRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : AdminRepository {

    /** Adjunta la cabecera Bearer con el token en memoria de [SesionHolder]. */
    private fun HttpRequestBuilder.auth() {
        // Sin token no se manda la cabecera: interpolar `null` produciría un
        // literal "Bearer null" (pasa en el arranque en frío hacia una pantalla
        // de admin, antes de que la guardia redirija).
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun solicitudes(estado: String): ResultadoAdmin<List<SolicitudResumen>> =
        peticion {
            http.get("$API_BASE_URL/admin/solicitudes") {
                auth()
                parameter("estado", estado)
            }.body<List<SolicitudResumen>>()
        }

    override suspend fun aprobar(id: Long): ResultadoAdmin<AprobarResponse> =
        peticion {
            http.post("$API_BASE_URL/admin/solicitudes/$id/aprobar") { auth() }
                .body<AprobarResponse>()
        }

    override suspend fun rechazar(id: Long, motivo: String?): ResultadoAdmin<Unit> =
        peticion {
            http.post("$API_BASE_URL/admin/solicitudes/$id/rechazar") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(RechazoRequest(motivo))
            }.bodyAsText().let { }
        }

    override suspend fun miembros(): ResultadoAdmin<List<MiembroResumen>> =
        peticion {
            http.get("$API_BASE_URL/admin/miembros") { auth() }
                .body<List<MiembroResumen>>()
        }

    override suspend fun cambiarRol(id: Long, rol: String): ResultadoAdmin<Unit> =
        peticion {
            http.put("$API_BASE_URL/admin/miembros/$id/rol") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(RolRequest(rol))
            }.bodyAsText().let { }
        }

    override suspend fun cambiarActivo(id: Long, activo: Boolean): ResultadoAdmin<Unit> =
        peticion {
            http.put("$API_BASE_URL/admin/miembros/$id/activo") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(ActivoRequest(activo))
            }.bodyAsText().let { }
        }

    override suspend fun cambiarAreas(id: Long, areas: List<String>): ResultadoAdmin<Unit> =
        peticion {
            http.put("$API_BASE_URL/admin/miembros/$id/areas") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(AreasRequest(areas))
            }.bodyAsText().let { }
        }

    override suspend fun pendientesPorArea(): ResultadoAdmin<Map<String, Int>> =
        peticion {
            http.get("$API_BASE_URL/admin/pendientes") { auth() }
                .body<Map<String, Int>>()
        }

    override suspend fun solicitudesEvento(estado: String): ResultadoAdmin<List<SolicitudEventoResumen>> =
        peticion {
            http.get("$API_BASE_URL/admin/solicitudes-evento") {
                auth()
                parameter("estado", estado)
            }.body<List<SolicitudEventoResumen>>()
        }

    override suspend fun aprobarSolicitudEvento(id: Long): ResultadoAdmin<Unit> =
        peticion {
            http.post("$API_BASE_URL/admin/solicitudes-evento/$id/aprobar") { auth() }
                .bodyAsText().let { }
        }

    override suspend fun rechazarSolicitudEvento(id: Long, motivo: String?): ResultadoAdmin<Unit> =
        peticion {
            http.post("$API_BASE_URL/admin/solicitudes-evento/$id/rechazar") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(RechazoRequest(motivo))
            }.bodyAsText().let { }
        }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoAdmin.Error.
     * Un ResponseException (4xx/5xx) → se lee `codigo` del cuerpo con
     * [ErrorResponse]; cualquier otra excepción (red, timeout, serialización) →
     * SIN_CONEXION. La CancellationException se relanza para no romper la
     * concurrencia estructurada.
     */
    override suspend fun logs(usuarioId: Long?, pagina: Int, tamano: Int): ResultadoAdmin<PaginaLogsDto> =
        peticion {
            http.get("$API_BASE_URL/logs") {
                auth()
                usuarioId?.let { parameter("usuarioId", it) }
                parameter("pagina", pagina)
                parameter("tamano", tamano)
            }.body<PaginaLogsDto>()
        }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoAdmin<T> =
        try {
            ResultadoAdmin.Exito(bloque())
        } catch (e: ResponseException) {
            // try acotado, no runCatching: runCatching captura Throwable, incluida
            // CancellationException, y se cargaría la concurrencia estructurada.
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorAdmin.deCodigoBackend(codigo)
            ResultadoAdmin.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAdmin.Error(CodigoErrorAdmin.SIN_CONEXION, CodigoErrorAdmin.SIN_CONEXION.mensaje)
        }
}
