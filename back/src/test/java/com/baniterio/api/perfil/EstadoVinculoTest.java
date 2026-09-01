package com.baniterio.api.perfil;

import com.baniterio.api.identidad.EstadoVinculo;
import org.junit.jupiter.api.Test;

import static com.baniterio.api.identidad.EstadoVinculo.ACEPTADO;
import static com.baniterio.api.identidad.EstadoVinculo.PENDIENTE;
import static com.baniterio.api.identidad.EstadoVinculo.RECHAZADO;
import static com.baniterio.api.identidad.EstadoVinculo.SIN_CUENTA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabla de transiciones de {@link EstadoVinculo#puedePasarA}: función pura, sin
 * Spring. Refleja la máquina de estados del spec ("Flujo: vínculo de pareja").
 */
class EstadoVinculoTest {

    @Test
    void sin_cuenta_solo_pasa_a_pendiente() {
        assertThat(SIN_CUENTA.puedePasarA(PENDIENTE)).isTrue();
        assertThat(SIN_CUENTA.puedePasarA(ACEPTADO)).isFalse();
        assertThat(SIN_CUENTA.puedePasarA(RECHAZADO)).isFalse();
        assertThat(SIN_CUENTA.puedePasarA(SIN_CUENTA)).isFalse();
    }

    @Test
    void pendiente_pasa_a_aceptado_o_rechazado() {
        assertThat(PENDIENTE.puedePasarA(ACEPTADO)).isTrue();
        assertThat(PENDIENTE.puedePasarA(RECHAZADO)).isTrue();
        assertThat(PENDIENTE.puedePasarA(SIN_CUENTA)).isFalse();
        assertThat(PENDIENTE.puedePasarA(PENDIENTE)).isFalse();
    }

    @Test
    void aceptado_es_terminal() {
        for (EstadoVinculo destino : EstadoVinculo.values()) {
            assertThat(ACEPTADO.puedePasarA(destino)).isFalse();
        }
    }

    @Test
    void rechazado_es_terminal() {
        for (EstadoVinculo destino : EstadoVinculo.values()) {
            assertThat(RECHAZADO.puedePasarA(destino)).isFalse();
        }
    }
}
