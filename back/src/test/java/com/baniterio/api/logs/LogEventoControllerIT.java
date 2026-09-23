package com.baniterio.api.logs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
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

class LogEventoControllerIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LogEventoRepository logs;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        logs.deleteAllInBatch();
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@logctrl.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lc").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void soloAdminPuedeListar() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/logs")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isEqualTo(403);
    }

    @Test
    void sinSesionEs401() {
        http.get().uri("/api/v1/logs").exchange().expectStatus().isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtraPorUsuarioYDevuelvePaginado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-log-ctrl-a-" + ThreadLocalRandom.current().nextInt(1_000_000)))
                .exchange().expectStatus().isEqualTo(201);
        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of("nombre", "x"))
                .exchange().expectStatus().isEqualTo(403);

        Map<String, Object> pagina = http.get().uri("/api/v1/logs?usuarioId=" + otro.id())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        List<Map<String, Object>> contenido = (List<Map<String, Object>>) pagina.get("contenido");
        assertThat(contenido).hasSize(1);
        assertThat(contenido.get(0).get("codigoError")).isEqualTo("SIN_PERMISO");
        assertThat(((Number) pagina.get("total")).longValue()).isEqualTo(1L);
    }

    @Test
    void unOrigenInvalidoOUnaPaginaFueraDeRangoNoRompenLaConsulta() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);

        http.get().uri("/api/v1/logs?origen=no-existe&pagina=-5&tamano=0")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk();
    }
}
