package com.baniterio.api.compra;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.baniterio.api.compra.CalculadoraListaCompra.DatosEvento;
import com.baniterio.api.compra.CalculadoraListaCompra.LineaCalculada;
import com.baniterio.api.compra.CalculadoraListaCompra.PersonaCompra;
import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.inventario.CategoriaInventario;
import org.junit.jupiter.api.Test;

class CalculadoraListaCompraTest {

    private static ReglaCompraEvento regla(TipoFormulaCompra tipo, double factor, Integer porCada,
                                           CategoriaInventario cat, String nombre, String tamano) {
        return ReglaCompraEvento.builder()
                .tipoFormula(tipo).factor(BigDecimal.valueOf(factor)).porCada(porCada)
                .categoria(cat).nombre(nombre).tamano(tamano).orden(1)
                .origen(OrigenReglaCompra.PLANTILLA).activa(true).build();
    }

    private static PersonaCompra p(int dias, boolean ficha, String alcohol, String refresco, Alternativa alt) {
        return new PersonaCompra(dias, ficha, alcohol, refresco, alt);
    }

    private static DatosEvento sanMiguel(List<PersonaCompra> personas) {
        return new DatosEvento(personas.size(), 2, true, personas);
    }

    @Test
    void por_penista() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA),
                p(1, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_PENISTA, 3, null, CategoriaInventario.LIMPIEZA, "Platos", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("6");
    }

    @Test
    void por_penista_dia_usa_dias_que_va() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA),
                p(1, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_PENISTA_DIA, 3, null, CategoriaInventario.LIMPIEZA, "Vasos de mini", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("9");
    }

    @Test
    void por_dia() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_DIA, 1, null, CategoriaInventario.LIMPIEZA, "Mantel", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("2");
    }

    @Test
    void por_evento() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_EVENTO, 50, null, CategoriaInventario.LIMPIEZA, "Vasos de invitar", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("50");
    }

    @Test
    void por_cada_n_penistas_redondea_los_grupos_hacia_arriba() {
        var personas = new ArrayList<PersonaCompra>();
        for (int i = 0; i < 31; i++) {
            personas.add(p(2, true, null, "Fanta", Alternativa.NADA));
        }
        var d = sanMiguel(personas);
        var r = regla(TipoFormulaCompra.POR_CADA_N_PENISTAS, 2, 30, CategoriaInventario.LIMPIEZA, "Bayetas", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("4");
    }

    @Test
    void por_cada_n_penistas_dia_usa_la_suma_de_dias_que_va() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA),
                p(1, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_CADA_N_PENISTAS_DIA, 1, 7,
                CategoriaInventario.COMIDA, "Tortilla de patatas", "unidad");
        // 2 + 1 = 3 persona-días, ceil(3/7) = 1 grupo
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("1");
    }

    @Test
    void cerveza_alternativa_solo_cuenta_a_quien_bebe_cerveza() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.CERVEZA),
                p(1, true, null, "Fanta", Alternativa.CERVEZA),
                p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.CERVEZA_ALTERNATIVA, 5, null, CategoriaInventario.CERVEZA, "Cerveza", "lata");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("15");
    }

    @Test
    void tinto_alternativa() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.TINTO_VERANO)));
        var r = regla(TipoFormulaCompra.TINTO_ALTERNATIVA, 1, null, CategoriaInventario.REFRESCOS, "Tinto de verano", "botella");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("2");
    }

    @Test
    void alcohol_seleccionado_una_linea_por_marca_y_1L_si_es_una_botella() {
        var d = sanMiguel(List.of(
                p(2, true, "Legendario", "Fanta", Alternativa.NADA),
                p(2, true, "Legendario", "Fanta", Alternativa.NADA),
                p(2, true, "Legendario", "Fanta", Alternativa.NADA),
                p(2, true, "Barceló", "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.ALCOHOL_SELECCIONADO, 0.5, null, CategoriaInventario.ALCOHOL, "", "70 cl");
        List<LineaCalculada> lineas = CalculadoraListaCompra.lineasDe(r, d);
        assertThat(lineas).extracting(LineaCalculada::nombre).containsExactly("Barceló", "Legendario");
        assertThat(lineas.get(0).tamano()).isEqualTo("1 L");
        assertThat(lineas.get(0).bruto()).isEqualByComparingTo("1");
        assertThat(lineas.get(1).tamano()).isEqualTo("70 cl");
        assertThat(lineas.get(1).bruto()).isEqualByComparingTo("3");
    }

    @Test
    void refresco_seleccionado_una_linea_por_marca_siempre_en_botella() {
        var d = sanMiguel(List.of(
                p(2, true, null, "Coca-Cola Zero", Alternativa.NADA),
                p(1, true, null, "Coca-Cola Zero", Alternativa.NADA),
                p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.REFRESCO_SELECCIONADO, 1, null, CategoriaInventario.REFRESCOS, "", "botella");
        List<LineaCalculada> lineas = CalculadoraListaCompra.lineasDe(r, d);
        assertThat(lineas).extracting(LineaCalculada::nombre).containsExactly("Coca-Cola Zero", "Fanta");
        assertThat(lineas.get(0).bruto()).isEqualByComparingTo("3");
        assertThat(lineas.get(0).tamano()).isEqualTo("botella");
    }

    @Test
    void evento_sin_ficha_las_reglas_de_bebida_dan_cero_y_marcan_necesitaFicha() {
        var d = new DatosEvento(3, 1, false,
                List.of(p(1, false, null, null, null), p(1, false, null, null, null), p(1, false, null, null, null)));
        var cerveza = regla(TipoFormulaCompra.CERVEZA_ALTERNATIVA, 5, null, CategoriaInventario.CERVEZA, "Cerveza", "lata");
        var alcohol = regla(TipoFormulaCompra.ALCOHOL_SELECCIONADO, 0.5, null, CategoriaInventario.ALCOHOL, "", "70 cl");
        var lc = CalculadoraListaCompra.lineasDe(cerveza, d).get(0);
        assertThat(lc.bruto()).isEqualByComparingTo("0");
        assertThat(lc.necesitaFicha()).isTrue();
        var la = CalculadoraListaCompra.lineasDe(alcohol, d).get(0);
        assertThat(la.bruto()).isEqualByComparingTo("0");
        assertThat(la.necesitaFicha()).isTrue();
        assertThat(la.nombre()).isEqualTo("Alcohol");
    }

    @Test
    void ceil_redondea_hacia_arriba() {
        assertThat(CalculadoraListaCompra.ceil(new BigDecimal("3.01"))).isEqualByComparingTo("4");
        assertThat(CalculadoraListaCompra.ceil(new BigDecimal("3.00"))).isEqualByComparingTo("3");
    }
}
