package com.baniterio.app.data

import com.baniterio.app.data.dto.AjustarReglaBody
import com.baniterio.app.data.dto.CambiarBloqueoBody
import com.baniterio.app.data.dto.CrearReglaBody
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.EventoListaCompraDto
import com.baniterio.app.data.dto.ListaCompraAdminResponse
import com.baniterio.app.data.dto.ListaCompraResponse
import com.baniterio.app.data.dto.ReglaCompraEventoDto
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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class ListaCompraRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : ListaCompraRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun lista(eventoId: Long): ResultadoListaCompra<ListaCompraResponse> = peticion {
        http.get("$API_BASE_URL/eventos/$eventoId/lista-compra") { auth() }.body()
    }

    override suspend fun adminEventos(): ResultadoListaCompra<List<EventoListaCompraDto>> = peticion {
        http.get("$API_BASE_URL/admin/lista-compra/eventos") { auth() }.body()
    }

    override suspend fun adminEvento(eventoId: Long): ResultadoListaCompra<ListaCompraAdminResponse> = peticion {
        http.get("$API_BASE_URL/admin/lista-compra/eventos/$eventoId") { auth() }.body()
    }

    override suspend fun ajustarRegla(
        eventoId: Long,
        reglaId: Long,
        cantidadAjustada: Double?,
        activa: Boolean,
    ): ResultadoListaCompra<Unit> = peticion {
        http.put("$API_BASE_URL/admin/lista-compra/eventos/$eventoId/reglas/$reglaId") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AjustarReglaBody(cantidadAjustada, activa))
        }.let { }
    }

    override suspend fun crearRegla(eventoId: Long, body: CrearReglaBody): ResultadoListaCompra<ReglaCompraEventoDto> = peticion {
        http.post("$API_BASE_URL/admin/lista-compra/eventos/$eventoId/reglas") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    override suspend fun borrarRegla(eventoId: Long, reglaId: Long): ResultadoListaCompra<Unit> = peticion {
        http.delete("$API_BASE_URL/admin/lista-compra/eventos/$eventoId/reglas/$reglaId") { auth() }.let { }
    }

    override suspend fun marcarComprada(eventoId: Long, lineaId: Long): ResultadoListaCompra<Unit> = peticion {
        http.post("$API_BASE_URL/eventos/$eventoId/lista-compra/lineas/$lineaId/comprado") { auth() }.let { }
    }

    override suspend fun cambiarBloqueo(eventoId: Long, bloqueada: Boolean): ResultadoListaCompra<Unit> = peticion {
        http.put("$API_BASE_URL/eventos/$eventoId/lista-compra/bloqueo") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CambiarBloqueoBody(bloqueada))
        }.let { }
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoListaCompra<T> =
        try {
            ResultadoListaCompra.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorListaCompra.deCodigoBackend(codigo)
            ResultadoListaCompra.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoListaCompra.Error(
                CodigoErrorListaCompra.SIN_CONEXION,
                CodigoErrorListaCompra.SIN_CONEXION.mensaje,
            )
        }
}
