package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DispositivoRepositoryImplTest {

    /** Registra "MÉTODO encodedPath" de cada petición vista por el motor mock. */
    private fun repo(
        status: HttpStatusCode = HttpStatusCode.NoContent,
    ): Pair<DispositivoRepositoryImpl, MutableList<String>> {
        val vistas = mutableListOf<String>()
        val engine = MockEngine { req ->
            vistas += "${req.method.value} ${req.url.encodedPath}"
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
    fun registrar_hace_post_a_dispositivos_y_devuelve_true_en_2xx() = runTest {
        val (r, vistas) = repo()
        assertTrue(r.registrar("tok", "ANDROID"))
        assertEquals(1, vistas.size)
        val (metodo, path) = vistas.single().split(" ")
        assertEquals("POST", metodo)
        assertTrue(path.endsWith("/dispositivos"), "path inesperado: $path")
    }

    @Test
    fun eliminar_hace_delete_del_token_en_la_ruta() = runTest {
        val (r, vistas) = repo()
        assertTrue(r.eliminar("tok-123"))
        assertEquals(1, vistas.size)
        val (metodo, path) = vistas.single().split(" ")
        assertEquals("DELETE", metodo)
        assertTrue(path.endsWith("/dispositivos/tok-123"), "path inesperado: $path")
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
