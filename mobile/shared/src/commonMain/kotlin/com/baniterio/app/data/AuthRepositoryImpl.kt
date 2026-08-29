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
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthRepositoryImpl(private val http: HttpClient) : AuthRepository {

    private var token: String? = null
    private var _usuario: UsuarioResponse? = null
    override val usuarioActual: UsuarioResponse? get() = _usuario

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
                token = res.dato.token
                _usuario = res.dato.usuario
                ResultadoAuth.Exito(res.dato.usuario)
            }
            is ResultadoAuth.Error -> res
        }
    }

    override suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit> =
        peticion {
            http.post("$API_BASE_URL/auth/solicitudes") {
                contentType(ContentType.Application.Json)
                setBody(r)
            }
            Unit
        }

    override fun logout() {
        token = null
        _usuario = null
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
            val codigo = runCatching { e.response.body<ErrorResponse>().codigo }.getOrNull()
            val cod = CodigoErrorAuth.deCodigoBackend(codigo)
            ResultadoAuth.Error(cod, cod.mensaje)
        } catch (e: Exception) {
            ResultadoAuth.Error(CodigoErrorAuth.SIN_CONEXION, CodigoErrorAuth.SIN_CONEXION.mensaje)
        }
}
