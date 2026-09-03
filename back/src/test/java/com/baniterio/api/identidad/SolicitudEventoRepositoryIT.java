package com.baniterio.api.identidad;

import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración de {@link SolicitudEventoRepository} contra Postgres real:
 * el índice único parcial {@code uk_solicitud_evento_crear_pendiente} de V14
 * impide dos solicitudes CREAR pendientes del mismo miembro, y el finder del
 * "crédito" encuentra una solicitud CREAR aprobada sin consumir.
 */
class SolicitudEventoRepositoryIT extends IntegrationTest {

    @Autowired
    SolicitudEventoRepository solicitudes;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PenaRepository penas;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    private Usuario nuevoUsuario() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        return usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sol-evt.test")
                .passwordHash(passwordEncoder.encode("x"))
                .nombre("Sol").apellidos("Evento").esSuperadmin(false).activo(true).build());
    }

    @Test
    void bloquea_dos_solicitudes_crear_pendientes_del_mismo_miembro() {
        Usuario u = nuevoUsuario();
        solicitudes.saveAndFlush(SolicitudEvento.builder()
                .pena(pena()).solicitante(u).tipo(TipoSolicitudEvento.CREAR)
                .estado(EstadoSolicitud.PENDIENTE).build());

        SolicitudEvento segunda = SolicitudEvento.builder()
                .pena(pena()).solicitante(u).tipo(TipoSolicitudEvento.CREAR)
                .estado(EstadoSolicitud.PENDIENTE).build();

        assertThatThrownBy(() -> solicitudes.saveAndFlush(segunda)).isNotNull();
    }

    @Test
    void encuentra_el_credito_aprobado_sin_consumir() {
        Usuario u = nuevoUsuario();
        solicitudes.saveAndFlush(SolicitudEvento.builder()
                .pena(pena()).solicitante(u).tipo(TipoSolicitudEvento.CREAR)
                .estado(EstadoSolicitud.APROBADA).build());

        assertThat(solicitudes.findFirstBySolicitanteIdAndTipoAndEstadoAndEventoIsNullOrderByIdAsc(
                u.getId(), TipoSolicitudEvento.CREAR, EstadoSolicitud.APROBADA)).isPresent();
    }
}
