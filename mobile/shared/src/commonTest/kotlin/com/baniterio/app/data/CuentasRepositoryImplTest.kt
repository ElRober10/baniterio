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

class CuentasRepositoryImplTest {

    private data class Vista(
        val metodo: String,
        val path: String,
        val auth: String?,
        val cuerpo: String,
    )

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
    ): Pair<CuentasRepositoryImpl, MutableList<Vista>> {
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
        return CuentasRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun listar_hace_get_con_bearer_y_devuelve_las_cuentas() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta =
            """[{"id":1,"nombre":"San Miguel","descripcion":null}]""")
        val res = r.listar()
        assertIs<ResultadoCuenta.Exito<List<*>>>(res)
        assertEquals(1, res.dato.size)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/cuentas", vistas[0].path)
        assertEquals("Bearer jwt-x", vistas[0].auth)
    }

    @Test
    fun detalle_hace_get_por_id_y_deserializa_saldo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """
            {"id":3,"nombre":"San Miguel","saldo":91.13,"estimacion":107.13,
             "movimientos":[{"concepto":"Saldo del año anterior","importe":91.13,
                             "fecha":"2026-01-01","saldoTras":91.13}],
             "puedoGestionar":true,"cobradoSinIngresar":16.0}
        """.trimIndent())
        val res = r.detalle(3)
        assertIs<ResultadoCuenta.Exito<com.baniterio.app.data.dto.CuentaDetalleDto>>(res)
        assertEquals("/api/v1/cuentas/3", vistas[0].path)
        assertEquals(91.13, res.dato.saldo)
        assertEquals(1, res.dato.movimientos.size)
    }

    @Test
    fun marcarTransferido_hace_post() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta =
            """{"id":3,"nombre":"San Miguel","saldo":107.13,"estimacion":107.13,"puedoGestionar":true,"cobradoSinIngresar":0.0}""")
        r.marcarTransferido(3)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/3/transferencia-a-pena", vistas[0].path)
    }

    @Test
    fun detalle_404_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.NotFound,
            cuerpoRespuesta = """{"codigo":"CUENTA_NO_ENCONTRADA"}""")
        val res = r.detalle(99)
        assertIs<ResultadoCuenta.Error>(res)
        assertEquals(CodigoErrorCuenta.CUENTA_NO_ENCONTRADA, res.codigo)
    }
}
