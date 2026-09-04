package com.baniterio.api.evento;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.identidad.Modalidad;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de casos de {@link CalculadoraCuota}: las 4 modalidades y la precedencia. */
class CalculadoraCuotaTest {

    private static final BigDecimal M = new BigDecimal("26");

    @Test
    void completa_paga_M() {
        var r = CalculadoraCuota.calcular(M, false, true, true, true, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.COMPLETA);
        assertThat(r.cuota()).isEqualByComparingTo("26.00");
    }

    @Test
    void solo_cerveza_paga_M_menos_10() {
        var r = CalculadoraCuota.calcular(M, false, true, true, false, Alternativa.CERVEZA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.SOLO_CERVEZA);
        assertThat(r.cuota()).isEqualByComparingTo("16.00");
    }

    @Test
    void tinto_de_verano_sin_alcohol_tambien_es_solo_cerveza() {
        var r = CalculadoraCuota.calcular(M, false, true, true, false, Alternativa.TINTO_VERANO);
        assertThat(r.modalidad()).isEqualTo(Modalidad.SOLO_CERVEZA);
    }

    @Test
    void un_dia_paga_M_partido_2_mas_1() {
        var r = CalculadoraCuota.calcular(M, false, true, false, true, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isEqualByComparingTo("14.00");
    }

    @Test
    void embarazada_paga_5_aunque_vaya_los_dos_dias() {
        var r = CalculadoraCuota.calcular(M, true, true, true, false, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.EMBARAZADA);
        assertThat(r.cuota()).isEqualByComparingTo("5.00");
    }

    @Test
    void embarazada_y_un_dia_sigue_siendo_5() {
        var r = CalculadoraCuota.calcular(M, true, true, false, false, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.EMBARAZADA);
        assertThat(r.cuota()).isEqualByComparingTo("5.00");
    }

    @Test
    void solo_cerveza_y_un_dia_gana_un_dia() {
        var r = CalculadoraCuota.calcular(M, false, false, true, false, Alternativa.CERVEZA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isEqualByComparingTo("14.00");
    }

    @Test
    void no_bebe_nada_y_no_embarazada_paga_completa() {
        var r = CalculadoraCuota.calcular(M, false, true, true, false, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.COMPLETA);
        assertThat(r.cuota()).isEqualByComparingTo("26.00");
    }

    @Test
    void cuota_maxima_null_da_cuota_null_pero_modalidad_calculada() {
        var r = CalculadoraCuota.calcular(null, false, true, false, true, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isNull();
    }

    @Test
    void M_menor_que_10_no_da_cuota_negativa() {
        var r = CalculadoraCuota.calcular(new BigDecimal("8"), false, true, true, false, Alternativa.CERVEZA);
        assertThat(r.cuota()).isEqualByComparingTo("0.00");
    }

    @Test
    void un_dia_con_M_impar_redondea_a_dos_decimales() {
        var r = CalculadoraCuota.calcular(new BigDecimal("25"), false, true, false, true, Alternativa.NADA);
        assertThat(r.cuota()).isEqualByComparingTo("13.50");
    }
}
