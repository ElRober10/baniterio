package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BebidaRepositoryImplTest {

    private data class Vista(val metodo: String, val path: String, val query: String, val auth: String?)

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
    ): Pair<BebidaRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(req.method.value, req.url.encodedPath, req.url.encodedQuery,
                req.headers[HttpHeaders.Authorization])
            if (status.value >= 400) {
                respondError(status, cuerpoRespuesta, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(ByteReadChannel(cuerpoRespuesta), status,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return BebidaRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun catalogo_hace_get_con_bearer() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"alcohol":[{"id":1,"nombre":"Barceló"}],"refresco":[]}""")
        val res = r.catalogo()
        assertIs<ResultadoBebida.Exito<*>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/bebidas/catalogo", vistas[0].path)
        assertEquals("Bearer jwt-x", vistas[0].auth)
    }

    @Test
    fun pendientes_hace_get_con_estado() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "[]")
        r.pendientes()
        assertEquals("/api/v1/bebidas", vistas[0].path)
        assert(vistas[0].query.contains("estado=PENDIENTE")) { vistas[0].query }
    }

    @Test
    fun aceptar_hace_post() = runTest {
        val (r, vistas) = repo()
        val res = r.aceptar(3)
        assertIs<ResultadoBebida.Exito<*>>(res)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/bebidas/3/aceptar", vistas[0].path)
    }

    @Test
    fun aceptar_inexistente_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.NotFound,
            cuerpoRespuesta = """{"codigo":"BEBIDA_NO_ENCONTRADA"}""")
        val res = r.aceptar(9)
        assertIs<ResultadoBebida.Error>(res)
        assertEquals(CodigoErrorBebida.BEBIDA_NO_ENCONTRADA, res.codigo)
    }
}
