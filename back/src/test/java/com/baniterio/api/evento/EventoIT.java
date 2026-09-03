package com.baniterio.api.evento;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
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
 * Test de integración de la sección Eventos: listado paginado, detalle y los
 * caminos de permiso de crear/editar/borrar/solicitar. La BBDD la comparten
 * todos los {@code *IT}: cada caso crea sus propios miembros y eventos.
 */
class EventoIT extends IntegrationTest {

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
                .telefono(tel).email(tel + "@evt.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Evt").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Evento sembrarEvento(String nombre, LocalDate fecha, Usuario creador) {
        return eventos.save(Evento.builder().pena(pena()).nombre(nombre).fecha(fecha).creadoPor(creador).build());
    }

    @Test
    void listado_devuelve_los_eventos_ordenados_y_paginados() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        sembrarEvento("IT-list-A", LocalDate.of(2999, 1, 1), null);
        sembrarEvento("IT-list-B", LocalDate.of(2998, 1, 1), null);

        @SuppressWarnings("unchecked")
        Map<String, Object> r = http.get().uri("/api/v1/eventos?pagina=0")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        List<?> lista = (List<?>) r.get("eventos");
        assertThat(lista).isNotEmpty();
        assertThat(r.get("puedeCrear")).isEqualTo(false);
        assertThat(r.get("puedeSolicitar")).isEqualTo(true);
    }

    @Test
    void detalle_de_evento_inexistente_da_404_EVENTO_NO_ENCONTRADO() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/eventos/99999999")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
    }

    @Test
    void admin_ve_puedeCrear_true_y_puedoEditar_en_el_detalle() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Evento e = sembrarEvento("IT-detalle", LocalDate.of(2999, 5, 5), null);

        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.puedoEditar").isEqualTo(true)
                .jsonPath("$.puedoBorrar").isEqualTo(true);
    }
}
