package com.baniterio.app.data

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

class AsistenciaRepositoryImplTest {

    private data class Vista(
        val metodo: String,
        val path: String,
        val auth: String?,
        val cuerpo: String,
    )

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
    ): Pair<AsistenciaRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(
                metodo = req.method.value,
                path = req.url.encodedPath,
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
        return AsistenciaRepositoryImpl(http, sesion) to vistas
    }

    private val detalleJson = """
        {"id":1,"nombre":"San Miguel","fecha":"2999-01-01","pasado":false,
         "cuenta":{"id":2,"nombre":"San Miguel"},"puedoEditar":false,"puedoBorrar":false,
         "borradoPendiente":false,
         "asistencia":{"miAsistencia":"APUNTADO","puedeNotificar":false,
           "apuntados":1,"noVoy":0,"enDuda":0,"sinContestar":3}}
    """.trimIndent()

    @Test
    fun responder_hace_put_con_bearer_y_estado() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = detalleJson)
        val res = r.responder(1, "APUNTADO")
        assertIs<ResultadoAsistencia.Exito<*>>(res)
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/eventos/1/asistencia", vistas[0].path)
        assertEquals("Bearer jwt-x", vistas[0].auth)
        assertTrue(vistas[0].cuerpo.contains("\"estado\":\"APUNTADO\""), vistas[0].cuerpo)
    }

    @Test
    fun mandar_notificacion_409_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"NOTIFICACION_REENVIO_PRONTO"}""")
        val res = r.mandarNotificacion(1, "hola")
        assertIs<ResultadoAsistencia.Error>(res)
        assertEquals(CodigoErrorAsistencia.NOTIFICACION_REENVIO_PRONTO, res.codigo)
    }

    @Test
    fun anadir_hace_post_a_asistencias() = runTest {
        val (r, vistas) = repo(
            status = HttpStatusCode.Created,
            cuerpoRespuesta = """{"id":9,"nombre":"Primo","estado":"APUNTADO","esManual":true}""",
        )
        val res = r.anadir(1, "Primo", "APUNTADO")
        assertIs<ResultadoAsistencia.Exito<*>>(res)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/eventos/1/asistencias", vistas[0].path)
        assertTrue(vistas[0].cuerpo.contains("\"nombre\":\"Primo\""), vistas[0].cuerpo)
    }

    @Test
    fun quitar_la_respuesta_de_un_usuario_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"ASISTENCIA_NO_MANUAL"}""")
        val res = r.quitar(1, 9)
        assertIs<ResultadoAsistencia.Error>(res)
        assertEquals(CodigoErrorAsistencia.ASISTENCIA_NO_MANUAL, res.codigo)
    }

    @Test
    fun guardar_ficha_hace_put_a_ficha_bebida() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"modalidad":"COMPLETA","cuota":26.0,"cuotaPendiente":false}""")
        val res = r.guardarFicha(1, com.baniterio.app.data.dto.FichaBebidaBody(
            estado = "APUNTADO", refrescoBebidaId = 20, alternativa = "NADA"))
        assertIs<ResultadoAsistencia.Exito<*>>(res)
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/eventos/1/ficha-bebida", vistas[0].path)
        assertTrue(vistas[0].cuerpo.contains("\"estado\":\"APUNTADO\""), vistas[0].cuerpo)
    }

    @Test
    fun guardar_ficha_en_evento_sin_ficha_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"EVENTO_SIN_FICHA"}""")
        val res = r.guardarFicha(1, com.baniterio.app.data.dto.FichaBebidaBody(
            estado = "APUNTADO", refrescoBebidaId = 20, alternativa = "NADA"))
        assertIs<ResultadoAsistencia.Error>(res)
        assertEquals(CodigoErrorAsistencia.EVENTO_SIN_FICHA, res.codigo)
    }

    @Test
    fun pendientes_hace_get_y_devuelve_la_lista() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """
            {"eventos":[{"id":5,"nombre":"San Miguel","fecha":"2999-01-01","pasado":false,
              "cuenta":{"id":2,"nombre":"San Miguel"}}]}
        """.trimIndent())
        val res = r.pendientes()
        assertIs<ResultadoAsistencia.Exito<List<*>>>(res)
        assertEquals(1, res.dato.size)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/eventos/pendientes-respuesta", vistas[0].path)
    }
}
