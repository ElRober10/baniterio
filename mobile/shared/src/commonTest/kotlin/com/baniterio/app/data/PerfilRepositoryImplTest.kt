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
import com.baniterio.app.data.dto.GuardarPerfilRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerfilRepositoryImplTest {

    private data class Vista(val metodo: String, val path: String, val auth: String?, val cuerpo: String)

    /** Repo con `SesionHolder` que ya lleva `jwt-x` y una lista de peticiones vistas. */
    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
        lanzaRed: Boolean = false,
    ): Pair<PerfilRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(
                metodo = req.method.value,
                path = req.url.encodedPath,
                auth = req.headers[HttpHeaders.Authorization],
                cuerpo = req.body.toByteArray().decodeToString(),
            )
            if (lanzaRed) throw RuntimeException("sin red")
            if (status.value >= 400) {
                respondError(status, cuerpoRespuesta, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(
                    ByteReadChannel(cuerpoRespuesta),
                    status,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return PerfilRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun miPerfil_hace_get_con_bearer_y_deserializa() = runTest {
        val (r, vistas) = repo(
            cuerpoRespuesta = """{"usuarioId":1,"nombre":"A","apellidos":"B","completado":false}""",
        )
        val res = r.miPerfil()
        assertTrue(res is ResultadoPerfil.Exito)
        assertEquals(false, res.dato.completado)
        val v = vistas.single()
        assertEquals("GET", v.metodo)
        assertTrue(v.path.endsWith("/perfil"), "path: ${v.path}")
        assertEquals("Bearer jwt-x", v.auth)
    }

    @Test
    fun guardar_hace_put_con_el_cuerpo_json() = runTest {
        val (r, vistas) = repo(
            cuerpoRespuesta = """{"usuarioId":1,"nombre":"A","apellidos":"B","completado":true}""",
        )
        r.guardar(
            GuardarPerfilRequest(
                nombre = "A", apellidos = "B", imagenTipo = "AVATAR", imagenRef = "01_chico",
                tienePareja = false,
            ),
        )
        val v = vistas.single()
        assertEquals("PUT", v.metodo)
        assertTrue(v.path.endsWith("/perfil"))
        assertTrue(v.cuerpo.contains("\"imagenTipo\":\"AVATAR\""), "cuerpo: ${v.cuerpo}")
        assertTrue(v.cuerpo.contains("\"tienePareja\":false"))
    }

    @Test
    fun subirFoto_hace_post_multipart_y_devuelve_la_ref() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"imagenRef":"abc.jpg"}""")
        val res = r.subirFoto(FotoElegida(byteArrayOf(1, 2, 3), "foto.jpg", "image/jpeg"))
        assertTrue(res is ResultadoPerfil.Exito)
        assertEquals("abc.jpg", res.dato)
        val v = vistas.single()
        assertEquals("POST", v.metodo)
        assertTrue(v.path.endsWith("/perfil/foto"), "path: ${v.path}")
    }

    @Test
    fun avatares_devuelve_la_lista() = runTest {
        val (r, _) = repo(cuerpoRespuesta = """[{"id":"01_chico","genero":"CHICO"}]""")
        val res = r.avatares()
        assertTrue(res is ResultadoPerfil.Exito)
        assertEquals(1, res.dato.size)
        assertEquals("01_chico", res.dato.first().id)
    }

    @Test
    fun aceptar_rechazar_romper_pegan_a_sus_rutas() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        r.aceptarPareja()
        r.rechazarPareja()
        r.romperPareja()
        assertEquals("POST", vistas[0].metodo)
        assertTrue(vistas[0].path.endsWith("/perfil/pareja/aceptar"))
        assertEquals("POST", vistas[1].metodo)
        assertTrue(vistas[1].path.endsWith("/perfil/pareja/rechazar"))
        assertEquals("DELETE", vistas[2].metodo)
        assertTrue(vistas[2].path.endsWith("/perfil/pareja"))
    }

    @Test
    fun miembros_hace_get_a_miembros() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = "[]")
        val res = r.miembros()
        assertTrue(res is ResultadoPerfil.Exito)
        assertTrue(vistas.single().path.endsWith("/miembros"))
    }

    @Test
    fun un_409_con_codigo_se_traduce() = runTest {
        val (r, _) = repo(
            status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"TELEFONO_YA_EMPAREJADO"}""",
        )
        val res = r.guardar(
            GuardarPerfilRequest("A", "B", imagenTipo = "AVATAR", imagenRef = "x", tienePareja = true),
        )
        assertTrue(res is ResultadoPerfil.Error)
        assertEquals(CodigoErrorPerfil.TELEFONO_YA_EMPAREJADO, res.codigo)
    }

    @Test
    fun un_fallo_de_red_es_sin_conexion() = runTest {
        val (r, _) = repo(lanzaRed = true)
        val res = r.miPerfil()
        assertTrue(res is ResultadoPerfil.Error)
        assertEquals(CodigoErrorPerfil.SIN_CONEXION, res.codigo)
    }

    @Test
    fun urlMedia_prefija_el_origen_sin_duplicar_api_v1() {
        assertEquals(null, urlMedia(null))
        val u = urlMedia("/api/v1/media/fotos/x.jpg")
        assertTrue(u!!.endsWith("/api/v1/media/fotos/x.jpg"), "url: $u")
        assertTrue(!u.contains("/api/v1/api/v1"), "url duplica el prefijo: $u")
    }
}
