# Panel de administración — Backend — Plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usa superpowers:subagent-driven-development
> para ejecutar este plan tarea a tarea. Los pasos usan checkbox (`- [ ]`).

**Goal:** Añadir al backend el modelo de permisos por área, los endpoints de
administración (revisar/aprobar/rechazar solicitudes, gestionar rol/estado/áreas
de los miembros) y el envío de correo transaccional.

**Architecture:** Paquete nuevo `com.baniterio.api.admin` (controlador + servicio
+ DTOs + excepciones). Paquete nuevo `com.baniterio.api.email` (`ServicioEmail`
+ implementaciones + plantillas). `ServicioPermisos` en `com.baniterio.api.auth`
es el punto único de verdad de "¿este usuario puede X?". La aprobación de
solicitudes crea el usuario en una transacción y encola el correo con un
`@TransactionalEventListener(AFTER_COMMIT)`.

**Tech Stack:** Spring Boot 4.1.1, Java 17, Spring Security 7, JPA/Hibernate,
Flyway, PostgreSQL, `spring-boot-starter-mail`, jjwt 0.12.6. Tests: JUnit 5 +
Testcontainers (Postgres 17) vía `maven-failsafe-plugin` (`mvn verify`).

**Spec:** `docs/superpowers/specs/2026-08-29-panel-administracion-design.md`

## Global Constraints

- **Java 17.** No subir la versión. `back/pom.xml` `<java.version>17</java.version>`.
- **Migraciones Flyway inmutables.** V1–V7 ya aplicadas: NO tocarlas (ni comentarios
  — cambia el checksum y el arranque falla). La nueva es **V8**.
- **`ddl-auto: validate`.** Las entidades JPA deben cuadrar exactamente con el
  esquema que crea Flyway, o la app no arranca.
- **Estilo de entidades:** Lombok (`@Getter @Setter @NoArgsConstructor
  @AllArgsConstructor @Builder`), `@Id @GeneratedValue(strategy =
  GenerationType.IDENTITY) private Long id;`, `@CreationTimestamp` para
  `created_at`. Cabecera Javadoc explicativa en cada clase nueva (proyecto de
  aprendizaje).
- **Respuestas de error:** lanzar excepción de dominio; traducir a HTTP en
  `ApiExceptionHandler` con cuerpo `{ "codigo": "..." }` (código estable en
  MAYÚSCULAS_CON_GUION_BAJO).
- **Peña piloto:** se resuelve por slug `"baniterio"` (constante local, como en
  `AuthService`). Sin multipeña.
- **Idioma:** identificadores y comentarios en español, como el resto del código.
- **Tests:** cada tarea deja `mvn -q verify` en verde (desde `back/`). Los `*IT`
  extienden `com.baniterio.api.support.IntegrationTest`.
- **Correo en tests:** nunca se envía de verdad. Se usa `@MockitoBean ServicioEmail`.

---

## File Structure

**Nuevos:**
- `back/src/main/resources/db/migration/V8__permiso_area_y_password_solicitud.sql`
- `back/src/main/java/com/baniterio/api/identidad/AreaProtegida.java` — enum de áreas protegidas
- `back/src/main/java/com/baniterio/api/identidad/PermisoArea.java` — entidad
- `back/src/main/java/com/baniterio/api/identidad/PermisoAreaRepository.java`
- `back/src/main/java/com/baniterio/api/auth/ServicioPermisos.java` — acceso efectivo a áreas
- `back/src/main/java/com/baniterio/api/email/ServicioEmail.java` — interfaz
- `back/src/main/java/com/baniterio/api/email/EmailLog.java` — impl por defecto (log)
- `back/src/main/java/com/baniterio/api/email/EmailSmtp.java` — impl SMTP
- `back/src/main/java/com/baniterio/api/email/PlantillasCorreo.java` — textos
- `back/src/main/java/com/baniterio/api/admin/AdminController.java`
- `back/src/main/java/com/baniterio/api/admin/AdminService.java`
- `back/src/main/java/com/baniterio/api/admin/dto/*.java` — records de petición/respuesta
- `back/src/main/java/com/baniterio/api/admin/SolicitudResueltaEvent.java`
- `back/src/main/java/com/baniterio/api/admin/ManejadorCorreoSolicitud.java`
- `back/src/main/java/com/baniterio/api/admin/*Exception.java` — excepciones de dominio
- `back/src/test/java/com/baniterio/api/auth/ServicioPermisosIT.java`
- `back/src/test/java/com/baniterio/api/admin/AdminSolicitudesIT.java`
- `back/src/test/java/com/baniterio/api/admin/AdminMiembrosIT.java`
- `back/src/test/java/com/baniterio/api/email/PlantillasCorreoTest.java`

**Modificados:**
- `back/pom.xml` — `spring-boot-starter-mail`
- `back/src/main/resources/application.yml` — bloque `app.email`
- `back/src/main/java/com/baniterio/api/config/AppProperties.java` — `Email`
- `back/src/main/java/com/baniterio/api/auth/dto/UsuarioResponse.java` — `rol`, `areas`
- `back/src/main/java/com/baniterio/api/auth/AuthService.java` — `login` con rol/áreas; `solicitarIngreso` con password
- `back/src/main/java/com/baniterio/api/auth/AuthController.java` — `yo` con rol/áreas
- `back/src/main/java/com/baniterio/api/auth/dto/SolicitudIngresoRequest.java` — `password` opcional
- `back/src/main/java/com/baniterio/api/identidad/SolicitudIngreso.java` — `passwordHash`
- `back/src/main/java/com/baniterio/api/identidad/MembresiaRepository.java` — `findByPenaIdAndRol`
- `back/src/main/java/com/baniterio/api/identidad/TelefonoAutorizadoRepository.java` — `findByTelefono`
- `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java` — códigos nuevos
- `back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java` — V8
- `back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java` — asserts de rol/áreas y password en solicitud

---

## Task 1: Migración V8, enum `AreaProtegida`, entidad `PermisoArea`, `passwordHash` en `SolicitudIngreso`

**Files:**
- Create: `back/src/main/resources/db/migration/V8__permiso_area_y_password_solicitud.sql`
- Create: `back/src/main/java/com/baniterio/api/identidad/AreaProtegida.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/PermisoArea.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/PermisoAreaRepository.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/SolicitudIngreso.java`
- Modify: `back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java`

**Interfaces:**
- Produces:
  - `enum AreaProtegida { ADMIN_SOLICITUDES, ADMIN_PERMISOS }`
  - `class PermisoArea` con `Long getId()`, `Usuario getUsuario()`, `AreaProtegida getArea()`, `Usuario getConcedidoPor()`, `Instant getCreatedAt()` + `@Builder`
  - `interface PermisoAreaRepository extends JpaRepository<PermisoArea, Long>`:
    `List<PermisoArea> findByUsuarioId(Long usuarioId)`,
    `boolean existsByUsuarioIdAndArea(Long usuarioId, AreaProtegida area)`,
    `void deleteByUsuarioId(Long usuarioId)`
  - `SolicitudIngreso.getPasswordHash()` / `setPasswordHash(String)` (nullable)

- [ ] **Step 1: Escribir la migración V8**

Create `back/src/main/resources/db/migration/V8__permiso_area_y_password_solicitud.sql`:

```sql
-- Áreas del panel a las que se puede conceder acceso a un usuario concreto.
-- Un admin/superadmin las tiene todas de forma implícita (no aparecen aquí).
CREATE TABLE permiso_area (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    usuario_id    BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    area          VARCHAR(40) NOT NULL,
    concedido_por BIGINT      REFERENCES usuario (id),
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_permiso_area UNIQUE (usuario_id, area)
);

-- Hash BCrypt de la contraseña que el solicitante puso al intentar registrarse.
-- Puede faltar (llegó directo a "solicitar acceso" o refrescó la página).
ALTER TABLE solicitud_ingreso ADD COLUMN password_hash VARCHAR(72);
```

- [ ] **Step 2: Crear el enum `AreaProtegida`**

Create `back/src/main/java/com/baniterio/api/identidad/AreaProtegida.java`:

```java
package com.baniterio.api.identidad;

/**
 * Áreas del panel cuyo acceso se puede conceder a un usuario concreto que no
 * es admin. Un {@code es_superadmin} o un miembro con rol {@code ADMIN} tiene
 * todas de forma implícita; al resto se le conceden una a una
 * ({@link PermisoArea}).
 *
 * <p>Añadir un valor aquí = nueva área protegida: aparece sola en la pantalla
 * de permisos y en {@code GET /api/v1/auth/yo}. De momento solo las dos
 * secciones del panel de administración.
 */
public enum AreaProtegida {
    ADMIN_SOLICITUDES,
    ADMIN_PERMISOS
}
```

- [ ] **Step 3: Crear la entidad `PermisoArea`**

Create `back/src/main/java/com/baniterio/api/identidad/PermisoArea.java` siguiendo
el patrón de `Membresia.java`: entidad `@Table(name = "permiso_area")`, Lombok
completo, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`,
`@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "usuario_id",
nullable = false) Usuario usuario`, `@Enumerated(EnumType.STRING) @Column(nullable
= false, length = 40) AreaProtegida area`, `@ManyToOne(fetch = LAZY)
@JoinColumn(name = "concedido_por") Usuario concedidoPor`, `@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false) Instant
createdAt`. Cabecera Javadoc: "Concesión explícita de acceso a un
{@link AreaProtegida} para un usuario que no es admin."

- [ ] **Step 4: Crear `PermisoAreaRepository`**

```java
package com.baniterio.api.identidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link PermisoArea} (concesiones de área por usuario). */
public interface PermisoAreaRepository extends JpaRepository<PermisoArea, Long> {

    List<PermisoArea> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndArea(Long usuarioId, AreaProtegida area);

    void deleteByUsuarioId(Long usuarioId);
}
```

- [ ] **Step 5: Añadir `passwordHash` a `SolicitudIngreso`**

En `back/src/main/java/com/baniterio/api/identidad/SolicitudIngreso.java`, añadir
tras el campo `mote`:

```java
    /**
     * Hash BCrypt de la contraseña que el solicitante puso al intentar
     * registrarse. Si viene, al aprobar la solicitud se crea el usuario ya con
     * contraseña; si es {@code null}, al aprobar solo se autoriza el teléfono.
     */
    @Column(name = "password_hash", length = 72)
    private String passwordHash;
```

- [ ] **Step 6: Ampliar `FlywayMigrationIT` para V8**

Leer el fichero actual. Añadir aserciones (en el estilo que ya use) de que tras
migrar existen: la tabla `permiso_area` con columnas `id, usuario_id, area,
concedido_por, created_at` y la restricción única `uk_permiso_area`; y la
columna `solicitud_ingreso.password_hash`. Si el test itera un listado
esperado de tablas/columnas, añadir las nuevas.

- [ ] **Step 7: Verificar y commitear**

Run: `cd back && ./mvnw -q verify` (desde Windows: `mvnw.cmd`). Expected: PASS
(Flyway aplica V8 sobre el Postgres de Testcontainers, Hibernate `validate` no
se queja de `PermisoArea` ni de `passwordHash`).

```bash
git add back/src/main/resources/db/migration/V8__permiso_area_y_password_solicitud.sql \
        back/src/main/java/com/baniterio/api/identidad/AreaProtegida.java \
        back/src/main/java/com/baniterio/api/identidad/PermisoArea.java \
        back/src/main/java/com/baniterio/api/identidad/PermisoAreaRepository.java \
        back/src/main/java/com/baniterio/api/identidad/SolicitudIngreso.java \
        back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java
git commit -m "feat(back): V8 permiso_area + password_hash en solicitud_ingreso"
```

---

## Task 2: `ServicioPermisos`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/auth/ServicioPermisos.java`
- Create: `back/src/test/java/com/baniterio/api/auth/ServicioPermisosIT.java`

**Interfaces:**
- Consumes: `AreaProtegida`, `PermisoAreaRepository` (Task 1); `MembresiaRepository`,
  `PenaRepository`, `UsuarioRepository`, `RolMembresia`, `Membresia`, `Usuario`.
- Produces: `@Service class ServicioPermisos` con:
  - `boolean puede(Long usuarioId, AreaProtegida area)`
  - `java.util.Set<AreaProtegida> areasDe(Long usuarioId)`
  - `RolMembresia rolDe(Long usuarioId)` — puede devolver `null`
  - `boolean esAdministrador(Long usuarioId)`

- [ ] **Step 1: Escribir el test `ServicioPermisosIT`**

Create `back/src/test/java/com/baniterio/api/auth/ServicioPermisosIT.java` extendiendo
`IntegrationTest`. Inyectar `ServicioPermisos` y los repos de identidad + `PasswordEncoder`.
Helper que cree un `Usuario` (activo, `esSuperadmin` param) + `Membresia` (rol param,
activa) en la peña `baniterio` (resolver con `penas.findBySlug("baniterio")`).

Casos:
```java
@Test void superadmin_puede_todas_las_areas() {
    Long id = crearUsuario(true, RolMembresia.ADMIN);
    assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_SOLICITUDES)).isTrue();
    assertThat(servicioPermisos.areasDe(id))
        .containsExactlyInAnyOrder(AreaProtegida.values());
    assertThat(servicioPermisos.esAdministrador(id)).isTrue();
}

@Test void admin_no_superadmin_puede_todas_las_areas() {
    Long id = crearUsuario(false, RolMembresia.ADMIN);
    assertThat(servicioPermisos.areasDe(id)).containsExactlyInAnyOrder(AreaProtegida.values());
}

@Test void miembro_sin_concesiones_no_puede_ninguna() {
    Long id = crearUsuario(false, RolMembresia.MIEMBRO);
    assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_PERMISOS)).isFalse();
    assertThat(servicioPermisos.areasDe(id)).isEmpty();
    assertThat(servicioPermisos.esAdministrador(id)).isFalse();
}

@Test void miembro_con_una_concesion_puede_solo_esa() {
    Long id = crearUsuario(false, RolMembresia.MIEMBRO);
    permisos.save(PermisoArea.builder()
        .usuario(usuarios.findById(id).orElseThrow())
        .area(AreaProtegida.ADMIN_SOLICITUDES)
        .build());
    assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_SOLICITUDES)).isTrue();
    assertThat(servicioPermisos.puede(id, AreaProtegida.ADMIN_PERMISOS)).isFalse();
    assertThat(servicioPermisos.areasDe(id)).containsExactly(AreaProtegida.ADMIN_SOLICITUDES);
}

@Test void rolDe_es_null_si_no_es_miembro() {
    Usuario u = usuarios.save(Usuario.builder().telefono(tel()).email(tel()+"@x.com")
        .passwordHash("x").nombre("N").apellidos("A").esSuperadmin(false).activo(true).build());
    assertThat(servicioPermisos.rolDe(u.getId())).isNull();
}
```

- [ ] **Step 2: Ejecutar el test — debe fallar por compilación**

Run: `cd back && ./mvnw -q test-compile`. Expected: FAIL (`ServicioPermisos` no existe).

- [ ] **Step 3: Implementar `ServicioPermisos`**

```java
package com.baniterio.api.auth;

import java.util.EnumSet;
import java.util.Set;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoAreaRepository;
import com.baniterio.api.identidad.RolMembresia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Punto único de verdad de "¿este usuario puede acceder a esta área del panel?".
 *
 * <p>Regla: {@code es_superadmin} o rol {@code ADMIN} activo en la peña → todas
 * las áreas. Cualquier otro → solo las que tenga concedidas en
 * {@code permiso_area}. Los controladores de {@code admin/} preguntan aquí, no
 * se fían de lo que diga el cliente.
 */
@Service
public class ServicioPermisos {

    private static final String SLUG_PENA = "baniterio";

    private final MembresiaRepository membresias;
    private final PermisoAreaRepository permisos;
    private final PenaRepository penas;

    public ServicioPermisos(MembresiaRepository membresias, PermisoAreaRepository permisos,
                            PenaRepository penas) {
        this.membresias = membresias;
        this.permisos = permisos;
        this.penas = penas;
    }

    @Transactional(readOnly = true)
    public boolean esAdministrador(Long usuarioId) {
        return rolDe(usuarioId) == RolMembresia.ADMIN;
    }
    // OJO: un superadmin del proyecto SIEMPRE se registra con membresía ADMIN
    // (ver AuthService), así que basta con mirar el rol. Si en el futuro pudiera
    // haber un superadmin sin membresía, añadir aquí la comprobación de
    // usuario.esSuperadmin.

    @Transactional(readOnly = true)
    public RolMembresia rolDe(Long usuarioId) {
        Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
        return membresias.findByUsuarioIdAndPenaId(usuarioId, penaId)
                .filter(m -> m.isActiva())
                .map(m -> m.getRol())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean puede(Long usuarioId, AreaProtegida area) {
        return esAdministrador(usuarioId)
                || permisos.existsByUsuarioIdAndArea(usuarioId, area);
    }

    @Transactional(readOnly = true)
    public Set<AreaProtegida> areasDe(Long usuarioId) {
        if (esAdministrador(usuarioId)) {
            return EnumSet.allOf(AreaProtegida.class);
        }
        Set<AreaProtegida> resultado = EnumSet.noneOf(AreaProtegida.class);
        permisos.findByUsuarioId(usuarioId).forEach(p -> resultado.add(p.getArea()));
        return resultado;
    }
}
```

**Ruling de diseño (anótalo si el revisor lo cuestiona):** `esAdministrador` mira
solo el rol de la membresía, no `usuario.esSuperadmin`, porque en este proyecto el
fundador/superadmin siempre entra con membresía `ADMIN` (`AuthService.registrar`).
Coste si cambia esa invariante: un superadmin sin membresía no vería el panel —
se arregla añadiendo un `|| usuario.isEsSuperadmin()`.

- [ ] **Step 4: Ejecutar los tests**

Run: `cd back && ./mvnw -q verify`. Expected: PASS (incluido `ServicioPermisosIT`).

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/auth/ServicioPermisos.java \
        back/src/test/java/com/baniterio/api/auth/ServicioPermisosIT.java
git commit -m "feat(back): ServicioPermisos (acceso efectivo a áreas del panel)"
```

---

## Task 3: `UsuarioResponse` con `rol` + `areas`; `/login` y `/yo` los devuelven

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/auth/dto/UsuarioResponse.java`
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthService.java`
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthController.java`
- Modify: `back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: `ServicioPermisos` (Task 2), `AreaProtegida`, `RolMembresia`.
- Produces:
  - `record UsuarioResponse(Long id, String nombre, String apellidos, String mote,
    boolean esSuperadmin, String rol, List<String> areas)`
  - `UsuarioResponse.de(Usuario u)` → `rol = null`, `areas = List.of()` (para el 201 de registro)
  - `UsuarioResponse.de(Usuario u, RolMembresia rol, Collection<AreaProtegida> areas)`
  - `/login` (`LoginResponse.usuario`) y `GET /yo` devuelven `rol` + `areas` reales
    (consultados en vivo, NO desde el JWT).

- [ ] **Step 1: Ampliar el test `AuthControllerIT`**

Añadir asserts:
- En `login_correcto_devuelve_token_y_usuario`: el mapa `usuario` contiene
  `"rol"` y `"areas"` (lista). Para un registro normal `rol == "MIEMBRO"`,
  `areas` vacía.
- Nuevo test `el_fundador_ve_todas_las_areas`: registra al `TELEFONO_FUNDADOR`,
  hace login, y comprueba `usuario.get("rol") == "ADMIN"` y que `areas` contiene
  `"ADMIN_SOLICITUDES"` y `"ADMIN_PERMISOS"`.
- En `el_token_del_login_vale_para_yo`: el cuerpo de `/yo` también trae `"rol"` y
  `"areas"`.

- [ ] **Step 2: Ejecutar — debe fallar**

Run: `cd back && ./mvnw -q test-compile` → compila; `./mvnw -q verify -Dit.test=AuthControllerIT`
→ FAIL en los asserts nuevos (`rol`/`areas` ausentes).

- [ ] **Step 3: Reescribir `UsuarioResponse`**

```java
package com.baniterio.api.auth.dto;

import java.util.Collection;
import java.util.List;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;

/**
 * Vista pública de un usuario (JSON). Nunca incluye hash de contraseña ni
 * teléfono/email.
 *
 * <p>{@code rol} y {@code areas} solo se rellenan en las respuestas de
 * {@code /login} y {@code /yo} (con {@link #de(Usuario, RolMembresia,
 * Collection)}), que consultan permisos en vivo. El 201 de registro usa
 * {@link #de(Usuario)} y los deja vacíos (el cliente hace login a continuación).
 */
public record UsuarioResponse(
        Long id, String nombre, String apellidos, String mote, boolean esSuperadmin,
        String rol, List<String> areas) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getApellidos(), u.getMote(),
                u.isEsSuperadmin(), null, List.of());
    }

    public static UsuarioResponse de(Usuario u, RolMembresia rol, Collection<AreaProtegida> areas) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getApellidos(), u.getMote(),
                u.isEsSuperadmin(),
                rol == null ? null : rol.name(),
                areas.stream().map(Enum::name).sorted().toList());
    }
}
```

- [ ] **Step 4: `AuthService.login` usa el constructor rico**

Inyectar `ServicioPermisos` en el constructor de `AuthService` (añadir parámetro y
campo). En `login(...)`:

```java
        String token = jwtService.generar(usuario.getId(), usuario.isEsSuperadmin());
        UsuarioResponse dto = UsuarioResponse.de(usuario,
                servicioPermisos.rolDe(usuario.getId()),
                servicioPermisos.areasDe(usuario.getId()));
        return new LoginResponse(token, dto);
```

- [ ] **Step 5: `AuthController.yo` usa el constructor rico**

Inyectar `ServicioPermisos` en `AuthController`. En `yo(...)`:

```java
        return usuarios.findById(principal.id())
                .filter(Usuario::isActivo)
                .map(u -> UsuarioResponse.de(u,
                        servicioPermisos.rolDe(u.getId()),
                        servicioPermisos.areasDe(u.getId())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
```

- [ ] **Step 6: Verificar y commitear**

Run: `cd back && ./mvnw -q verify`. Expected: PASS (toda la suite, incluidos los
tests previos de auth que usan `Map` y siguen valiendo).

```bash
git add -A && git commit -m "feat(back): /login y /yo devuelven rol y areas del usuario"
```

---

## Task 4: `password` opcional en `POST /auth/solicitudes`

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/auth/dto/SolicitudIngresoRequest.java`
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthService.java`
- Modify: `back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: `SolicitudIngreso.setPasswordHash` (Task 1), `PasswordEncoder` (ya en `AuthService`).
- Produces: `SolicitudIngresoRequest.password()` (nullable). Contrato: los
  clientes envían `null` (no `""`) cuando no hay contraseña.

- [ ] **Step 1: Añadir el test**

En `AuthControllerIT`, sección "Solicitud de ingreso":

```java
@Test
void solicitud_con_password_guarda_el_hash() {
    String telefono = telefonoSinAutorizar();
    Map<String, Object> req = new java.util.HashMap<>(solicitudValida(telefono));
    req.put("password", "secreto1");

    http.post().uri("/api/v1/auth/solicitudes").body(req)
            .exchange().expectStatus().isCreated();

    var sol = solicitudes.findByPenaId(
            penas.findBySlug("baniterio").orElseThrow().getId()).stream()
            .filter(s -> s.getTelefono().equals(telefono)).findFirst().orElseThrow();
    assertThat(sol.getPasswordHash()).isNotBlank();
    assertThat(sol.getPasswordHash()).isNotEqualTo("secreto1"); // está hasheada
}

@Test
void solicitud_sin_password_no_falla_y_deja_hash_null() {
    String telefono = telefonoSinAutorizar();
    http.post().uri("/api/v1/auth/solicitudes").body(solicitudValida(telefono))
            .exchange().expectStatus().isCreated();

    var sol = solicitudes.findByPenaId(
            penas.findBySlug("baniterio").orElseThrow().getId()).stream()
            .filter(s -> s.getTelefono().equals(telefono)).findFirst().orElseThrow();
    assertThat(sol.getPasswordHash()).isNull();
}
```

- [ ] **Step 2: Ejecutar — debe fallar**

Run: `cd back && ./mvnw -q verify -Dit.test=AuthControllerIT` → FAIL (`password`
no se guarda; `getPasswordHash()` no compila si Task 1 no está — debería estarlo).

- [ ] **Step 3: Añadir el campo al DTO**

En `SolicitudIngresoRequest`, añadir como último parámetro del record:

```java
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String password) {
```

(sin `@NotBlank`: `null` es válido para `@Size`. Los clientes mandan `null`, no `""`).
Actualizar el Javadoc del record mencionando el campo opcional.

- [ ] **Step 4: Guardar el hash en `AuthService.solicitarIngreso`**

Antes de `return solicitudes.save(...)`, construir el builder y, si hay password,
setearla. Sustituir el `return` por:

```java
        SolicitudIngreso solicitud = SolicitudIngreso.builder()
                .pena(pena)
                .telefono(req.telefono())
                .email(req.email())
                .nombre(req.nombre())
                .apellidos(req.apellidos())
                .motivo(req.motivo())
                .relacion(req.relacion())
                .conocidos(req.conocidos())
                .estado(EstadoSolicitud.PENDIENTE)
                .build();

        if (org.springframework.util.StringUtils.hasText(req.password())) {
            solicitud.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        return solicitudes.save(solicitud);
```

- [ ] **Step 5: Verificar y commitear**

Run: `cd back && ./mvnw -q verify`. Expected: PASS.

```bash
git add -A && git commit -m "feat(back): password opcional en POST /auth/solicitudes"
```

---

## Task 5: `spring-boot-starter-mail`, `ServicioEmail`, `PlantillasCorreo`, config

**Files:**
- Modify: `back/pom.xml`
- Modify: `back/src/main/resources/application.yml`
- Modify: `back/src/main/java/com/baniterio/api/config/AppProperties.java`
- Create: `back/src/main/java/com/baniterio/api/email/ServicioEmail.java`
- Create: `back/src/main/java/com/baniterio/api/email/EmailLog.java`
- Create: `back/src/main/java/com/baniterio/api/email/EmailSmtp.java`
- Create: `back/src/main/java/com/baniterio/api/email/PlantillasCorreo.java`
- Create: `back/src/test/java/com/baniterio/api/email/PlantillasCorreoTest.java`

**Interfaces:**
- Produces:
  - `interface ServicioEmail { void enviar(String destinatario, String asunto, String cuerpo); }`
  - `record Correo(String asunto, String cuerpo)` (en `PlantillasCorreo` o suelto en el paquete)
  - `PlantillasCorreo` (`@Component`) con:
    `Correo aprobacionCuentaCreada(String nombre)`,
    `Correo aprobacionCompletaRegistro(String nombre)`,
    `Correo rechazo(String nombre, String motivo)`
  - `AppProperties.email()` → `record Email(String modo, String from, String enlaceRegistro)`

- [ ] **Step 1: Dependencia en `pom.xml`**

Añadir junto a los demás starters:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-mail</artifactId>
		</dependency>
```

- [ ] **Step 2: Config en `application.yml`**

Añadir al bloque `app:` (NO añadir `spring.mail` aquí — vive en
`back/config/application.yml` local y en variables de entorno en prod):

```yaml
  email:
    # "smtp" = envía de verdad (requiere spring.mail.* configurado) · "log" = solo log
    modo: ${MAIL_MODO:log}
    from: ${MAIL_FROM:Bañiterio <no-reply@baniterio.local>}
    enlace-registro: ${MAIL_ENLACE_REGISTRO:http://localhost:4200/registro}
```

- [ ] **Step 3: `AppProperties.Email`**

En `AppProperties`, añadir `Email email` al record principal y el sub-record:

```java
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Identidad identidad, Cors cors, Email email) {
    // ... records existentes ...
    public record Email(String modo, String from, String enlaceRegistro) {
    }
}
```

- [ ] **Step 4: `ServicioEmail` + `EmailLog` + `EmailSmtp`**

`ServicioEmail.java`:

```java
package com.baniterio.api.email;

/**
 * Envío de un correo de texto plano. Dos implementaciones seleccionadas por
 * {@code app.email.modo}: {@link EmailLog} (por defecto, solo escribe en el log)
 * y {@link EmailSmtp} (envía de verdad por SMTP).
 */
public interface ServicioEmail {
    void enviar(String destinatario, String asunto, String cuerpo);
}
```

`EmailLog.java` — `@Component @ConditionalOnProperty(name = "app.email.modo",
havingValue = "log", matchIfMissing = true)`. Loggea a `INFO`:
`"[EMAIL:log] para={} asunto={}\n{}"`. Cabecera Javadoc explicando que es el modo
de desarrollo.

`EmailSmtp.java` — `@Component @ConditionalOnProperty(name = "app.email.modo",
havingValue = "smtp")`. Constructor `(JavaMailSender mailSender, AppProperties
props)`. `enviar(...)` construye un `SimpleMailMessage` con `setFrom(props.email()
.from())`, `setTo`, `setSubject`, `setText`, y `mailSender.send(msg)`. Loggea
éxito a `INFO`. Si `JavaMailSender` no está disponible (no hay `spring.mail.host`)
el bean no se crea y el arranque falla con un mensaje claro — es configuración
incorrecta (modo smtp sin SMTP), aceptable.

- [ ] **Step 5: Escribir `PlantillasCorreoTest`**

```java
package com.baniterio.api.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlantillasCorreoTest {

    private final PlantillasCorreo plantillas = new PlantillasCorreo();

    @Test void aprobacion_cuenta_creada_incluye_el_nombre_y_habla_de_iniciar_sesion() {
        var c = plantillas.aprobacionCuentaCreada("Marta");
        assertThat(c.asunto()).isNotBlank();
        assertThat(c.cuerpo()).contains("Marta").containsIgnoringCase("iniciar sesión");
    }

    @Test void aprobacion_completa_registro_incluye_indicacion_de_registrarse() {
        var c = plantillas.aprobacionCompletaRegistro("Luis");
        assertThat(c.cuerpo()).contains("Luis").containsIgnoringCase("registr");
    }

    @Test void rechazo_incluye_el_motivo() {
        var c = plantillas.rechazo("Ana", "No hemos podido confirmar tu vinculación.");
        assertThat(c.cuerpo()).contains("Ana").contains("No hemos podido confirmar tu vinculación.");
    }
}
```

- [ ] **Step 6: Ejecutar — debe fallar**

Run: `cd back && ./mvnw -q test-compile` → FAIL (`PlantillasCorreo` no existe).

- [ ] **Step 7: Implementar `PlantillasCorreo`**

`@Component`. Constructor `(AppProperties props)` para leer `enlaceRegistro`.
`record Correo(String asunto, String cuerpo)` como tipo anidado público estático
o clase suelta en el paquete. Textos en español, tono cercano de la peña, texto
plano. Ejemplo del cuerpo de `aprobacionCuentaCreada`:

```
¡Hola {nombre}!

Tu solicitud para entrar en la Bañiterio ha sido aprobada. Ya te hemos creado
el usuario: entra con tu número de teléfono y la contraseña que pusiste al
solicitar el acceso.

Puedes iniciar sesión aquí: {enlaceRegistro sin la parte /registro, o el enlace de login}

¡Nos vemos en la peña!
```

Para `aprobacionCompletaRegistro`: indica entrar en `{enlaceRegistro}` y crear la
cuenta con su teléfono. Para `rechazo`: incluye `{motivo}` literal. Asuntos:
"Tu acceso a la Bañiterio: aprobado" / "Sobre tu solicitud de acceso a la Bañiterio".

- [ ] **Step 8: Verificar y commitear**

Run: `cd back && ./mvnw -q verify`. Expected: PASS (arranca en `modo: log`;
`EmailLog` es el bean activo).

```bash
git add -A && git commit -m "feat(back): ServicioEmail (log/smtp) + plantillas de correo"
```

---

## Task 6: Admin — listar / aprobar / rechazar solicitudes + correo

**Files:**
- Create: `back/src/main/java/com/baniterio/api/admin/AdminController.java`
- Create: `back/src/main/java/com/baniterio/api/admin/AdminService.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/SolicitudResumen.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/RechazoRequest.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/AprobarResponse.java`
- Create: `back/src/main/java/com/baniterio/api/admin/SinPermisoException.java`
- Create: `back/src/main/java/com/baniterio/api/admin/SolicitudYaResueltaException.java`
- Create: `back/src/main/java/com/baniterio/api/admin/SolicitudResueltaEvent.java`
- Create: `back/src/main/java/com/baniterio/api/admin/ManejadorCorreoSolicitud.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/TelefonoAutorizadoRepository.java`
- Create: `back/src/test/java/com/baniterio/api/admin/AdminSolicitudesIT.java`

**Interfaces:**
- Consumes: `ServicioPermisos.puede` (Task 2), `AreaProtegida.ADMIN_SOLICITUDES`,
  `SolicitudIngreso` (+`passwordHash`), `PlantillasCorreo` + `ServicioEmail` (Task 5),
  `UsuarioPrincipal`, repos de identidad, `PasswordEncoder`.
- Produces (contrato HTTP que consumen los planes web/móvil):
  - `GET /api/v1/admin/solicitudes?estado=PENDIENTE` → `200 List<SolicitudResumen>`
  - `POST /api/v1/admin/solicitudes/{id}/aprobar` → `200 AprobarResponse`
  - `POST /api/v1/admin/solicitudes/{id}/rechazar` (body `RechazoRequest`) → `204`
  - `record SolicitudResumen(Long id, String nombre, String apellidos, String telefono,
    String email, String motivo, String relacion, String conocidos, boolean traeContrasena,
    String estado, java.time.Instant createdAt)`
  - `record RechazoRequest(@Size(max = 2000) String motivo)` (nullable)
  - `record AprobarResponse(String resultado)` — `"CUENTA_CREADA"` | `"TELEFONO_AUTORIZADO"`
  - Códigos de error nuevos: `403 SIN_PERMISO`, `409 SOLICITUD_YA_RESUELTA`,
    `409 YA_REGISTRADO` (reutilizado).

- [ ] **Step 1: `TelefonoAutorizadoRepository.findByTelefono`**

Añadir: `Optional<TelefonoAutorizado> findByTelefono(String telefono);`

- [ ] **Step 2: Excepciones + `ApiExceptionHandler`**

`SinPermisoException extends RuntimeException` y `SolicitudYaResueltaException
extends RuntimeException` en `com.baniterio.api.admin`, con Javadoc de una línea.

En `ApiExceptionHandler` añadir:

```java
    @ExceptionHandler(com.baniterio.api.admin.SinPermisoException.class)
    ResponseEntity<Map<String, Object>> sinPermiso() {
        return error(HttpStatus.FORBIDDEN, "SIN_PERMISO");
    }

    @ExceptionHandler(com.baniterio.api.admin.SolicitudYaResueltaException.class)
    ResponseEntity<Map<String, Object>> solicitudYaResuelta() {
        return error(HttpStatus.CONFLICT, "SOLICITUD_YA_RESUELTA");
    }
```

- [ ] **Step 3: DTOs y evento**

`SolicitudResumen`, `RechazoRequest`, `AprobarResponse` como records en
`admin/dto/`. `SolicitudResueltaEvent` como record en `admin/`:

```java
public record SolicitudResueltaEvent(
        String email, String nombre, boolean aprobada, boolean cuentaCreada, String motivoRechazo) {
}
```

- [ ] **Step 4: Escribir `AdminSolicitudesIT`**

Extiende `IntegrationTest`. `@MockitoBean ServicioEmail servicioEmail;` (para
capturar los envíos con `ArgumentCaptor` / `Mockito.verify`). Helpers:
- `registrarFundadorYLoguear()` → token del fundador (que es admin).
- `crearMiembroSinPermisos()` → token de un usuario MIEMBRO normal.
- `crearSolicitudPendiente(boolean conPassword)` → devuelve el id (usa el
  endpoint público `POST /auth/solicitudes` con/sin `password`).

Casos:
```java
@Test void listar_solicitudes_pendientes_requiere_area() {
    String token = crearMiembroSinPermisos();
    http.get().uri("/api/v1/admin/solicitudes")
        .header(AUTHORIZATION, "Bearer " + token)
        .exchange().expectStatus().isForbidden()
        .expectBody(Map.class).returnResult().getResponseBody(); // codigo SIN_PERMISO
}

@Test void aprobar_con_password_crea_usuario_membresia_y_telefono_usado() {
    String admin = registrarFundadorYLoguear();
    Long id = crearSolicitudPendiente(true);

    Map<String,Object> r = http.post().uri("/api/v1/admin/solicitudes/" + id + "/aprobar")
        .header(AUTHORIZATION, "Bearer " + admin)
        .exchange().expectStatus().isOk()
        .expectBody(Map.class).returnResult().getResponseBody();
    assertThat(r.get("resultado")).isEqualTo("CUENTA_CREADA");

    // el solicitante ya puede iniciar sesión con la contraseña que puso
    // (la solicitud se creó con password "secreto1")
    http.post().uri("/api/v1/auth/login")
        .body(Map.of("telefono", telefonoDeLaSolicitud, "password", "secreto1"))
        .exchange().expectStatus().isOk();

    verify(servicioEmail).enviar(eq(emailDeLaSolicitud), any(), contains("iniciar sesión"));
}

@Test void aprobar_sin_password_solo_autoriza_el_telefono() {
    String admin = registrarFundadorYLoguear();
    Long id = crearSolicitudPendiente(false);
    Map<String,Object> r = http.post().uri("/api/v1/admin/solicitudes/" + id + "/aprobar")
        .header(AUTHORIZATION, "Bearer " + admin)
        .exchange().expectStatus().isOk()
        .expectBody(Map.class).returnResult().getResponseBody();
    assertThat(r.get("resultado")).isEqualTo("TELEFONO_AUTORIZADO");
    assertThat(telefonos.findByTelefono(telefonoDeLaSolicitud)).get()
        .extracting(TelefonoAutorizado::isUsado).isEqualTo(false);
    // y ahora el solicitante puede registrarse normalmente
    verify(servicioEmail).enviar(eq(emailDeLaSolicitud), any(), containsIgnoringCase("registr"));
}

@Test void rechazar_marca_estado_y_guarda_motivo() {
    String admin = registrarFundadorYLoguear();
    Long id = crearSolicitudPendiente(true);
    http.post().uri("/api/v1/admin/solicitudes/" + id + "/rechazar")
        .header(AUTHORIZATION, "Bearer " + admin)
        .body(Map.of("motivo", "No te conocemos de nada."))
        .exchange().expectStatus().isNoContent();
    var sol = solicitudes.findById(id).orElseThrow();
    assertThat(sol.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
    assertThat(sol.getMotivoRechazo()).isEqualTo("No te conocemos de nada.");
    verify(servicioEmail).enviar(eq(sol.getEmail()), any(), contains("No te conocemos de nada."));
}

@Test void rechazar_sin_motivo_usa_el_texto_por_defecto() { ... assertThat(motivoRechazo).contains("vinculación"); }

@Test void doble_resolucion_devuelve_409() {
    ... aprobar una vez (200) ...
    http.post().uri(".../aprobar").header(...).exchange()
        .expectStatus().isEqualTo(409)
        .expectBody(Map.class)...; // codigo SOLICITUD_YA_RESUELTA
}
```

- [ ] **Step 5: Ejecutar — debe fallar**

Run: `cd back && ./mvnw -q test-compile` → FAIL (no existe `AdminController`).

- [ ] **Step 6: Implementar `AdminService` (parte solicitudes)**

`@Service`. Constructor con: `SolicitudIngresoRepository`, `UsuarioRepository`,
`MembresiaRepository`, `TelefonoAutorizadoRepository`, `PenaRepository`,
`ApplicationEventPublisher`, y (Task 7 añadirá `PermisoAreaRepository`). Constante
`MOTIVO_RECHAZO_POR_DEFECTO` con el texto del spec (§2, "No hemos podido confirmar
tu vinculación...").

```java
@Transactional(readOnly = true)
public List<SolicitudResumen> listarSolicitudes(EstadoSolicitud estado) {
    Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
    return solicitudes.findByPenaIdAndEstado(penaId, estado).stream()
        .sorted(java.util.Comparator.comparing(SolicitudIngreso::getCreatedAt))
        .map(s -> new SolicitudResumen(s.getId(), s.getNombre(), s.getApellidos(),
            s.getTelefono(), s.getEmail(), s.getMotivo(), s.getRelacion(), s.getConocidos(),
            s.getPasswordHash() != null, s.getEstado().name(), s.getCreatedAt()))
        .toList();
}

@Transactional
public AprobarResponse aprobarSolicitud(Long solicitudId, Long adminId) {
    SolicitudIngreso sol = solicitudes.findById(solicitudId)
        .orElseThrow(() -> new SolicitudYaResueltaException()); // 409 también si no existe (no filtramos)
    if (sol.getEstado() != EstadoSolicitud.PENDIENTE) throw new SolicitudYaResueltaException();
    if (usuarios.existsByTelefono(sol.getTelefono()) || usuarios.existsByEmail(sol.getEmail()))
        throw new RegistroConflictoException(); // com.baniterio.api.auth

    Usuario admin = usuarios.findById(adminId).orElseThrow();
    Pena pena = sol.getPena();
    boolean cuentaCreada;

    // reutiliza el telefono_autorizado si ya existe (columna unique)
    TelefonoAutorizado tel = telefonos.findByTelefono(sol.getTelefono())
        .orElseGet(() -> TelefonoAutorizado.builder().telefono(sol.getTelefono()).pena(pena)
            .usado(false).build());
    tel.setAutorizadoPor(admin);

    if (sol.getPasswordHash() != null) {
        Usuario u = usuarios.save(Usuario.builder()
            .telefono(sol.getTelefono()).email(sol.getEmail())
            .passwordHash(sol.getPasswordHash())
            .nombre(sol.getNombre()).apellidos(sol.getApellidos()).mote(null)
            .esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena)
            .rol(RolMembresia.MIEMBRO).activa(true).build());
        tel.setUsado(true);
        cuentaCreada = true;
    } else {
        cuentaCreada = false;
    }
    telefonos.save(tel);

    sol.setEstado(EstadoSolicitud.APROBADA);
    sol.setResueltaPor(admin);
    sol.setResueltaAt(java.time.Instant.now());

    publisher.publishEvent(new SolicitudResueltaEvent(
        sol.getEmail(), sol.getNombre(), true, cuentaCreada, null));
    return new AprobarResponse(cuentaCreada ? "CUENTA_CREADA" : "TELEFONO_AUTORIZADO");
}

@Transactional
public void rechazarSolicitud(Long solicitudId, Long adminId, String motivo) {
    SolicitudIngreso sol = solicitudes.findById(solicitudId).orElseThrow(SolicitudYaResueltaException::new);
    if (sol.getEstado() != EstadoSolicitud.PENDIENTE) throw new SolicitudYaResueltaException();
    String texto = org.springframework.util.StringUtils.hasText(motivo) ? motivo : MOTIVO_RECHAZO_POR_DEFECTO;
    sol.setEstado(EstadoSolicitud.RECHAZADA);
    sol.setMotivoRechazo(texto);
    sol.setResueltaPor(usuarios.findById(adminId).orElseThrow());
    sol.setResueltaAt(java.time.Instant.now());
    publisher.publishEvent(new SolicitudResueltaEvent(sol.getEmail(), sol.getNombre(), false, false, texto));
}
```

- [ ] **Step 7: `ManejadorCorreoSolicitud`**

```java
package com.baniterio.api.admin;

import com.baniterio.api.email.PlantillasCorreo;
import com.baniterio.api.email.ServicioEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el correo de aprobación/rechazo DESPUÉS de que la transacción de
 * {@link AdminService} confirme. Si el envío falla, se registra pero NO se
 * revierte la resolución de la solicitud (ya está confirmada en BBDD).
 */
@Component
public class ManejadorCorreoSolicitud {

    private static final Logger log = LoggerFactory.getLogger(ManejadorCorreoSolicitud.class);

    private final ServicioEmail email;
    private final PlantillasCorreo plantillas;

    public ManejadorCorreoSolicitud(ServicioEmail email, PlantillasCorreo plantillas) {
        this.email = email;
        this.plantillas = plantillas;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alResolverSolicitud(SolicitudResueltaEvent e) {
        try {
            var correo = !e.aprobada()
                    ? plantillas.rechazo(e.nombre(), e.motivoRechazo())
                    : e.cuentaCreada()
                        ? plantillas.aprobacionCuentaCreada(e.nombre())
                        : plantillas.aprobacionCompletaRegistro(e.nombre());
            email.enviar(e.email(), correo.asunto(), correo.cuerpo());
        } catch (Exception ex) {
            log.error("No se pudo enviar el correo de la solicitud a {}: {}", e.email(), ex.toString());
        }
    }
}
```

- [ ] **Step 8: `AdminController` (parte solicitudes)**

`@RestController @RequestMapping("/api/v1/admin")`. Constructor con `AdminService`
y `ServicioPermisos`. Helper privado:

```java
private void exigirArea(UsuarioPrincipal principal, AreaProtegida area) {
    if (principal == null || !permisos.puede(principal.id(), area)) {
        throw new SinPermisoException();
    }
}
```

Endpoints:

```java
@GetMapping("/solicitudes")
public List<SolicitudResumen> solicitudes(
        @AuthenticationPrincipal UsuarioPrincipal principal,
        @RequestParam(defaultValue = "PENDIENTE") EstadoSolicitud estado) {
    exigirArea(principal, AreaProtegida.ADMIN_SOLICITUDES);
    return adminService.listarSolicitudes(estado);
}

@PostMapping("/solicitudes/{id}/aprobar")
public AprobarResponse aprobar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
    exigirArea(principal, AreaProtegida.ADMIN_SOLICITUDES);
    return adminService.aprobarSolicitud(id, principal.id());
}

@PostMapping("/solicitudes/{id}/rechazar")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void rechazar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
        @Valid @RequestBody(required = false) RechazoRequest req) {
    exigirArea(principal, AreaProtegida.ADMIN_SOLICITUDES);
    adminService.rechazarSolicitud(id, principal.id(), req == null ? null : req.motivo());
}
```

- [ ] **Step 9: Verificar y commitear**

Run: `cd back && ./mvnw -q verify`. Expected: PASS (`AdminSolicitudesIT` verde;
el `@TransactionalEventListener` dispara con el mock de `ServicioEmail`).

```bash
git add -A && git commit -m "feat(back): endpoints admin de solicitudes (listar/aprobar/rechazar) + correo"
```

---

## Task 7: Admin — gestión de miembros (rol / activo / áreas) con salvaguardas

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/admin/AdminController.java`
- Modify: `back/src/main/java/com/baniterio/api/admin/AdminService.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/MiembroResumen.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/RolRequest.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/ActivoRequest.java`
- Create: `back/src/main/java/com/baniterio/api/admin/dto/AreasRequest.java`
- Create: `back/src/main/java/com/baniterio/api/admin/UltimoAdminException.java`
- Create: `back/src/main/java/com/baniterio/api/admin/AutoModificacionException.java`
- Create: `back/src/main/java/com/baniterio/api/admin/SoloSuperadminException.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/MembresiaRepository.java`
- Create: `back/src/test/java/com/baniterio/api/admin/AdminMiembrosIT.java`

**Interfaces:**
- Consumes: `PermisoAreaRepository` (Task 1), `AreaProtegida.ADMIN_PERMISOS`,
  `ServicioPermisos`, repos de identidad.
- Produces (contrato HTTP):
  - `GET /api/v1/admin/miembros` → `200 List<MiembroResumen>`
  - `PUT /api/v1/admin/miembros/{id}/rol` body `{ "rol": "ADMIN"|"MIEMBRO" }` → `204`
  - `PUT /api/v1/admin/miembros/{id}/activo` body `{ "activo": true|false }` → `204`
  - `PUT /api/v1/admin/miembros/{id}/areas` body `{ "areas": ["ADMIN_SOLICITUDES", ...] }` → `204`
  - `record MiembroResumen(Long id, String nombre, String apellidos, String mote,
    String telefono, String rol, boolean activo, boolean esSuperadmin, List<String> areas)`
  - Códigos error: `409 ULTIMO_ADMIN`, `409 NO_TE_PUEDES_DEGRADAR`,
    `409 NO_TE_PUEDES_DESACTIVAR`, `409 SOLO_EL_SUPERADMIN`, `400 VALIDACION`.

- [ ] **Step 1: `MembresiaRepository.findByPenaIdAndRol`**

Añadir: `List<Membresia> findByPenaIdAndRol(Long penaId, RolMembresia rol);`

- [ ] **Step 2: Excepciones + handler**

`UltimoAdminException` y `SoloSuperadminException` (RuntimeException, Javadoc de
una línea). `AutoModificacionException`:

```java
package com.baniterio.api.admin;

/** El admin intenta degradarse o desactivarse a sí mismo. El {@code codigo}
 *  distingue el caso (NO_TE_PUEDES_DEGRADAR / NO_TE_PUEDES_DESACTIVAR). */
public class AutoModificacionException extends RuntimeException {
    private final String codigo;
    public AutoModificacionException(String codigo) { this.codigo = codigo; }
    public String getCodigo() { return codigo; }
}
```

En `ApiExceptionHandler`:

```java
    @ExceptionHandler(com.baniterio.api.admin.UltimoAdminException.class)
    ResponseEntity<Map<String, Object>> ultimoAdmin() {
        return error(HttpStatus.CONFLICT, "ULTIMO_ADMIN");
    }

    @ExceptionHandler(com.baniterio.api.admin.SoloSuperadminException.class)
    ResponseEntity<Map<String, Object>> soloSuperadmin() {
        return error(HttpStatus.CONFLICT, "SOLO_EL_SUPERADMIN");
    }

    @ExceptionHandler(com.baniterio.api.admin.AutoModificacionException.class)
    ResponseEntity<Map<String, Object>> autoModificacion(com.baniterio.api.admin.AutoModificacionException ex) {
        return error(HttpStatus.CONFLICT, ex.getCodigo());
    }
```

- [ ] **Step 3: DTOs**

`MiembroResumen` (record). `RolRequest(@NotNull RolMembresia rol)`,
`ActivoRequest(@NotNull Boolean activo)`, `AreasRequest(@NotNull List<@NotNull
AreaProtegida> areas)` — al deserializar un string que no es valor del enum,
Jackson lanza y `ApiExceptionHandler` ya lo convierte... comprobar: si no da 400
`VALIDACION`, añadir manejo de `HttpMessageNotReadableException` → 400 `VALIDACION`
(hazlo solo si el test de "área inválida" lo exige).

- [ ] **Step 4: Escribir `AdminMiembrosIT`**

Extiende `IntegrationTest`. Helpers: `tokenFundador()` (admin), `crearMiembro(rol)`
→ id, `token(usuarioId)`.

Casos:
```java
@Test void listar_miembros_requiere_area_permisos() { miembro normal → 403 SIN_PERMISO }

@Test void cambiar_rol_a_admin_y_de_vuelta() {
    String admin = tokenFundador();
    Long m = crearMiembro(RolMembresia.MIEMBRO);
    http.put().uri("/api/v1/admin/miembros/" + m + "/rol").header(AUTHORIZATION, "Bearer " + admin)
        .body(Map.of("rol", "ADMIN")).exchange().expectStatus().isNoContent();
    assertThat(membresias.findByUsuarioIdAndPenaId(m, penaId).orElseThrow().getRol())
        .isEqualTo(RolMembresia.ADMIN);
}

@Test void no_puedes_degradar_al_ultimo_admin() {
    // el fundador es el único admin
    String admin = tokenFundador();
    Long fundadorId = ...;
    http.put().uri("/api/v1/admin/miembros/" + fundadorId + "/rol").header(...)
        .body(Map.of("rol", "MIEMBRO")).exchange()
        .expectStatus().isEqualTo(409).expectBody(Map.class)...; // NO_TE_PUEDES_DEGRADAR (es él mismo) 
    // matiz: cae antes en NO_TE_PUEDES_DEGRADAR porque adminId == miembroId. Para
    // provocar ULTIMO_ADMIN: crear un 2º admin, que ese 2º degrade al fundador →
    // SOLO_EL_SUPERADMIN. Para ULTIMO_ADMIN puro: 2 admins no-superadmin, uno degrada al otro,
    // luego intenta degradarse él → NO_TE_PUEDES_DEGRADAR. => el único camino a ULTIMO_ADMIN
    // es degradar a un tercer admin cuando quedaría 0; construir ese escenario.
}

@Test void no_te_puedes_desactivar_a_ti_mismo() { admin sobre sí mismo activo=false → 409 NO_TE_PUEDES_DESACTIVAR }

@Test void solo_el_superadmin_se_toca_a_si_mismo() {
    // crear admin2 (no superadmin); admin2 intenta desactivar al fundador → 409 SOLO_EL_SUPERADMIN
}

@Test void put_areas_reemplaza_el_conjunto() {
    String admin = tokenFundador();
    Long m = crearMiembro(RolMembresia.MIEMBRO);
    http.put().uri("/api/v1/admin/miembros/" + m + "/areas").header(...)
        .body(Map.of("areas", List.of("ADMIN_SOLICITUDES"))).exchange().expectStatus().isNoContent();
    assertThat(permisos.findByUsuarioId(m)).extracting(p -> p.getArea().name())
        .containsExactly("ADMIN_SOLICITUDES");
    // reemplazo total
    http.put().uri(".../areas").header(...).body(Map.of("areas", List.of("ADMIN_PERMISOS")))
        .exchange().expectStatus().isNoContent();
    assertThat(permisos.findByUsuarioId(m)).extracting(p -> p.getArea().name())
        .containsExactly("ADMIN_PERMISOS");
    // y ese miembro ahora ve el área en /yo
    Map<String,Object> yo = http.get().uri("/api/v1/auth/yo").header(AUTHORIZATION, "Bearer " + token(m))
        .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    assertThat((List<?>) yo.get("areas")).containsExactly("ADMIN_PERMISOS");
}

@Test void put_areas_con_valor_invalido_devuelve_400() {
    http.put().uri(".../areas").header(...).body(Map.of("areas", List.of("NO_EXISTE")))
        .exchange().expectStatus().isBadRequest();
}

@Test void un_miembro_con_area_concedida_puede_usar_ese_endpoint_admin() {
    // concede ADMIN_SOLICITUDES a un miembro; con su token, GET /admin/solicitudes → 200
}
```

- [ ] **Step 5: Ejecutar — debe fallar**

Run: `cd back && ./mvnw -q test-compile` → FAIL.

- [ ] **Step 6: Implementar en `AdminService`**

Añadir `PermisoAreaRepository permisos` al constructor. Métodos:

```java
@Transactional(readOnly = true)
public List<MiembroResumen> listarMiembros() {
    Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
    return membresias.findByPenaId(penaId).stream()
        .map(m -> {
            Usuario u = m.getUsuario();
            List<String> areas = permisos.findByUsuarioId(u.getId()).stream()
                .map(p -> p.getArea().name()).sorted().toList();
            return new MiembroResumen(u.getId(), u.getNombre(), u.getApellidos(), u.getMote(),
                u.getTelefono(), m.getRol().name(), u.isActivo(), u.isEsSuperadmin(), areas);
        })
        .sorted(java.util.Comparator.comparing(MiembroResumen::apellidos).thenComparing(MiembroResumen::nombre))
        .toList();
}

@Transactional
public void cambiarRol(Long miembroId, Long adminId, RolMembresia nuevoRol) {
    Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
    Membresia m = membresias.findByUsuarioIdAndPenaId(miembroId, penaId).orElseThrow();
    Usuario objetivo = m.getUsuario();

    if (objetivo.isEsSuperadmin() && !adminId.equals(miembroId)) throw new SoloSuperadminException();
    if (adminId.equals(miembroId) && nuevoRol == RolMembresia.MIEMBRO)
        throw new AutoModificacionException("NO_TE_PUEDES_DEGRADAR");
    if (nuevoRol == RolMembresia.MIEMBRO && esUltimoAdminActivo(miembroId, penaId))
        throw new UltimoAdminException();

    m.setRol(nuevoRol);
    membresias.save(m);
}

@Transactional
public void cambiarActivo(Long miembroId, Long adminId, boolean activo) {
    Long penaId = penas.findBySlug(SLUG_PENA).orElseThrow().getId();
    Membresia m = membresias.findByUsuarioIdAndPenaId(miembroId, penaId).orElseThrow();
    Usuario objetivo = m.getUsuario();

    if (!activo && adminId.equals(miembroId))
        throw new AutoModificacionException("NO_TE_PUEDES_DESACTIVAR");
    if (objetivo.isEsSuperadmin() && !adminId.equals(miembroId)) throw new SoloSuperadminException();
    if (!activo && m.getRol() == RolMembresia.ADMIN && esUltimoAdminActivo(miembroId, penaId))
        throw new UltimoAdminException();

    objetivo.setActivo(activo);
    m.setActiva(activo);
    usuarios.save(objetivo);
    membresias.save(m);
}

@Transactional
public void reemplazarAreas(Long miembroId, Long adminId, List<AreaProtegida> areas) {
    Usuario objetivo = usuarios.findById(miembroId).orElseThrow();
    Usuario admin = usuarios.findById(adminId).orElseThrow();
    permisos.deleteByUsuarioId(miembroId);
    for (AreaProtegida area : new java.util.LinkedHashSet<>(areas)) {
        permisos.save(PermisoArea.builder().usuario(objetivo).area(area).concedidoPor(admin).build());
    }
}

private boolean esUltimoAdminActivo(Long usuarioId, Long penaId) {
    List<Membresia> adminsActivos = membresias.findByPenaIdAndRol(penaId, RolMembresia.ADMIN).stream()
        .filter(mm -> mm.isActiva() && mm.getUsuario().isActivo())
        .toList();
    return adminsActivos.size() == 1 && adminsActivos.get(0).getUsuario().getId().equals(usuarioId);
}
```

**Nota sobre `deleteByUsuarioId` + `save` en la misma transacción:** si Hibernate
ordena mal las sentencias y choca con `uk_permiso_area`, añadir
`permisos.flush()` tras el `deleteByUsuarioId`. Comprobar con el test.

- [ ] **Step 7: Endpoints en `AdminController`**

```java
@GetMapping("/miembros")
public List<MiembroResumen> miembros(@AuthenticationPrincipal UsuarioPrincipal principal) {
    exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
    return adminService.listarMiembros();
}

@PutMapping("/miembros/{id}/rol")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void rol(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
        @Valid @RequestBody RolRequest req) {
    exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
    adminService.cambiarRol(id, principal.id(), req.rol());
}

@PutMapping("/miembros/{id}/activo")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void activo(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
        @Valid @RequestBody ActivoRequest req) {
    exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
    adminService.cambiarActivo(id, principal.id(), req.activo());
}

@PutMapping("/miembros/{id}/areas")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void areas(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
        @Valid @RequestBody AreasRequest req) {
    exigirArea(principal, AreaProtegida.ADMIN_PERMISOS);
    adminService.reemplazarAreas(id, principal.id(), req.areas());
}
```

- [ ] **Step 8: Verificar y commitear**

Run: `cd back && ./mvnw -q verify`. Expected: PASS (suite completa).

```bash
git add -A && git commit -m "feat(back): endpoints admin de miembros (rol/activo/areas) con salvaguardas"
```

---

## Self-review (rellenado al escribir el plan)

- **Cobertura del spec:** §1 permisos → Tareas 1-3. §2 solicitudes + password → Tareas 4, 6.
  §3 miembros → Tarea 7. §4 correo → Tarea 5, 6 (listener). §7 migración → Tarea 1.
  §8 errores → Tareas 6, 7. Pruebas §"Plan de pruebas" → tests en cada tarea.
- **Fuera de este plan (van en los planes web/móvil):** cambios en front/móvil,
  `CodigoError` de cliente, nav. El interceptor Bearer de móvil va en el plan móvil.
- **Consistencia de tipos:** `UsuarioResponse` (7 componentes) fijada en Tarea 3 y
  usada igual en `/login` y `/yo`. `SolicitudResumen`/`MiembroResumen` definidas una
  vez. `AprobarResponse.resultado` ∈ {`CUENTA_CREADA`,`TELEFONO_AUTORIZADO`}.
- **Riesgo conocido:** el escenario `ULTIMO_ADMIN` "puro" es difícil de provocar
  porque `NO_TE_PUEDES_DEGRADAR` y `SOLO_EL_SUPERADMIN` lo tapan casi siempre. El
  test de Tarea 7 Step 4 documenta cómo construirlo (≥3 admins no-superadmin). Si
  resulta inalcanzable en la práctica, dejar el guard (defensa en profundidad) y
  un test unitario directo de `esUltimoAdminActivo`.
