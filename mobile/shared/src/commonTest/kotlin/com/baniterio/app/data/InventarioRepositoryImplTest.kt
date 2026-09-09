package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import com.baniterio.app.data.dto.InventarioResponse
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InventarioRepositoryImplTest {

    private data class Vista(val metodo: String, val path: String, val cuerpo: String, val auth: String?)

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
    ): Pair<InventarioRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(
                req.method.value,
                req.url.encodedPath,
                req.body.toByteArray().decodeToString(),
                req.headers[HttpHeaders.Authorization],
            )
            if (status.value >= 400) {
                respondError(status, cuerpoRespuesta, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(ByteReadChannel(cuerpoRespuesta), status,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return InventarioRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun ver_hace_get_con_bearer() = runTest {
        val (r, vistas) = repo(
            cuerpoRespuesta = """{"puedoEditar":true,"categorias":[
                {"categoria":"CERVEZA","etiqueta":"Cerveza","tamanos":["lata"],
                 "articulos":[{"id":1,"nombre":"Mahou","tamano":"lata","cantidad":192}]}]}""",
        )
        val res = r.ver()
        assertIs<ResultadoInventario.Exito<InventarioResponse>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/inventario", vistas[0].path)
        assertEquals("Bearer jwt-x", vistas[0].auth)
        assertEquals(192.0, res.dato.categorias[0].articulos[0].cantidad)
    }

    @Test
    fun actualizar_hace_put_con_cuerpo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":1,"nombre":"Mahou","tamano":"lata","cantidad":5}""")
        val res = r.actualizar(1, "Mahou", "lata", 5.0)
        assertIs<ResultadoInventario.Exito<*>>(res)
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/inventario/1", vistas[0].path)
        assert(vistas[0].cuerpo.contains("\"tamano\":\"lata\"")) { vistas[0].cuerpo }
    }

    @Test
    fun crear_hace_post_con_categoria() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":9,"nombre":"Mixta","tamano":"lata","cantidad":6}""")
        val res = r.crear("CERVEZA", "Mixta", "lata", 6.0)
        assertIs<ResultadoInventario.Exito<*>>(res)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/inventario", vistas[0].path)
        assert(vistas[0].cuerpo.contains("\"categoria\":\"CERVEZA\"")) { vistas[0].cuerpo }
    }

    @Test
    fun borrar_hace_delete() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        val res = r.borrar(4)
        assertIs<ResultadoInventario.Exito<*>>(res)
        assertEquals("DELETE", vistas[0].metodo)
        assertEquals("/api/v1/inventario/4", vistas[0].path)
    }

    @Test
    fun borrar_inexistente_devuelve_error_tipado() = runTest {
        val (r, _) = repo(
            status = HttpStatusCode.NotFound,
            cuerpoRespuesta = """{"codigo":"ARTICULO_INVENTARIO_NO_ENCONTRADO"}""",
        )
        val res = r.borrar(9)
        assertIs<ResultadoInventario.Error>(res)
        assertEquals(CodigoErrorInventario.ARTICULO_NO_ENCONTRADO, res.codigo)
    }

    @Test
    fun actualizar_tamano_no_valido_devuelve_error_tipado() = runTest {
        val (r, _) = repo(
            status = HttpStatusCode.BadRequest,
            cuerpoRespuesta = """{"codigo":"TAMANO_INVENTARIO_NO_VALIDO"}""",
        )
        val res = r.actualizar(1, "Mahou", "barril", 5.0)
        assertIs<ResultadoInventario.Error>(res)
        assertEquals(CodigoErrorInventario.TAMANO_NO_VALIDO, res.codigo)
    }

    @Test
    fun crear_sin_permiso_devuelve_error_tipado() = runTest {
        val (r, _) = repo(
            status = HttpStatusCode.Forbidden,
            cuerpoRespuesta = """{"codigo":"SIN_PERMISO_INVENTARIO"}""",
        )
        val res = r.crear("CERVEZA", "Mixta", "lata", 6.0)
        assertIs<ResultadoInventario.Error>(res)
        assertEquals(CodigoErrorInventario.SIN_PERMISO, res.codigo)
    }
}
