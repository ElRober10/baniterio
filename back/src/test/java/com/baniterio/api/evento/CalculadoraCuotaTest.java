package com.baniterio.api.evento;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Modalidad;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de casos de {@link CalculadoraCuota}: las 4 modalidades y la precedencia. */
class CalculadoraCuotaTest {

    private static final CalculadoraCuota.Cuotas CUOTAS = new CalculadoraCuota.Cuotas(
            new BigDecimal("26"), new BigDecimal("16"), new BigDecimal("14"),
            new BigDecimal("9"), new BigDecimal("5"));

    private static final CalculadoraCuota.Cuotas SIN_CUOTAS =
            new CalculadoraCuota.Cuotas(null, null, null, null, null);

    @Test
    void completa_paga_cuota_cubatas() {
        var r = CalculadoraCuota.calcular(CUOTAS, false, true, true, true);
        assertThat(r.modalidad()).isEqualTo(Modalidad.COMPLETA);
        assertThat(r.cuota()).isEqualByComparingTo("26.00");
    }

    @Test
    void solo_cerveza_paga_cuota_cervezas() {
        var r = CalculadoraCuota.calcular(CUOTAS, false, true, true, false);
        assertThat(r.modalidad()).isEqualTo(Modalidad.SOLO_CERVEZA);
        assertThat(r.cuota()).isEqualByComparingTo("16.00");
    }

    @Test
    void un_dia_bebiendo_alcohol_paga_cuota_cubatas_1dia() {
        var r = CalculadoraCuota.calcular(CUOTAS, false, true, false, true);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isEqualByComparingTo("14.00");
    }

    @Test
    void un_dia_sin_alcohol_paga_cuota_cervezas_1dia() {
        var r = CalculadoraCuota.calcular(CUOTAS, false, false, true, false);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isEqualByComparingTo("9.00");
    }

    @Test
    void embarazada_paga_cuota_embarazada_aunque_vaya_los_dos_dias() {
        var r = CalculadoraCuota.calcular(CUOTAS, true, true, true, false);
        assertThat(r.modalidad()).isEqualTo(Modalidad.EMBARAZADA);
        assertThat(r.cuota()).isEqualByComparingTo("5.00");
    }

    @Test
    void embarazada_y_un_dia_sigue_pagando_cuota_embarazada() {
        var r = CalculadoraCuota.calcular(CUOTAS, true, true, false, false);
        assertThat(r.modalidad()).isEqualTo(Modalidad.EMBARAZADA);
        assertThat(r.cuota()).isEqualByComparingTo("5.00");
    }

    @Test
    void sin_esa_cuota_puesta_da_cuota_null_pero_modalidad_calculada() {
        var r = CalculadoraCuota.calcular(SIN_CUOTAS, false, true, false, true);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isNull();
    }

    @Test
    void derivar_calcula_las_otras_4_cuotas_a_partir_de_cubatas() {
        var c = CalculadoraCuota.derivar(new BigDecimal("26"));
        assertThat(c.cubatas()).isEqualByComparingTo("26.00");
        assertThat(c.cervezas()).isEqualByComparingTo("16.00");
        assertThat(c.cubatas1Dia()).isEqualByComparingTo("14.00");
        assertThat(c.cervezas1Dia()).isEqualByComparingTo("9.00");
        assertThat(c.embarazada()).isEqualByComparingTo("5.00");
    }

    @Test
    void derivar_cervezas_no_baja_de_cero_si_cubatas_es_menor_que_10() {
        var c = CalculadoraCuota.derivar(new BigDecimal("4"));
        assertThat(c.cervezas()).isEqualByComparingTo("0.00");
        assertThat(c.cervezas1Dia()).isEqualByComparingTo("1.00");
        assertThat(c.embarazada()).isEqualByComparingTo("5.00");
    }

    @Test
    void derivar_sin_cubatas_da_las_5_cuotas_null() {
        var c = CalculadoraCuota.derivar(null);
        assertThat(c.cubatas()).isNull();
        assertThat(c.cervezas()).isNull();
        assertThat(c.cubatas1Dia()).isNull();
        assertThat(c.cervezas1Dia()).isNull();
        assertThat(c.embarazada()).isNull();
    }
}
