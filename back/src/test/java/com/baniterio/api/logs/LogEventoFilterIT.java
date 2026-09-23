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

/** IT del filtro que registra toda petición de escritura de la API (V63). */
class LogEventoFilterIT extends IntegrationTest {

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
                .telefono(tel).email(tel + "@log.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lg").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void unaPeticionDeEscrituraQueSaleBienDejaFilaConElUsuarioYElEstado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        String nombreTienda = "IT-log-tienda-" + ThreadLocalRandom.current().nextInt(1_000_000);

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", nombreTienda))
                .exchange().expectStatus().isEqualTo(201);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "/api/v1/precio-bebida/tiendas".equals(l.getRuta()))
                .toList();
        assertThat(filas).hasSize(1);
        LogEvento fila = filas.get(0);
        assertThat(fila.getUsuario().getId()).isEqualTo(admin.id());
        assertThat(fila.getMetodo()).isEqualTo("POST");
        assertThat(fila.getEstado()).isEqualTo(201);
        assertThat(fila.getCodigoError()).isNull();
        assertThat(fila.getOrigen()).isEqualTo(OrigenLog.BACKEND);
    }

    @Test
    void unaPeticionRechazadaDejaFilaConElCodigoDeError() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of("nombre", "IT-log-rechazo"))
                .exchange().expectStatus().isEqualTo(403);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "/api/v1/precio-bebida/tiendas".equals(l.getRuta())
                        && miembro.id().equals(l.getUsuario().getId()))
                .toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getEstado()).isEqualTo(403);
        assertThat(filas.get(0).getCodigoError()).isEqualTo("SIN_PERMISO");
    }

    @Test
    void unaPeticionDeSoloLecturaNoDejaFila() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.get().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk();

        assertThat(logs.findAll().stream().filter(l -> "/api/v1/precio-bebida/tiendas".equals(l.getRuta())))
                .isEmpty();
    }
}
