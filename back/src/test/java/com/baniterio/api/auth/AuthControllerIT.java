package com.baniterio.api.auth;

import java.util.Map;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends IntegrationTest {

    @LocalServerPort
    int port;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // 600000001 (fundador) y 600000002 / 600010016 están sembrados por V6.
    private Map<String, String> registroValido(String telefono, String email) {
        return Map.of("telefono", telefono, "email", email, "password", "secreto1",
                "nombre", "Ana", "apellidos", "López", "mote", "");
    }

    @Test
    void registro_con_telefono_autorizado_crea_el_usuario() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600000002", "ana@baniterio.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body).containsKey("id");
        assertThat(body.get("esSuperadmin")).isEqualTo(false);
        assertThat(body.get("nombre")).isEqualTo("Ana");
    }

    @Test
    void el_fundador_queda_como_superadmin() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600000001", "fundador@baniterio.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("esSuperadmin")).isEqualTo(true);
    }

    @Test
    void registro_con_telefono_no_autorizado_devuelve_403_con_codigo() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600000000", "x@x.com"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("TELEFONO_NO_AUTORIZADO");
    }

    @Test
    void no_se_puede_registrar_dos_veces_el_mismo_telefono() {
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010016", "primero@x.com"))
                .exchange()
                .expectStatus().isCreated();

        // el teléfono queda 'usado' → el segundo intento cae como no autorizado
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010016", "segundo@x.com"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void mismo_email_con_otro_telefono_autorizado_devuelve_409() {
        // Teléfono A (600010040) con email X → 201
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010040", "duplicado@x.com"))
                .exchange()
                .expectStatus().isCreated();

        // Teléfono B (600010038, aún sin usar) con el MISMO email X → pasa la puerta 403,
        // pero existsByEmail(X) es true → 409 YA_REGISTRADO
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010038", "duplicado@x.com"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("YA_REGISTRADO");
    }

    @Test
    void registro_con_telefono_mal_formado_devuelve_400() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido("12345", "x@x.com"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("VALIDACION");
        assertThat(body).containsKey("errores");
    }

    // --- Login ---
    // Teléfonos usados solo aquí (sembrados por V6, no consumidos por otros tests):
    // 600010009, 600010043, 600010047. 700000000 no está sembrado.

    @Test
    void login_correcto_devuelve_token_y_usuario() {
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010009", "login-ok@x.com"))
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", "600010009", "password", "secreto1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat((String) body.get("token")).isNotBlank();
        @SuppressWarnings("unchecked")
        Map<String, Object> usuario = (Map<String, Object>) body.get("usuario");
        assertThat(usuario).containsKey("id");
    }

    @Test
    void login_con_password_incorrecta_devuelve_401() {
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010043", "login-bad@x.com"))
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", "600010043", "password", "otra-cosa"))
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test
    void login_con_telefono_desconocido_devuelve_401() {
        http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", "700000000", "password", "loquesea"))
                .exchange()
                .expectStatus().isEqualTo(401);
    }

    @Test
    void el_token_del_login_vale_para_yo() {
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido("600010047", "yo@x.com"))
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        Map<String, Object> login = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", "600010047", "password", "secreto1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        String token = (String) login.get("token");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/auth/yo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("nombre")).isEqualTo("Ana");
    }
}
