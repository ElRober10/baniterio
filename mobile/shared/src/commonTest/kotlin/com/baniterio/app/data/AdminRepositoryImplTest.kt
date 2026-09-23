package com.baniterio.app.data

import com.baniterio.app.data.dto.PaginaLogsDto
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

/** Tests de los 3 métodos de solicitudes de evento de [AdminRepositoryImpl]. */
class AdminRepositoryImplTest {

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
    ): Pair<AdminRepositoryImpl, MutableList<Vista>> {
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
        return AdminRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun solicitudesEvento_hace_get_con_estado_y_bearer() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "[]")
        val res = r.solicitudesEvento()
        assertIs<ResultadoAdmin.Exito<*>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/admin/solicitudes-evento", vistas[0].path)
        assertTrue(vistas[0].query.contains("estado=PENDIENTE"), "query: ${vistas[0].query}")
        assertEquals("Bearer jwt-x", vistas[0].auth)
    }

    @Test
    fun aprobarSolicitudEvento_hace_post_a_su_ruta() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        r.aprobarSolicitudEvento(9)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/admin/solicitudes-evento/9/aprobar", vistas[0].path)
    }

    @Test
    fun rechazarSolicitudEvento_manda_el_motivo() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        r.rechazarSolicitudEvento(3, "tarde")
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/admin/solicitudes-evento/3/rechazar", vistas[0].path)
        assertTrue(vistas[0].cuerpo.contains("\"motivo\":\"tarde\""), "cuerpo: ${vistas[0].cuerpo}")
    }

    @Test
    fun logs_hace_get_con_paginacion_y_devuelve_la_pagina() = runTest {
        val (r, vistas) = repo(
            cuerpoRespuesta = """{"contenido":[{"id":1,"origen":"MOBILE","usuarioId":null,
                "usuarioNombre":null,"metodo":null,"ruta":"cuentas.crearMovimiento",
                "estado":null,"codigoError":null,"mensaje":"IOException","creadoEn":"2026-09-23T10:00:00Z"}],
                "total":1,"pagina":0,"tamano":50}""",
        )
        val res = r.logs(pagina = 0, tamano = 50)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/logs", vistas[0].path)
        assertTrue(vistas[0].query.contains("tamano=50"), "query: ${vistas[0].query}")
        assertEquals("Bearer jwt-x", vistas[0].auth)
        val pagina = assertIs<ResultadoAdmin.Exito<PaginaLogsDto>>(res).dato
        assertEquals(1, pagina.contenido.size)
        assertEquals("cuentas.crearMovimiento", pagina.contenido[0].ruta)
    }
}
