package com.baniterio.app.data

import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.LoginRequest
import com.baniterio.app.data.dto.LoginResponse
import com.baniterio.app.data.dto.RegistroRequest
import com.baniterio.app.data.dto.SolicitudIngresoRequest
import com.baniterio.app.data.dto.UsuarioResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class AuthRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : AuthRepository {

    override val usuarioActual: UsuarioResponse? get() = sesion.usuario

    override suspend fun registro(r: RegistroRequest): ResultadoAuth<UsuarioResponse> =
        peticion {
            http.post("$API_BASE_URL/auth/registro") {
                contentType(ContentType.Application.Json)
                setBody(r)
            }.body<UsuarioResponse>()
        }

    override suspend fun login(telefono: String, password: String): ResultadoAuth<UsuarioResponse> {
        val res = peticion {
            http.post("$API_BASE_URL/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(telefono, password))
            }.body<LoginResponse>()
        }
        return when (res) {
            is ResultadoAuth.Exito -> {
                sesion.token = res.dato.token
                sesion.usuario = res.dato.usuario
                ResultadoAuth.Exito(res.dato.usuario)
            }
            is ResultadoAuth.Error -> res
        }
    }

    override suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit> =
        peticion {
            val respuesta = http.post("$API_BASE_URL/auth/solicitudes") {
                contentType(ContentType.Application.Json)
                setBody(r)
            }
            // Se consume el cuerpo para que la conexión vuelva limpia al pool y se
            // descarta: con expectSuccess=true un 4xx/5xx ya habría lanzado
            // ResponseException antes de llegar aquí. El `let` vacío deja el bloque
            // devolviendo Unit, que es la T de este ResultadoAuth.
            respuesta.bodyAsText().let { }
        }

    override fun logout() {
        sesion.limpiar()
    }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoAuth.Error.
     * Un ResponseException (4xx/5xx) → se lee `codigo` del cuerpo; cualquier
     * otra excepción (red, timeout, serialización) → SIN_CONEXION.
     */
    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoAuth<T> =
        try {
            ResultadoAuth.Exito(bloque())
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
            val cod = CodigoErrorAuth.deCodigoBackend(codigo)
            ResultadoAuth.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAuth.Error(CodigoErrorAuth.SIN_CONEXION, CodigoErrorAuth.SIN_CONEXION.mensaje)
        }
}
