package com.baniterio.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NormalizadorTelefonoTest {
    @Test
    fun acepta_movil_de_9_digitos() = assertEquals("611223344", normalizarTelefonoEs("611223344"))

    @Test
    fun quita_prefijo_mas_34() = assertEquals("611223344", normalizarTelefonoEs("+34611223344"))

    @Test
    fun quita_prefijo_0034() = assertEquals("711223344", normalizarTelefonoEs("0034711223344"))

    @Test
    fun quita_espacios_guiones_y_parentesis() =
        assertEquals("611223344", normalizarTelefonoEs(" (611) 22-33-44 "))

    @Test
    fun rechaza_si_no_empieza_por_6_o_7() = assertNull(normalizarTelefonoEs("911223344"))

    @Test
    fun rechaza_longitud_incorrecta() = assertNull(normalizarTelefonoEs("61122334"))

    @Test
    fun rechaza_texto() = assertNull(normalizarTelefonoEs("no soy un telefono"))
}
