package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpClientFactoryTest {

    @Test
    fun reportaUnFalloDeRedQueNuncaLlegaARespuesta() = runTest {
        var pantallaReportada: String? = null
        val engine = MockEngine { throw RuntimeException("sin red") }
        val client = HttpClient(engine) {
            configComun(reportarError = { pantalla, _ -> pantallaReportada = pantalla })
        }

        runCatching { client.get("https://x.test/api/v1/cuentas/1") }

        assertEquals("/api/v1/cuentas/1", pantallaReportada)
    }

    @Test
    fun noReportaLaPropiaPeticionDeLogsCliente() = runTest {
        var pantallaReportada: String? = null
        val engine = MockEngine { throw RuntimeException("sin red") }
        val client = HttpClient(engine) {
            configComun(reportarError = { pantalla, _ -> pantallaReportada = pantalla })
        }

        runCatching { client.get("https://x.test/api/v1/logs/cliente") }

        assertNull(pantallaReportada)
    }

    @Test
    fun noReportaUnErrorConRespuestaDelServidor() = runTest {
        var pantallaReportada: String? = null
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val client = HttpClient(engine) {
            configComun(reportarError = { pantalla, _ -> pantallaReportada = pantalla })
        }

        runCatching { client.get("https://x.test/api/v1/cuentas/1") }

        assertNull(pantallaReportada)
    }
}
