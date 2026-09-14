package com.baniterio.api.identidad;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración de {@link BebidaRepository} y {@link FichaBebidaRepository}
 * contra Postgres real: el seed del catálogo, el flag de la cuenta y la relación
 * 1:1 {@code @MapsId} de la ficha con la asistencia.
 */
class BebidaRepositoryIT extends IntegrationTest {

    @Autowired
    BebidaRepository bebidas;

    @Autowired
    FichaBebidaRepository fichas;

    @Autowired
    AsistenciaEventoRepository asistencias;

    @Autowired
    EventoRepository eventos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    PenaRepository penas;

    @AfterEach
    void limpiar() {
        fichas.deleteAllInBatch();
        asistencias.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-ficha-repo-")).toList());
        bebidas.deleteAll(bebidas.findAll().stream()
                .filter(b -> b.getNombre().startsWith("IT-")).toList());
    }

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    @Test
    void el_seed_dejo_el_catalogo_aceptado_y_ordenado() {
        var alcohol = bebidas.findByTipoAndEstadoOrderByNombreAsc(TipoBebida.ALCOHOL, EstadoBebida.ACEPTADA);
        var refresco = bebidas.findByTipoAndEstadoOrderByNombreAsc(TipoBebida.REFRESCO, EstadoBebida.ACEPTADA);
        assertThat(alcohol).hasSize(18);
        assertThat(refresco).hasSize(13);
        assertThat(alcohol.get(0).getNombre()).isEqualTo("Absolut");
    }

    @Test
    void busca_por_nombre_sin_distinguir_mayusculas() {
        assertThat(bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.ALCOHOL, "barceló"))
                .get().extracting(Bebida::getNombre).isEqualTo("Barceló");
    }

    @Test
    void las_pendientes_salen_por_orden_de_creacion() {
        bebidas.save(Bebida.builder().tipo(TipoBebida.ALCOHOL).nombre("IT-ron-abuelo")
                .estado(EstadoBebida.PENDIENTE).build());
        assertThat(bebidas.findByEstadoOrderByCreatedAtAsc(EstadoBebida.PENDIENTE))
                .extracting(Bebida::getNombre).contains("IT-ron-abuelo");
    }

    @Test
    void solo_san_miguel_lleva_ficha() {
        assertThat(cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow()
                .isLlevaFichaBebida()).isTrue();
        assertThat(cuentas.findByPenaIdAndNombre(pena().getId(), "Chuletas Santas").orElseThrow()
                .isLlevaFichaBebida()).isFalse();
    }

    @Test
    void la_ficha_es_uno_a_uno_con_la_asistencia() {
        Cuenta sanMiguel = cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow();
        Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(sanMiguel)
                .nombre("IT-ficha-repo-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).build());
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(e).nombre("Invitado").estado(EstadoAsistencia.APUNTADO).build());
        Bebida refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, "Coca-Cola").orElseThrow();

        fichas.save(FichaBebida.builder()
                .asistencia(a).refresco(refresco).alternativa(Alternativa.NADA)
                .asisteDia1(true).asisteDia2(true).modalidad(Modalidad.COMPLETA).build());

        assertThat(fichas.findByAsistenciaId(a.getId())).isPresent();
        assertThat(fichas.findByEventoId(e.getId())).hasSize(1);
    }
}
