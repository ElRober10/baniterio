package com.baniterio.app.data

import com.baniterio.app.data.dto.GuardarEventoRequest
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
import kotlin.test.assertTrue

class EventosRepositoryImplTest {

    private data class Vista(
        val metodo: String,
        val path: String,
        val query: String,
        val auth: String?,
        val cuerpo: String,
    )

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
    ): Pair<EventosRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(
                metodo = req.method.value,
                path = req.url.encodedPath,
                query = req.url.encodedQuery,
                auth = req.headers[HttpHeaders.Authorization],
                cuerpo = req.body.toByteArray().decodeToString(),
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
        return EventosRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun listar_hace_get_con_pagina_y_bearer() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta =
            """{"eventos":[],"pagina":1,"totalPaginas":2,"puedeCrear":false,"puedeSolicitar":true}""")
        val res = r.listar(1)
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/eventos", vistas[0].path)
        assertTrue(vistas[0].query.contains("pagina=1"), "query: ${vistas[0].query}")
        assertEquals("Bearer jwt-x", vistas[0].auth)
    }

    @Test
    fun asistentes_hace_get_con_bearer_y_deserializa() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """
            {"asistentes":[
              {"nombre":"Ana","estado":"APUNTADO","esManual":false,
               "bebida":{"alcohol":"Barceló","refresco":"Coca-Cola","alternativa":"NADA","modalidad":"COMPLETA"},
               "cuota":45.0,"pagado":false}],
             "totalCuotas":45.0,"totalPagado":0.0,"miCuota":45.0,
             "puedoPagarPor":[
              {"nombre":"Luis","cuota":45.0,"relacion":"HIJO","usuarioId":9,"asistenciaId":null}]}
        """.trimIndent())
        val res = r.asistentes(3)
        assertIs<ResultadoEvento.Exito<com.baniterio.app.data.dto.ListadoAsistentesDto>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/eventos/3/asistentes", vistas[0].path)
        assertEquals("Bearer jwt-x", vistas[0].auth)
        assertEquals(1, res.dato.asistentes.size)
        assertEquals("Ana", res.dato.asistentes[0].nombre)
        assertEquals("HIJO", res.dato.puedoPagarPor[0].relacion)
        assertEquals(9L, res.dato.puedoPagarPor[0].usuarioId)
    }

    @Test
    fun crear_incluye_la_cuenta_en_el_cuerpo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "{}")
        r.crear(GuardarEventoRequest(nombre = "X", fecha = "2999-01-01", cuentaId = 7))
        assertTrue(vistas[0].cuerpo.contains("\"cuentaId\":7"), vistas[0].cuerpo)
    }

    @Test
    fun crear_incluye_la_cuota_de_cubatas_si_se_pasa() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "{}")
        r.crear(GuardarEventoRequest(nombre = "X", fecha = "2999-01-01", cuentaId = 7,
            cuotaCubatas = 26.0))
        assertTrue(vistas[0].cuerpo.contains("\"cuotaCubatas\":26"), vistas[0].cuerpo)
    }

    @Test
    fun crear_con_cuenta_nueva_devuelve_error_tipado_si_el_nombre_existe() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"CUENTA_YA_EXISTE"}""")
        val res = r.crear(GuardarEventoRequest(nombre = "San Miguel", fecha = "2999-01-01", cuentaNueva = true))
        assertIs<ResultadoEvento.Error>(res)
        assertEquals(CodigoErrorEvento.CUENTA_YA_EXISTE, res.codigo)
    }

    @Test
    fun crear_sin_credito_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"SIN_CREDITO_EVENTO"}""")
        val res = r.crear(GuardarEventoRequest(nombre = "X", fecha = "2999-01-01"))
        assertIs<ResultadoEvento.Error>(res)
        assertEquals(CodigoErrorEvento.SIN_CREDITO_EVENTO, res.codigo)
    }

    @Test
    fun ocultar_hace_delete() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        val res = r.ocultar(5)
        assertIs<ResultadoEvento.Exito<Unit>>(res)
        assertEquals("DELETE", vistas[0].metodo)
        assertEquals("/api/v1/eventos/5", vistas[0].path)
    }

    @Test
    fun recuperar_hace_put_y_devuelve_el_detalle() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":5,"nombre":"San Miguel","fecha":"2026-09-25",
            "pasado":false,"cuenta":{"id":1,"nombre":"San Miguel"},
            "puedoEditar":true,"puedoBorrar":true,"oculto":false}""")
        val res = r.recuperar(5)
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/eventos/5/recuperar", vistas[0].path)
    }

    private val listadoVacio = """
        {"asistentes":[],"totalCuotas":0.0,"totalPagado":0.0,"miCuota":null,
         "puedoPagarPor":[],"puedoConfirmarPagos":true}
    """.trimIndent()

    @Test
    fun confirmarPago_hace_put_con_el_metodo_en_el_cuerpo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = listadoVacio)
        val res = r.confirmarPago(3, 7, "BIZUM")
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/eventos/3/asistencias/7/pago", vistas[0].path)
        assertTrue(vistas[0].cuerpo.contains("\"metodo\":\"BIZUM\""), vistas[0].cuerpo)
    }

    @Test
    fun deshacerPago_hace_delete() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = listadoVacio)
        val res = r.deshacerPago(3, 7)
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("DELETE", vistas[0].metodo)
        assertEquals("/api/v1/eventos/3/asistencias/7/pago", vistas[0].path)
    }

    @Test
    fun confirmarPago_ficha_sin_cuota_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"FICHA_SIN_CUOTA"}""")
        val res = r.confirmarPago(3, 7, "EFECTIVO")
        assertIs<ResultadoEvento.Error>(res)
        assertEquals(CodigoErrorEvento.FICHA_SIN_CUOTA, res.codigo)
    }

    @Test
    fun listarOcultos_hace_get() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"eventos":[]}""")
        val res = r.listarOcultos()
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/eventos/ocultos", vistas[0].path)
    }
}
