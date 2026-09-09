package com.baniterio.api.compra;

import java.util.List;

import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** La plantilla de reglas de compra (V38) se siembra con las 24 fórmulas de la peña. */
class ReglaCompraRepositoryIT extends IntegrationTest {

    @Autowired
    ReglaCompraRepository reglas;

    @Autowired
    PenaRepository penas;

    Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    @Test
    void la_plantilla_se_siembra_con_las_24_reglas() {
        List<ReglaCompra> todas = reglas.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId());

        assertThat(todas).hasSize(24);
        assertThat(todas).anyMatch(r -> r.getTipoFormula() == TipoFormulaCompra.ALCOHOL_SELECCIONADO);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Bayetas")
                && r.getPorCada() == 30
                && r.getTipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS);
    }
}
