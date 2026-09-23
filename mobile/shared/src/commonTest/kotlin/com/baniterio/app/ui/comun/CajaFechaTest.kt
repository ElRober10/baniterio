package com.baniterio.app.ui.comun

import kotlin.test.Test
import kotlin.test.assertEquals

class CajaFechaTest {
    @Test
    fun ida_y_vuelta() {
        listOf("1970-01-01", "2024-02-29", "2026-09-23", "2027-03-26", "1999-12-31").forEach {
            assertEquals(it, millisAIso(isoAMillis(it)!!))
        }
    }

    @Test
    fun epoch_es_cero() = assertEquals(0L, isoAMillis("1970-01-01"))
}
