package com.baniterio.api.admin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoAreaRepository;
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
 * Test de integración de los endpoints de gestión de miembros
 * ({@code /api/v1/admin/miembros}), de punta a punta contra la app y el Postgres
 * reales.
 *
 * <p>Cubre el contrato: solo con el área {@code ADMIN_PERMISOS} se entra; cambiar
 * rol / activo / áreas de un miembro; y las salvaguardas contra bloqueo
 * (no degradarte ni desactivarte a ti mismo, solo el superadmin se toca a sí
 * mismo, y no dejar la peña sin ningún admin activo).
 *
 * <p>Como en {@link AdminSolicitudesIT}, cada token se fabrica creando el usuario
 * y su membresía por repos y haciendo login por HTTP, para no depender del
 * fundador sembrado ni de otros tests.
 */
class AdminMiembrosIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PenaRepository penas;

    @Autowired
    PermisoAreaRepository permisos;

    @Autowired
    PasswordEncoder passwordEncoder;

    private RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    // --- Helpers ---

    private record Persona(Long id, String telefono, String token) {
    }

    /** Número con formato válido ({@code ^[67]\d{8}$}) que no está en ninguna tabla. */
    private String telefonoLibre() {
        String numero;
        do {
            numero = (ThreadLocalRandom.current().nextBoolean() ? "6" : "7")
                    + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        } while (usuarios.existsByTelefono(numero));
        return numero;
    }

    private Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    /** Crea un usuario activo con membresía {@code rol} y devuelve id + teléfono + JWT. */
    private Persona crearPersona(RolMembresia rol, boolean superadmin) {
        String telefono = telefonoLibre();
        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(telefono)
                .email(telefono + "@miembros.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("Persona")
                .apellidos("De Test " + telefono)
                .esSuperadmin(superadmin)
                .activo(true)
                .build());
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(pena)
                .rol(rol)
                .activa(true)
                .build());
        return new Persona(usuario.getId(), telefono, login(telefono));
    }

    private Persona crearAdmin() {
        return crearPersona(RolMembresia.ADMIN, false);
    }

    private Persona crearMiembro() {
        return crearPersona(RolMembresia.MIEMBRO, false);
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

    private void concederAreas(String adminToken, Long miembroId, String... areas) {
        http.put().uri("/api/v1/admin/miembros/" + miembroId + "/areas")
                .header(AUTHORIZATION, "Bearer " + adminToken)
                .body(Map.of("areas", List.of(areas)))
                .exchange()
                .expectStatus().isNoContent();
    }

    /** Deja sin admins activos: base determinista para el escenario ULTIMO_ADMIN. */
    private void desactivarAdminsExistentes() {
        for (Membresia m : membresias.findByPenaId(penaId())) {
            if (m.getRol() == RolMembresia.ADMIN && m.isActiva()) {
                m.setActiva(false);
                membresias.save(m);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> conflicto(RestTestClient.ResponseSpec spec) {
        return spec.expectStatus().isEqualTo(409)
                .expectBody(Map.class)
                .returnResult().getResponseBody();
    }

    // --- Casos ---

    @Test
    void listar_miembros_requiere_el_area_permisos() {
        String token = crearMiembro().token();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/admin/miembros")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("SIN_PERMISO");
    }

    @Test
    void cambiar_rol_de_miembro_a_admin() {
        String admin = crearAdmin().token();
        Persona m = crearMiembro();

        http.put().uri("/api/v1/admin/miembros/" + m.id() + "/rol")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("rol", "ADMIN"))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(membresias.findByUsuarioIdAndPenaId(m.id(), penaId()).orElseThrow().getRol())
                .isEqualTo(RolMembresia.ADMIN);
    }

    @Test
    void no_te_puedes_degradar_a_ti_mismo() {
        Persona admin = crearAdmin();

        Map<String, Object> body = conflicto(
                http.put().uri("/api/v1/admin/miembros/" + admin.id() + "/rol")
                        .header(AUTHORIZATION, "Bearer " + admin.token())
                        .body(Map.of("rol", "MIEMBRO"))
                        .exchange());

        assertThat(body.get("codigo")).isEqualTo("NO_TE_PUEDES_DEGRADAR");
        assertThat(membresias.findByUsuarioIdAndPenaId(admin.id(), penaId()).orElseThrow().getRol())
                .isEqualTo(RolMembresia.ADMIN);
    }

    @Test
    void no_te_puedes_desactivar_a_ti_mismo() {
        Persona admin = crearAdmin();

        Map<String, Object> body = conflicto(
                http.put().uri("/api/v1/admin/miembros/" + admin.id() + "/activo")
                        .header(AUTHORIZATION, "Bearer " + admin.token())
                        .body(Map.of("activo", false))
                        .exchange());

        assertThat(body.get("codigo")).isEqualTo("NO_TE_PUEDES_DESACTIVAR");
        assertThat(usuarios.findById(admin.id()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void solo_el_superadmin_se_toca_a_si_mismo() {
        Persona admin2 = crearAdmin();
        Persona superadmin = crearPersona(RolMembresia.ADMIN, true);

        Map<String, Object> body = conflicto(
                http.put().uri("/api/v1/admin/miembros/" + superadmin.id() + "/activo")
                        .header(AUTHORIZATION, "Bearer " + admin2.token())
                        .body(Map.of("activo", false))
                        .exchange());

        assertThat(body.get("codigo")).isEqualTo("SOLO_EL_SUPERADMIN");
        assertThat(usuarios.findById(superadmin.id()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void no_puedes_dejar_la_pena_sin_ningun_admin_activo() {
        desactivarAdminsExistentes();
        Persona a = crearAdmin();                 // A: único admin activo de la peña
        Persona c = crearMiembro();               // C: no es admin, pero...
        concederAreas(a.token(), c.id(), "ADMIN_PERMISOS"); // ...tiene el área para tocar miembros

        Map<String, Object> body = conflicto(
                http.put().uri("/api/v1/admin/miembros/" + a.id() + "/rol")
                        .header(AUTHORIZATION, "Bearer " + c.token())
                        .body(Map.of("rol", "MIEMBRO"))
                        .exchange());

        assertThat(body.get("codigo")).isEqualTo("ULTIMO_ADMIN");
        assertThat(membresias.findByUsuarioIdAndPenaId(a.id(), penaId()).orElseThrow().getRol())
                .isEqualTo(RolMembresia.ADMIN);
    }

    @Test
    void put_areas_reemplaza_el_conjunto_completo() {
        String admin = crearAdmin().token();
        Persona m = crearMiembro();

        concederAreas(admin, m.id(), "ADMIN_SOLICITUDES");
        assertThat(permisos.findByUsuarioId(m.id())).extracting(p -> p.getArea().name())
                .containsExactly("ADMIN_SOLICITUDES");

        // Conjunto solapado: fuerza el orden delete-antes-de-insert contra uk_permiso_area.
        concederAreas(admin, m.id(), "ADMIN_SOLICITUDES", "ADMIN_PERMISOS");
        assertThat(permisos.findByUsuarioId(m.id())).extracting(p -> p.getArea().name())
                .containsExactlyInAnyOrder("ADMIN_SOLICITUDES", "ADMIN_PERMISOS");

        // Reemplazo total por un único área distinta.
        concederAreas(admin, m.id(), "ADMIN_PERMISOS");
        assertThat(permisos.findByUsuarioId(m.id())).extracting(p -> p.getArea().name())
                .containsExactly("ADMIN_PERMISOS");

        // Y ese miembro ve exactamente esa área en /auth/yo.
        @SuppressWarnings("unchecked")
        Map<String, Object> yo = http.get().uri("/api/v1/auth/yo")
                .header(AUTHORIZATION, "Bearer " + m.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        @SuppressWarnings("unchecked")
        List<String> areasYo = (List<String>) yo.get("areas");
        assertThat(areasYo).containsExactly("ADMIN_PERMISOS");
    }

    @Test
    void put_areas_con_valor_invalido_devuelve_400() {
        String admin = crearAdmin().token();
        Persona m = crearMiembro();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.put().uri("/api/v1/admin/miembros/" + m.id() + "/areas")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("areas", List.of("NO_EXISTE")))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult().getResponseBody();

        assertThat(body.get("codigo")).isEqualTo("VALIDACION");
        assertThat(permisos.findByUsuarioId(m.id())).isEmpty();
    }

    @Test
    void un_miembro_con_area_concedida_puede_usar_ese_endpoint_admin() {
        String admin = crearAdmin().token();
        Persona m = crearMiembro();
        concederAreas(admin, m.id(), "ADMIN_SOLICITUDES");

        http.get().uri("/api/v1/admin/solicitudes")
                .header(AUTHORIZATION, "Bearer " + m.token())
                .exchange()
                .expectStatus().isOk();

        // El área concedida no da acceso a OTRAS áreas del panel.
        http.get().uri("/api/v1/admin/miembros")
                .header(AUTHORIZATION, "Bearer " + m.token())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listar_miembros_devuelve_el_resumen_con_rol_activo_y_areas() {
        String admin = crearAdmin().token();
        Persona m = crearMiembro();
        concederAreas(admin, m.id(), "ADMIN_SOLICITUDES");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lista = http.get().uri("/api/v1/admin/miembros")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isOk()
                .expectBody(List.class)
                .returnResult().getResponseBody();

        assertThat(lista).anySatisfy(item -> {
            assertThat(item.get("id")).isEqualTo(m.id().intValue());
            assertThat(item.get("telefono")).isEqualTo(m.telefono());
            assertThat(item.get("rol")).isEqualTo("MIEMBRO");
            assertThat(item.get("activo")).isEqualTo(true);
            assertThat(item.get("esSuperadmin")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            List<String> areas = (List<String>) item.get("areas");
            assertThat(areas).containsExactly("ADMIN_SOLICITUDES");
        });
    }
}
