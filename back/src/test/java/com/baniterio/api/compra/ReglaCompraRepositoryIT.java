package com.baniterio.api.compra;

import java.util.List;

import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** La plantilla de reglas de compra (V38 + V40 + V42 + V58 + V60) se siembra con las 30 fórmulas de la peña. */
class ReglaCompraRepositoryIT extends IntegrationTest {

    @Autowired
    ReglaCompraRepository reglas;

    @Autowired
    PenaRepository penas;

    Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    @Test
    void la_plantilla_se_siembra_con_las_30_reglas() {
        List<ReglaCompra> todas = reglas.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId());

        assertThat(todas).hasSize(30);
        assertThat(todas).anyMatch(r -> r.getTipoFormula() == TipoFormulaCompra.ALCOHOL_SELECCIONADO);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Bayetas")
                && r.getPorCada() == 30
                && r.getTipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Tortilla de patatas")
                && r.getPorCada() == 7
                && r.getTipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS_DIA);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Revueltos grande")
                && r.getTipoFormula() == TipoFormulaCompra.POR_EVENTO);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Gominolas")
                && r.getTipoFormula() == TipoFormulaCompra.POR_DIA);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Empanada de carne")
                && r.getPorCada() == 7
                && r.getTipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS_DIA);
        assertThat(todas).anyMatch(r -> r.getNombre().equals("Empanada de pollo con setas")
                && r.getPorCada() == 7
                && r.getTipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS_DIA);
    }
}
