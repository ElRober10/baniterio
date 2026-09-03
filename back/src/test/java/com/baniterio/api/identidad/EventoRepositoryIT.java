package com.baniterio.api.identidad;

import java.time.LocalDate;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración de {@link EventoRepository} contra un Postgres real: que
 * {@code listar} devuelve la página con el orden de la sección Eventos (futuros
 * por fecha ascendente, luego pasados por fecha descendente) y que la regla
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
    void futuros_ascendente_luego_pasados_descendente() {
        LocalDate hoy = LocalDate.now();
        LocalDate limite = hoy.minusDays(3);

        // "futuro" incluye lo que ocurrió hace <=3 días (aún no ha "pasado")
        Evento recienteVigente = crear("IT-vigente-2d", hoy.minusDays(2));
        Evento futuroCercano = crear("IT-fut-cercano", hoy.plusDays(5));
        Evento futuroLejano = crear("IT-fut-lejano", hoy.plusYears(2));
        Evento pasadoReciente = crear("IT-pas-reciente", hoy.minusDays(10));
        Evento pasadoViejo = crear("IT-pas-viejo", hoy.minusYears(5));

        var pagina = eventos.listar(pena().getId(), limite, PageRequest.of(0, 100));
        var ids = pagina.getContent().stream().map(Evento::getId).toList();

        assertThat(ids).containsSubsequence(
                recienteVigente.getId(), futuroCercano.getId(), futuroLejano.getId(),
                pasadoReciente.getId(), pasadoViejo.getId());
    }

    @Test
    void ck_fecha_fin_rechaza_fin_anterior_al_inicio() {
        Evento e = Evento.builder().pena(pena()).nombre("IT-malas-fechas")
                .fecha(LocalDate.of(2027, 3, 27)).fechaFin(LocalDate.of(2027, 3, 26)).build();
        assertThatThrownBy(() -> eventos.saveAndFlush(e)).isNotNull();
    }

    @Test
    void la_siembra_v15_queda_ordenada_por_fecha_ascendente() {
        // limite muy antiguo: los tres eventos caen en el grupo "futuro" pase el tiempo que pase,
        // asi el orden es puramente por fecha ascendente y el test no caduca.
        var pagina = eventos.listar(pena().getId(), LocalDate.of(1900, 1, 1), PageRequest.of(0, 100));
        var nombres = pagina.getContent().stream().map(Evento::getNombre).toList();

        assertThat(nombres).contains(
                "Fiestas de San Miguel 2026", "Chuletas Santas 2027", "Migas Santas 2027");
        // San Miguel (25/09/2026) antes que Chuletas (26/03/2027) antes que Migas (27/03/2027).
        assertThat(nombres).containsSubsequence(
                "Fiestas de San Miguel 2026", "Chuletas Santas 2027", "Migas Santas 2027");
    }
}
