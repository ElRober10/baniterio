package com.baniterio.api.inventario;

import java.util.List;

import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** La siembra V35 del inventario existe y se lista ordenada por categoría/orden. */
class ArticuloInventarioRepositoryIT extends IntegrationTest {

    @Autowired
    ArticuloInventarioRepository articulos;

    @Autowired
    PenaRepository penas;

    Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    @Test
    void la_siembra_incluye_las_cuatro_categorias_con_datos() {
        List<ArticuloInventario> lista =
                articulos.findByPenaIdOrderByCategoriaAscNombreAsc(penaId());

        assertThat(lista).extracting(ArticuloInventario::getCategoria)
                .contains(CategoriaInventario.ALCOHOL, CategoriaInventario.CERVEZA,
                        CategoriaInventario.LIMPIEZA, CategoriaInventario.REFRESCOS);
        assertThat(lista).filteredOn(a -> a.getCategoria() == CategoriaInventario.CERVEZA)
                .anyMatch(a -> a.getNombre().equals("Mahou Clásica")
                        && a.getCantidad().intValue() == 192
                        && a.getTamano().equals("lata"));
    }

    @Test
    void cada_articulo_tiene_un_tamano_valido_para_su_categoria() {
        List<ArticuloInventario> lista =
                articulos.findByPenaIdOrderByCategoriaAscNombreAsc(penaId());

        assertThat(lista).isNotEmpty();
        assertThat(lista).allMatch(a -> a.getCategoria().permiteTamano(a.getTamano()));
    }
}
