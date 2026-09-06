package com.baniterio.api.evento;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.SolicitudEvento;
import com.baniterio.api.identidad.SolicitudEventoRepository;
import com.baniterio.api.identidad.TipoSolicitudEvento;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración del panel de solicitudes de evento
 * ({@code /api/v1/admin/solicitudes-evento}): solo admins, aprobar CREAR deja un
 * crédito, aprobar BORRAR borra el evento, y una solicitud ya resuelta da 409.
 */
class SolicitudEventoAdminIT extends IntegrationTest {

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
    SolicitudEventoRepository solicitudes;

    @Autowired
    PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    record Sesion(Long id, String token) {
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@sol-adm.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Adm").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Cuenta cuenta() {
        return cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow();
    }

    Evento sembrarEvento(String nombre, LocalDate fecha, Usuario creador) {
        return eventos.save(Evento.builder().pena(pena()).cuenta(cuenta())
                .nombre(nombre).fecha(fecha).creadoPor(creador).build());
    }

    long pedirCredito(Sesion miembro) {
        @SuppressWarnings("unchecked")
        Map<String, Object> r = http.post().uri("/api/v1/eventos/solicitudes")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of())
                .exchange().expectStatus().isCreated()
                .expectBody(Map.class).returnResult().getResponseBody();
        return ((Number) r.get("id")).longValue();
    }

    @Test
    void miembro_normal_no_ve_las_solicitudes_403() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/admin/solicitudes-evento")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void aprobar_CREAR_deja_un_credito_y_el_miembro_ya_puede_crear() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        long solId = pedirCredito(miembro);

        http.post().uri("/api/v1/admin/solicitudes-evento/" + solId + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        http.post().uri("/api/v1/eventos")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of("nombre", "IT-tras-credito", "fecha", "2999-10-01",
                        "cuentaId", cuenta().getId()))
                .exchange().expectStatus().isCreated();
    }

    /**
     * Desde el rediseño de eventos (2026-09-04), nadie puede ya crear una
     * solicitud BORRAR (borrar un evento pasó a ser ocultarlo, directo, solo
     * admin/superadmin — ver {@code EventoService.ocultar}). Este test sigue
     * probando que aprobar una BORRAR ya existente borra de verdad el evento
     * (comportamiento legado, por si queda alguna en BBDD de antes), sembrándola
     * directo por repositorio en vez de por la API, que ya no la crea.
     */
    @Test
    void aprobar_BORRAR_borra_el_evento() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Usuario creador = usuarios.findById(miembro.id()).orElseThrow();
        Evento e = sembrarEvento("IT-borrar-aprobado", LocalDate.of(2999, 10, 2), creador);
        long solId = solicitudes.save(SolicitudEvento.builder()
                .pena(pena())
                .solicitante(creador)
                .tipo(TipoSolicitudEvento.BORRAR)
                .evento(e)
                .estado(EstadoSolicitud.PENDIENTE)
                .build()).getId();

        http.post().uri("/api/v1/admin/solicitudes-evento/" + solId + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(eventos.findById(e.getId())).isEmpty();
    }

    @Test
    void rechazar_una_solicitud_ya_resuelta_da_409() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        long solId = pedirCredito(miembro);
        http.post().uri("/api/v1/admin/solicitudes-evento/" + solId + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        http.post().uri("/api/v1/admin/solicitudes-evento/" + solId + "/rechazar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("motivo", "tarde"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("SOLICITUD_EVENTO_YA_RESUELTA");
    }
}
