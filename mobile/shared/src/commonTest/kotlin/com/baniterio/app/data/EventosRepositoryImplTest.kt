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
    fun crear_incluye_la_cuenta_en_el_cuerpo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "{}")
        r.crear(GuardarEventoRequest(nombre = "X", fecha = "2999-01-01", cuentaId = 7))
        assertTrue(vistas[0].cuerpo.contains("\"cuentaId\":7"), vistas[0].cuerpo)
    }

    @Test
    fun crear_incluye_la_cuota_maxima_si_se_pasa() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "{}")
        r.crear(GuardarEventoRequest(nombre = "X", fecha = "2999-01-01", cuentaId = 7, cuotaMaxima = 26.0))
        assertTrue(vistas[0].cuerpo.contains("\"cuotaMaxima\":26"), vistas[0].cuerpo)
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
    fun borrar_202_es_SOLICITUD_CREADA() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Accepted, cuerpoRespuesta = """{"estado":"PENDIENTE"}""")
        val res = r.borrar(5)
        assertIs<ResultadoEvento.Exito<BorradoEvento>>(res)
        assertEquals(BorradoEvento.SOLICITUD_CREADA, res.dato)
    }
}
