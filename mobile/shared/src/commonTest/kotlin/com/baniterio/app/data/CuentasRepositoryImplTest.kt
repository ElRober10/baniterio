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
        val query: String,
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

    @Test
    fun detalle_con_anio_lo_pasa_como_query() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta =
            """{"id":3,"nombre":"San Miguel","anio":2024,"anios":[2026,2025,2024],"esAnioActual":false}""")
        val res = r.detalle(3, 2024)
        assertIs<ResultadoCuenta.Exito<com.baniterio.app.data.dto.CuentaDetalleDto>>(res)
        assertEquals("/api/v1/cuentas/3", vistas[0].path)
        assertEquals("anio=2024", vistas[0].query)
        assertEquals(2024, res.dato.anio)
        assertEquals(listOf(2026, 2025, 2024), res.dato.anios)
    }

    @Test
    fun detalle_sin_anio_no_pone_query() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":3,"nombre":"San Miguel"}""")
        r.detalle(3)
        assertEquals("", vistas[0].query)
    }

    @Test
    fun detalle_deserializa_ropa_y_saldo_inicial() = runTest {
        val (r, _) = repo(cuerpoRespuesta = """
            {"id":3,"nombre":"San Miguel","saldoInicial":91.13,
             "penistas":[{"asistenciaId":10,"nombre":"Ana","anio":2026,"cuota":16.0,
                          "estadoPago":"CONFIRMADO_EN_CUENTA","metodoPago":"BIZUM",
                          "camisetaCantidad":2,"camisetaTalla":"M","camisetaConfirmada":true,
                          "ingreso":16.0,"saldoTras":107.13}],
             "movimientos":[{"concepto":"Camiseta Ana","importe":-20.0,"fecha":"2026-03-01",
                             "saldoTras":87.13,"manual":false,"origen":"CAMISETA"}]}
        """.trimIndent())
        val res = r.detalle(3)
        assertIs<ResultadoCuenta.Exito<com.baniterio.app.data.dto.CuentaDetalleDto>>(res)
        assertEquals(91.13, res.dato.saldoInicial)
        val p = res.dato.penistas[0]
        assertEquals(2, p.camisetaCantidad)
        assertEquals("M", p.camisetaTalla)
        assertEquals(16.0, p.ingreso)
        assertEquals("CAMISETA", res.dato.movimientos[0].origen)
    }

    @Test
    fun cerrarAnio_hace_post() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":3,"nombre":"San Miguel","anio":2027,"esAnioActual":true}""")
        r.cerrarAnio(3)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/3/cerrar-anio", vistas[0].path)
    }

    @Test
    fun crearMovimiento_hace_post_multipart() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":3,"nombre":"San Miguel"}""")
        val res = r.crearMovimiento(
            3,
            com.baniterio.app.data.dto.CrearMovimientoInput(
                tipo = "GASTO", concepto = "Hielo", importe = 12.5, categoria = "HIELOS",
                recibo = ArchivoElegido(byteArrayOf(1, 2, 3), "recibo.pdf", "application/pdf"),
            ),
        )
        assertIs<ResultadoCuenta.Exito<com.baniterio.app.data.dto.CuentaDetalleDto>>(res)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/3/movimientos", vistas[0].path)
        assertEquals(true, vistas[0].cuerpo.contains("Hielo"))
    }

    @Test
    fun borrarMovimiento_hace_delete() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":3,"nombre":"San Miguel"}""")
        r.borrarMovimiento(55)
        assertEquals("DELETE", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/movimientos/55", vistas[0].path)
    }

    @Test
    fun marcarRopa_hace_put_con_json_de_lo_que_cambia() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"id":3,"nombre":"San Miguel"}""")
        r.marcarRopa(3, 10, com.baniterio.app.data.dto.MarcarRopaInput(camisetaCantidad = 2, camisetaTalla = "L"))
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/3/asistencias/10/ropa", vistas[0].path)
        assertEquals(true, vistas[0].cuerpo.contains("\"camisetaCantidad\":2"))
        assertEquals(true, vistas[0].cuerpo.contains("\"camisetaTalla\":\"L\""))
    }

    @Test
    fun marcarRopa_sin_precio_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"SIN_PRECIO_ROPA"}""")
        val res = r.marcarRopa(3, 10, com.baniterio.app.data.dto.MarcarRopaInput(camisetaCantidad = 1))
        assertIs<ResultadoCuenta.Error>(res)
        assertEquals(CodigoErrorCuenta.SIN_PRECIO_ROPA, res.codigo)
    }
}
