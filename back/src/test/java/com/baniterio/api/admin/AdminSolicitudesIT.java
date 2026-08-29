package com.baniterio.api.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.email.ServicioEmail;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.SolicitudIngreso;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Test de integración de los endpoints de administración de solicitudes
 * ({@code /api/v1/admin/solicitudes}), de punta a punta contra la app y el
 * Postgres reales.
 *
 * <p>Cubre el contrato: sin el área {@code ADMIN_SOLICITUDES} → 403 SIN_PERMISO;
 * aprobar con contraseña crea usuario + membresía y marca el teléfono como usado;
 * aprobar sin contraseña solo autoriza el teléfono; rechazar guarda el motivo (o
 * el texto por defecto); y una segunda resolución de la misma solicitud → 409.
 * En cada resolución se comprueba que sale el correo correspondiente (con
 * {@link ServicioEmail} mockeado).
 *
 * <p>El token de admin de cada test se fabrica creando un usuario con membresía
 * {@code ADMIN} directamente por repos y haciendo login por HTTP: así no depende
 * del fundador sembrado por V6 (que solo puede registrarse una vez por suite) y
 * los tests quedan aislados entre sí.
 */
class AdminSolicitudesIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TelefonoAutorizadoRepository telefonos;

    @Autowired
    PenaRepository penas;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    SolicitudIngresoRepository solicitudes;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    ServicioEmail servicioEmail;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // --- Helpers ---

    /** Número con formato válido ({@code ^[67]\d{8}$}) que no está en ninguna tabla. */
    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero) || telefonos.findByTelefono(numero).isPresent());
        return numero;
    }

    /** Crea un usuario con membresía {@code ADMIN} activa y devuelve su JWT (login por HTTP). */
    private String tokenAdmin() {
        return tokenDeUsuarioConRol(RolMembresia.ADMIN);
    }

    /** Crea un usuario con membresía {@code MIEMBRO} activa (sin áreas del panel) y devuelve su JWT. */
    private String tokenMiembro() {
        return tokenDeUsuarioConRol(RolMembresia.MIEMBRO);
    }

    private String tokenDeUsuarioConRol(RolMembresia rol) {
        String telefono = telefonoLibre();
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@admin.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("Admin")
                .apellidos("De Test")
                .esSuperadmin(false)
                .activo(true)
                .build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(pena)
                .rol(rol)
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

    /** Datos de una solicitud pendiente recién creada por el endpoint público. */
    private record SolicitudCreada(Long id, String telefono, String email) {
    }

    private SolicitudCreada crearSolicitudPendiente(boolean conPassword) {
        String telefono = telefonoLibre();
        String email = "solicitante-" + telefono + "@x.com";

        Map<String, Object> req = new HashMap<>();
        req.put("telefono", telefono);
        req.put("email", email);
        req.put("nombre", "Marta");
        req.put("apellidos", "García");
        req.put("motivo", "Quiero entrar porque soy de la peña de toda la vida.");
        req.put("relacion", "Mi familia lleva en la peña tres generaciones.");
        req.put("conocidos", "Conozco a Roberto y a media peña.");
        if (conPassword) {
            req.put("password", "secreto1");
        }

        http.post().uri("/api/v1/auth/solicitudes").body(req)
                .exchange().expectStatus().isCreated();

        Long penaId = penas.findBySlug("baniterio").orElseThrow().getId();
        SolicitudIngreso sol = solicitudes.findByPenaId(penaId).stream()
                .filter(s -> s.getTelefono().equals(telefono))
                .findFirst().orElseThrow();
        return new SolicitudCreada(sol.getId(), telefono, email);
    }

    // --- Casos ---

    @Test
    void listar_solicitudes_pendientes_requiere_el_area() {
        String token = tokenMiembro();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/admin/solicitudes")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("SIN_PERMISO");
    }

    @Test
    void aprobar_con_password_crea_usuario_membresia_y_telefono_usado() {
        String admin = tokenAdmin();
        SolicitudCreada s = crearSolicitudPendiente(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> r = http.post().uri("/api/v1/admin/solicitudes/" + s.id() + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(r.get("resultado")).isEqualTo("CUENTA_CREADA");

        Usuario creado = usuarios.findByTelefono(s.telefono()).orElseThrow();
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.getEmail()).isEqualTo(s.email());
        assertThat(membresias.findByUsuarioId(creado.getId()))
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.getRol()).isEqualTo(RolMembresia.MIEMBRO);
                    assertThat(m.isActiva()).isTrue();
                });
        assertThat(telefonos.findByTelefono(s.telefono())).get()
                .extracting(TelefonoAutorizado::isUsado).isEqualTo(true);

        // El solicitante ya puede iniciar sesión con la contraseña que puso.
        http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", s.telefono(), "password", "secreto1"))
                .exchange().expectStatus().isOk();

        verify(servicioEmail).enviar(eq(s.email()), any(), contains("iniciar sesión"));
    }

    @Test
    void aprobar_sin_password_solo_autoriza_el_telefono() {
        String admin = tokenAdmin();
        SolicitudCreada s = crearSolicitudPendiente(false);

        @SuppressWarnings("unchecked")
        Map<String, Object> r = http.post().uri("/api/v1/admin/solicitudes/" + s.id() + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(r.get("resultado")).isEqualTo("TELEFONO_AUTORIZADO");

        assertThat(telefonos.findByTelefono(s.telefono())).get()
                .extracting(TelefonoAutorizado::isUsado).isEqualTo(false);
        assertThat(usuarios.findByTelefono(s.telefono())).isEmpty();

        verify(servicioEmail).enviar(eq(s.email()), any(), contains("registro"));
    }

    @Test
    void rechazar_con_motivo_marca_estado_y_guarda_el_motivo() {
        String admin = tokenAdmin();
        SolicitudCreada s = crearSolicitudPendiente(true);

        http.post().uri("/api/v1/admin/solicitudes/" + s.id() + "/rechazar")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("motivo", "No te conocemos de nada."))
                .exchange().expectStatus().isNoContent();

        SolicitudIngreso sol = solicitudes.findById(s.id()).orElseThrow();
        assertThat(sol.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(sol.getMotivoRechazo()).isEqualTo("No te conocemos de nada.");
        assertThat(sol.getResueltaAt()).isNotNull();

        verify(servicioEmail).enviar(eq(s.email()), any(), contains("No te conocemos de nada."));
    }

    @Test
    void rechazar_sin_motivo_usa_el_texto_por_defecto() {
        String admin = tokenAdmin();
        SolicitudCreada s = crearSolicitudPendiente(false);

        http.post().uri("/api/v1/admin/solicitudes/" + s.id() + "/rechazar")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        SolicitudIngreso sol = solicitudes.findById(s.id()).orElseThrow();
        assertThat(sol.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(sol.getMotivoRechazo()).contains("vinculación");

        verify(servicioEmail).enviar(eq(s.email()), any(), contains("vinculación"));
    }

    @Test
    void doble_resolucion_de_la_misma_solicitud_devuelve_409() {
        String admin = tokenAdmin();
        SolicitudCreada s = crearSolicitudPendiente(false);

        http.post().uri("/api/v1/admin/solicitudes/" + s.id() + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isOk();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/admin/solicitudes/" + s.id() + "/aprobar")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("SOLICITUD_YA_RESUELTA");
    }

    @Test
    void listar_solicitudes_pendientes_devuelve_las_del_estado_pedido() {
        String admin = tokenAdmin();
        SolicitudCreada s = crearSolicitudPendiente(true);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> lista = http.get().uri("/api/v1/admin/solicitudes?estado=PENDIENTE")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(java.util.List.class)
                .returnResult().getResponseBody();

        assertThat(lista).anySatisfy(item -> {
            assertThat(item.get("telefono")).isEqualTo(s.telefono());
            assertThat(item.get("traeContrasena")).isEqualTo(true);
            assertThat(item.get("estado")).isEqualTo("PENDIENTE");
        });
    }
}
