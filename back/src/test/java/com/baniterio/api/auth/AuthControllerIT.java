package com.baniterio.api.auth;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
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

class AuthControllerIT extends IntegrationTest {

    /** Sembrado por V6 y además es `app.identidad.telefono-fundador`. */
    private static final String TELEFONO_FUNDADOR = "616985168";

    @LocalServerPort
    int port;

    @Autowired
    TelefonoAutorizadoRepository telefonos;

    @Autowired
    PenaRepository penas;

    @Autowired
    UsuarioRepository usuarios;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /**
     * Da de alta un teléfono autorizado nuevo (aleatorio, formato `^[67]\d{8}$`) en la peña
     * piloto y lo devuelve. Los tests no dependen de la siembra de V6 ni se pisan entre sí.
     */
    private String telefonoAutorizadoNuevo() {
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (telefonos.existsByTelefonoAndUsadoFalse(numero));

        telefonos.save(TelefonoAutorizado.builder()
                .telefono(numero)
                .pena(pena)
                .usado(false)
                .build());
        return numero;
    }

    private Map<String, String> registroValido(String telefono, String email) {
        return Map.of("telefono", telefono, "email", email, "password", "secreto1",
                "nombre", "Ana", "apellidos", "López", "mote", "");
    }

    @Test
    void registro_con_telefono_autorizado_crea_el_usuario() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefonoAutorizadoNuevo(), "ana@baniterio.com"))
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
                .body(registroValido(TELEFONO_FUNDADOR, "fundador@baniterio.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("esSuperadmin")).isEqualTo(true);
    }

    @Test
    void registro_con_telefono_no_autorizado_devuelve_403_con_codigo() {
        // 600000000 tiene formato válido pero nunca se autoriza.
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
        String telefono = telefonoAutorizadoNuevo();

        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "primero@x.com"))
                .exchange()
                .expectStatus().isCreated();

        // el teléfono queda 'usado' → el segundo intento cae como no autorizado
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "segundo@x.com"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void mismo_email_con_otro_telefono_autorizado_devuelve_409() {
        // Teléfono A con email X → 201
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefonoAutorizadoNuevo(), "duplicado@x.com"))
                .exchange()
                .expectStatus().isCreated();

        // Teléfono B (aún sin usar) con el MISMO email X → pasa la puerta 403,
        // pero existsByEmail(X) es true → 409 YA_REGISTRADO
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefonoAutorizadoNuevo(), "duplicado@x.com"))
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

    @Test
    void registro_con_email_demasiado_largo_devuelve_400() {
        // La columna email es VARCHAR(160): sin @Size esto acababa en 500.
        // Ojo: @Email limita la parte local a 64 caracteres, así que la longitud
        // extra tiene que ir en el dominio para que llegue a la validación de tamaño.
        String emailLargo = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(37) + ".com";
        assertThat(emailLargo).hasSize(170);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefonoAutorizadoNuevo(), emailLargo))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("VALIDACION");
        assertThat(body).containsKey("errores");
    }

    // --- Login ---

    @Test
    void login_correcto_devuelve_token_y_usuario() {
        String telefono = telefonoAutorizadoNuevo();
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "login-ok@x.com"))
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
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
        String telefono = telefonoAutorizadoNuevo();
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "login-bad@x.com"))
                .exchange()
                .expectStatus().isCreated();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "otra-cosa"))
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
        String telefono = telefonoAutorizadoNuevo();
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "yo@x.com"))
                .exchange()
                .expectStatus().isCreated();

        String token = login(telefono);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/auth/yo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("nombre")).isEqualTo("Ana");
    }

    // --- Usuarios desactivados (única palanca de revocación con JWT stateless de 7 días) ---

    @Test
    void un_usuario_desactivado_no_puede_iniciar_sesion() {
        String telefono = telefonoAutorizadoNuevo();
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "baja-login@x.com"))
                .exchange()
                .expectStatus().isCreated();

        desactivar(telefono);

        // Mismo 401 CREDENCIALES_INVALIDAS que una contraseña mala: no se filtra que la cuenta existe.
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test
    void un_token_previo_deja_de_valer_al_desactivar_al_usuario() {
        String telefono = telefonoAutorizadoNuevo();
        http.post().uri("/api/v1/auth/registro")
                .body(registroValido(telefono, "baja-token@x.com"))
                .exchange()
                .expectStatus().isCreated();

        String token = login(telefono);

        // El token sigue siendo criptográficamente válido...
        http.get().uri("/api/v1/auth/yo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        desactivar(telefono);

        // ...pero /yo debe rechazarlo en cuanto la cuenta queda inactiva.
        http.get().uri("/api/v1/auth/yo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String login(String telefono) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        return (String) body.get("token");
    }

    private void desactivar(String telefono) {
        Usuario u = usuarios.findByTelefono(telefono).orElseThrow();
        u.setActivo(false);
        usuarios.save(u);
    }
}
