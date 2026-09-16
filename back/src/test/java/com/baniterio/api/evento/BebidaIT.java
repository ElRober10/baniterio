package com.baniterio.api.evento;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Bebida;
import com.baniterio.api.identidad.BebidaRepository;
import com.baniterio.api.identidad.EstadoBebida;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TipoBebida;
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

/** IT del catálogo de bebidas y su aprobación por admin (pieza 3b). */
class BebidaIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    BebidaRepository bebidas;

    @Autowired
    PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        bebidas.deleteAll(bebidas.findAll().stream()
                .filter(b -> b.getNombre().startsWith("IT-")).toList());
    }

    private Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    private record Sesion(Long id, String token) {
    }

    private Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@beb.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Beb").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    private Bebida pendiente(String nombre) {
        return bebidas.save(Bebida.builder().tipo(TipoBebida.ALCOHOL).nombre(nombre)
                .estado(EstadoBebida.PENDIENTE).build());
    }

    @Test
    void catalogo_trae_solo_aceptadas_ordenadas() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        pendiente("IT-ron-oculto");

        http.get().uri("/api/v1/bebidas/catalogo")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.alcohol.length()").isEqualTo(17)
                .jsonPath("$.alcohol[0].nombre").isEqualTo("Absolut")
                .jsonPath("$.refresco.length()").isEqualTo(14);
    }

    @Test
    void pendientes_sin_ser_admin_403() {
        Sesion s = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/bebidas?estado=PENDIENTE")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }

    @Test
    void admin_acepta_una_bebida_y_pasa_a_salir_en_el_catalogo() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Bebida b = pendiente("IT-ron-del-abuelo");

        http.post().uri("/api/v1/bebidas/" + b.getId() + "/aceptar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(bebidas.findById(b.getId()).orElseThrow().getEstado()).isEqualTo(EstadoBebida.ACEPTADA);
        http.get().uri("/api/v1/bebidas/catalogo")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.alcohol[?(@.nombre == 'IT-ron-del-abuelo')].id").exists();
    }

    @Test
    void aceptar_inexistente_404() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.post().uri("/api/v1/bebidas/99999999/aceptar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("BEBIDA_NO_ENCONTRADA");
    }

    @Test
    void admin_rechaza_una_bebida() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Bebida b = pendiente("IT-priorat-raro");

        http.post().uri("/api/v1/bebidas/" + b.getId() + "/rechazar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(bebidas.findById(b.getId()).orElseThrow().getEstado()).isEqualTo(EstadoBebida.RECHAZADA);
    }
}
