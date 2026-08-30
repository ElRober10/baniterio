# Avisos de pendientes (campanita) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un admin/superadmin vea una campanita con el número de cosas sin atender: el total en la tarjeta "Administración" de la home del panel y el desglose por área en el índice de administración.

**Architecture:** El back expone un contrato extensible `ContadorPendientes` (una implementación por área que genere trabajo; hoy solo solicitudes de ingreso pendientes) y un endpoint `GET /api/v1/admin/pendientes` que agrega y autofiltra a las áreas del usuario. El front tiene un componente tonto `app-aviso-pendientes` (campana + número, nada si 0) y un `AdminAvisosService` con un signal compartido `pendientes` + `total` que se refresca al navegar y tras resolver una solicitud.

**Tech Stack:** Backend Java 17 + Spring Boot (JPA, `@Transactional`), tests con JUnit 5 + Testcontainers (Postgres) bajo `maven-failsafe-plugin` (`*IT`) y Mockito para tests unitarios. Frontend Angular 22 standalone (signals: `signal`, `computed`, `input.required`), Tailwind v4, tests con vitest + `HttpTestingController`.

**Spec:** `docs/superpowers/specs/2026-08-30-avisos-pendientes-design.md`

## Global Constraints

- **Java 17** en el backend (no subir la versión).
- **Alcance: una sola peña.** El id de la peña se resuelve por slug `"baniterio"` (patrón ya usado en `AdminService` y `ServicioPermisos`); si falta, `IllegalStateException` (fallo de arranque legítimo, 500).
- **Sin cambios de esquema:** no hay migración Flyway en esta tarea.
- **Sin tocar el móvil** (`mobile/`).
- **Idioma:** identificadores y comentarios en castellano, como el resto del código.
- Backend base URL de la API: `/api/v1`. Front la lee de `environment.apiBaseUrl`.
- Los endpoints `/api/v1/admin/**` exigen JWT (lo impone `SecurityConfig`); el token se fabrica en los tests creando usuario + membresía por repos y haciendo login por HTTP (ver `AdminSolicitudesIT`).
- Enum `AreaProtegida` actual: `ADMIN_SOLICITUDES`, `ADMIN_PERMISOS`. En el front el tipo `Area` es `'ADMIN_SOLICITUDES' | 'ADMIN_PERMISOS'` y `AREAS` mapea a etiquetas (`'Solicitudes'`, `'Permisos'`).

---

## Estructura de ficheros

**Backend — nuevos**
- `back/src/main/java/com/baniterio/api/admin/ContadorPendientes.java` — interfaz: `area()` + `contar()`.
- `back/src/main/java/com/baniterio/api/admin/SolicitudesPendientesContador.java` — `@Component`, cuenta `solicitud_ingreso` en estado `PENDIENTE` de la peña.
- `back/src/test/java/com/baniterio/api/admin/SolicitudesPendientesContadorIT.java` — test del contador contra Postgres real.
- `back/src/test/java/com/baniterio/api/admin/AdminPendientesIT.java` — test del endpoint.
- `back/src/test/java/com/baniterio/api/admin/AdminServiceTest.java` — test unitario (Mockito) de la agregación/filtrado.

**Backend — modificados**
- `back/src/main/java/com/baniterio/api/identidad/SolicitudIngresoRepository.java` — `long countByPenaIdAndEstado(Long, EstadoSolicitud)`.
- `back/src/main/java/com/baniterio/api/admin/AdminService.java` — inyecta `List<ContadorPendientes>`; método `pendientesPorArea(Long): Map<AreaProtegida, Long>`.
- `back/src/main/java/com/baniterio/api/admin/AdminController.java` — `GET /pendientes`.

**Frontend — nuevos**
- `front/src/app/shared/aviso-pendientes/aviso-pendientes.ts` — componente presentacional.
- `front/src/app/shared/aviso-pendientes/aviso-pendientes.spec.ts`.
- `front/src/app/admin/admin-avisos.service.ts` — signal compartido + `refrescar()`.
- `front/src/app/admin/admin-avisos.service.spec.ts`.

**Frontend — modificados**
- `front/src/app/admin/admin.types.ts` — `export type PendientesPorArea = Partial<Record<Area, number>>;`
- `front/src/app/panel/inicio/inicio.ts` + `inicio.html` + `inicio.spec.ts`.
- `front/src/app/admin/indice/indice.ts` + `indice.html` + `indice.spec.ts`.
- `front/src/app/admin/solicitudes/solicitudes.ts` + `solicitudes.spec.ts`.

---

## Task 1: Contador de solicitudes pendientes (back)

**Files:**
- Create: `back/src/main/java/com/baniterio/api/admin/ContadorPendientes.java`
- Create: `back/src/main/java/com/baniterio/api/admin/SolicitudesPendientesContador.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/SolicitudIngresoRepository.java`
- Test: `back/src/test/java/com/baniterio/api/admin/SolicitudesPendientesContadorIT.java`

**Interfaces:**
- Consumes: `SolicitudIngresoRepository`, `PenaRepository`, `EstadoSolicitud.PENDIENTE`, `AreaProtegida.ADMIN_SOLICITUDES`.
- Produces:
  - `interface ContadorPendientes { AreaProtegida area(); long contar(); }`
  - `@Component class SolicitudesPendientesContador implements ContadorPendientes`
  - `long SolicitudIngresoRepository.countByPenaIdAndEstado(Long penaId, EstadoSolicitud estado)`

- [ ] **Step 1: Escribir el test que falla**

Create `back/src/test/java/com/baniterio/api/admin/SolicitudesPendientesContadorIT.java`:

```java
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
        long antes = contador.contar();

        Long id = crearSolicitudPendiente();
        assertThat(contador.contar()).isEqualTo(antes + 1);

        String admin = tokenAdmin();
        http.post().uri("/api/v1/admin/solicitudes/" + id + "/aprobar")
                .header("Authorization", "Bearer " + admin)
                .exchange().expectStatus().isOk();

        assertThat(contador.contar()).isEqualTo(antes);
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
```

- [ ] **Step 2: Ejecutar el test y ver que no compila**

Run: `cd back && ./mvnw.cmd -q -Dit.test=SolicitudesPendientesContadorIT verify`
Expected: fallo de compilación — no existen `SolicitudesPendientesContador` ni `countByPenaIdAndEstado`.

- [ ] **Step 3: Crear la interfaz**

Create `back/src/main/java/com/baniterio/api/admin/ContadorPendientes.java`:

```java
package com.baniterio.api.admin;

import com.baniterio.api.identidad.AreaProtegida;

/**
 * Contrato de "cuántas cosas hay sin atender" de un área del panel. Cada área
 * que genere trabajo de aprobar/verificar (solicitudes de ingreso hoy;
 * confirmación de pagos, etc. en el futuro) aporta una implementación
 * {@code @Component}; {@link AdminService#pendientesPorArea} las agrega y las
 * filtra a las áreas del usuario que pregunta.
 *
 * <p>Alcance de una sola peña: {@link #contar()} devuelve el total de la peña.
 */
public interface ContadorPendientes {

    /** Área del panel a la que pertenece este contador. */
    AreaProtegida area();

    /** Nº de cosas sin atender en esa área, en toda la peña. Nunca negativo. */
    long contar();
}
```

- [ ] **Step 4: Añadir el `count` al repositorio**

Modify `back/src/main/java/com/baniterio/api/identidad/SolicitudIngresoRepository.java` — añade junto a los demás métodos:

```java
    /** Nº de solicitudes de una peña en un estado dado (para la campanita de pendientes). */
    long countByPenaIdAndEstado(Long penaId, EstadoSolicitud estado);
```

- [ ] **Step 5: Implementar el contador**

Create `back/src/main/java/com/baniterio/api/admin/SolicitudesPendientesContador.java`:

```java
package com.baniterio.api.admin;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.EstadoSolicitud;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.SolicitudIngresoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pendientes del área {@code ADMIN_SOLICITUDES}: solicitudes de ingreso a la
 * peña que aún no se han aprobado ni rechazado.
 */
@Component
public class SolicitudesPendientesContador implements ContadorPendientes {

    private static final String SLUG_PENA = "baniterio";

    private final SolicitudIngresoRepository solicitudes;
    private final PenaRepository penas;

    public SolicitudesPendientesContador(SolicitudIngresoRepository solicitudes, PenaRepository penas) {
        this.solicitudes = solicitudes;
        this.penas = penas;
    }

    @Override
    public AreaProtegida area() {
        return AreaProtegida.ADMIN_SOLICITUDES;
    }

    @Override
    @Transactional(readOnly = true)
    public long contar() {
        Long penaId = penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
        return solicitudes.countByPenaIdAndEstado(penaId, EstadoSolicitud.PENDIENTE);
    }
}
```

- [ ] **Step 6: Ejecutar el test y verlo pasar**

Run: `cd back && ./mvnw.cmd -q -Dit.test=SolicitudesPendientesContadorIT verify`
Expected: PASS (2 tests). Necesita Docker en marcha (Testcontainers).

- [ ] **Step 7: Commit**

```bash
git add back/src/main/java/com/baniterio/api/admin/ContadorPendientes.java \
        back/src/main/java/com/baniterio/api/admin/SolicitudesPendientesContador.java \
        back/src/main/java/com/baniterio/api/identidad/SolicitudIngresoRepository.java \
        back/src/test/java/com/baniterio/api/admin/SolicitudesPendientesContadorIT.java
git commit -m "feat(avisos): contador de solicitudes de ingreso pendientes"
```

---

## Task 2: Agregación + endpoint `GET /api/v1/admin/pendientes` (back)

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/admin/AdminService.java`
- Modify: `back/src/main/java/com/baniterio/api/admin/AdminController.java`
- Test: `back/src/test/java/com/baniterio/api/admin/AdminServiceTest.java` (nuevo, unitario)
- Test: `back/src/test/java/com/baniterio/api/admin/AdminPendientesIT.java` (nuevo, integración)

**Interfaces:**
- Consumes: `ContadorPendientes` (Task 1), `ServicioPermisos.areasDe(Long): Set<AreaProtegida>`.
- Produces:
  - `Map<AreaProtegida, Long> AdminService.pendientesPorArea(Long usuarioId)` — solo entradas con `contar() > 0` y área que el usuario tenga.
  - `GET /api/v1/admin/pendientes` → `200` con JSON `{"ADMIN_SOLICITUDES": 3}` (mapa vacío `{}` si nada).

- [ ] **Step 1: Escribir el test unitario que falla**

Create `back/src/test/java/com/baniterio/api/admin/AdminServiceTest.java`:

```java
package com.baniterio.api.admin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.AreaProtegida;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test unitario de {@link AdminService#pendientesPorArea}: agrega los
 * {@link ContadorPendientes}, filtra a las áreas del usuario y omite las que
 * están a cero. El resto de {@link AdminService} se prueba en sus *IT.
 */
class AdminServiceTest {

    private final ServicioPermisos servicioPermisos = mock(ServicioPermisos.class);

    private ContadorPendientes contador(AreaProtegida area, long n) {
        ContadorPendientes c = mock(ContadorPendientes.class);
        when(c.area()).thenReturn(area);
        when(c.contar()).thenReturn(n);
        return c;
    }

    private AdminService servicioCon(List<ContadorPendientes> contadores) {
        return new AdminService(null, null, null, null, null, null, servicioPermisos, null, contadores);
    }

    @Test
    void incluye_solo_las_areas_del_usuario_con_pendientes() {
        when(servicioPermisos.areasDe(7L)).thenReturn(Set.of(AreaProtegida.ADMIN_SOLICITUDES));
        AdminService servicio = servicioCon(List.of(
                contador(AreaProtegida.ADMIN_SOLICITUDES, 3),
                contador(AreaProtegida.ADMIN_PERMISOS, 9)));

        assertThat(servicio.pendientesPorArea(7L))
                .isEqualTo(Map.of(AreaProtegida.ADMIN_SOLICITUDES, 3L));
    }

    @Test
    void omite_las_areas_a_cero() {
        when(servicioPermisos.areasDe(7L)).thenReturn(Set.of(AreaProtegida.ADMIN_SOLICITUDES));
        AdminService servicio = servicioCon(List.of(contador(AreaProtegida.ADMIN_SOLICITUDES, 0)));

        assertThat(servicio.pendientesPorArea(7L)).isEmpty();
    }

    @Test
    void usuario_sin_areas_recibe_mapa_vacio() {
        when(servicioPermisos.areasDe(7L)).thenReturn(Set.of());
        AdminService servicio = servicioCon(List.of(contador(AreaProtegida.ADMIN_SOLICITUDES, 5)));

        assertThat(servicio.pendientesPorArea(7L)).isEmpty();
    }
}
```

- [ ] **Step 2: Ejecutar y ver que no compila**

Run: `cd back && ./mvnw.cmd -q -Dtest=AdminServiceTest test`
Expected: no compila — el constructor de `AdminService` tiene 8 parámetros, no 9, y no existe `pendientesPorArea`.

- [ ] **Step 3: Añadir `List<ContadorPendientes>` al constructor de `AdminService`**

Modify `back/src/main/java/com/baniterio/api/admin/AdminService.java`:

1. Import: `import java.util.EnumMap;`, `import java.util.List;` (ya está), `import java.util.Map;`, `import java.util.Set;`, `import com.baniterio.api.identidad.AreaProtegida;` (ya está).
2. Campo nuevo junto a los demás:

```java
    private final List<ContadorPendientes> contadores;
```

3. En el constructor, añade el parámetro **al final** y su asignación:

```java
    public AdminService(SolicitudIngresoRepository solicitudes, UsuarioRepository usuarios,
                        MembresiaRepository membresias, TelefonoAutorizadoRepository telefonos,
                        PenaRepository penas, PermisoAreaRepository permisos,
                        ServicioPermisos servicioPermisos,
                        ApplicationEventPublisher publisher,
                        List<ContadorPendientes> contadores) {
        // ...asignaciones existentes...
        this.contadores = contadores;
    }
```

> Spring inyecta automáticamente todos los beans que implementan
> `ContadorPendientes` como un `List`.

- [ ] **Step 4: Implementar `pendientesPorArea`**

Añade el método a `AdminService` (después de `listarSolicitudes`, junto al bloque de solicitudes):

```java
    /**
     * Cuántas cosas sin atender tiene el usuario en cada área del panel a la que
     * puede acceder. Solo aparecen las áreas con al menos un pendiente. Mapa
     * vacío si el usuario no tiene ninguna área o no hay nada que atender.
     */
    @Transactional(readOnly = true)
    public Map<AreaProtegida, Long> pendientesPorArea(Long usuarioId) {
        Set<AreaProtegida> mias = servicioPermisos.areasDe(usuarioId);
        Map<AreaProtegida, Long> resultado = new EnumMap<>(AreaProtegida.class);
        for (ContadorPendientes contador : contadores) {
            if (!mias.contains(contador.area())) {
                continue;
            }
            long n = contador.contar();
            if (n > 0) {
                resultado.put(contador.area(), n);
            }
        }
        return resultado;
    }
```

- [ ] **Step 5: Ejecutar el test unitario y verlo pasar**

Run: `cd back && ./mvnw.cmd -q -Dtest=AdminServiceTest test`
Expected: PASS (3 tests).

- [ ] **Step 6: Escribir el test de integración del endpoint**

Create `back/src/test/java/com/baniterio/api/admin/AdminPendientesIT.java`:

```java
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
```

- [ ] **Step 7: Ejecutarlo y verlo fallar (404 / no mapea)**

Run: `cd back && ./mvnw.cmd -q -Dit.test=AdminPendientesIT verify`
Expected: FAIL — el endpoint `GET /api/v1/admin/pendientes` aún no existe (404).

- [ ] **Step 8: Añadir el endpoint al controlador**

Modify `back/src/main/java/com/baniterio/api/admin/AdminController.java`:

1. Imports: `import java.util.Map;`, `import com.baniterio.api.identidad.AreaProtegida;` (ya está).
2. Método nuevo (al final de la clase, tras `areas(...)`):

```java
    /**
     * Cuántas cosas sin atender tiene quien pregunta en cada área del panel.
     * Se autofiltra a sus áreas (no lleva {@code exigirArea}): sin áreas → {}.
     */
    @GetMapping("/pendientes")
    public Map<AreaProtegida, Long> pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        if (principal == null) {
            return Map.of();
        }
        return adminService.pendientesPorArea(principal.id());
    }
```

- [ ] **Step 9: Ejecutar el IT y el resto de ITs de admin**

Run: `cd back && ./mvnw.cmd -q -Dit.test='AdminPendientesIT,AdminAutorizacionIT,AdminSolicitudesIT' verify`
Expected: PASS. (`AdminAutorizacionIT` recorre `/api/v1/admin/**` con un miembro sin áreas; `GET /pendientes` NO está en su lista y responde 200 `{}` en vez de 403 — es correcto y no lo rompe porque esa ruta no está listada ahí.)

- [ ] **Step 10: Commit**

```bash
git add back/src/main/java/com/baniterio/api/admin/AdminService.java \
        back/src/main/java/com/baniterio/api/admin/AdminController.java \
        back/src/test/java/com/baniterio/api/admin/AdminServiceTest.java \
        back/src/test/java/com/baniterio/api/admin/AdminPendientesIT.java
git commit -m "feat(avisos): endpoint GET /api/v1/admin/pendientes"
```

---

## Task 3: `AdminAvisosService` + tipo `PendientesPorArea` (front)

**Files:**
- Modify: `front/src/app/admin/admin.types.ts`
- Create: `front/src/app/admin/admin-avisos.service.ts`
- Test: `front/src/app/admin/admin-avisos.service.spec.ts`

**Interfaces:**
- Consumes: `environment.apiBaseUrl`, `HttpClient`.
- Produces:
  - `type PendientesPorArea = Partial<Record<Area, number>>`
  - `AdminAvisosService` con: `readonly pendientes: Signal<PendientesPorArea>`, `readonly total: Signal<number>`, `refrescar(): void`.

- [ ] **Step 1: Escribir el test que falla**

Create `front/src/app/admin/admin-avisos.service.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { AdminAvisosService } from './admin-avisos.service';

/**
 * `AdminAvisosService`: `refrescar()` pide GET /admin/pendientes y publica el
 * resultado en el signal `pendientes`; `total` suma los valores. Un error del
 * backend no rompe: deja el último valor conocido.
 */
describe('AdminAvisosService', () => {
  let servicio: AdminAvisosService;
  let httpMock: HttpTestingController;
  const url = `${environment.apiBaseUrl}/admin/pendientes`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AdminAvisosService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('empieza vacío con total 0', () => {
    expect(servicio.pendientes()).toEqual({});
    expect(servicio.total()).toBe(0);
  });

  it('refrescar() puebla el signal y total suma los valores', () => {
    servicio.refrescar();
    httpMock.expectOne(url).flush({ ADMIN_SOLICITUDES: 3 });

    expect(servicio.pendientes()).toEqual({ ADMIN_SOLICITUDES: 3 });
    expect(servicio.total()).toBe(3);
  });

  it('un error del backend deja el último valor conocido', () => {
    servicio.refrescar();
    httpMock.expectOne(url).flush({ ADMIN_SOLICITUDES: 2 });

    servicio.refrescar();
    httpMock.expectOne(url).error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

    expect(servicio.pendientes()).toEqual({ ADMIN_SOLICITUDES: 2 });
    expect(servicio.total()).toBe(2);
  });
});
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npx ng test --watch=false --include='src/app/admin/admin-avisos.service.spec.ts'`
Expected: FAIL — `admin-avisos.service` no existe.

- [ ] **Step 3: Añadir el tipo**

Modify `front/src/app/admin/admin.types.ts` — al final del fichero:

```ts
/**
 * Cuántas cosas sin atender tiene el usuario en cada área del panel. La clave es
 * la misma que viaja en `usuario.areas`; falta = 0. Lo devuelve
 * `GET /api/v1/admin/pendientes`.
 */
export type PendientesPorArea = Partial<Record<Area, number>>;
```

- [ ] **Step 4: Implementar el servicio**

Create `front/src/app/admin/admin-avisos.service.ts`:

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { PendientesPorArea } from './admin.types';

/**
 * Estado compartido de la "campanita" del panel: cuántas cosas tiene sin atender
 * el usuario por área. Vive en `root` para que la home del panel, el índice de
 * administración y la pantalla de solicitudes lean el mismo número.
 *
 * `refrescar()` se llama al entrar en esas pantallas y tras resolver una
 * solicitud. Es silencioso ante error (la campanita es información secundaria):
 * deja el último valor conocido y no rompe la pantalla.
 */
@Injectable({ providedIn: 'root' })
export class AdminAvisosService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  private readonly _pendientes = signal<PendientesPorArea>({});

  /** Pendientes por área. Área ausente = 0. */
  readonly pendientes = this._pendientes.asReadonly();

  /** Suma de todos los pendientes (para la tarjeta "Administración"). */
  readonly total = computed(() =>
    Object.values(this._pendientes()).reduce((suma, n) => suma + (n ?? 0), 0),
  );

  /** Vuelve a pedir el recuento al backend. */
  refrescar(): void {
    this.http.get<PendientesPorArea>(`${this.base}/admin/pendientes`).subscribe({
      next: (mapa) => this._pendientes.set(mapa),
      error: () => {},
    });
  }
}
```

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `cd front && npx ng test --watch=false --include='src/app/admin/admin-avisos.service.spec.ts'`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add front/src/app/admin/admin.types.ts \
        front/src/app/admin/admin-avisos.service.ts \
        front/src/app/admin/admin-avisos.service.spec.ts
git commit -m "feat(avisos): AdminAvisosService con signal compartido de pendientes"
```

---

## Task 4: Componente `app-aviso-pendientes` (front)

**Files:**
- Create: `front/src/app/shared/aviso-pendientes/aviso-pendientes.ts`
- Test: `front/src/app/shared/aviso-pendientes/aviso-pendientes.spec.ts`

**Interfaces:**
- Consumes: nada (presentacional puro).
- Produces: componente standalone `AvisoPendientes`, selector `app-aviso-pendientes`, input `cuenta = input.required<number>()`. Con `cuenta() <= 0` no renderiza nada; con `> 0` renderiza un `<span role="status">` con `aria-label="N pendientes"` y el texto `N` (o `99+` si `> 99`).

- [ ] **Step 1: Escribir el test que falla**

Create `front/src/app/shared/aviso-pendientes/aviso-pendientes.spec.ts`:

```ts
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AvisoPendientes } from './aviso-pendientes';

/**
 * `AvisoPendientes` es presentacional: pinta una campana + número cuando
 * `cuenta > 0`, nada cuando es 0, y `99+` cuando pasa de 99.
 */
@Component({
  imports: [AvisoPendientes],
  template: `<app-aviso-pendientes [cuenta]="n" />`,
})
class Host {
  n = 0;
}

describe('AvisoPendientes', () => {
  function render(n: number): HTMLElement {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.n = n;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('con cuenta 0 no pinta nada', () => {
    const el = render(0);
    expect(el.textContent?.trim()).toBe('');
    expect(el.querySelector('[role="status"]')).toBeNull();
  });

  it('con cuenta 3 pinta "3" y el aria-label', () => {
    const el = render(3);
    const status = el.querySelector('[role="status"]');
    expect(status).not.toBeNull();
    expect(status?.textContent).toContain('3');
    expect(status?.getAttribute('aria-label')).toBe('3 pendientes');
  });

  it('con cuenta 120 pinta "99+"', () => {
    const el = render(120);
    expect(el.querySelector('[role="status"]')?.textContent).toContain('99+');
    expect(el.querySelector('[role="status"]')?.getAttribute('aria-label')).toBe('120 pendientes');
  });
});
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npx ng test --watch=false --include='src/app/shared/aviso-pendientes/aviso-pendientes.spec.ts'`
Expected: FAIL — `./aviso-pendientes` no existe.

- [ ] **Step 3: Implementar el componente**

Create `front/src/app/shared/aviso-pendientes/aviso-pendientes.ts`:

```ts
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Campanita con un número: "tienes N cosas sin atender". Presentacional puro —
 * el número se lo pasa quien lo usa (`[cuenta]`). Con `cuenta` 0 (o menos) no
 * pinta nada: el aviso solo existe cuando hay trabajo. Se usa en la tarjeta
 * "Administración" de la home del panel (con el total) y en cada tarjeta del
 * índice de administración (con el pendiente de esa área).
 */
@Component({
  selector: 'app-aviso-pendientes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (cuenta() > 0) {
      <span
        role="status"
        [attr.aria-label]="cuenta() + ' pendientes'"
        class="inline-flex items-center gap-1 rounded-full bg-brand/20 px-2 py-0.5 text-xs font-bold text-gold-soft"
      >
        <svg viewBox="0 0 16 16" class="h-3.5 w-3.5" fill="currentColor" aria-hidden="true">
          <path
            d="M8 1.5a.9.9 0 0 1 .9.9v.43a4.25 4.25 0 0 1 3.35 4.15v2.2l.86 1.44A.7.7 0 0 1 12.4 11.7H3.6a.7.7 0 0 1-.6-1.08l.86-1.44v-2.2A4.25 4.25 0 0 1 7.1 2.83V2.4a.9.9 0 0 1 .9-.9ZM6.6 12.7h2.8a1.4 1.4 0 0 1-2.8 0Z"
          />
        </svg>
        {{ mostrada() }}
      </span>
    }
  `,
})
export class AvisoPendientes {
  /** Nº de cosas sin atender. 0 o menos → no se pinta nada. */
  readonly cuenta = input.required<number>();

  /** Texto del globo: el número, o `99+` si se pasa. */
  protected readonly mostrada = computed(() =>
    this.cuenta() > 99 ? '99+' : String(this.cuenta()),
  );
}
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `cd front && npx ng test --watch=false --include='src/app/shared/aviso-pendientes/aviso-pendientes.spec.ts'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add front/src/app/shared/aviso-pendientes/
git commit -m "feat(avisos): componente presentacional app-aviso-pendientes"
```

---

## Task 5: Cablear la tarjeta "Administración" de la home del panel (front)

**Files:**
- Modify: `front/src/app/panel/inicio/inicio.ts`
- Modify: `front/src/app/panel/inicio/inicio.html`
- Test: `front/src/app/panel/inicio/inicio.spec.ts`

**Interfaces:**
- Consumes: `AdminAvisosService` (Task 3: `refrescar()`, `total()`), `AvisoPendientes` (Task 4).
- Produces: `PanelInicio implements OnInit`; en `ngOnInit`, si el usuario tiene áreas, llama `avisos.refrescar()`. La plantilla pinta `<app-aviso-pendientes [cuenta]="avisos.total()" />` en la tarjeta "Administración".

- [ ] **Step 1: Actualizar el test**

Modify `front/src/app/panel/inicio/inicio.spec.ts`. Reemplaza el contenido entero por:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { UsuarioDto } from '../../auth/auth.types';
import { environment } from '../../../environments/environment';
import { PanelInicio } from './inicio';

/**
 * Tests de `PanelInicio`:
 * - La tarjeta "Administración" solo aparece si el usuario tiene alguna área.
 * - Si la tiene, en `ngOnInit` se pide `GET /admin/pendientes` y la tarjeta
 *   muestra el total (campanita). Sin áreas no se pide nada.
 */
describe('PanelInicio · tarjeta de administración', () => {
  const usuarioSesion = signal<UsuarioDto | null>(null);
  const authFalso: Partial<AuthService> = { usuarioActual: usuarioSesion };
  const pendientesUrl = `${environment.apiBaseUrl}/admin/pendientes`;

  let httpMock: HttpTestingController;

  function crear(): ComponentFixture<PanelInicio> {
    const fixture = TestBed.createComponent(PanelInicio);
    fixture.detectChanges();
    return fixture;
  }

  function usuario(areas: string[]): UsuarioDto {
    return {
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol: 'MIEMBRO',
      areas,
    };
  }

  beforeEach(() => {
    usuarioSesion.set(null);
    TestBed.configureTestingModule({
      imports: [PanelInicio],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sin usuario cargado todavía: no hay tarjeta "Administración" ni se pide el recuento', () => {
    const fixture = crear();
    httpMock.expectNone(pendientesUrl);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Administración');
  });

  it('usuario sin áreas: no hay tarjeta "Administración" ni se pide el recuento', () => {
    usuarioSesion.set(usuario([]));
    const fixture = crear();
    httpMock.expectNone(pendientesUrl);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Administración');
  });

  it('usuario con área: pide el recuento y la tarjeta muestra el total', async () => {
    usuarioSesion.set(usuario(['ADMIN_SOLICITUDES']));
    const fixture = crear();

    httpMock.expectOne(pendientesUrl).flush({ ADMIN_SOLICITUDES: 2 });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Administración');
    const status = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
    expect(status?.textContent).toContain('2');
  });
});
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npx ng test --watch=false --include='src/app/panel/inicio/inicio.spec.ts'`
Expected: FAIL — `PanelInicio` no pide nada en `ngOnInit` todavía (y no importa `AvisoPendientes`).

- [ ] **Step 3: Actualizar el componente**

Modify `front/src/app/panel/inicio/inicio.ts` — reemplaza el fichero por:

```ts
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminAvisosService } from '../../admin/admin-avisos.service';
import { AuthService } from '../../auth/auth.service';
import { AvisoPendientes } from '../../shared/aviso-pendientes/aviso-pendientes';
import { SECCIONES } from '../secciones';

/**
 * Contenido de bienvenida del panel: se pinta dentro del `<router-outlet>` de
 * `Panel` en la ruta `/panel` (hija `''`). Pinta las secciones "próximamente" de
 * la peña, cuya lista vive en `panel/secciones.ts` (compartida con el nav).
 *
 * Si el usuario tiene alguna área de administración concedida, se añade además
 * una tarjeta clicable "Administración" que lleva al índice `/panel/administracion`,
 * con una campanita del total de cosas sin atender (`AdminAvisosService`).
 */
@Component({
  selector: 'app-panel-inicio',
  imports: [RouterLink, AvisoPendientes],
  styleUrl: './inicio.css',
  templateUrl: './inicio.html',
})
export class PanelInicio implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly avisos = inject(AdminAvisosService);
  protected readonly secciones = SECCIONES;

  ngOnInit(): void {
    if (this.auth.usuarioActual()?.areas?.length) {
      this.avisos.refrescar();
    }
  }
}
```

- [ ] **Step 4: Actualizar la plantilla**

Modify `front/src/app/panel/inicio/inicio.html` — en el bloque `@if (auth.usuarioActual()?.areas?.length)`, cambia el `<h2>` suelto por una fila con la campanita:

```html
  @if (auth.usuarioActual()?.areas?.length) {
    <a
      routerLink="/panel/administracion"
      class="group relative overflow-hidden rounded-2xl border border-outline bg-panel p-6 transition hover:border-gold"
    >
      <div class="flex items-center justify-between gap-2">
        <h2 class="font-display text-lg font-bold transition group-hover:text-gold">Administración</h2>
        <app-aviso-pendientes [cuenta]="avisos.total()" />
      </div>
      <p class="mt-2 text-sm leading-relaxed text-muted">
        Solicitudes de acceso y permisos de los miembros de la peña.
      </p>
    </a>
  }
```

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `cd front && npx ng test --watch=false --include='src/app/panel/inicio/inicio.spec.ts'`
Expected: PASS (3 tests).

- [ ] **Step 6: Prettier + commit**

```bash
cd front && npx prettier --write src/app/panel/inicio/inicio.ts src/app/panel/inicio/inicio.html src/app/panel/inicio/inicio.spec.ts
git add front/src/app/panel/inicio/
git commit -m "feat(avisos): campanita del total en la tarjeta Administración"
```

---

## Task 6: Cablear las tarjetas del índice de administración (front)

**Files:**
- Modify: `front/src/app/admin/indice/indice.ts`
- Modify: `front/src/app/admin/indice/indice.html`
- Test: `front/src/app/admin/indice/indice.spec.ts`

**Interfaces:**
- Consumes: `AdminAvisosService` (Task 3: `refrescar()`, `pendientes()`), `AvisoPendientes` (Task 4).
- Produces: `AdminIndice implements OnInit`; `ngOnInit` llama `avisos.refrescar()`. Cada tarjeta pinta `<app-aviso-pendientes [cuenta]="avisos.pendientes()[seccion.area] ?? 0" />`.

- [ ] **Step 1: Actualizar el test**

Modify `front/src/app/admin/indice/indice.spec.ts` — reemplaza el contenido por:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { environment } from '../../../environments/environment';
import { PendientesPorArea } from '../admin.types';
import { AdminIndice } from './indice';

/**
 * Tests de `AdminIndice`: pinta como tarjetas solo las secciones para las que el
 * usuario tiene el área; en `ngOnInit` pide `GET /admin/pendientes` y cada
 * tarjeta muestra su campanita (nada si esa área está a 0).
 */
describe('AdminIndice · tarjetas por área', () => {
  let areas: string[] = [];
  const authFalso: Partial<AuthService> = {
    tieneArea: (area: string) => areas.includes(area),
  };
  const pendientesUrl = `${environment.apiBaseUrl}/admin/pendientes`;

  let httpMock: HttpTestingController;

  beforeEach(() => {
    areas = [];
    TestBed.configureTestingModule({
      imports: [AdminIndice],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authFalso },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function render(areasConcedidas: string[], pendientes: PendientesPorArea = {}): HTMLElement {
    areas = areasConcedidas;
    const fixture = TestBed.createComponent(AdminIndice);
    fixture.detectChanges();
    httpMock.expectOne(pendientesUrl).flush(pendientes);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('sin áreas: muestra el aviso', () => {
    expect(render([]).textContent).toContain('No tienes ninguna sección de administración disponible.');
  });

  it('con ADMIN_SOLICITUDES: tarjeta "Solicitudes" presente, "Permisos" ausente', () => {
    const texto = render(['ADMIN_SOLICITUDES']).textContent ?? '';
    expect(texto).toContain('Solicitudes');
    expect(texto).not.toContain('Permisos');
  });

  it('la tarjeta de un área con pendientes muestra el número; la de 0 no', () => {
    const el = render(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS'], { ADMIN_SOLICITUDES: 4 });
    const status = el.querySelectorAll('[role="status"]');
    expect(status.length).toBe(1);
    expect(status[0].textContent).toContain('4');
  });
});
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npx ng test --watch=false --include='src/app/admin/indice/indice.spec.ts'`
Expected: FAIL — `AdminIndice` no pide `/admin/pendientes` (y no importa `AvisoPendientes`).

- [ ] **Step 3: Actualizar el componente**

Modify `front/src/app/admin/indice/indice.ts`:

1. Imports nuevos:

```ts
import { Component, OnInit, inject } from '@angular/core';
import { AdminAvisosService } from '../admin-avisos.service';
import { AvisoPendientes } from '../../shared/aviso-pendientes/aviso-pendientes';
```

2. `imports` del `@Component`: añade `AvisoPendientes` → `imports: [RouterLink, Volver, AvisoPendientes]`.

3. La clase pasa a implementar `OnInit` e inyecta el servicio:

```ts
export class AdminIndice implements OnInit {
  private readonly auth = inject(AuthService);
  protected readonly avisos = inject(AdminAvisosService);

  /** Etiqueta legible de cada área (`AREAS['ADMIN_SOLICITUDES'] === 'Solicitudes'`). */
  protected readonly etiquetas = AREAS;

  /** Secciones que este usuario puede abrir, según sus áreas. */
  protected readonly disponibles = SECCIONES_ADMIN.filter((s) => this.auth.tieneArea(s.area));

  ngOnInit(): void {
    this.avisos.refrescar();
  }
}
```

- [ ] **Step 4: Actualizar la plantilla**

Modify `front/src/app/admin/indice/indice.html` — dentro del `@for`, envuelve el `<h2>` en una fila con la campanita:

```html
      @for (seccion of disponibles; track seccion.area) {
        <a
          [routerLink]="seccion.ruta"
          class="group relative overflow-hidden rounded-2xl border border-outline bg-panel p-6 transition hover:border-gold"
        >
          <div class="flex items-center justify-between gap-2">
            <h2 class="font-display text-lg font-bold transition group-hover:text-gold">
              {{ etiquetas[seccion.area] }}
            </h2>
            <app-aviso-pendientes [cuenta]="avisos.pendientes()[seccion.area] ?? 0" />
          </div>
          <p class="mt-2 text-sm leading-relaxed text-muted">{{ seccion.descripcion }}</p>
        </a>
      }
```

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `cd front && npx ng test --watch=false --include='src/app/admin/indice/indice.spec.ts'`
Expected: PASS (3 tests).

- [ ] **Step 6: Prettier + commit**

```bash
cd front && npx prettier --write src/app/admin/indice/indice.ts src/app/admin/indice/indice.html src/app/admin/indice/indice.spec.ts
git add front/src/app/admin/indice/
git commit -m "feat(avisos): campanita por área en el índice de administración"
```

---

## Task 7: Refrescar el recuento tras resolver una solicitud (front)

**Files:**
- Modify: `front/src/app/admin/solicitudes/solicitudes.ts`
- Test: `front/src/app/admin/solicitudes/solicitudes.spec.ts`

**Interfaces:**
- Consumes: `AdminAvisosService` (Task 3: `refrescar()`).
- Produces: tras aprobar o rechazar con éxito, `AdminSolicitudes` llama `this.avisos.refrescar()` (además de recargar su lista).

- [ ] **Step 1: Escribir el test que falla**

Modify `front/src/app/admin/solicitudes/solicitudes.spec.ts`. Primero mira cómo están montados los tests actuales (mismo patrón que `permisos.spec.ts`: `AdminService` real + `HttpTestingController`). Añade la URL de pendientes y un test nuevo. En el `describe`, junto a las demás constantes:

```ts
  const pendientesUrl = `${base}/admin/pendientes`;
```

Y un test nuevo (colócalo tras el de aprobar existente):

```ts
  it('tras aprobar una solicitud se vuelve a pedir el recuento de pendientes', async () => {
    await iniciarConLista([solicitud]); // helper existente que hace flush de /admin/solicitudes

    boton('Aprobar').click();
    fixture.detectChanges();

    httpMock
      .expectOne(`${base}/admin/solicitudes/${solicitud.id}/aprobar`)
      .flush({ resultado: 'CUENTA_CREADA' });
    fixture.detectChanges();
    await fixture.whenStable();

    // recarga de la lista + recuento de la campanita
    httpMock.expectOne(listaUrl).flush([]);
    httpMock.expectOne(pendientesUrl).flush({});
    fixture.detectChanges();
    await fixture.whenStable();
  });
```

> **Antes de escribir:** abre `solicitudes.spec.ts` y ajusta los nombres reales
> (`solicitud` / `solicitudes`, `listaUrl`, el helper de arranque, la etiqueta
> exacta del botón "Aprobar"). El patrón de `permisos.spec.ts` es la referencia:
> `iniciarConLista`, `boton()`, `httpMock.expectOne(...).flush(...)`.
> `afterEach(() => httpMock.verify())` obliga a consumir la petición nueva: sin
> el `refrescar()` en el componente, el test falla porque `expectOne(pendientesUrl)`
> no encuentra ninguna petición.

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npx ng test --watch=false --include='src/app/admin/solicitudes/solicitudes.spec.ts'`
Expected: FAIL — `expectOne(pendientesUrl)` no encuentra la petición (el componente no la hace).

- [ ] **Step 3: Actualizar el componente**

Modify `front/src/app/admin/solicitudes/solicitudes.ts`:

1. Import:

```ts
import { AdminAvisosService } from '../admin-avisos.service';
```

2. Inyección junto a `adminService`:

```ts
  private readonly avisos = inject(AdminAvisosService);
```

3. En `aprobar()`, dentro del `next`, después de `this.cargar();`:

```ts
        this.cargar();
        this.avisos.refrescar();
```

4. Lo mismo en `confirmarRechazo()`, dentro del `next`, tras `this.cargar();`:

```ts
        this.cargar();
        this.avisos.refrescar();
```

- [ ] **Step 4: Ejecutar y ver pasar**

Run: `cd front && npx ng test --watch=false --include='src/app/admin/solicitudes/solicitudes.spec.ts'`
Expected: PASS (los tests existentes + el nuevo). Si algún test existente de aprobar/rechazar ahora falla por una petición `pendientesUrl` sin consumir, añádele `httpMock.expectOne(pendientesUrl).flush({});` tras el `flush` de la recarga de la lista.

- [ ] **Step 5: Prettier + commit**

```bash
cd front && npx prettier --write src/app/admin/solicitudes/solicitudes.ts src/app/admin/solicitudes/solicitudes.spec.ts
git add front/src/app/admin/solicitudes/
git commit -m "feat(avisos): refrescar el recuento al resolver una solicitud"
```

---

## Task 8: Verificación completa

- [ ] **Step 1: Suite backend completa**

Run: `cd back && ./mvnw.cmd verify`
Expected: BUILD SUCCESS. Necesita Docker en marcha.

- [ ] **Step 2: Suite frontend completa**

Run: `cd front && npx ng test --watch=false`
Expected: todos los ficheros de test en verde.

- [ ] **Step 3: Comprobación manual (opcional pero recomendable)**

Con el back y el front en marcha, entrar como admin/superadmin:
- Sin solicitudes pendientes: la tarjeta "Administración" y la de "Solicitudes" no muestran campanita.
- Crear una solicitud de acceso (desde `/solicitar-acceso` con un teléfono no autorizado). Recargar `/panel`: la tarjeta "Administración" muestra "1"; en `/panel/administracion` la tarjeta "Solicitudes" muestra "1".
- Aprobar/rechazar la solicitud: el número baja a 0 y la campanita desaparece sin recargar.

- [ ] **Step 4: Merge de la rama**

Seguir `superpowers:finishing-a-development-branch` (fast-forward a `main`, patrón del repo).

---

## Self-review (hecho al escribir el plan)

**Cobertura del spec:**
- Contrato `ContadorPendientes` + impl solicitudes → Task 1. ✓
- Repo `countByPenaIdAndEstado` → Task 1. ✓
- `AdminService.pendientesPorArea` (filtra por áreas, omite ceros) → Task 2 (unit + IT). ✓
- Endpoint `GET /api/v1/admin/pendientes`, autofiltrado, `{}` sin áreas → Task 2. ✓
- Tipo `PendientesPorArea` → Task 3. ✓
- `AdminAvisosService` (`pendientes`, `total`, `refrescar`, silencioso ante error) → Task 3. ✓
- Componente `app-aviso-pendientes` (nada si 0, `99+`, aria) → Task 4. ✓
- Home del panel: total + `refrescar` en init con guarda de áreas → Task 5. ✓
- Índice: por área + `refrescar` en init → Task 6. ✓
- Solicitudes: `refrescar` tras resolver → Task 7. ✓
- Sin móvil, sin migración → respetado (no hay tareas de eso). ✓

**Placeholders:** el Step 1 de la Task 1 contiene a propósito un helper a medio
hacer con un `throw`; el Step 3 lo sustituye por el código real completo. No hay
otros TBD.

**Consistencia de tipos:**
- `pendientesPorArea(Long): Map<AreaProtegida, Long>` — mismo nombre y firma en Task 2 (servicio), Task 2 (controlador la usa), tests.
- `refrescar(): void`, `pendientes: Signal<PendientesPorArea>`, `total: Signal<number>` — mismos en Tasks 3, 5, 6, 7.
- `cuenta = input.required<number>()` — Task 4, usado como `[cuenta]` en Tasks 5 y 6.
- `PendientesPorArea = Partial<Record<Area, number>>` — Task 3, usado en Tasks 3 y 6.
