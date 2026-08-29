package com.baniterio.api.admin;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Red de seguridad transversal: recorre TODAS las rutas de
 * {@code /api/v1/admin/**} con un token de {@code MIEMBRO} sin ningún área
 * concedida y comprueba que cada una responde {@code 403 SIN_PERMISO}.
 *
 * <p>Los tests por endpoint ya cubren su caso concreto; este pilla el despiste
 * de que un endpoint admin nuevo se olvide de llamar a {@code exigirArea}.
 * Los cuerpos de los {@code PUT} se envían válidos a propósito: si no, saltaría
 * la validación de bean (400) antes de llegar a la comprobación de permiso.
 */
class AdminAutorizacionIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    PasswordEncoder passwordEncoder;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Una ruta del panel: método HTTP, URI y (para PUT/POST) un cuerpo válido. */
    private record RutaAdmin(String metodo, String uri, Object cuerpo) {
    }

    private static final List<RutaAdmin> RUTAS = List.of(
            new RutaAdmin("GET", "/api/v1/admin/solicitudes", null),
            new RutaAdmin("POST", "/api/v1/admin/solicitudes/1/aprobar", null),
            new RutaAdmin("POST", "/api/v1/admin/solicitudes/1/rechazar", null),
            new RutaAdmin("GET", "/api/v1/admin/miembros", null),
            new RutaAdmin("PUT", "/api/v1/admin/miembros/1/rol", Map.of("rol", "MIEMBRO")),
            new RutaAdmin("PUT", "/api/v1/admin/miembros/1/activo", Map.of("activo", true)),
            new RutaAdmin("PUT", "/api/v1/admin/miembros/1/areas", Map.of("areas", List.of())));

    @Test
    void toda_ruta_admin_rechaza_a_un_miembro_sin_areas_con_403_sin_permiso() {
        String token = tokenMiembroSinAreas();

        for (RutaAdmin r : RUTAS) {
            RestTestClient.ResponseSpec resp = switch (r.metodo()) {
                case "GET" -> http.get().uri(r.uri())
                        .header(AUTHORIZATION, "Bearer " + token)
                        .exchange();
                case "POST" -> http.post().uri(r.uri())
                        .header(AUTHORIZATION, "Bearer " + token)
                        .exchange();
                case "PUT" -> http.put().uri(r.uri())
                        .header(AUTHORIZATION, "Bearer " + token)
                        .body(r.cuerpo())
                        .exchange();
                default -> throw new IllegalStateException(r.metodo());
            };

            @SuppressWarnings("unchecked")
            Map<String, Object> body = resp
                    .expectStatus().isForbidden()
                    .expectBody(Map.class)
                    .returnResult().getResponseBody();
            assertThat(body)
                    .as("%s %s debería ser 403 SIN_PERMISO", r.metodo(), r.uri())
                    .containsEntry("codigo", "SIN_PERMISO");
        }
    }

    /** Número con formato válido ({@code ^[67]\d{8}$}) que no está en {@code usuario}. */
    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    /** Crea un usuario activo con membresía {@code MIEMBRO} (sin áreas) y devuelve su JWT. */
    private String tokenMiembroSinAreas() {
        String telefono = telefonoLibre();
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@autorizacion.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("Sin")
                .apellidos("Areas")
                .esSuperadmin(false)
                .activo(true)
                .build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(pena)
                .rol(RolMembresia.MIEMBRO)
                .activa(true)
                .build());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        return (String) body.get("token");
    }
}
