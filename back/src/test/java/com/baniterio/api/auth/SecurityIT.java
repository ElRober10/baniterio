package com.baniterio.api.auth;

import java.util.UUID;

import com.baniterio.api.auth.dto.UsuarioResponse;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    JwtService jwt;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PenaRepository penas;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void yo_sin_token_devuelve_401() {
        http.get().uri("/api/v1/auth/yo")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void yo_con_token_valido_devuelve_el_usuario() {
        penas.save(Pena.builder()
            .nombre("Test")
            .slug("test-" + UUID.randomUUID())
            .activa(true)
            .build());

        Usuario u = usuarios.save(Usuario.builder()
            .telefono("6" + (10_000_000 + (int) (Math.random() * 80_000_000)))
            .email(UUID.randomUUID() + "@test.com")
            .passwordHash("x")
            .nombre("Ana")
            .apellidos("Pérez")
            .mote("Anita")
            .activo(true)
            .esSuperadmin(false)
            .build());

        String token = jwt.generar(u.getId(), false);

        UsuarioResponse body = http.get().uri("/api/v1/auth/yo")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isOk()
            .expectBody(UsuarioResponse.class)
            .returnResult().getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(u.getId().toString());
        assertThat(body.nombre()).isEqualTo("Ana");
        assertThat(body.apellidos()).isEqualTo("Pérez");
        assertThat(body.mote()).isEqualTo("Anita");
        assertThat(body.esSuperadmin()).isFalse();
    }
}
