package com.baniterio.api.push;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
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

class DispositivoControllerIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DispositivoRepository dispositivos;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private record Sesion(Long usuarioId, String token) {}

    private Sesion nuevaSesion() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@d.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("D").apellidos("T").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder()
                .usuario(u).pena(penas.findBySlug("baniterio").orElseThrow())
                .rol(RolMembresia.MIEMBRO).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void sin_token_jwt_devuelve_401() {
        http.post().uri("/api/v1/dispositivos")
                .body(Map.of("token", "tk", "plataforma", "ANDROID"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void registra_y_luego_da_de_baja_el_propio_dispositivo() {
        Sesion s = nuevaSesion();
        String tk = "tk-" + s.usuarioId();

        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("token", tk, "plataforma", "ANDROID"))
                .exchange().expectStatus().isNoContent();
        assertThat(dispositivos.findByToken(tk)).isPresent();

        http.delete().uri("/api/v1/dispositivos/" + tk).header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isNoContent();
        assertThat(dispositivos.findByToken(tk)).isEmpty();
    }

    @Test
    void un_segundo_registro_del_mismo_token_reasigna_sin_duplicar() {
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        String tk = "compartido-" + a.usuarioId();

        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + a.token())
                .body(Map.of("token", tk, "plataforma", "ANDROID"))
                .exchange().expectStatus().isNoContent();
        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + b.token())
                .body(Map.of("token", tk, "plataforma", "IOS"))
                .exchange().expectStatus().isNoContent();

        assertThat(dispositivos.findByUsuarioIdIn(java.util.List.of(a.usuarioId()))).isEmpty();
        assertThat(dispositivos.findByToken(tk)).get()
                .extracting(d -> d.getUsuario().getId()).isEqualTo(b.usuarioId());
    }

    @Test
    void baja_de_un_token_ajeno_no_lo_borra() {
        Sesion dueno = nuevaSesion();
        Sesion otro = nuevaSesion();
        String tk = "de-" + dueno.usuarioId();
        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + dueno.token())
                .body(Map.of("token", tk, "plataforma", "ANDROID"))
                .exchange().expectStatus().isNoContent();

        http.delete().uri("/api/v1/dispositivos/" + tk).header(AUTHORIZATION, "Bearer " + otro.token())
                .exchange().expectStatus().isNoContent();

        assertThat(dispositivos.findByToken(tk)).isPresent();
    }
}
