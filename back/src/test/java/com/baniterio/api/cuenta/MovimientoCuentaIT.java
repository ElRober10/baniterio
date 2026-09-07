package com.baniterio.api.cuenta;

import java.math.BigDecimal;

import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** IT del libro de movimientos y el saldo de una cuenta (V28). */
class MovimientoCuentaIT extends IntegrationTest {

    @Autowired CuentaRepository cuentas;
    @Autowired MovimientoCuentaRepository movimientos;
    @Autowired PenaRepository penas;

    private Long cuentaSanMiguelId() {
        Long penaId = penas.findBySlug("baniterio").orElseThrow().getId();
        return cuentas.findByPenaIdAndNombre(penaId, "San Miguel").orElseThrow().getId();
    }

    @Test
    void la_migracion_siembra_el_saldo_inicial_de_san_miguel() {
        Long sanMiguel = cuentaSanMiguelId();

        var filas = movimientos.findByCuentaIdOrderByFechaAscIdAsc(sanMiguel);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getOrigen()).isEqualTo(OrigenMovimiento.SALDO_INICIAL);
        assertThat(filas.get(0).getImporte()).isEqualByComparingTo(new BigDecimal("91.13"));
        assertThat(movimientos.sumImporte(sanMiguel)).isEqualByComparingTo(new BigDecimal("91.13"));
    }
}
