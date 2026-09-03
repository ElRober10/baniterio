package com.baniterio.api.identidad;

import java.time.LocalDate;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración de {@link EventoRepository} contra un Postgres real: que
 * la página vuelve en el orden pedido (fecha DESC, id DESC) y que la regla
 * {@code ck_evento_fecha_fin} de V13 rechaza una fecha de fin anterior a la de
 * inicio. La BBDD la comparten todos los {@code *IT}: cada caso trabaja con
 * eventos que crea él y comprueba el orden relativo de ese subconjunto.
 */
class EventoRepositoryIT extends IntegrationTest {

    @Autowired
    EventoRepository eventos;

    @Autowired
    PenaRepository penas;

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    private Evento crear(String nombre, LocalDate fecha) {
        return eventos.save(Evento.builder().pena(pena()).nombre(nombre).fecha(fecha).build());
    }

    @Test
    void pagina_ordenada_por_fecha_desc_luego_id_desc() {
        Evento viejo = crear("IT-viejo", LocalDate.of(2000, 1, 1));
        Evento nuevoA = crear("IT-nuevoA", LocalDate.of(2999, 1, 1));
        Evento nuevoB = crear("IT-nuevoB", LocalDate.of(2999, 1, 1));

        var pagina = eventos.findByPenaId(pena().getId(),
                PageRequest.of(0, 50, Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));

        var ids = pagina.getContent().stream().map(Evento::getId).toList();
        assertThat(ids).containsSubsequence(nuevoB.getId(), nuevoA.getId(), viejo.getId());
    }

    @Test
    void ck_fecha_fin_rechaza_fin_anterior_al_inicio() {
        Evento e = Evento.builder().pena(pena()).nombre("IT-malas-fechas")
                .fecha(LocalDate.of(2027, 3, 27)).fechaFin(LocalDate.of(2027, 3, 26)).build();
        assertThatThrownBy(() -> eventos.saveAndFlush(e)).isNotNull();
    }

    @Test
    void la_siembra_v15_deja_los_tres_eventos_ordenados() {
        var pagina = eventos.findByPenaId(pena().getId(),
                PageRequest.of(0, 100, Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));
        var nombres = pagina.getContent().stream().map(Evento::getNombre).toList();

        assertThat(nombres).contains(
                "Fiestas de San Miguel 2026", "Chuletas Santas 2027", "Migas Santas 2027");
        // Migas (27/03/2027) va antes que Chuletas (26/03/2027), y ambas antes que San Miguel (25/09/2026).
        assertThat(nombres).containsSubsequence(
                "Migas Santas 2027", "Chuletas Santas 2027", "Fiestas de San Miguel 2026");
    }
}
