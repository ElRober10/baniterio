package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DispositivoRepositoryImplTest {

    /** Lo que el motor mock vio de una petición: método+ruta, cabecera Auth y cuerpo. */
    private data class Vista(val metodoYRuta: String, val auth: String?, val cuerpo: String)

    /**
     * Crea el repo con un `SesionHolder` que ya lleva `jwt-x` y una lista donde se
     * registran las peticiones vistas por el motor mock.
     */
    private fun repo(
        status: HttpStatusCode = HttpStatusCode.NoContent,
    ): Pair<DispositivoRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(
                metodoYRuta = "${req.method.value} ${req.url.encodedPath}",
                auth = req.headers[HttpHeaders.Authorization],
                cuerpo = req.body.toByteArray().decodeToString(),
            )
            respond(
                content = ByteReadChannel(""),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return DispositivoRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun registrar_hace_post_a_dispositivos_con_bearer_y_cuerpo_correctos() = runTest {
        val (r, vistas) = repo()
        assertTrue(r.registrar("tok", "ANDROID"))
        assertEquals(1, vistas.size)
        val vista = vistas.single()
        val (metodo, path) = vista.metodoYRuta.split(" ")
        assertEquals("POST", metodo)
        assertTrue(path.endsWith("/dispositivos"), "path inesperado: $path")
        assertEquals("Bearer jwt-x", vista.auth, "falta la cabecera Bearer de la sesión")
        assertTrue(vista.cuerpo.contains("tok"), "el cuerpo no lleva el token: ${vista.cuerpo}")
        assertTrue(vista.cuerpo.contains("ANDROID"), "el cuerpo no lleva la plataforma: ${vista.cuerpo}")
    }

    @Test
    fun eliminar_hace_delete_del_token_en_la_ruta() = runTest {
        val (r, vistas) = repo()
        assertTrue(r.eliminar("tok-123"))
        assertEquals(1, vistas.size)
        val (metodo, path) = vistas.single().metodoYRuta.split(" ")
        assertEquals("DELETE", metodo)
        assertTrue(path.endsWith("/dispositivos/tok-123"), "path inesperado: $path")
    }

    @Test
    fun eliminar_usa_el_bearer_explicito_y_no_el_de_la_sesion() = runTest {
        // Guarda de la regresión del logout (T10): al cerrar sesión el JWT en
        // memoria ya se ha borrado, así que `eliminar` debe usar el bearer que se
        // le pasa, capturado antes del logout, no `sesion.token`.
        val (r, vistas) = repo()
        assertTrue(r.eliminar("tok-9", "jwt-y"))
        assertEquals(1, vistas.size)
        val vista = vistas.single()
        val (metodo, path) = vista.metodoYRuta.split(" ")
        assertEquals("DELETE", metodo)
        assertTrue(path.endsWith("/dispositivos/tok-9"), "path inesperado: $path")
        assertEquals("Bearer jwt-y", vista.auth, "debe usar el bearer explícito, no el de la sesión")
    }

    @Test
    fun registrar_devuelve_false_si_el_backend_responde_500() = runTest {
        val (r, _) = repo(status = HttpStatusCode.InternalServerError)
        assertEquals(false, r.registrar("tok", "ANDROID"))
    }

    @Test
    fun eliminar_devuelve_false_si_el_backend_responde_404() = runTest {
        val (r, _) = repo(status = HttpStatusCode.NotFound)
        assertEquals(false, r.eliminar("tok-x"))
    }
}
