package com.baniterio.api.evento;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AsistenciaEventoRepository;
import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoAsistencia;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.NotificacionEvento;
import com.baniterio.api.identidad.NotificacionEventoRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración de la pieza 3a (asistencia a eventos): responder Me apunto
 * / No voy / En duda, mandar/reenviar notificación, añadir a mano y pendientes de
 * respuesta. Helpers al estilo de {@code EventoIT}.
 */
class AsistenciaIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    EventoRepository eventos;

    @Autowired
    CuentaRepository cuentas;

    @Autowired
    AsistenciaEventoRepository asistencias;

    @Autowired
    NotificacionEventoRepository notificaciones;

    @Autowired
    PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /**
     * La BBDD la comparten todos los {@code *IT}. Esta suite crea bastantes
     * eventos como fixtures y {@code EventoIT} asume que hay pocos (el listado
     * pagina de 8). Se limpian al terminar cada caso.
     */
    @AfterEach
    void limpiar() {
        asistencias.deleteAllInBatch();
        notificaciones.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-asis")).toList());
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Cuenta cuenta() {
        return cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow();
    }

    record Sesion(Long id, String token) {
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@asis.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Asis").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Evento sembrarEvento(String nombre, LocalDate fecha, Usuario creador) {
        return eventos.save(Evento.builder().pena(pena()).cuenta(cuenta())
                .nombre(nombre).fecha(fecha).creadoPor(creador).build());
    }

    @Test
    void responder_crea_la_fila_APUNTADO() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-asis-apunto", LocalDate.now().plusDays(30), null);

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("estado", "APUNTADO"))
                .exchange().expectStatus().isOk();

        assertThat(asistencias.findByEventoIdAndUsuarioId(e.getId(), s.id()))
                .get().extracting(a -> a.getEstado()).isEqualTo(EstadoAsistencia.APUNTADO);
    }

    @Test
    void responder_de_nuevo_actualiza_el_estado_sin_duplicar() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-asis-cambio", LocalDate.now().plusDays(30), null);

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("estado", "APUNTADO")).exchange().expectStatus().isOk();
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("estado", "EN_DUDA")).exchange().expectStatus().isOk();

        assertThat(asistencias.findByEventoId(e.getId())).hasSize(1);
        assertThat(asistencias.findByEventoIdAndUsuarioId(e.getId(), s.id()))
                .get().extracting(a -> a.getEstado()).isEqualTo(EstadoAsistencia.EN_DUDA);
    }

    @Test
    void responder_evento_pasado_es_409_EVENTO_YA_PASADO() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-asis-pasado", LocalDate.now().minusDays(1), null);

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("estado", "APUNTADO"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_YA_PASADO");
    }

    @Test
    void responder_evento_inexistente_404() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.put().uri("/api/v1/eventos/99999999/asistencia")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("estado", "APUNTADO"))
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
    }

    @Test
    void admin_manda_notificacion_primera_vez_204_y_la_registra() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-asis-notif", LocalDate.now().plusDays(30), null);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("texto", "¡Nos vemos en San Miguel!"))
                .exchange().expectStatus().isNoContent();

        assertThat(notificaciones.existsByEventoId(e.getId())).isTrue();
    }

    @Test
    void el_organizador_no_admin_tambien_puede_mandar_notificacion() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Usuario creador = usuarios.findById(miembro.id()).orElseThrow();
        Evento e = sembrarEvento("IT-asis-notif-org", LocalDate.now().plusDays(30), creador);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of())
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void mandar_notificacion_sin_ser_admin_ni_organizador_403() {
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-asis-notif-403", LocalDate.now().plusDays(30), null);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void reenviar_antes_de_48h_es_409_NOTIFICACION_REENVIO_PRONTO() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Usuario u = usuarios.findById(admin.id()).orElseThrow();
        Evento e = sembrarEvento("IT-asis-reenvio-pronto", LocalDate.now().plusDays(30), null);
        notificaciones.save(NotificacionEvento.builder().evento(e).enviadaPor(u).build());

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("NOTIFICACION_REENVIO_PRONTO");
    }

    @Test
    void reenviar_pasadas_48h_204() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Usuario u = usuarios.findById(admin.id()).orElseThrow();
        Evento e = sembrarEvento("IT-asis-reenvio-ok", LocalDate.now().plusDays(30), null);
        NotificacionEvento vieja = notificaciones.save(
                NotificacionEvento.builder().evento(e).enviadaPor(u).build());
        vieja.setEnviadaAt(Instant.now().minus(49, ChronoUnit.HOURS));
        notificaciones.save(vieja);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/notificacion")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of())
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void anadir_a_mano_crea_fila_sin_usuario() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-asis-add", LocalDate.now().plusDays(30), null);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "Primo de Juan", "estado", "APUNTADO"))
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.nombre").isEqualTo("Primo de Juan")
                .jsonPath("$.esManual").isEqualTo(true)
                .jsonPath("$.estado").isEqualTo("APUNTADO");

        assertThat(asistencias.findByEventoId(e.getId()))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getUsuario()).isNull();
                    assertThat(a.getRegistradoPor()).isNotNull();
                    assertThat(a.getNombre()).isEqualTo("Primo de Juan");
                });
    }

    @Test
    void anadir_a_mano_sin_permiso_403() {
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-asis-add-403", LocalDate.now().plusDays(30), null);

        http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of("nombre", "Alguien", "estado", "APUNTADO"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void quitar_a_mano_ok_204() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Usuario u = usuarios.findById(admin.id()).orElseThrow();
        Evento e = sembrarEvento("IT-asis-del", LocalDate.now().plusDays(30), null);
        var fila = asistencias.save(com.baniterio.api.identidad.AsistenciaEvento.builder()
                .evento(e).nombre("Invitado").estado(EstadoAsistencia.APUNTADO).registradoPor(u).build());

        http.delete().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + fila.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(asistencias.findById(fila.getId())).isEmpty();
    }

    @Test
    void quitar_la_respuesta_de_un_usuario_es_409_ASISTENCIA_NO_MANUAL() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sembrarEvento("IT-asis-del-409", LocalDate.now().plusDays(30), null);
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of("estado", "APUNTADO")).exchange().expectStatus().isOk();
        Long filaId = asistencias.findByEventoIdAndUsuarioId(e.getId(), miembro.id()).orElseThrow().getId();

        http.delete().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + filaId)
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("ASISTENCIA_NO_MANUAL");
    }

    @Test
    void quitar_inexistente_404() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-asis-del-404", LocalDate.now().plusDays(30), null);

        http.delete().uri("/api/v1/eventos/" + e.getId() + "/asistencias/99999999")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("ASISTENCIA_NO_ENCONTRADA");
    }
}
