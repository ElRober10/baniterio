package com.baniterio.api.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.email.ServicioEmail;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que {@link SolicitudesPendientesContador} cuenta solo las
 * solicitudes de ingreso en estado PENDIENTE de la peña, contra Postgres real.
 * Usa deltas (crear/resolver y medir el cambio) porque la BBDD de la suite es
 * compartida y otros tests dejan solicitudes.
 */
class SolicitudesPendientesContadorIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    SolicitudesPendientesContador contador;

    @Autowired
    SolicitudIngresoRepository solicitudes;

    @Autowired
    PenaRepository penas;

    @Autowired
    UsuarioRepository usuarios;

    @MockitoBean
    ServicioEmail servicioEmail;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void area_es_admin_solicitudes() {
        assertThat(contador.area().name()).isEqualTo("ADMIN_SOLICITUDES");
    }

    @Test
    void contar_sube_al_crear_una_pendiente_y_baja_al_aprobarla() {
        Long penaId = penas.findBySlug("baniterio").orElseThrow().getId();
        long antes = contador.contar(penaId);

        Long id = crearSolicitudPendiente();
        assertThat(contador.contar(penaId)).isEqualTo(antes + 1);

        String admin = tokenAdmin();
        http.post().uri("/api/v1/admin/solicitudes/" + id + "/aprobar")
                .header("Authorization", "Bearer " + admin)
                .exchange().expectStatus().isOk();

        assertThat(contador.contar(penaId)).isEqualTo(antes);
    }

    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    private Long crearSolicitudPendiente() {
        String telefono = telefonoLibre();
        Map<String, Object> req = new HashMap<>();
        req.put("telefono", telefono);
        req.put("email", "sol-" + telefono + "@x.com");
        req.put("nombre", "Marta");
        req.put("apellidos", "García");
        req.put("motivo", "Soy de la peña de toda la vida y quiero entrar.");
        req.put("relacion", "Mi familia lleva tres generaciones en la peña.");
        req.put("conocidos", "Conozco a Roberto y a media peña.");
        req.put("password", "secreto1");
        http.post().uri("/api/v1/auth/solicitudes").body(req)
                .exchange().expectStatus().isCreated();
        Long penaId = penas.findBySlug("baniterio").orElseThrow().getId();
        return solicitudes.findByPenaId(penaId).stream()
                .filter(s -> s.getTelefono().equals(telefono))
                .findFirst().orElseThrow().getId();
    }

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    com.baniterio.api.identidad.MembresiaRepository membresias;

    /** Crea un usuario con membresía ADMIN activa y devuelve su JWT (patrón de AdminSolicitudesIT). */
    private String tokenAdmin() {
        String telefono = telefonoLibre();
        com.baniterio.api.identidad.Usuario usuario = usuarios.save(
                com.baniterio.api.identidad.Usuario.builder()
                        .telefono(telefono).email(telefono + "@admin.test")
                        .passwordHash(passwordEncoder.encode("secreto1"))
                        .nombre("Admin").apellidos("Test")
                        .esSuperadmin(false).activo(true).build());
        com.baniterio.api.identidad.Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(com.baniterio.api.identidad.Membresia.builder()
                .usuario(usuario).pena(pena)
                .rol(com.baniterio.api.identidad.RolMembresia.ADMIN).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", telefono, "password", "secreto1"))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }
}
