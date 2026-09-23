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

class LogClienteIT extends IntegrationTest {

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

    Sesion crearMiembro() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@logcliente.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lc").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(RolMembresia.MIEMBRO).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void guardaUnLogDeClienteSinSesion() {
        http.post().uri("/api/v1/logs/cliente")
                .body(Map.of("origen", "MOBILE", "pantalla", "cuentas.crearMovimiento", "mensaje", "IOException"))
                .exchange().expectStatus().isEqualTo(202);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "cuentas.crearMovimiento".equals(l.getRuta())).toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getOrigen()).isEqualTo(OrigenLog.MOBILE);
        assertThat(filas.get(0).getUsuario()).isNull();
        assertThat(filas.get(0).getEstado()).isNull();
        assertThat(filas.get(0).getMensaje()).isEqualTo("IOException");
    }

    @Test
    void guardaUnLogDeClienteConElUsuarioSiHaySesion() {
        Sesion s = crearMiembro();
        http.post().uri("/api/v1/logs/cliente")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("origen", "WEB", "pantalla", "cuentas.crearMovimiento", "mensaje", "Error de red"))
                .exchange().expectStatus().isEqualTo(202);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "cuentas.crearMovimiento".equals(l.getRuta()) && l.getOrigen() == OrigenLog.WEB)
                .toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getUsuario().getId()).isEqualTo(s.id());
    }

    @Test
    void laPropiaPeticionDeLogsNoGeneraOtroLogDeAccion() {
        http.post().uri("/api/v1/logs/cliente")
                .body(Map.of("origen", "MOBILE", "pantalla", "x", "mensaje", "y"))
                .exchange().expectStatus().isEqualTo(202);

        assertThat(logs.findAll().stream().filter(l -> l.getOrigen() == OrigenLog.BACKEND)).isEmpty();
    }
}
