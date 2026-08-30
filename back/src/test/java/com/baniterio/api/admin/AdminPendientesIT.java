package com.baniterio.api.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.email.ServicioEmail;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Integración de {@code GET /api/v1/admin/pendientes}: agrega los contadores y
 * autofiltra a las áreas del usuario del token. Deltas por BBDD compartida.
 */
class AdminPendientesIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    com.baniterio.api.identidad.PermisoAreaRepository permisos;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    ServicioEmail servicioEmail;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void un_admin_ve_subir_admin_solicitudes_al_crear_una_pendiente() {
        String admin = tokenConRol(RolMembresia.ADMIN);

        long antes = leerPendiente(admin, "ADMIN_SOLICITUDES");
        crearSolicitudPendiente();
        long despues = leerPendiente(admin, "ADMIN_SOLICITUDES");

        assertThat(despues).isEqualTo(antes + 1);
    }

    @Test
    void un_miembro_sin_areas_recibe_un_mapa_sin_admin_solicitudes() {
        String miembro = tokenConRol(RolMembresia.MIEMBRO);
        crearSolicitudPendiente(); // aunque haya pendientes, no es su área

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/admin/pendientes")
                .header(AUTHORIZATION, "Bearer " + miembro)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body).doesNotContainKey("ADMIN_SOLICITUDES");
    }

    @Test
    void la_respuesta_nunca_trae_admin_permisos_porque_no_tiene_contador() {
        String admin = tokenConRol(RolMembresia.ADMIN); // tiene todas las áreas

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/admin/pendientes")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body).doesNotContainKey("ADMIN_PERMISOS");
    }

    private long leerPendiente(String token, String area) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/admin/pendientes")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        Object v = body.get(area);
        return v == null ? 0L : ((Number) v).longValue();
    }

    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    private String tokenConRol(RolMembresia rol) {
        String telefono = telefonoLibre();
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono).email(telefono + "@pend.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("Persona").apellidos("Test")
                .esSuperadmin(false).activo(true).build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario).pena(pena).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    private void crearSolicitudPendiente() {
        String telefono = telefonoLibre();
        Map<String, Object> req = new HashMap<>();
        req.put("telefono", telefono);
        req.put("email", "sol-" + telefono + "@x.com");
        req.put("nombre", "Marta");
        req.put("apellidos", "García");
        req.put("motivo", "Soy de la peña de toda la vida y quiero entrar.");
        req.put("relacion", "Mi familia lleva tres generaciones en la peña.");
        req.put("conocidos", "Conozco a Roberto y a media peña.");
        http.post().uri("/api/v1/auth/solicitudes").body(req)
                .exchange().expectStatus().isCreated();
    }
}
