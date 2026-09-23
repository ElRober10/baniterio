# Registro de eventos y errores — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Registrar en una tabla toda petición de escritura de la API (quién, cuándo, qué endpoint, si salió bien o mal) y los errores de móvil/web que nunca llegan al backend, consultables desde una pantalla de admin en web y móvil.

**Architecture:** Una tabla `log_evento` única. Un `Filter` de Spring registra automáticamente toda petición `POST/PUT/PATCH/DELETE` de la API (éxito o error, sin tocar cada controlador). Un endpoint público `POST /api/v1/logs/cliente` recoge los errores que el móvil/web no llegan a mandar al backend (sin conexión), llamado de forma centralizada desde el cliente HTTP de cada plataforma. Un endpoint `GET /api/v1/logs`, solo para administradores de verdad, sirve la consulta paginada que pintan la pantalla web y la de móvil.

**Tech Stack:** Java 17, Spring Boot 3 + Spring Data JPA + Flyway (Postgres) + Lombok; JUnit 5 + `RestTestClient` para IT (`./mvnw verify`), JUnit 5 puro para unit (`./mvnw test`). Angular standalone + signals + control flow, Vitest (`npx ng test --no-watch --include <dir>`). Kotlin Multiplatform + Compose Multiplatform + Ktor 3.5 + kotlinx.serialization; tests con `ktor-client-mock` (`./gradlew :shared:testAndroidHostTest --tests "com.baniterio.app.data.*"`).

**Spec:** `docs/superpowers/specs/2026-09-23-logs-eventos-design.md`

## Global Constraints

- Java se queda en 17.
- Rama: crear `desarrollo` desde `main` antes de la Tarea 1 (ver Tarea 1, Paso 0) — es la rama única del proyecto, no `feature/*` por tarea.
- Peña piloto/actual por `PenaPilotoService.entidad()` (resuelve la peña del usuario autenticado, o la piloto si no hay sesión) — nunca `PenaRepository.findBySlug` directamente en código nuevo.
- "Administrador de verdad" = `ServicioPermisos.esAdministrador(usuarioId)` (rol `ADMIN` de la membresía activa o `esSuperadmin`), **no** un `AreaProtegida` delegable — mismo patrón que "Bebidas propuestas" / "Confirmar pagos".
- `ApiExceptionHandler` (`@RestControllerAdvice`) traduce cada excepción de dominio a `{ "codigo": "<CODE>" }`; su método privado `error(...)` es el punto único que tocamos para que el filtro de logs vea el código de cada error.
- Ningún fallo de este subsistema (guardar un log, mandar un log de cliente) puede tumbar la petición real ni mostrarse al usuario: siempre capturado y text a nivel `warn`/ignorado.
- Prosa normal en código, comentarios, commits y docs (el modo caveman es solo para el chat).
- Los commits terminan con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- No abrir PR; al terminar todas las tareas, la última hace merge a `main`, push, y despliegue en producción (VPS `/opt/baniterio`) — lo pidió el usuario explícitamente para este plan.

## Review Focus

- **`GET /api/v1/logs?origen=algo-invalido`**: `OrigenLog.valueOf` lanzaría
  `IllegalArgumentException` sin traducir → 500 en vez de ignorar el filtro.
  Cubierto en la Tarea 4 (Step 3): un origen que no reconoce se trata como
  "sin filtro", no como error.
- **`GET /api/v1/logs?pagina=-1` o `tamano=0`**: `PageRequest.of` lanza
  `IllegalArgumentException` con valores negativos o cero. Cubierto en la
  Tarea 4 (Step 3): `pagina`/`tamano` se acotan antes de construir el
  `Pageable` (`pagina` mínimo 0, `tamano` entre 1 y 200).
- **`POST /api/v1/logs/cliente` con `origen` que no es ni "WEB" ni "MOBILE"
  (o ausente)**: no debe fallar; cae a `MOBILE` por defecto — ya cubierto por
  el propio `if` de `registrarCliente` (Tarea 3), sin caso de test dedicado
  porque no hay rama de error que pueda tomar un camino distinto.
- **Un mensaje/pantalla de cliente absurdamente largo** (un mensaje de
  excepción con stacktrace pegado, por ejemplo) podría superar
  `VARCHAR(300)`/`TEXT` y que Postgres lo rechace. Como `registrarAccion` y
  `registrarCliente` ya envuelven el guardado en `try/catch` (Tareas 2 y 3),
  esto se traga como cualquier otro fallo de guardado — no hace falta un test
  aparte, pero si se toca ese `try/catch` en el futuro hay que conservarlo.
- **Filtro de auditoría sobre una petición que el propio filtro no sabe
  fechar** (reloj del servidor, huso horario): `@CreationTimestamp` usa el
  reloj del servidor igual que el resto de columnas `creado_en` de la app
  (`Dispositivo`, etc.) — mismo comportamiento ya aceptado en el resto del
  proyecto, no es una regresión nueva de este plan.

## Convención de nombres (usada en todas las tareas)

Backend, paquete nuevo `com.baniterio.api.logs` (DTOs en `com.baniterio.api.logs.dto`):

- Entidad `LogEvento` (tabla `log_evento`), enum `OrigenLog { BACKEND, WEB, MOBILE }`.
- Repositorio `LogEventoRepository extends JpaRepository<LogEvento, Long>`.
- Servicio `LogEventoService` (registra y consulta).
- Filtro `LogEventoFilter extends OncePerRequestFilter`.
- Controlador `LogEventoController`.
- DTOs: `LogEventoDto`, `LogEventoPageDto`, `LogClienteRequest`.
- Migración `V63__log_evento.sql`.

Rutas nuevas:

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/logs/cliente` | público (sesión opcional) |
| GET | `/api/v1/logs` | solo administrador |

Web: `front/src/app/admin/logs/*` (componente `AdminLogs`) + cambios en `front/src/app/admin/admin.service.ts`, `admin.types.ts`, `admin/indice/indice.html`, `app.routes.ts`, `auth/auth.interceptor.ts`.

Móvil: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/{data,data/dto,ui/admin}/*` + `commonTest`.

---

## Task 1: Migración V63, entidad `LogEvento` y repositorio

**Files:**
- Create: `back/src/main/resources/db/migration/V63__log_evento.sql`
- Create: `back/src/main/java/com/baniterio/api/logs/OrigenLog.java`
- Create: `back/src/main/java/com/baniterio/api/logs/LogEvento.java`
- Create: `back/src/main/java/com/baniterio/api/logs/LogEventoRepository.java`
- Test: `back/src/test/java/com/baniterio/api/logs/LogEventoRepositoryIT.java`

**Interfaces:**
- Produces: `LogEvento` (entidad, getters/setters Lombok, `Builder`), `OrigenLog` (enum), `LogEventoRepository.buscar(Long penaId, Long usuarioId, OrigenLog origen, Instant desde, Instant hasta, Pageable pageable): Page<LogEvento>`, `LogEventoRepository` hereda también `save(LogEvento): LogEvento` de `JpaRepository`.

- [ ] **Step 0: Crear la rama de trabajo**

```bash
git checkout main
git pull
git checkout -b desarrollo
git add mobile/androidApp/src/main/kotlin/com/baniterio/app/ArchivoUtil.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoCuenta.kt \
        docs/superpowers/specs/2026-09-23-logs-eventos-design.md \
        docs/superpowers/plans/2026-09-23-logs-eventos.md
git commit -m "fix(cuentas): mensaje claro y detección de MIME por extensión al subir un recibo

Añade docs/spec y plan del registro de eventos y errores."
```

- [ ] **Step 1: Escribir el test que falla**

```java
package com.baniterio.api.logs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LogEventoRepositoryIT extends IntegrationTest {

    @Autowired LogEventoRepository logs;
    @Autowired PenaRepository penas;

    @AfterEach
    void limpiar() {
        logs.deleteAllInBatch();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    @Test
    void guardaYBuscaPorPenaOrigenYFecha() {
        Instant ahora = Instant.now();
        logs.save(LogEvento.builder().pena(pena()).origen(OrigenLog.BACKEND)
                .metodo("POST").ruta("/api/v1/cuentas/1/movimientos").estado(200).build());
        logs.save(LogEvento.builder().pena(pena()).origen(OrigenLog.MOBILE)
                .ruta("cuentas.crearMovimiento").mensaje("SinConexion").build());

        Page<LogEvento> soloMobile = logs.buscar(pena().getId(), null, OrigenLog.MOBILE, null, null,
                PageRequest.of(0, 10));
        assertThat(soloMobile.getTotalElements()).isEqualTo(1);
        assertThat(soloMobile.getContent().get(0).getRuta()).isEqualTo("cuentas.crearMovimiento");
        assertThat(soloMobile.getContent().get(0).getCreadoEn()).isNotNull();

        Page<LogEvento> desdeManana = logs.buscar(pena().getId(), null, null,
                ahora.plus(1, ChronoUnit.DAYS), null, PageRequest.of(0, 10));
        assertThat(desdeManana.getTotalElements()).isZero();

        List<LogEvento> todos = logs.buscar(pena().getId(), null, null, null, null,
                PageRequest.of(0, 10)).getContent();
        assertThat(todos).hasSize(2);
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogEventoRepositoryIT`
Expected: FAIL en compilación (`LogEvento`, `OrigenLog`, `LogEventoRepository` no existen todavía).

- [ ] **Step 3: Escribir la migración**

```sql
CREATE TABLE log_evento (
    id BIGSERIAL PRIMARY KEY,
    pena_id BIGINT NOT NULL REFERENCES pena(id),
    usuario_id BIGINT NULL REFERENCES usuario(id),
    origen VARCHAR(20) NOT NULL,
    metodo VARCHAR(10) NULL,
    ruta VARCHAR(300) NULL,
    estado INTEGER NULL,
    codigo_error VARCHAR(60) NULL,
    mensaje TEXT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_log_evento_pena_creado ON log_evento (pena_id, creado_en DESC);
CREATE INDEX idx_log_evento_usuario ON log_evento (usuario_id);
```

- [ ] **Step 4: Escribir el enum, la entidad y el repositorio**

```java
package com.baniterio.api.logs;

/** De dónde sale una fila de {@code log_evento}. */
public enum OrigenLog {
    BACKEND,
    WEB,
    MOBILE
}
```

```java
package com.baniterio.api.logs;

import java.time.Instant;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.Usuario;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila del registro de eventos: una petición de escritura de la API
 * (éxito o error) o un error reportado por el cliente que nunca llegó a
 * golpear el backend. Ver spec
 * {@code docs/superpowers/specs/2026-09-23-logs-eventos-design.md}.
 */
@Entity
@Table(name = "log_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pena_id", nullable = false)
    private Pena pena;

    /** {@code null} si no había sesión (login/registro nunca lo hay; un error de cliente puede no tenerla). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenLog origen;

    /** {@code null} en un log de cliente (no hubo petición HTTP de verdad, o no aplica). */
    @Column(length = 10)
    private String metodo;

    /** La ruta de la API en un log de backend; la pantalla/repositorio de origen en uno de cliente. */
    @Column(length = 300)
    private String ruta;

    /** Código HTTP de la respuesta; {@code null} en un log de cliente (nunca hubo respuesta). */
    private Integer estado;

    @Column(name = "codigo_error", length = 60)
    private String codigoError;

    @Column(columnDefinition = "text")
    private String mensaje;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
```

```java
package com.baniterio.api.logs;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogEventoRepository extends JpaRepository<LogEvento, Long> {

    @Query("""
            SELECT l FROM LogEvento l
            WHERE l.pena.id = :penaId
              AND (:usuarioId IS NULL OR l.usuario.id = :usuarioId)
              AND (:origen IS NULL OR l.origen = :origen)
              AND (:desde IS NULL OR l.creadoEn >= :desde)
              AND (:hasta IS NULL OR l.creadoEn < :hasta)
            ORDER BY l.creadoEn DESC
            """)
    Page<LogEvento> buscar(@Param("penaId") Long penaId, @Param("usuarioId") Long usuarioId,
            @Param("origen") OrigenLog origen, @Param("desde") Instant desde, @Param("hasta") Instant hasta,
            Pageable pageable);
}
```

- [ ] **Step 5: Ejecutar y comprobar que pasa**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogEventoRepositoryIT`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add back/src/main/resources/db/migration/V63__log_evento.sql \
        back/src/main/java/com/baniterio/api/logs/ \
        back/src/test/java/com/baniterio/api/logs/
git commit -m "feat(logs): migración V63, entidad LogEvento y repositorio con filtros"
```

---

## Task 2: Filtro de captura de acciones (`LogEventoFilter`)

**Files:**
- Create: `back/src/main/java/com/baniterio/api/logs/LogEventoService.java`
- Create: `back/src/main/java/com/baniterio/api/logs/LogEventoFilter.java`
- Modify: `back/src/main/java/com/baniterio/api/config/SecurityConfig.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Test: `back/src/test/java/com/baniterio/api/logs/LogEventoFilterIT.java`

**Interfaces:**
- Consumes: `LogEventoRepository` (Task 1), `PenaPilotoService.entidad(): Pena` (existente), `UsuarioRepository` (existente), `UsuarioPrincipal(Long id, boolean esSuperadmin, boolean esDemo)` (existente).
- Produces: `LogEventoService.registrarAccion(Long usuarioId, String metodo, String ruta, int estado, String codigoError): void` — lo consume el filtro de esta tarea; lo reutiliza indirectamente el resto del backend a través del filtro, ningún otro código lo llama a mano.

- [ ] **Step 1: Escribir el test que falla**

```java
package com.baniterio.api.logs;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT del filtro que registra toda petición de escritura de la API (V63). */
class LogEventoFilterIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LogEventoRepository logs;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        logs.deleteAllInBatch();
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@log.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lg").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void unaPeticionDeEscrituraQueSaleBienDejaFilaConElUsuarioYElEstado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        String nombreTienda = "IT-log-tienda-" + ThreadLocalRandom.current().nextInt(1_000_000);

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", nombreTienda))
                .exchange().expectStatus().isEqualTo(201);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "/api/v1/precio-bebida/tiendas".equals(l.getRuta()))
                .toList();
        assertThat(filas).hasSize(1);
        LogEvento fila = filas.get(0);
        assertThat(fila.getUsuario().getId()).isEqualTo(admin.id());
        assertThat(fila.getMetodo()).isEqualTo("POST");
        assertThat(fila.getEstado()).isEqualTo(201);
        assertThat(fila.getCodigoError()).isNull();
        assertThat(fila.getOrigen()).isEqualTo(OrigenLog.BACKEND);
    }

    @Test
    void unaPeticionRechazadaDejaFilaConElCodigoDeError() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .body(Map.of("nombre", "IT-log-rechazo"))
                .exchange().expectStatus().isEqualTo(403);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "/api/v1/precio-bebida/tiendas".equals(l.getRuta())
                        && miembro.id().equals(l.getUsuario().getId()))
                .toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getEstado()).isEqualTo(403);
        assertThat(filas.get(0).getCodigoError()).isEqualTo("SIN_PERMISO");
    }

    @Test
    void unaPeticionDeSoloLecturaNoDejaFila() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        http.get().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk();

        assertThat(logs.findAll().stream().filter(l -> "/api/v1/precio-bebida/tiendas".equals(l.getRuta())))
                .isEmpty();
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogEventoFilterIT`
Expected: FAIL (todavía no se guarda ninguna fila: el filtro no existe).

- [ ] **Step 3: Hacer que `ApiExceptionHandler` deje el código de error en la petición**

Modificar el método privado `error(...)` (línea ~49) y los dos manejadores que no pasan por él:

```java
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

// ...

/** {@code { "codigo": <codigo> }} con el estado dado. Deja el código en un atributo de la
 *  petición para que LogEventoFilter lo recoja tras completarse la respuesta. */
private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String codigo) {
    RequestContextHolder.currentRequestAttributes()
            .setAttribute("logEvento.codigo", codigo, RequestAttributes.SCOPE_REQUEST);
    return ResponseEntity.status(estado).body(Map.of("codigo", codigo));
}
```

Y en `entradaInvalida()` y `validacion(...)` (los dos que no llaman a `error(...)`), añadir la misma línea antes de devolver:

```java
@ExceptionHandler({HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
ResponseEntity<Map<String, Object>> entradaInvalida() {
    RequestContextHolder.currentRequestAttributes()
            .setAttribute("logEvento.codigo", "VALIDACION", RequestAttributes.SCOPE_REQUEST);
    return ResponseEntity.badRequest()
            .body(Map.of("codigo", "VALIDACION", "errores", Map.of()));
}
```

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
    Map<String, String> errores = new HashMap<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
        errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    RequestContextHolder.currentRequestAttributes()
            .setAttribute("logEvento.codigo", "VALIDACION", RequestAttributes.SCOPE_REQUEST);
    return ResponseEntity.badRequest()
            .body(Map.of("codigo", "VALIDACION", "errores", errores));
}
```

- [ ] **Step 4: Escribir `LogEventoService.registrarAccion`**

```java
package com.baniterio.api.logs;

import com.baniterio.api.identidad.PenaPilotoService;
import com.baniterio.api.identidad.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guarda las filas del registro de eventos. Nunca deja que un fallo suyo llegue a quien lo llama. */
@Service
public class LogEventoService {

    private static final Logger log = LoggerFactory.getLogger(LogEventoService.class);

    private final LogEventoRepository repo;
    private final PenaPilotoService pena;
    private final UsuarioRepository usuarios;

    public LogEventoService(LogEventoRepository repo, PenaPilotoService pena, UsuarioRepository usuarios) {
        this.repo = repo;
        this.pena = pena;
        this.usuarios = usuarios;
    }

    /** Lo llama {@link LogEventoFilter} tras cada petición de escritura de la API. */
    @Transactional
    public void registrarAccion(Long usuarioId, String metodo, String ruta, int estado, String codigoError) {
        try {
            LogEvento fila = LogEvento.builder()
                    .pena(pena.entidad())
                    .usuario(usuarioId == null ? null : usuarios.getReferenceById(usuarioId))
                    .origen(OrigenLog.BACKEND)
                    .metodo(metodo)
                    .ruta(ruta)
                    .estado(estado)
                    .codigoError(codigoError)
                    .build();
            repo.save(fila);
        } catch (Exception e) {
            log.warn("no se pudo registrar el log de {} {}", metodo, ruta, e);
        }
    }
}
```

- [ ] **Step 5: Escribir `LogEventoFilter` y engancharlo en `SecurityConfig`**

```java
package com.baniterio.api.logs;

import java.io.IOException;
import java.util.Set;

import com.baniterio.api.auth.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registra automáticamente toda petición de escritura de la API en
 * {@code log_evento}: quién la hizo, qué endpoint, y si salió bien o mal (el
 * código de error, si lo hay, lo deja {@link com.baniterio.api.web.ApiExceptionHandler}
 * en un atributo de la petición). Se engancha DESPUÉS del filtro JWT
 * ({@code SecurityConfig}) para poder leer el usuario autenticado.
 */
@Component
public class LogEventoFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_ESCRITURA = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final LogEventoService service;

    public LogEventoFilter(LogEventoService service) {
        this.service = service;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !METODOS_ESCRITURA.contains(request.getMethod())
                || !ruta.startsWith("/api/v1/")
                || ruta.startsWith("/api/v1/logs")
                || ruta.equals("/api/v1/auth/login")
                || ruta.equals("/api/v1/auth/registro");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        String codigo = (String) request.getAttribute("logEvento.codigo");
        service.registrarAccion(usuarioIdActual(), request.getMethod(), request.getRequestURI(),
                response.getStatus(), codigo);
    }

    private Long usuarioIdActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p) {
            return p.id();
        }
        return null;
    }
}
```

En `SecurityConfig.java`: añadir el parámetro y el filtro a la cadena.

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
        com.baniterio.api.logs.LogEventoFilter logEventoFilter) throws Exception {
    return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers("/api/v1/health", "/api/v1/auth/registro", "/api/v1/auth/login",
                            "/api/v1/auth/solicitudes", "/api/v1/logs/cliente", "/api/v1/media/**").permitAll()
                    .requestMatchers("/api", "/api/api-docs/**", "/swagger-ui/**").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(
                    (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(logEventoFilter, JwtAuthenticationFilter.class)
            .build();
}
```

(`/api/v1/logs/cliente` se añade aquí ya pensando en la Tarea 3; `/api/v1/logs` sin sufijo, el listado, se queda fuera de `permitAll` a propósito: exige sesión, y dentro el servicio exige además ser administrador.)

- [ ] **Step 6: Ejecutar y comprobar que pasa**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogEventoFilterIT`
Expected: PASS.

- [ ] **Step 7: Comprobar que el resto de IT del backend siguen en verde**

Run: `./mvnw -f back/pom.xml verify`
Expected: BUILD SUCCESS (el filtro no debe romper ningún IT existente).

- [ ] **Step 8: Commit**

```bash
git add back/src/main/java/com/baniterio/api/logs/LogEventoService.java \
        back/src/main/java/com/baniterio/api/logs/LogEventoFilter.java \
        back/src/main/java/com/baniterio/api/config/SecurityConfig.java \
        back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java \
        back/src/test/java/com/baniterio/api/logs/LogEventoFilterIT.java
git commit -m "feat(logs): filtro que registra toda petición de escritura de la API"
```

---

## Task 3: Endpoint público `POST /api/v1/logs/cliente`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/logs/dto/LogClienteRequest.java`
- Create: `back/src/main/java/com/baniterio/api/logs/LogEventoController.java`
- Modify: `back/src/main/java/com/baniterio/api/logs/LogEventoService.java`
- Test: `back/src/test/java/com/baniterio/api/logs/LogClienteIT.java`

**Interfaces:**
- Consumes: `LogEventoService` (Task 2), `LogEventoRepository` (Task 1).
- Produces: `LogEventoService.registrarCliente(Long usuarioId, String origenTexto, String pantalla, String mensaje): void`, ruta `POST /api/v1/logs/cliente` (202, sin cuerpo de respuesta) — la consume el front (Task 6) y el móvil (Task 8).

- [ ] **Step 1: Escribir el test que falla**

```java
package com.baniterio.api.logs;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

class LogClienteIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LogEventoRepository logs;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        logs.deleteAllInBatch();
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Sesion crearMiembro() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@logcliente.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lc").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(RolMembresia.MIEMBRO).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void guardaUnLogDeClienteSinSesion() {
        http.post().uri("/api/v1/logs/cliente")
                .body(Map.of("origen", "MOBILE", "pantalla", "cuentas.crearMovimiento", "mensaje", "IOException"))
                .exchange().expectStatus().isEqualTo(202);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "cuentas.crearMovimiento".equals(l.getRuta())).toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getOrigen()).isEqualTo(OrigenLog.MOBILE);
        assertThat(filas.get(0).getUsuario()).isNull();
        assertThat(filas.get(0).getEstado()).isNull();
        assertThat(filas.get(0).getMensaje()).isEqualTo("IOException");
    }

    @Test
    void guardaUnLogDeClienteConElUsuarioSiHaySesion() {
        Sesion s = crearMiembro();
        http.post().uri("/api/v1/logs/cliente")
                .header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("origen", "WEB", "pantalla", "cuentas.crearMovimiento", "mensaje", "Error de red"))
                .exchange().expectStatus().isEqualTo(202);

        List<LogEvento> filas = logs.findAll().stream()
                .filter(l -> "cuentas.crearMovimiento".equals(l.getRuta()) && l.getOrigen() == OrigenLog.WEB)
                .toList();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getUsuario().getId()).isEqualTo(s.id());
    }

    @Test
    void laPropiaPeticionDeLogsNoGeneraOtroLogDeAccion() {
        // El filtro de la Tarea 2 excluye /api/v1/logs/**: no debe quedar una
        // segunda fila de tipo BACKEND para esta misma petición.
        http.post().uri("/api/v1/logs/cliente")
                .body(Map.of("origen", "MOBILE", "pantalla", "x", "mensaje", "y"))
                .exchange().expectStatus().isEqualTo(202);

        assertThat(logs.findAll().stream().filter(l -> l.getOrigen() == OrigenLog.BACKEND)).isEmpty();
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogClienteIT`
Expected: FAIL (404: el endpoint no existe).

- [ ] **Step 3: Escribir el DTO, el método del servicio y el controlador**

```java
package com.baniterio.api.logs.dto;

/** Cuerpo de {@code POST /api/v1/logs/cliente}. Sin validación estricta a
 *  propósito: nada de lo que mande el cliente debe poder devolver un 4xx. */
public record LogClienteRequest(String origen, String pantalla, String mensaje) {
}
```

Añadir a `LogEventoService`:

```java
/** Lo llama LogEventoController ante un error de móvil/web que nunca llegó a golpear el backend. */
@Transactional
public void registrarCliente(Long usuarioId, String origenTexto, String pantalla, String mensaje) {
    try {
        OrigenLog origen = "WEB".equalsIgnoreCase(origenTexto) ? OrigenLog.WEB : OrigenLog.MOBILE;
        LogEvento fila = LogEvento.builder()
                .pena(pena.entidad())
                .usuario(usuarioId == null ? null : usuarios.getReferenceById(usuarioId))
                .origen(origen)
                .ruta(pantalla)
                .mensaje(mensaje)
                .build();
        repo.save(fila);
    } catch (Exception e) {
        log.warn("no se pudo registrar el log de cliente ({}): {}", pantalla, mensaje, e);
    }
}
```

```java
package com.baniterio.api.logs;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.logs.dto.LogClienteRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro de eventos y errores, solo para depuración interna (ver spec 2026-09-23). */
@Tag(name = "Logs", description = "Registro de acciones y errores de la app.")
@RestController
@RequestMapping("/api/v1/logs")
public class LogEventoController {

    private final LogEventoService service;

    public LogEventoController(LogEventoService service) {
        this.service = service;
    }

    /** Público: un error de cliente puede ocurrir antes de tener sesión. Siempre 202. */
    @PostMapping("/cliente")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void logCliente(@AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestBody LogClienteRequest req) {
        service.registrarCliente(principal == null ? null : principal.id(), req.origen(), req.pantalla(),
                req.mensaje());
    }
}
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogClienteIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/logs/
git commit -m "feat(logs): endpoint público POST /logs/cliente para errores que no llegan al backend"
```

---

## Task 4: Endpoint `GET /api/v1/logs` (solo administrador)

**Files:**
- Create: `back/src/main/java/com/baniterio/api/logs/dto/LogEventoDto.java`
- Create: `back/src/main/java/com/baniterio/api/logs/dto/LogEventoPageDto.java`
- Modify: `back/src/main/java/com/baniterio/api/logs/LogEventoService.java`
- Modify: `back/src/main/java/com/baniterio/api/logs/LogEventoController.java`
- Test: `back/src/test/java/com/baniterio/api/logs/LogEventoControllerIT.java`

**Interfaces:**
- Consumes: `ServicioPermisos.esAdministrador(Long): boolean` (existente), `LogEventoRepository.buscar(...)` (Task 1).
- Produces: `GET /api/v1/logs?usuarioId=&origen=&desde=&hasta=&pagina=&tamano=` → `LogEventoPageDto(List<LogEventoDto> contenido, long total, int pagina, int tamano)` — lo consumen la web (Task 5) y el móvil (Task 7).

- [ ] **Step 1: Escribir el test que falla**

```java
package com.baniterio.api.logs;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

class LogEventoControllerIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LogEventoRepository logs;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach
    void limpiar() {
        logs.deleteAllInBatch();
    }

    record Sesion(Long id, String token) {
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@logctrl.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Lc").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void soloAdminPuedeListar() {
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        http.get().uri("/api/v1/logs")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isEqualTo(403);
    }

    @Test
    void sinSesionEs401() {
        http.get().uri("/api/v1/logs").exchange().expectStatus().isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtraPorUsuarioYDevuelvePaginado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);

        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("nombre", "IT-log-ctrl-a-" + ThreadLocalRandom.current().nextInt(1_000_000)))
                .exchange().expectStatus().isEqualTo(201);
        http.post().uri("/api/v1/precio-bebida/tiendas")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of("nombre", "x"))
                .exchange().expectStatus().isEqualTo(403);

        Map<String, Object> pagina = http.get().uri("/api/v1/logs?usuarioId=" + otro.id())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        List<Map<String, Object>> contenido = (List<Map<String, Object>>) pagina.get("contenido");
        assertThat(contenido).hasSize(1);
        assertThat(contenido.get(0).get("codigoError")).isEqualTo("SIN_PERMISO");
        assertThat(((Number) pagina.get("total")).longValue()).isEqualTo(1L);
    }

    @Test
    void unOrigenInvalidoOUnaPaginaFueraDeRangoNoRompenLaConsulta() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);

        http.get().uri("/api/v1/logs?origen=no-existe&pagina=-5&tamano=0")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk();
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogEventoControllerIT`
Expected: FAIL (404: `GET /api/v1/logs` no existe).

- [ ] **Step 3: Escribir los DTOs, el método del servicio y ampliar el controlador**

```java
package com.baniterio.api.logs.dto;

import java.time.Instant;

/** Fila del listado `GET /api/v1/logs`. `usuarioNombre` viene ya resuelto: nadie más hace el join. */
public record LogEventoDto(long id, String origen, Long usuarioId, String usuarioNombre, String metodo,
        String ruta, Integer estado, String codigoError, String mensaje, Instant creadoEn) {
}
```

```java
package com.baniterio.api.logs.dto;

import java.util.List;

public record LogEventoPageDto(List<LogEventoDto> contenido, long total, int pagina, int tamano) {
}
```

Añadir a `LogEventoService` (con `import` de `ServicioPermisos`, `SinPermisoException`, los DTOs, `LocalDate`, `ZoneOffset`, `PageRequest`):

```java
private final ServicioPermisos permisos;

// añadir ServicioPermisos al constructor existente y asignarlo (this.permisos = permisos;)

@Transactional(readOnly = true)
public LogEventoPageDto listar(Long usuarioIdSolicitante, Long filtroUsuarioId, String origenTexto,
        LocalDate desde, LocalDate hasta, int pagina, int tamano) {
    if (usuarioIdSolicitante == null || !permisos.esAdministrador(usuarioIdSolicitante)) {
        throw new SinPermisoException();
    }
    // Un origen que no reconoce, o pagina/tamano fuera de rango, se acotan en
    // vez de reventar con un 500: esto es una herramienta de depuración para
    // un admin, no una API pública que deba rechazar la entrada con un 400.
    OrigenLog origen = null;
    if (origenTexto != null) {
        try {
            origen = OrigenLog.valueOf(origenTexto.toUpperCase());
        } catch (IllegalArgumentException ignorado) {
            origen = null;
        }
    }
    Instant desdeInstant = desde == null ? null : desde.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant hastaInstant = hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    int paginaSegura = Math.max(pagina, 0);
    int tamanoSeguro = Math.min(Math.max(tamano, 1), 200);

    Page<LogEvento> resultado = repo.buscar(pena.id(), filtroUsuarioId, origen, desdeInstant, hastaInstant,
            PageRequest.of(paginaSegura, tamanoSeguro));
    List<LogEventoDto> contenido = resultado.getContent().stream().map(this::aDto).toList();
    return new LogEventoPageDto(contenido, resultado.getTotalElements(), paginaSegura, tamanoSeguro);
}

private LogEventoDto aDto(LogEvento l) {
    return new LogEventoDto(l.getId(), l.getOrigen().name(),
            l.getUsuario() == null ? null : l.getUsuario().getId(),
            l.getUsuario() == null ? null : l.getUsuario().getNombre() + " " + l.getUsuario().getApellidos(),
            l.getMetodo(), l.getRuta(), l.getEstado(), l.getCodigoError(), l.getMensaje(), l.getCreadoEn());
}
```

Añadir a `LogEventoController`:

```java
@GetMapping
public LogEventoPageDto listar(@AuthenticationPrincipal UsuarioPrincipal principal,
        @RequestParam(required = false) Long usuarioId,
        @RequestParam(required = false) String origen,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate desde,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate hasta,
        @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "50") int tamano) {
    return service.listar(principal == null ? null : principal.id(), usuarioId, origen, desde, hasta, pagina,
            tamano);
}
```

`ApiExceptionHandler` ya traduce `SinPermisoException` a 403 (línea 88); no hace falta tocarlo.

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `./mvnw -f back/pom.xml verify -Dit.test=LogEventoControllerIT`
Expected: PASS.

- [ ] **Step 5: Ejecutar toda la suite de backend**

Run: `./mvnw -f back/pom.xml verify`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/logs/ back/src/test/java/com/baniterio/api/logs/
git commit -m "feat(logs): endpoint GET /logs paginado y filtrable, solo administrador"
```

---

## Task 5: Web — pantalla de logs en el panel de administración

**Files:**
- Modify: `front/src/app/admin/admin.types.ts`
- Modify: `front/src/app/admin/admin.service.ts`
- Create: `front/src/app/admin/logs/logs.ts`
- Create: `front/src/app/admin/logs/logs.html`
- Create: `front/src/app/admin/logs/logs.spec.ts`
- Modify: `front/src/app/admin/indice/indice.html`
- Modify: `front/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `GET /api/v1/logs` (Task 4), `AuthService.usuarioActual()` / `Volver` (existentes, mismo patrón que `AdminBebidas`).
- Produces: componente `AdminLogs`, ruta `/panel/administracion/logs`.

- [ ] **Step 1: Escribir el test que falla**

```typescript
// front/src/app/admin/logs/logs.spec.ts
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { AdminLogs } from './logs';

describe('AdminLogs', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminLogs, HttpClientTestingModule],
      providers: [provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('pinta las filas que devuelve el backend', () => {
    const auth = TestBed.inject(AuthService);
    auth.usuarioActual.set({
      id: 1,
      nombre: 'Ada',
      apellidos: 'L',
      telefono: '600000000',
      rol: 'ADMIN',
      esSuperadmin: false,
      areas: [],
      perfilCompleto: true,
    } as never);

    const fixture = TestBed.createComponent(AdminLogs);
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.endsWith('/logs'));
    req.flush({
      contenido: [
        {
          id: 1,
          origen: 'MOBILE',
          usuarioId: null,
          usuarioNombre: null,
          metodo: null,
          ruta: 'cuentas.crearMovimiento',
          estado: null,
          codigoError: null,
          mensaje: 'IOException',
          creadoEn: '2026-09-23T10:00:00Z',
        },
      ],
      total: 1,
      pagina: 0,
      tamano: 50,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance['filas']().length).toBe(1);
  });
});
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `npx ng test --no-watch --include src/app/admin/logs/logs.spec.ts`
Expected: FAIL (`./logs` no existe).

- [ ] **Step 3: Tipos y servicio**

Añadir a `admin.types.ts`:

```typescript
/** Fila de `GET /api/v1/logs` (ver back `com.baniterio.api.logs.dto.LogEventoDto`). */
export interface LogEvento {
  id: number;
  origen: 'BACKEND' | 'WEB' | 'MOBILE';
  usuarioId: number | null;
  usuarioNombre: string | null;
  metodo: string | null;
  ruta: string | null;
  estado: number | null;
  codigoError: string | null;
  mensaje: string | null;
  creadoEn: string;
}

export interface LogEventoPagina {
  contenido: LogEvento[];
  total: number;
  pagina: number;
  tamano: number;
}
```

Añadir a `admin.service.ts` (con `import { HttpClient, HttpParams } from '@angular/common/http';` ya presente salvo `HttpParams`, y `LogEvento`/`LogEventoPagina` en los imports de tipos):

```typescript
/** Consulta paginada de `GET /api/v1/logs`. Solo administrador (lo comprueba el backend). */
listarLogs(filtros: {
  usuarioId?: number;
  origen?: string;
  desde?: string;
  hasta?: string;
  pagina?: number;
  tamano?: number;
} = {}): Observable<LogEventoPagina> {
  let params = new HttpParams().set('pagina', filtros.pagina ?? 0).set('tamano', filtros.tamano ?? 50);
  if (filtros.usuarioId != null) params = params.set('usuarioId', filtros.usuarioId);
  if (filtros.origen) params = params.set('origen', filtros.origen);
  if (filtros.desde) params = params.set('desde', filtros.desde);
  if (filtros.hasta) params = params.set('hasta', filtros.hasta);
  return this.http.get<LogEventoPagina>(`${this.base}/logs`, { params });
}
```

- [ ] **Step 4: Componente y plantilla**

```typescript
// front/src/app/admin/logs/logs.ts
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Volver } from '../../shared/volver/volver';
import { AdminService } from '../admin.service';
import { LogEvento } from '../admin.types';

/**
 * Registro de eventos y errores: toda petición de escritura de la API y los
 * errores de móvil/web que nunca llegaron a golpear el backend. Cualquier
 * admin/superadmin (no un área del panel); si el usuario no lo es, se le
 * devuelve a `/panel`, igual que `AdminBebidas`/`AdminPagos`.
 */
@Component({
  selector: 'app-admin-logs',
  imports: [Volver],
  templateUrl: './logs.html',
})
export class AdminLogs implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly esAdmin = computed(() => {
    const u = this.auth.usuarioActual();
    return u?.rol === 'ADMIN' || u?.esSuperadmin === true;
  });

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly filas = signal<LogEvento[]>([]);
  protected readonly total = signal(0);
  protected readonly pagina = signal(0);
  protected readonly tamano = 50;

  ngOnInit(): void {
    this.auth.asegurarYo().subscribe(() => {
      if (!this.esAdmin()) {
        this.router.navigateByUrl('/panel');
        return;
      }
      this.cargar();
    });
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.adminService.listarLogs({ pagina: this.pagina(), tamano: this.tamano }).subscribe({
      next: (p) => {
        this.filas.set(p.contenido);
        this.total.set(p.total);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected anterior(): void {
    if (this.pagina() === 0) return;
    this.pagina.update((p) => p - 1);
    this.cargar();
  }

  protected siguiente(): void {
    if ((this.pagina() + 1) * this.tamano >= this.total()) return;
    this.pagina.update((p) => p + 1);
    this.cargar();
  }
}
```

```html
<!-- front/src/app/admin/logs/logs.html -->
<section class="mx-auto w-full max-w-5xl">
  <div class="mb-6">
    <app-volver destino="/panel/administracion" />
  </div>

  <h1 class="font-display text-2xl font-extrabold leading-tight">Registro de eventos y errores</h1>
  <p class="mt-2 text-sm leading-relaxed text-muted">
    Toda petición que escribe algo en la app, y los errores de móvil/web que no llegaron a golpear el
    servidor.
  </p>

  <div class="mt-8">
    @switch (estado()) {
      @case ('cargando') {
        <p class="text-sm text-muted">Cargando…</p>
      }
      @case ('error') {
        <p class="text-sm text-red-300">No se ha podido cargar el registro.</p>
      }
      @case ('lista') {
        <div class="overflow-x-auto rounded-2xl border border-outline">
          <table class="w-full text-left text-sm">
            <thead class="bg-panel text-muted">
              <tr>
                <th class="px-4 py-3">Fecha</th>
                <th class="px-4 py-3">Origen</th>
                <th class="px-4 py-3">Usuario</th>
                <th class="px-4 py-3">Método</th>
                <th class="px-4 py-3">Ruta / pantalla</th>
                <th class="px-4 py-3">Estado</th>
                <th class="px-4 py-3">Código / mensaje</th>
              </tr>
            </thead>
            <tbody>
              @for (fila of filas(); track fila.id) {
                <tr class="border-t border-outline">
                  <td class="px-4 py-3">{{ fila.creadoEn | date: 'short' }}</td>
                  <td class="px-4 py-3">{{ fila.origen }}</td>
                  <td class="px-4 py-3">{{ fila.usuarioNombre ?? '—' }}</td>
                  <td class="px-4 py-3">{{ fila.metodo ?? '—' }}</td>
                  <td class="px-4 py-3">{{ fila.ruta ?? '—' }}</td>
                  <td class="px-4 py-3">{{ fila.estado ?? '—' }}</td>
                  <td class="px-4 py-3">{{ fila.codigoError ?? fila.mensaje ?? '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="mt-4 flex items-center justify-between text-sm text-muted">
          <button type="button" (click)="anterior()" [disabled]="pagina() === 0" class="disabled:opacity-40">
            ← Anterior
          </button>
          <span>{{ total() }} en total</span>
          <button
            type="button"
            (click)="siguiente()"
            [disabled]="(pagina() + 1) * tamano >= total()"
            class="disabled:opacity-40"
          >
            Siguiente →
          </button>
        </div>
      }
    }
  </div>
</section>
```

Necesita `import { DatePipe } from '@angular/common';` y añadirlo a `imports: [Volver, DatePipe]` en `logs.ts` (el `date` pipe del template).

- [ ] **Step 5: Enlazar la ruta y la tarjeta del índice**

En `app.routes.ts`, junto a `administracion/pagos`:

```typescript
{
  path: 'administracion/logs',
  component: AdminLogs,
  canActivate: [perfilCompletoGuard],
},
```

(con `import { AdminLogs } from './admin/logs/logs';` arriba, junto a `AdminBebidas`/`AdminPagos`).

En `indice.html`, dentro del bloque `@if (esAdmin())`, junto a la tarjeta de "Confirmar pagos":

```html
<a
  routerLink="/panel/administracion/logs"
  class="group relative overflow-hidden rounded-2xl border border-outline bg-panel p-6 transition hover:border-gold"
>
  <h2 class="font-display text-lg font-bold transition group-hover:text-gold">Registro</h2>
  <p class="mt-2 text-sm leading-relaxed text-muted">
    Qué ha hecho cada uno y qué errores han dado móvil y web.
  </p>
</a>
```

- [ ] **Step 6: Ejecutar y comprobar que pasa**

Run: `npx ng test --no-watch --include src/app/admin/logs/logs.spec.ts`
Expected: PASS.

- [ ] **Step 7: Ejecutar toda la suite del front**

Run: `npx ng test --no-watch`
Expected: todos los specs en verde (nada existente debe romperse por las rutas/índice tocados).

- [ ] **Step 8: Commit**

```bash
git add front/src/app/admin/admin.types.ts front/src/app/admin/admin.service.ts \
        front/src/app/admin/logs/ front/src/app/admin/indice/indice.html front/src/app/app.routes.ts
git commit -m "feat(logs): pantalla web del registro de eventos y errores, solo admin"
```

---

## Task 6: Web — reporte de errores de red al backend

**Files:**
- Modify: `front/src/app/auth/auth.interceptor.ts`
- Modify: `front/src/app/auth/auth.interceptor.spec.ts`

**Interfaces:**
- Consumes: `POST /api/v1/logs/cliente` (Task 3).
- Produces: ninguno nuevo (comportamiento añadido al interceptor existente).

- [ ] **Step 1: Escribir el test que falla**

Añadir a `auth.interceptor.spec.ts` (revisar primero el fichero existente para reutilizar su `TestBed`/mocks de `HttpTestingController`; el test nuevo sigue el mismo patrón que los que ya hay ahí):

```typescript
it('reporta a /logs/cliente cuando una petición a la API falla sin respuesta (error de red)', () => {
  const httpMock = TestBed.inject(HttpTestingController);
  const http = TestBed.inject(HttpClient);

  http.get(`${environment.apiBaseUrl}/cuentas/1`).subscribe({ error: () => {} });

  const peticionOriginal = httpMock.expectOne(`${environment.apiBaseUrl}/cuentas/1`);
  peticionOriginal.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

  const reporte = httpMock.expectOne(`${environment.apiBaseUrl}/logs/cliente`);
  expect(reporte.request.method).toBe('POST');
  expect(reporte.request.body.origen).toBe('WEB');
  expect(reporte.request.body.pantalla).toBe(`${environment.apiBaseUrl}/cuentas/1`);
  reporte.flush(null, { status: 202, statusText: 'Accepted' });

  httpMock.verify();
});
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `npx ng test --no-watch --include src/app/auth/auth.interceptor.spec.ts`
Expected: FAIL (no se manda ninguna petición a `/logs/cliente`).

- [ ] **Step 3: Ampliar el interceptor**

```typescript
import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const http = inject(HttpClient);

  const esApi = req.url.startsWith(environment.apiBaseUrl);
  const esPublica =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/registro') ||
    req.url.includes('/auth/solicitudes');
  const esLogCliente = req.url.endsWith('/logs/cliente');
  const token = auth.token();
  const protegida = esApi && !esPublica;

  const peticion =
    protegida && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(peticion).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && protegida) {
        auth.cerrarSesion();
        router.navigate(['/login'], { queryParams: { expirada: 1 } });
      }
      // status === 0: la petición nunca llegó a tener respuesta (sin red, CORS,
      // servidor caído). Se reporta best-effort, sin esperar ni propagar el
      // resultado: si esto también falla, no hay nada más que hacer.
      if (err.status === 0 && esApi && !esLogCliente) {
        http
          .post(`${environment.apiBaseUrl}/logs/cliente`, { origen: 'WEB', pantalla: req.url, mensaje: 'Sin conexión' })
          .subscribe({ error: () => {} });
      }
      return throwError(() => err);
    }),
  );
};
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `npx ng test --no-watch --include src/app/auth/auth.interceptor.spec.ts`
Expected: PASS.

- [ ] **Step 5: Ejecutar toda la suite del front**

Run: `npx ng test --no-watch`
Expected: todos los specs en verde.

- [ ] **Step 6: Commit**

```bash
git add front/src/app/auth/auth.interceptor.ts front/src/app/auth/auth.interceptor.spec.ts
git commit -m "feat(logs): el interceptor reporta a /logs/cliente los fallos de red"
```

---

## Task 7: Móvil — pantalla de logs en el panel de administración

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/LogEventoDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AdminRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AdminRepositoryImpl.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/LogsScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminIndexScreen.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/AdminRepositoryImplTest.kt` (añadir un test; revisar primero si el fichero ya existe con tests de `AdminRepositoryImpl` para seguir su mismo patrón de `MockEngine`)

**Interfaces:**
- Consumes: `GET /api/v1/logs` (Task 4).
- Produces: `AdminRepository.logs(usuarioId: Long?, pagina: Int, tamano: Int): ResultadoAdmin<PaginaLogsDto>`, pantalla `Screen.Logs`.

- [ ] **Step 1: Escribir el test que falla**

Si `AdminRepositoryImplTest.kt` no existe todavía, crearlo con el mismo patrón que `ListaCompraRepositoryImplTest.kt` (revisar ese fichero antes de escribir este). Si ya existe, añadir este test al final de la clase:

```kotlin
@Test
fun logsDevuelveLaPaginaDelBackend() = runTest {
    val engine = MockEngine { request ->
        assertEquals("/api/v1/logs", request.url.encodedPath)
        assertEquals("50", request.url.parameters["tamano"])
        respond(
            content = """{"contenido":[{"id":1,"origen":"MOBILE","usuarioId":null,
                "usuarioNombre":null,"metodo":null,"ruta":"cuentas.crearMovimiento",
                "estado":null,"codigoError":null,"mensaje":"IOException","creadoEn":"2026-09-23T10:00:00Z"}],
                "total":1,"pagina":0,"tamano":50}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val repo = AdminRepositoryImpl(HttpClient(engine) { configComun() }, SesionHolder().apply { token = "t" })

    val resultado = repo.logs(usuarioId = null, pagina = 0, tamano = 50)

    assertTrue(resultado is ResultadoAdmin.Exito)
    assertEquals(1, (resultado as ResultadoAdmin.Exito).dato.contenido.size)
    assertEquals("cuentas.crearMovimiento", resultado.dato.contenido[0].ruta)
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.baniterio.app.data.AdminRepositoryImplTest"`
Expected: FAIL (`AdminRepository.logs` no existe todavía).

- [ ] **Step 3: DTOs**

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Fila de `GET /api/v1/logs` (ver back `LogEventoDto`). */
@Serializable
data class LogEventoDto(
    val id: Long,
    val origen: String,
    val usuarioId: Long? = null,
    val usuarioNombre: String? = null,
    val metodo: String? = null,
    val ruta: String? = null,
    val estado: Int? = null,
    val codigoError: String? = null,
    val mensaje: String? = null,
    val creadoEn: String,
)

@Serializable
data class PaginaLogsDto(
    val contenido: List<LogEventoDto>,
    val total: Long,
    val pagina: Int,
    val tamano: Int,
)
```

- [ ] **Step 4: Método en `AdminRepository`/`AdminRepositoryImpl`**

Añadir a la interfaz (`AdminRepository.kt`):

```kotlin
import com.baniterio.app.data.dto.PaginaLogsDto

// ...

/** Registro de eventos y errores (`GET /admin` en realidad vive en `/logs`, ver spec 2026-09-23). Solo admin. */
suspend fun logs(usuarioId: Long? = null, pagina: Int = 0, tamano: Int = 50): ResultadoAdmin<PaginaLogsDto>
```

Añadir a la implementación (`AdminRepositoryImpl.kt`), con `import com.baniterio.app.data.dto.PaginaLogsDto`:

```kotlin
override suspend fun logs(usuarioId: Long?, pagina: Int, tamano: Int): ResultadoAdmin<PaginaLogsDto> =
    peticion {
        http.get("$API_BASE_URL/logs") {
            auth()
            usuarioId?.let { parameter("usuarioId", it) }
            parameter("pagina", pagina)
            parameter("tamano", tamano)
        }.body<PaginaLogsDto>()
    }
```

- [ ] **Step 5: Ejecutar y comprobar que pasa**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.baniterio.app.data.AdminRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 6: Pantalla y navegación**

```kotlin
package com.baniterio.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.AdminRepository
import com.baniterio.app.data.ResultadoAdmin
import com.baniterio.app.data.dto.LogEventoDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.CabeceraPantalla
import com.baniterio.app.ui.comun.relieveDeCarta

/** Registro de eventos y errores: solo lectura, paginado con "Cargar más". Solo admin. */
@Composable
fun LogsScreen(adminRepo: AdminRepository, onVolver: () -> Unit) {
    var filas by remember { mutableStateOf<List<LogEventoDto>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var pagina by remember { mutableStateOf(0) }
    var estado by remember { mutableStateOf("cargando") }
    val tamano = 50

    LaunchedEffect(pagina) {
        estado = "cargando"
        when (val r = adminRepo.logs(pagina = pagina, tamano = tamano)) {
            is ResultadoAdmin.Exito -> {
                filas = if (pagina == 0) r.dato.contenido else filas + r.dato.contenido
                total = r.dato.total
                estado = "lista"
            }
            is ResultadoAdmin.Error -> estado = "error"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CabeceraPantalla(onVolver)
        Text(
            "Registro de eventos y errores",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        if (estado == "error") {
            Text("No se ha podido cargar el registro.", color = BaniterioColors.muted)
        }
        filas.forEach { f ->
            Column(
                modifier = Modifier.fillMaxWidth().relieveDeCarta(RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text("${f.creadoEn}  ·  ${f.origen}", color = BaniterioColors.muted)
                Text(f.ruta ?: "—", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(f.usuarioNombre, f.metodo, f.estado?.toString(), f.codigoError ?: f.mensaje)
                        .joinToString(" · "),
                    color = BaniterioColors.muted,
                )
            }
        }
        if ((pagina + 1) * tamano < total) {
            OutlinedButton(onClick = { pagina++ }) { Text("Cargar más") }
        }
    }
}
```

En `Screen.kt`, junto a `AdminPagos`:

```kotlin
data object Logs : Screen()
```

En `App.kt`:

- import: `import com.baniterio.app.ui.admin.LogsScreen`
- clave: `private const val CLAVE_LOGS = "Logs"`
- mapeos: `Screen.Logs -> CLAVE_LOGS` y `CLAVE_LOGS -> Screen.Logs`
- añadir `Screen.Logs` a la lista de pantallas que ocultan la barra inferior (línea ~202, junto a `Screen.AdminPagos`)
- wiring, junto al bloque `is Screen.AdminPagos`:

```kotlin
is Screen.Logs -> {
    BackHandler { ir(Screen.AdminIndex) }
    LogsScreen(
        adminRepo = deps.adminRepo,
        onVolver = { ir(Screen.AdminIndex) },
    )
}
```

En `AdminIndexScreen.kt`, dentro del bloque `if (esAdmin)`, junto a la tarjeta "Confirmar pagos":

```kotlin
TarjetaAdmin(
    nombre = "Registro",
    descripcion = "Qué ha hecho cada uno y qué errores han dado móvil y web.",
    cuenta = 0,
    onClick = { onAbrir(Screen.Logs) },
)
Spacer(Modifier.height(16.dp))
```

- [ ] **Step 7: Compilar el módulo Android**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/LogEventoDtos.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AdminRepository.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AdminRepositoryImpl.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/LogsScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminIndexScreen.kt \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/AdminRepositoryImplTest.kt
git commit -m "feat(logs): pantalla móvil del registro de eventos y errores, solo admin"
```

---

## Task 8: Móvil — reporte de errores de cliente centralizado en el HttpClient

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/HttpClientFactory.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/HttpClientFactoryTest.kt`

**Interfaces:**
- Consumes: `POST /api/v1/logs/cliente` (Task 3).
- Produces: `configComun(reportarError: (pantalla: String, mensaje: String) -> Unit = ::reportarErrorCliente)` — mismo nombre y mismos llamadores (`HttpClientFactory.android.kt`, `.ios.kt`) que hoy llaman `configComun()` sin argumentos, así que no hace falta tocarlos.

- [ ] **Step 1: Escribir el test que falla**

```kotlin
package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpClientFactoryTest {

    @Test
    fun reportaUnFalloDeRedQueNuncaLlegaARespuesta() = runTest {
        var pantallaReportada: String? = null
        val engine = MockEngine { request -> throw HttpRequestTimeoutException(request, 0) }
        val client = HttpClient(engine) {
            configComun(reportarError = { pantalla, _ -> pantallaReportada = pantalla })
        }

        runCatching { client.get("https://x.test/api/v1/cuentas/1") }

        assertEquals("/api/v1/cuentas/1", pantallaReportada)
    }

    @Test
    fun noReportaLaPropiaPeticionDeLogsCliente() = runTest {
        var pantallaReportada: String? = null
        val engine = MockEngine { request -> throw HttpRequestTimeoutException(request, 0) }
        val client = HttpClient(engine) {
            configComun(reportarError = { pantalla, _ -> pantallaReportada = pantalla })
        }

        runCatching { client.get("https://x.test/api/v1/logs/cliente") }

        assertNull(pantallaReportada)
    }
}
```

- [ ] **Step 2: Ejecutar y comprobar que falla**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.baniterio.app.data.HttpClientFactoryTest"`
Expected: FAIL (`configComun` no acepta el parámetro `reportarError` todavía).

- [ ] **Step 3: Ampliar `HttpClientFactory.kt`**

```kotlin
package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

expect fun crearHttpClient(): HttpClient

/** Cuerpo de `POST /api/v1/logs/cliente` (ver back `LogClienteRequest`). */
@Serializable
private data class LogClienteBody(val origen: String, val pantalla: String, val mensaje: String)

private val scopeReporte = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Cliente HTTP aparte, mínimo, solo para `POST /logs/cliente`: sin
 * `HttpResponseValidator`, así nunca puede disparar un reporte de sí mismo.
 */
private val httpReporte: HttpClient by lazy {
    HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}

/**
 * Manda el error a `POST /api/v1/logs/cliente` en una corrutina aparte, sin
 * esperar su resultado ("fire and forget"): si esto también falla, se ignora.
 * Parámetro por defecto de [configComun]; el test la sustituye por un espía.
 */
fun reportarErrorCliente(pantalla: String, mensaje: String) {
    scopeReporte.launch {
        try {
            httpReporte.post("$API_BASE_URL/logs/cliente") {
                contentType(ContentType.Application.Json)
                setBody(LogClienteBody("MOBILE", pantalla, mensaje))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // fire and forget de verdad: si el propio log también falla, no hay nada más que hacer
        }
    }
}

/**
 * Config común de Ktor: JSON tolerante, timeouts, expectSuccess (para que un
 * 4xx/5xx lance ResponseException, ya explotado en cada `Repository`), y un
 * reporte a `/logs/cliente` cuando la petición NUNCA llega a tener respuesta
 * (sin red, timeout, DNS...). Un `ResponseException` no cuenta —eso ya lo
 * registra el backend, ver `LogEventoFilter`— ni la propia petición de log,
 * para no entrar en bucle si el reporte también falla.
 */
fun HttpClientConfig<*>.configComun(reportarError: (pantalla: String, mensaje: String) -> Unit = ::reportarErrorCliente) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
    }
    expectSuccess = true
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, request ->
            if (exception !is ResponseException && !request.url.encodedPath.endsWith("/logs/cliente")) {
                reportarError(request.url.encodedPath, exception::class.simpleName ?: "Error")
            }
        }
    }
}
```

- [ ] **Step 4: Ejecutar y comprobar que pasa**

Run: `./gradlew :shared:testAndroidHostTest --tests "com.baniterio.app.data.HttpClientFactoryTest"`
Expected: PASS.

- [ ] **Step 5: Ejecutar toda la suite compartida y compilar ambas plataformas**

Run: `./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (esto también confirma que `HttpClientFactory.android.kt`/`.ios.kt`, que llaman `configComun()` sin argumentos, siguen compilando con la nueva firma).

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/HttpClientFactory.kt \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/HttpClientFactoryTest.kt
git commit -m "feat(logs): el HttpClient móvil reporta a /logs/cliente los fallos sin respuesta"
```

---

## Task 9: Merge, push y despliegue a producción

**Files:** ninguno (operativa git/despliegue).

- [ ] **Step 1: Verificación final completa**

```bash
./mvnw -f back/pom.xml verify
npx ng test --no-watch
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug
```

Expected: los tres, en verde.

- [ ] **Step 2: Merge a `main`**

```bash
git checkout main
git pull
git merge --ff-only desarrollo
```

Si `--ff-only` falla (main avanzó mientras tanto), avisar y resolver antes de seguir — no forzar.

- [ ] **Step 3: Push**

```bash
git push origin main
```

- [ ] **Step 4: Desplegar en el VPS**

Seguir el procedimiento ya probado (memoria `baniterio_deploy_produccion`): SSH al VPS, `git pull` en `/opt/baniterio`, `docker compose` para reconstruir y levantar el backend (aplica `V63__log_evento.sql` solo automáticamente) y el front. El móvil no se despliega desde aquí: la APK/IPA con la pantalla de logs y el reporte de errores se compila e instala aparte cuando el usuario lo pida.

- [ ] **Step 5: Confirmar en producción**

Comprobar que `GET /api/v1/logs` responde (403 sin sesión válida de admin es la señal de que el endpoint existe y está protegido) y que la pantalla `/panel/administracion/logs` carga en la web de producción.
