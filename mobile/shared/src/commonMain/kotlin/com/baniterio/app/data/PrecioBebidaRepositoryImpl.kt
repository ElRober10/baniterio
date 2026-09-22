package com.baniterio.app.data

import com.baniterio.app.data.dto.AnadirTamanoBody
import com.baniterio.app.data.dto.CrearTiendaBody
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.EventoPrecioBebidaDto
import com.baniterio.app.data.dto.GrillaAlcoholDto
import com.baniterio.app.data.dto.GrillaArticuloDto
import com.baniterio.app.data.dto.GuardarPrecioArticuloBody
import com.baniterio.app.data.dto.GuardarPrecioBody
import com.baniterio.app.data.dto.GuardarProductoKiloBody
import com.baniterio.app.data.dto.GuardarTamanoArticuloBody
import com.baniterio.app.data.dto.TiendaDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class PrecioBebidaRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : PrecioBebidaRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun eventos(): ResultadoPrecioBebida<List<EventoPrecioBebidaDto>> = peticion {
        http.get("$API_BASE_URL/precio-bebida/eventos") { auth() }.body()
    }

    override suspend fun tiendas(): ResultadoPrecioBebida<List<TiendaDto>> = peticion {
        http.get("$API_BASE_URL/precio-bebida/tiendas") { auth() }.body()
    }

    override suspend fun crearTienda(nombre: String): ResultadoPrecioBebida<TiendaDto> = peticion {
        http.post("$API_BASE_URL/precio-bebida/tiendas") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CrearTiendaBody(nombre))
        }.body()
    }

    override suspend fun alcohol(eventoId: Long): ResultadoPrecioBebida<GrillaAlcoholDto> = peticion {
        http.get("$API_BASE_URL/precio-bebida/eventos/$eventoId/alcohol") { auth() }.body()
    }

    override suspend fun guardarPrecio(
        eventoId: Long,
        bebidaId: Long,
        tamano: String,
        tiendaId: Long,
        precio: Double?,
    ): ResultadoPrecioBebida<Unit> = peticion {
        http.put("$API_BASE_URL/precio-bebida/eventos/$eventoId/alcohol/precio") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(GuardarPrecioBody(bebidaId, tamano, tiendaId, precio))
        }.let { }
    }

    override suspend fun anadirTamano(eventoId: Long, tamano: String): ResultadoPrecioBebida<List<String>> = peticion {
        http.post("$API_BASE_URL/precio-bebida/eventos/$eventoId/alcohol/tamanos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AnadirTamanoBody(tamano))
        }.body()
    }

    override suspend fun articulos(eventoId: Long, categoria: String): ResultadoPrecioBebida<GrillaArticuloDto> = peticion {
        http.get("$API_BASE_URL/precio-bebida/eventos/$eventoId/articulos/$categoria") { auth() }.body()
    }

    override suspend fun guardarPrecioArticulo(
        eventoId: Long,
        categoria: String,
        nombreArticulo: String,
        tiendaId: Long,
        precio: Double?,
        cantidad: Int?,
    ): ResultadoPrecioBebida<Unit> = peticion {
        http.put("$API_BASE_URL/precio-bebida/eventos/$eventoId/articulos/$categoria/precio") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(GuardarPrecioArticuloBody(nombreArticulo, tiendaId, precio, cantidad))
        }.let { }
    }

    override suspend fun guardarTamanoArticulo(
        eventoId: Long,
        categoria: String,
        nombreArticulo: String,
        litros: Double,
    ): ResultadoPrecioBebida<Unit> = peticion {
        http.put("$API_BASE_URL/precio-bebida/eventos/$eventoId/articulos/$categoria/tamano") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(GuardarTamanoArticuloBody(nombreArticulo, litros))
        }.let { }
    }

    override suspend fun guardarProductoKilo(
        eventoId: Long,
        nombreArticulo: String,
        precioKilo: Double,
        pesoKg: Double,
    ): ResultadoPrecioBebida<Unit> = peticion {
        http.put("$API_BASE_URL/precio-bebida/eventos/$eventoId/articulos/COMIDA/kilo") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(GuardarProductoKiloBody(nombreArticulo, precioKilo, pesoKg))
        }.let { }
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoPrecioBebida<T> =
        try {
            ResultadoPrecioBebida.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorPrecioBebida.deCodigoBackend(codigo)
            ResultadoPrecioBebida.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoPrecioBebida.Error(
                CodigoErrorPrecioBebida.SIN_CONEXION,
                CodigoErrorPrecioBebida.SIN_CONEXION.mensaje,
            )
        }
}
