package com.baniterio.api.compra;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.baniterio.api.compra.OptimizadorPrecioBebida.ItemCompra;
import com.baniterio.api.compra.OptimizadorPrecioBebida.OpcionPrecio;
import org.junit.jupiter.api.Test;

class OptimizadorPrecioBebidaTest {

    private static OpcionPrecio opcion(String tamano, int cl, String tienda, double precio) {
        return new OpcionPrecio(tamano, cl, tienda, BigDecimal.valueOf(precio));
    }

    @Test
    void parseCl_reconoce_litros_y_centilitros() {
        assertThat(OptimizadorPrecioBebida.parseCl("70 cl")).contains(70);
        assertThat(OptimizadorPrecioBebida.parseCl("1 L")).contains(100);
        assertThat(OptimizadorPrecioBebida.parseCl("1.75 L")).contains(175);
        assertThat(OptimizadorPrecioBebida.parseCl("1,75L")).contains(175);
        assertThat(OptimizadorPrecioBebida.parseCl("1.75")).contains(175);
        assertThat(OptimizadorPrecioBebida.parseCl("2L")).contains(200);
        assertThat(OptimizadorPrecioBebida.parseCl("1.5L")).contains(150);
        assertThat(OptimizadorPrecioBebida.parseCl("3 litros")).isEmpty();
        assertThat(OptimizadorPrecioBebida.parseCl(null)).isEmpty();
    }

    @Test
    void sin_opciones_usables_devuelve_vacio() {
        assertThat(OptimizadorPrecioBebida.combinacionMasBarata(140, List.of())).isEmpty();
    }

    @Test
    void nada_que_comprar_si_el_restante_es_cero_o_negativo() {
        List<OpcionPrecio> opciones = List.of(opcion("70 cl", 70, "Mercadona", 10));
        assertThat(OptimizadorPrecioBebida.combinacionMasBarata(0, opciones)).contains(List.of());
    }

    @Test
    void ejemplo_del_barcelo_elige_1_75L_mas_70cl() {
        // 3 personas -> 210 cl. 70cl=10€, 1L=15€, 1.75L=18€ (mismo súper).
        // Más barato: 1.75L (175cl) + 70cl (70cl) = 245cl por 28€, frente a
        // 3x70cl=39€, 1L+1L+70cl(=270cl)=40€, etc.
        List<OpcionPrecio> opciones = List.of(
                opcion("70 cl", 70, "Mercadona", 10),
                opcion("1 L", 100, "Mercadona", 15),
                opcion("1.75 L", 175, "Mercadona", 18));

        Optional<List<ItemCompra>> resultado = OptimizadorPrecioBebida.combinacionMasBarata(210, opciones);

        assertThat(resultado).isPresent();
        assertThat(resultado.get()).containsExactlyInAnyOrder(
                new ItemCompra("1.75 L", "Mercadona", 1, BigDecimal.valueOf(18.0)),
                new ItemCompra("70 cl", "Mercadona", 1, BigDecimal.valueOf(10.0)));
    }

    @Test
    void mezcla_tiendas_si_sale_mas_barato() {
        // 70cl a 10€ en Mercadona pero a 6€ en Carrefour: para 70cl debe elegir Carrefour.
        List<OpcionPrecio> opciones = List.of(
                opcion("70 cl", 70, "Mercadona", 10),
                opcion("70 cl", 70, "Carrefour", 6),
                opcion("1.75 L", 175, "Mercadona", 18));

        Optional<List<ItemCompra>> resultado = OptimizadorPrecioBebida.combinacionMasBarata(70, opciones);

        assertThat(resultado).contains(List.of(new ItemCompra("70 cl", "Carrefour", 1, BigDecimal.valueOf(6.0))));
    }

    @Test
    void una_sola_persona_elige_la_botella_mas_barata_que_llegue_al_objetivo() {
        List<OpcionPrecio> opciones = List.of(
                opcion("70 cl", 70, "Mercadona", 10),
                opcion("1 L", 100, "Mercadona", 15));

        Optional<List<ItemCompra>> resultado = OptimizadorPrecioBebida.combinacionMasBarata(70, opciones);

        assertThat(resultado).contains(List.of(new ItemCompra("70 cl", "Mercadona", 1, BigDecimal.valueOf(10.0))));
    }

    @Test
    void barcelo_de_siete_personas_490_cl_dos_de_1_75_y_dos_de_70() {
        // Precios reales de San Miguel 2026 (tienda más barata de cada tamaño).
        List<OpcionPrecio> opciones = List.of(
                opcion("70 cl", 70, "Alcampo", 14.99),
                opcion("1 L", 100, "Makro", 21.18),
                opcion("1.75", 175, "Amazon", 33.30));

        Optional<List<ItemCompra>> resultado = OptimizadorPrecioBebida.combinacionMasBarata(490, opciones);

        // 2 x 1,75 L + 2 x 70 cl = 490 cl exactos por 96,58 €; 7 x 70 cl cuesta 104,93 y 5 x 1 L 105,90.
        assertThat(resultado).isPresent();
        assertThat(resultado.get()).containsExactlyInAnyOrder(
                new ItemCompra("1.75", "Amazon", 2, BigDecimal.valueOf(33.30)),
                new ItemCompra("70 cl", "Alcampo", 2, BigDecimal.valueOf(14.99)));
    }

    @Test
    void no_se_pasa_mas_de_40_cl_si_hay_una_combinacion_dentro_del_margen() {
        // Objetivo 100 cl: la de 175 cl (barata) se pasa 75 cl; 2 x 70 = 140 cl se pasa 40 cl (justo el margen).
        List<OpcionPrecio> opciones = List.of(
                opcion("70 cl", 70, "A", 10),
                opcion("1.75", 175, "A", 12));

        Optional<List<ItemCompra>> resultado = OptimizadorPrecioBebida.combinacionMasBarata(100, opciones);

        assertThat(resultado).contains(List.of(new ItemCompra("70 cl", "A", 2, BigDecimal.valueOf(10.0))));
    }

    @Test
    void si_nada_cabe_en_el_margen_se_pasa_lo_minimo_necesario() {
        List<OpcionPrecio> opciones = List.of(opcion("1.75", 175, "A", 12));

        Optional<List<ItemCompra>> resultado = OptimizadorPrecioBebida.combinacionMasBarata(100, opciones);

        assertThat(resultado).contains(List.of(new ItemCompra("1.75", "A", 1, BigDecimal.valueOf(12.0))));
    }
}
