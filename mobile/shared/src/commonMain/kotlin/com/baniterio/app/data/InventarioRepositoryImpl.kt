package com.baniterio.app.data

import com.baniterio.app.data.dto.ActualizarArticuloRequest
import com.baniterio.app.data.dto.ArticuloInventarioDto
import com.baniterio.app.data.dto.CrearArticuloRequest
import com.baniterio.app.data.dto.EnviarAEventoBody
import com.baniterio.app.data.dto.EnviarCategoriaBody
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.EventoAbiertoDto
import com.baniterio.app.data.dto.InventarioFiestaResponse
import com.baniterio.app.data.dto.InventarioResponse
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

class InventarioRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : InventarioRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun ver(): ResultadoInventario<InventarioResponse> = peticion {
        http.get("$API_BASE_URL/inventario") { auth() }.body()
    }

    override suspend fun actualizar(
        id: Long,
        nombre: String,
        tamano: String,
        cantidad: Double,
    ): ResultadoInventario<ArticuloInventarioDto> = peticion {
        http.put("$API_BASE_URL/inventario/$id") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ActualizarArticuloRequest(nombre, tamano, cantidad))
        }.body()
    }

    override suspend fun crear(
        categoria: String,
        nombre: String,
        tamano: String,
        cantidad: Double,
    ): ResultadoInventario<ArticuloInventarioDto> = peticion {
        http.post("$API_BASE_URL/inventario") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CrearArticuloRequest(categoria, nombre, tamano, cantidad))
        }.body()
    }

    override suspend fun borrar(id: Long): ResultadoInventario<Unit> = peticion {
        http.delete("$API_BASE_URL/inventario/$id") { auth() }.let { }
    }

    override suspend fun eventosAbiertos(): ResultadoInventario<List<EventoAbiertoDto>> = peticion {
        http.get("$API_BASE_URL/eventos/abiertos") { auth() }.body()
    }

    override suspend fun enviarAEvento(articuloId: Long, eventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/$articuloId/enviar") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(EnviarAEventoBody(eventoId))
        }.let { }
    }

    override suspend fun enviarCategoria(categoria: String, eventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/enviar-categoria") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(EnviarCategoriaBody(categoria, eventoId))
        }.let { }
    }

    override suspend fun inventarioFiesta(eventoId: Long): ResultadoInventario<InventarioFiestaResponse> = peticion {
        http.get("$API_BASE_URL/inventario/evento/$eventoId") { auth() }.body()
    }

    override suspend fun devolver(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/evento/$eventoId/$articuloEventoId/devolver") { auth() }.let { }
    }

    override suspend fun devolverALista(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/evento/$eventoId/$articuloEventoId/devolver-a-lista") { auth() }.let { }
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoInventario<T> =
        try {
            ResultadoInventario.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorInventario.deCodigoBackend(codigo)
            ResultadoInventario.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoInventario.Error(
                CodigoErrorInventario.SIN_CONEXION,
                CodigoErrorInventario.SIN_CONEXION.mensaje,
            )
        }
}
