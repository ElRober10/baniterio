package com.baniterio.app.data

import com.baniterio.app.data.dto.CrearReglaBody
import com.baniterio.app.data.dto.ListaCompraResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ListaCompraRepositoryImplTest {

    private data class Vista(val metodo: String, val path: String, val cuerpo: String, val auth: String?)

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpo: String = "",
    ): Pair<ListaCompraRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(
                req.method.value,
                req.url.encodedPath,
                req.body.toByteArray().decodeToString(),
                req.headers[HttpHeaders.Authorization],
            )
            if (status.value >= 400) {
                respondError(status, cuerpo, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(ByteReadChannel(cuerpo), status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return ListaCompraRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun lista_hace_get_con_bearer() = runTest {
        val (r, v) = repo(cuerpo = """{"puedoEditar":true,"llevaFicha":false,"apuntados":3,"diasFiesta":1,"categorias":[]}""")
        val res = r.lista(7)
        assertIs<ResultadoListaCompra.Exito<ListaCompraResponse>>(res)
        assertEquals("GET", v[0].metodo)
        assertEquals("/api/v1/eventos/7/lista-compra", v[0].path)
        assertEquals("Bearer jwt-x", v[0].auth)
    }

    @Test
    fun admin_eventos_hace_get() = runTest {
        val (r, v) = repo(cuerpo = "[]")
        r.adminEventos()
        assertEquals("/api/v1/admin/lista-compra/eventos", v[0].path)
    }

    @Test
    fun admin_evento_hace_get() = runTest {
        val (r, v) = repo(cuerpo = """{"evento":{"id":7,"nombre":"x","fecha":"2026-09-25"},"apuntados":0,"diasFiesta":1,"reglas":[]}""")
        r.adminEvento(7)
        assertEquals("/api/v1/admin/lista-compra/eventos/7", v[0].path)
    }

    @Test
    fun ajustar_regla_hace_put_con_body() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        r.ajustarRegla(7, 3, 12.0, true)
        assertEquals("PUT", v[0].metodo)
        assertEquals("/api/v1/admin/lista-compra/eventos/7/reglas/3", v[0].path)
        assert(v[0].cuerpo.contains("\"cantidadAjustada\":12")) { v[0].cuerpo }
    }

    @Test
    fun crear_regla_hace_post() = runTest {
        val (r, v) = repo(cuerpo = """{"id":9,"categoria":"COMIDA","etiqueta":"Comida","nombre":"Servilletas","tamano":"paquete","tipoFormula":"POR_EVENTO","factor":2.0,"origen":"MANUAL","cantidadCalculada":2.0,"cantidadFinal":2.0,"activa":true}""")
        r.crearRegla(7, CrearReglaBody("COMIDA", "Servilletas", "paquete", "POR_EVENTO", 2.0))
        assertEquals("POST", v[0].metodo)
        assertEquals("/api/v1/admin/lista-compra/eventos/7/reglas", v[0].path)
    }

    @Test
    fun borrar_regla_hace_delete() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        val res = r.borrarRegla(7, 3)
        assertIs<ResultadoListaCompra.Exito<*>>(res)
        assertEquals("DELETE", v[0].metodo)
        assertEquals("/api/v1/admin/lista-compra/eventos/7/reglas/3", v[0].path)
    }

    @Test
    fun borrar_regla_de_plantilla_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict, cuerpo = """{"codigo":"REGLA_COMPRA_NO_BORRABLE"}""")
        val res = r.borrarRegla(7, 3)
        assertIs<ResultadoListaCompra.Error>(res)
        assertEquals(CodigoErrorListaCompra.REGLA_NO_BORRABLE, res.codigo)
    }
}
