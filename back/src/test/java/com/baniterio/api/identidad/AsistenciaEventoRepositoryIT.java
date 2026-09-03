package com.baniterio.api.identidad;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración de {@link AsistenciaEventoRepository} y
 * {@link NotificacionEventoRepository} contra Postgres real: la unicidad parcial
 * de {@code (evento_id, usuario_id)}, los conteos y el "último envío".
 */
class AsistenciaEventoRepositoryIT extends IntegrationTest {

    @Autowired
    AsistenciaEventoRepository asistencias;

    @Autowired
    NotificacionEventoRepository notificaciones;

    @Autowired
    EventoRepository eventos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PenaRepository penas;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    private Evento evento() {
        Cuenta c = cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow();
        return eventos.save(Evento.builder().pena(pena()).cuenta(c)
                .nombre("IT-asis-repo-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).build());
    }

    private Usuario usuario() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        return usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@asis-repo.test").passwordHash(passwordEncoder.encode("x"))
                .nombre("N" + tel).apellidos("Asis").esSuperadmin(false).activo(true).build());
    }

    @Test
    void encuentra_la_respuesta_de_un_usuario_a_un_evento() {
        Evento e = evento();
        Usuario u = usuario();
        asistencias.save(AsistenciaEvento.builder()
                .evento(e).usuario(u).estado(EstadoAsistencia.APUNTADO).build());

        assertThat(asistencias.findByEventoIdAndUsuarioId(e.getId(), u.getId())).isPresent();
        assertThat(asistencias.idsUsuariosConRespuesta(e.getId())).containsExactly(u.getId());
    }

    @Test
    void dos_filas_a_mano_con_el_mismo_nombre_no_chocan() {
        Evento e = evento();
        asistencias.save(AsistenciaEvento.builder()
                .evento(e).nombre("Primo de Juan").estado(EstadoAsistencia.APUNTADO).build());
        asistencias.saveAndFlush(AsistenciaEvento.builder()
                .evento(e).nombre("Primo de Juan").estado(EstadoAsistencia.EN_DUDA).build());

        assertThat(asistencias.findByEventoId(e.getId())).hasSize(2);
    }

    @Test
    void dos_respuestas_del_mismo_usuario_al_mismo_evento_chocan() {
        Evento e = evento();
        Usuario u = usuario();
        asistencias.saveAndFlush(AsistenciaEvento.builder()
                .evento(e).usuario(u).estado(EstadoAsistencia.APUNTADO).build());
        AsistenciaEvento segunda = AsistenciaEvento.builder()
                .evento(e).usuario(u).estado(EstadoAsistencia.NO_VOY).build();

        assertThatThrownBy(() -> asistencias.saveAndFlush(segunda)).isNotNull();
    }

    @Test
    void cuenta_por_estado() {
        Evento e = evento();
        asistencias.save(AsistenciaEvento.builder().evento(e).usuario(usuario())
                .estado(EstadoAsistencia.APUNTADO).build());
        asistencias.save(AsistenciaEvento.builder().evento(e).usuario(usuario())
                .estado(EstadoAsistencia.APUNTADO).build());
        asistencias.save(AsistenciaEvento.builder().evento(e).nombre("X")
                .estado(EstadoAsistencia.EN_DUDA).build());

        assertThat(asistencias.countByEventoIdAndEstado(e.getId(), EstadoAsistencia.APUNTADO)).isEqualTo(2);
        assertThat(asistencias.countByEventoIdAndEstado(e.getId(), EstadoAsistencia.EN_DUDA)).isEqualTo(1);
    }

    @Test
    void el_ultimo_envio_es_el_mas_reciente() {
        Evento e = evento();
        Usuario u = usuario();
        NotificacionEvento vieja = notificaciones.save(NotificacionEvento.builder()
                .evento(e).enviadaPor(u).build());
        // fuerza una fecha anterior para la "vieja"
        vieja.setEnviadaAt(Instant.now().minus(3, ChronoUnit.DAYS));
        notificaciones.save(vieja);
        NotificacionEvento nueva = notificaciones.save(NotificacionEvento.builder()
                .evento(e).enviadaPor(u).texto("recordatorio").build());

        assertThat(notificaciones.existsByEventoId(e.getId())).isTrue();
        assertThat(notificaciones.findFirstByEventoIdOrderByEnviadaAtDesc(e.getId()))
                .get().extracting(NotificacionEvento::getId).isEqualTo(nueva.getId());
    }
}
