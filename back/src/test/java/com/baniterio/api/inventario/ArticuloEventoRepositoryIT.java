package com.baniterio.api.inventario;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ArticuloEventoRepositoryIT extends IntegrationTest {

    @Autowired
    ArticuloEventoRepository articulosEvento;
    @Autowired
    ArticuloInventarioRepository articulos;
    @Autowired
    EventoRepository eventos;
    @Autowired
    PenaRepository penas;

    private Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    private Evento algunEvento() {
        return eventos.findAll().stream()
                .filter(e -> e.getPena().getId().equals(penaId()))
                .findFirst().orElseThrow();
    }

    @Test
    void guarda_y_busca_por_evento_y_articulo_de_origen() {
        Evento ev = algunEvento();
        ArticuloInventario origen = articulos
                .findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId()).get(0);

        ArticuloEvento fila = articulosEvento.save(ArticuloEvento.builder()
                .evento(ev)
                .articuloInventario(origen)
                .categoria(origen.getCategoria())
                .nombre(origen.getNombre())
                .tamano(origen.getTamano())
                .cantidad(new BigDecimal("2.00"))
                .cantidadComprada(BigDecimal.ZERO)
                .orden(1)
                .build());

        assertThat(articulosEvento.findByEventoIdAndArticuloInventarioId(ev.getId(), origen.getId()))
                .get().extracting(ArticuloEvento::getId).isEqualTo(fila.getId());
        assertThat(articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(ev.getId()))
                .extracting(ArticuloEvento::getNombre).contains(origen.getNombre());
    }
}
