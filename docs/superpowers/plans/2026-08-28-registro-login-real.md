# Registro y login reales — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el registro y el login de Bañiterio funcionen de punta a punta — Postgres real → Spring Boot (JWT) → Angular — con el teléfono como puerta de entrada.

**Architecture:** El backend gana un paquete `auth` con dos endpoints (`POST /api/v1/auth/registro`, `POST /api/v1/auth/login`), un `JwtService` que firma/verifica tokens HS256, y un `OncePerRequestFilter` que autentica peticiones con `Authorization: Bearer`. Spring Security pasa a modo *stateless*: `/api/v1/auth/**` y `/api/v1/health` públicos, el resto tras el filtro. Una migración Flyway `V6` crea la peña "Bañiterio" y siembra los 56 teléfonos autorizados. El front gana un `AuthService` con `HttpClient` que guarda el JWT en `localStorage`; `login.ts` y `registro.ts` dejan de simular y llaman a la API.

**Tech Stack:** Java 17, Spring Boot 4.1.1 (Spring Security 7, Spring Data JPA, Flyway, Bean Validation), PostgreSQL 17, jjwt 0.12.6, Testcontainers (perfil de integración), Angular 22 (standalone, signals, reactive forms), Tailwind 4.

**Spec:** `docs/superpowers/specs/2026-08-28-registro-login-design.md` — el plan se argumenta desde el spec; los que ejecuten leen ambos.

## Global Constraints

- **Java 17.** No se sube la versión en este trabajo (`back/pom.xml` → `<java.version>17</java.version>`). Cualquier cambio de versión de Java es un trabajo aparte.
- **Versiones gestionadas por Spring Boot 4.1.1.** Solo `io.jsonwebtoken:jjwt-*` lleva versión explícita: `0.12.6` (property `<jjwt.version>`). Si al ejecutar hay una `0.12.x` más nueva, úsala; no subas a una minor distinta sin comprobar la API.
- **Raíz de paquetes backend:** `com.baniterio.api`. El código de identidad ya vive en `com.baniterio.api.identidad`; lo nuevo de autenticación va en `com.baniterio.api.auth`.
- **Prefijo de API:** `/api/v1`.
- **Formato de teléfono:** exactamente `^[67]\d{8}$` (9 dígitos, empieza por 6 o 7). Se valida en front y en back.
- **Contraseña:** mínimo 6 caracteres. Se hashea con BCrypt (`PasswordEncoder`).
- **Teléfono del fundador:** `616985168`. Al registrarse, su `usuario` queda con `esSuperadmin = true` y su `membresia` con rol `ADMIN`. El resto: `esSuperadmin = false`, rol `MIEMBRO`.
- **JWT:** HS256. Claims: `sub` = id de usuario (UUID como string), `esSuperadmin` (boolean). Caducidad 7 días. Clave desde `app.jwt.secret` (env `JWT_SECRET`), con valor por defecto solo para desarrollo local.
- **Peña piloto:** `nombre = 'Bañiterio'`, `slug = 'baniterio'`.
- **Copy de cara al usuario en español.** Mensajes de error incluidos.
- **Lombok** está en uso en las entidades; síguelo en clases nuevas que sean POJOs de datos. Los DTOs de request/response se hacen como `record` de Java (inmutables, sin Lombok).
- **Commits frecuentes**, estilo conventional commits, mensaje en español, terminando con:
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`
- **Front:** Angular 22 standalone + signals + reactive forms. Sin SSR. Sin interceptor de auth en este plan (el panel autenticado lo añadirá luego).
- **Rama:** `feature/registro-login-real` (ya creada desde `main`).

## Verificación previa (ya hecha, no repetir)

- Postgres 17 levantado con `docker compose up -d postgres` (`baniterio` / `baniterio-local`, puerto 5432).
- Flyway V1–V5 aplicadas y verdes en `flyway_schema_history` contra ese Postgres real.
- `./mvnw -q clean test-compile` pasa con JDK 17.
- `npx ng build` (front) pasa.

## File Structure

### Backend — nuevo

| Fichero | Responsabilidad |
|---|---|
| `back/src/main/resources/db/migration/V6__seed_baniterio.sql` | Crea la peña "Bañiterio" y siembra los 56 `telefono_autorizado`. Idempotente (`ON CONFLICT DO NOTHING`). |
| `back/src/main/java/com/baniterio/api/config/AppProperties.java` | `@ConfigurationProperties("app")` — tipa `app.jwt.*`, `app.identidad.telefono-fundador`, `app.cors.allowed-origins`. |
| `back/src/main/java/com/baniterio/api/auth/JwtService.java` | Firma y verifica JWT HS256. Sin dependencias de Spring web. |
| `back/src/main/java/com/baniterio/api/auth/JwtAuthenticationFilter.java` | `OncePerRequestFilter`: lee `Authorization: Bearer`, valida, puebla el `SecurityContext`. |
| `back/src/main/java/com/baniterio/api/auth/UsuarioPrincipal.java` | `record` con `id` (UUID) y `esSuperadmin` — el `principal` autenticado. |
| `back/src/main/java/com/baniterio/api/auth/AuthController.java` | `POST /registro`, `POST /login`, `GET /yo`. Solo orquesta: valida el body y delega. |
| `back/src/main/java/com/baniterio/api/auth/AuthService.java` | Lógica de registro y login (transaccional). |
| `back/src/main/java/com/baniterio/api/auth/dto/RegistroRequest.java` | `record` + anotaciones de Bean Validation. |
| `back/src/main/java/com/baniterio/api/auth/dto/LoginRequest.java` | `record` + validación. |
| `back/src/main/java/com/baniterio/api/auth/dto/LoginResponse.java` | `record` → `{ token, usuario }`. |
| `back/src/main/java/com/baniterio/api/auth/dto/UsuarioResponse.java` | `record` → `{ id, nombre, apellidos, mote, esSuperadmin }`. |
| `back/src/main/java/com/baniterio/api/auth/TelefonoNoAutorizadoException.java` | Se traduce a `403 { "codigo": "TELEFONO_NO_AUTORIZADO" }`. |
| `back/src/main/java/com/baniterio/api/auth/RegistroConflictoException.java` | Email o teléfono ya registrados → `409`. |
| `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java` | `@RestControllerAdvice`: traduce las excepciones de arriba + errores de validación a cuerpos JSON estables. |

### Backend — modificado

| Fichero | Cambio |
|---|---|
| `back/pom.xml` | Añade `jjwt-api`/`jjwt-impl`/`jjwt-jackson` (`<jjwt.version>0.12.6</jjwt.version>`), y en scope test `spring-boot-testcontainers` + `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter`. |
| `back/src/main/java/com/baniterio/api/BaniterioApiApplication.java` | Añade `@ConfigurationPropertiesScan`. |
| `back/src/main/java/com/baniterio/api/config/SecurityConfig.java` | Stateless, CORS desde `AppProperties`, `PasswordEncoder` bean, `/api/v1/auth/**` + `/api/v1/health` públicos, resto `authenticated()`, `addFilterBefore(jwtAuthenticationFilter, ...)`. |
| `back/src/main/resources/application.yml` | Añade `app.jwt.*` y `app.identidad.telefono-fundador`. |

### Backend — tests

| Fichero | Responsabilidad |
|---|---|
| `back/src/test/java/com/baniterio/api/support/TestcontainersConfiguration.java` | `@TestConfiguration` con `PostgreSQLContainer` + `@ServiceConnection`. |
| `back/src/test/java/com/baniterio/api/support/IntegrationTest.java` | Clase base abstracta: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestcontainersConfiguration.class)`. Flyway real corre sobre el contenedor. |
| `back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java` | Verifica que V1–V6 aplican y que la peña + 56 teléfonos existen. |
| `back/src/test/java/com/baniterio/api/auth/JwtServiceTest.java` | Test unitario puro (sin Spring). |
| `back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java` | Registro y login end-to-end vía HTTP contra Postgres real. |

El test existente `BaniterioApiApplicationTests` (perfil `test`, H2) se deja **tal cual**.

### Frontend — nuevo

| Fichero | Responsabilidad |
|---|---|
| `front/src/environments/environment.ts` | `apiBaseUrl` de producción (placeholder `/api/v1`). |
| `front/src/environments/environment.development.ts` | `apiBaseUrl: 'http://localhost:8080/api/v1'`. |
| `front/src/app/core/auth.service.ts` | `registro(body)`, `login(body)`, `guardarToken()`, `token()`, `cerrarSesion()`. Envuelve `HttpClient` + `localStorage`. |
| `front/src/app/core/auth.types.ts` | Interfaces de request/response compartidas por el servicio y los componentes. |

### Frontend — modificado

| Fichero | Cambio |
|---|---|
| `front/src/app/app.config.ts` | Añade `provideHttpClient(withFetch())`. |
| `front/angular.json` | `fileReplacements` de `environment.ts` → `environment.development.ts` en la config `development` (si el scaffold no lo trae ya). |
| `front/src/app/login/login.ts` | Llama a `AuthService.login`, guarda token, navega a `/panel`; error → mensaje. Signal `estado`. |
| `front/src/app/login/login.html` | Patrón de teléfono, `maxlength="9"`, bloque de error de servidor. |
| `front/src/app/registro/registro.ts` | Llama a `AuthService.registro`; 403 `TELEFONO_NO_AUTORIZADO` → mensaje de "solicitar acceso"; éxito → navega a `/login`. |
| `front/src/app/registro/registro.html` | Patrón de teléfono, `maxlength="9"`, mensajes de estado (`ok` / `no_autorizado` / `error`). |

---

## Task 1: Testcontainers + verificación de Flyway contra Postgres real

**Files:**
- Modify: `back/pom.xml`
- Create: `back/src/test/java/com/baniterio/api/support/TestcontainersConfiguration.java`
- Create: `back/src/test/java/com/baniterio/api/support/IntegrationTest.java`
- Test: `back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java`

**Interfaces:**
- Produces: `IntegrationTest` (abstract base, package `com.baniterio.api.support`) — las tasks 4-6 extienden de ella. Expone, vía Spring, un `TestRestTemplate` autoconfigurado y `@LocalServerPort int port`.
- Produces: `TestcontainersConfiguration` — `@Import`-able; arranca `postgres:17-alpine`.

- [ ] **Step 1: Añadir dependencias de test a `pom.xml`**

En `<properties>`:

```xml
<jjwt.version>0.12.6</jjwt.version>
```

En `<dependencies>`, junto a las de test existentes:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Descargar dependencias y comprobar que resuelven**

Run: `cd back && ./mvnw -q dependency:resolve -Dscope=test`
Expected: termina sin error; sin líneas `[ERROR]`.

- [ ] **Step 3: Crear `TestcontainersConfiguration`**

```java
package com.baniterio.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
```

- [ ] **Step 4: Crear la clase base `IntegrationTest`**

```java
package com.baniterio.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {
}
```

Nota: estos tests usan el perfil por defecto (no `test`), así que `application.yml` manda: Flyway habilitado, `ddl-auto: validate`. El datasource lo inyecta `@ServiceConnection` desde el contenedor.

- [ ] **Step 5: Escribir el test de migraciones (falla primero)**

`back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java`:

```java
package com.baniterio.api.identidad;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplica_todas_las_migraciones() {
        Integer aplicadas = jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(aplicadas).isGreaterThanOrEqualTo(6);
    }

    @Test
    void siembra_la_pena_baniterio_con_sus_telefonos() {
        Integer penas = jdbc.queryForObject(
            "SELECT count(*) FROM pena WHERE slug = 'baniterio'", Integer.class);
        Integer telefonos = jdbc.queryForObject("""
            SELECT count(*) FROM telefono_autorizado t
            JOIN pena p ON p.id = t.pena_id
            WHERE p.slug = 'baniterio'
            """, Integer.class);
        assertThat(penas).isEqualTo(1);
        assertThat(telefonos).isEqualTo(56);
    }
}
```

- [ ] **Step 6: Ejecutar — falla porque V6 aún no existe**

Run: `cd back && ./mvnw -q -Dtest=FlywayMigrationIT test`
Expected: `aplica_todas_las_migraciones` falla (5 < 6) y `siembra_...` falla (0 peñas). Confirma que Docker está corriendo y el contenedor arranca.

- [ ] **Step 7: Commit**

```bash
git add back/pom.xml back/src/test/java/com/baniterio/api/support/ back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java
git commit -m "test(back): infraestructura de integración con Testcontainers (Postgres real)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Migración V6 — siembra de la peña y los teléfonos autorizados

**Files:**
- Create: `back/src/main/resources/db/migration/V6__seed_baniterio.sql`
- Test: `back/src/test/java/com/baniterio/api/identidad/FlywayMigrationIT.java` (ya escrito en Task 1)

**Interfaces:**
- Produces: tras arrancar, existe una fila en `pena` con `slug = 'baniterio'` y 56 filas en `telefono_autorizado` (todas `usado = false`) apuntando a ella.

- [ ] **Step 1: Escribir la migración**

`back/src/main/resources/db/migration/V6__seed_baniterio.sql`:

```sql
-- Peña piloto. Idempotente: si ya existe (por slug), no se duplica.
INSERT INTO pena (nombre, slug, activa)
VALUES ('Bañiterio', 'baniterio', TRUE)
ON CONFLICT (slug) DO NOTHING;

-- Teléfonos autorizados de la peña Bañiterio.
-- El primero (616985168) es el del usuario fundador.
INSERT INTO telefono_autorizado (telefono, pena_id)
SELECT numero, (SELECT id FROM pena WHERE slug = 'baniterio')
FROM (VALUES
    ('616985168'),
    ('610832295'), ('628215369'), ('620330952'), ('679874772'), ('686072485'),
    ('669595418'), ('667846992'), ('679838983'), ('699783259'), ('658723954'),
    ('654419992'), ('686138781'), ('619365502'), ('600039136'), ('680995661'),
    ('630833954'), ('667625912'), ('636285861'), ('667339851'), ('651499128'),
    ('686842945'), ('658576814'), ('610531944'), ('680250908'), ('663302912'),
    ('653734658'), ('695085853'), ('653736710'), ('628904515'), ('646092105'),
    ('703676246'), ('646593935'), ('645301476'), ('620909665'), ('607967700'),
    ('696221164'), ('669076955'), ('652781296'), ('626238653'), ('626232586'),
    ('636703195'), ('626596944'), ('699657177'), ('637691976'), ('650031957'),
    ('609792628'), ('655146932'), ('620403257'), ('661038940'), ('680766641'),
    ('606332168'), ('618735707'), ('620704571'), ('677258998'), ('616416139')
) AS t(numero)
ON CONFLICT (telefono) DO NOTHING;
```

(56 números: 1 fundador + 55. La constraint `uk_telefono_autorizado_telefono` hace el `ON CONFLICT` seguro.)

- [ ] **Step 2: Ejecutar el test de migraciones — ahora pasa**

Run: `cd back && ./mvnw -q -Dtest=FlywayMigrationIT test`
Expected: los dos tests PASAN (≥6 migraciones, 1 peña, 56 teléfonos).

- [ ] **Step 3: Verificación manual contra el Postgres de docker compose**

Como el contenedor de `docker compose` ya tiene V1–V5 aplicadas, arranca la app una vez para que aplique V6:

Run: `cd back && ./mvnw -q spring-boot:run` (Ctrl+C cuando arranque)
Luego:
Run: `docker exec baniterio-postgres psql -U baniterio -d baniterio -c "SELECT count(*) FROM telefono_autorizado;"`
Expected: `56`.

- [ ] **Step 4: Commit**

```bash
git add back/src/main/resources/db/migration/V6__seed_baniterio.sql
git commit -m "feat(back): V6 siembra la peña Bañiterio y sus 56 teléfonos autorizados

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: `AppProperties` + `JwtService`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/config/AppProperties.java`
- Modify: `back/src/main/java/com/baniterio/api/BaniterioApiApplication.java`
- Modify: `back/src/main/resources/application.yml`
- Create: `back/src/main/java/com/baniterio/api/auth/JwtService.java`
- Test: `back/src/test/java/com/baniterio/api/auth/JwtServiceTest.java`

**Interfaces:**
- Produces: `AppProperties` con métodos `jwt().secret()`, `jwt().expiracionDias()`, `identidad().telefonoFundador()`, `cors().allowedOrigins()`.
- Produces: `JwtService`
  - `String generar(UUID usuarioId, boolean esSuperadmin)`
  - `Optional<UsuarioPrincipal> verificar(String token)` — vacío si inválido/caducado.
- Consumes: `UsuarioPrincipal` (Task 4 lo define; para esta task créalo aquí como `record UsuarioPrincipal(UUID id, boolean esSuperadmin)` en `com.baniterio.api.auth`).

- [ ] **Step 1: Crear `UsuarioPrincipal`**

`back/src/main/java/com/baniterio/api/auth/UsuarioPrincipal.java`:

```java
package com.baniterio.api.auth;

import java.util.UUID;

public record UsuarioPrincipal(UUID id, boolean esSuperadmin) {
}
```

- [ ] **Step 2: Crear `AppProperties`**

```java
package com.baniterio.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Identidad identidad, Cors cors) {

    public record Jwt(String secret, int expiracionDias) {
    }

    public record Identidad(String telefonoFundador) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
```

- [ ] **Step 3: Registrar el escaneo de properties**

En `BaniterioApiApplication.java`, añade la anotación:

```java
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BaniterioApiApplication {
```

- [ ] **Step 4: Añadir config a `application.yml`**

Bajo el `app:` existente (junto a `cors:`):

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:ZGV2LW9ubHktYmFuaXRlcmlvLXNlY3JldC1jaGFuZ2UtaW4tcHJvZC0xMjM0NQ==}
    expiracion-dias: 7
  identidad:
    telefono-fundador: ${TELEFONO_FUNDADOR:616985168}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}
```

(El default de `secret` es base64 de "dev-only-baniterio-secret-change-in-prod-12345", 45 bytes → suficiente para HS256. En producción se pasa `JWT_SECRET`.)

- [ ] **Step 5: Escribir `JwtServiceTest` (falla primero)**

`back/src/test/java/com/baniterio/api/auth/JwtServiceTest.java`:

```java
package com.baniterio.api.auth;

import java.util.Base64;
import java.util.UUID;

import com.baniterio.api.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
        Base64.getEncoder().encodeToString("clave-de-test-con-mas-de-32-bytes-para-hs256".getBytes());

    private final JwtService jwt = new JwtService(
        new AppProperties(new AppProperties.Jwt(SECRET, 7), null, null));

    @Test
    void genera_y_verifica_un_token() {
        UUID id = UUID.randomUUID();

        String token = jwt.generar(id, true);
        var principal = jwt.verificar(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().id()).isEqualTo(id);
        assertThat(principal.get().esSuperadmin()).isTrue();
    }

    @Test
    void rechaza_un_token_manipulado() {
        String token = jwt.generar(UUID.randomUUID(), false);
        assertThat(jwt.verificar(token + "x")).isEmpty();
    }

    @Test
    void rechaza_basura() {
        assertThat(jwt.verificar("no-es-un-jwt")).isEmpty();
    }
}
```

- [ ] **Step 6: Ejecutar — falla (no compila, `JwtService` no existe)**

Run: `cd back && ./mvnw -q -Dtest=JwtServiceTest test`
Expected: fallo de compilación.

- [ ] **Step 7: Implementar `JwtService`**

```java
package com.baniterio.api.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.baniterio.api.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final Duration expiracion;

    public JwtService(AppProperties props) {
        byte[] material = decodificar(props.jwt().secret());
        this.key = Keys.hmacShaKeyFor(material);
        this.expiracion = Duration.ofDays(props.jwt().expiracionDias());
    }

    public String generar(UUID usuarioId, boolean esSuperadmin) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("esSuperadmin", esSuperadmin)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracion)))
                .signWith(key)
                .compact();
    }

    public Optional<UsuarioPrincipal> verificar(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new UsuarioPrincipal(
                    UUID.fromString(claims.getSubject()),
                    Boolean.TRUE.equals(claims.get("esSuperadmin", Boolean.class))));
        } catch (Exception e) {
            log.debug("JWT rechazado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static byte[] decodificar(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException noEsBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 8: Ejecutar — pasa**

Run: `cd back && ./mvnw -q -Dtest=JwtServiceTest test`
Expected: 3 tests PASAN.

- [ ] **Step 9: Compilación completa**

Run: `cd back && ./mvnw -q clean test-compile`
Expected: sin error (verifica que `@ConfigurationPropertiesScan` y jjwt están bien).

- [ ] **Step 10: Commit**

```bash
git add back/src/main/java/com/baniterio/api/config/AppProperties.java \
        back/src/main/java/com/baniterio/api/auth/UsuarioPrincipal.java \
        back/src/main/java/com/baniterio/api/auth/JwtService.java \
        back/src/main/java/com/baniterio/api/BaniterioApiApplication.java \
        back/src/main/resources/application.yml \
        back/src/test/java/com/baniterio/api/auth/JwtServiceTest.java
git commit -m "feat(back): JwtService (HS256) y AppProperties tipadas

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: Spring Security stateless + filtro JWT + `GET /api/v1/auth/yo`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/auth/JwtAuthenticationFilter.java`
- Modify: `back/src/main/java/com/baniterio/api/config/SecurityConfig.java`
- Create: `back/src/main/java/com/baniterio/api/auth/AuthController.java` (solo el endpoint `/yo` en esta task; `/registro` y `/login` en Tasks 5-6)
- Create: `back/src/main/java/com/baniterio/api/auth/dto/UsuarioResponse.java`
- Test: `back/src/test/java/com/baniterio/api/auth/SecurityIT.java`

**Interfaces:**
- Consumes: `JwtService.verificar(String)`, `UsuarioPrincipal`.
- Produces: `JwtAuthenticationFilter` (bean). Tras autenticar, `SecurityContext` tiene un `UsernamePasswordAuthenticationToken` cuyo `principal` es un `UsuarioPrincipal` y con authority `ROLE_SUPERADMIN` si procede.
- Produces: `UsuarioResponse(String id, String nombre, String apellidos, String mote, boolean esSuperadmin)`.
- Produces: `GET /api/v1/auth/yo` → `200 UsuarioResponse` con token válido; `401` sin token.
- Produces: helper para los controladores `@AuthenticationPrincipal UsuarioPrincipal`.

- [ ] **Step 1: Crear `UsuarioResponse`**

```java
package com.baniterio.api.auth.dto;

import com.baniterio.api.identidad.Usuario;

public record UsuarioResponse(String id, String nombre, String apellidos, String mote, boolean esSuperadmin) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(
            u.getId().toString(), u.getNombre(), u.getApellidos(), u.getMote(), u.isEsSuperadmin());
    }
}
```

- [ ] **Step 2: Crear el filtro**

`back/src/main/java/com/baniterio/api/auth/JwtAuthenticationFilter.java`:

```java
package com.baniterio.api.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.verificar(header.substring(PREFIJO.length())).ifPresent(principal -> {
                var authorities = principal.esSuperadmin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
                        : List.<SimpleGrantedAuthority>of();
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Reescribir `SecurityConfig`**

```java
package com.baniterio.api.config;

import java.util.List;

import com.baniterio.api.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/auth/registro", "/api/v1/auth/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
```

Nota: `/api/v1/auth/yo` **no** está en `permitAll()` → queda protegido.

- [ ] **Step 4: Crear `AuthController` con solo `/yo`**

```java
package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.UsuarioResponse;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioRepository usuarios;

    public AuthController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/yo")
    public ResponseEntity<UsuarioResponse> yo(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return usuarios.findById(principal.id())
                .map(UsuarioResponse::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
```

- [ ] **Step 5: Escribir `SecurityIT` (falla primero)**

`back/src/test/java/com/baniterio/api/auth/SecurityIT.java`:

```java
package com.baniterio.api.auth;

import java.util.UUID;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SecurityIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired PenaRepository penas;

    private final RestTemplate http = new RestTemplateBuilder().build();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void yo_sin_token_devuelve_401() {
        var ex = catchThrowableOfType(
            () -> http.getForEntity(url("/api/v1/auth/yo"), String.class),
            RestClientResponseException.class);
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void yo_con_token_valido_devuelve_el_usuario() {
        Pena pena = penas.save(Pena.builder().nombre("Test").slug("test-" + UUID.randomUUID()).activa(true).build());
        Usuario u = usuarios.save(Usuario.builder()
            .telefono("6" + (10000000 + (int) (Math.random() * 80000000)))
            .email(UUID.randomUUID() + "@test.com")
            .passwordHash("x").nombre("Ana").apellidos("Pérez").activo(true).esSuperadmin(false)
            .build());

        String token = jwt.generar(u.getId(), false);
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);

        var res = http.exchange(url("/api/v1/auth/yo"), HttpMethod.GET,
            new HttpEntity<>(headers), com.baniterio.api.auth.dto.UsuarioResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().nombre()).isEqualTo("Ana");
    }
}
```

- [ ] **Step 6: Ejecutar — falla (no compila / 403 en vez de 401 antes del handler)**

Run: `cd back && ./mvnw -q -Dtest=SecurityIT test`
Expected: falla (compilación o assertion).

- [ ] **Step 7: Implementar lo que falte hasta que pase**

El código de los steps 2-4 debería bastar. Si el 401 llega como 403, añade a `SecurityConfig` un `exceptionHandling` con `authenticationEntryPoint` que haga `response.sendError(401)`:

```java
.exceptionHandling(e -> e.authenticationEntryPoint(
    (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
```

- [ ] **Step 8: Ejecutar — pasa**

Run: `cd back && ./mvnw -q -Dtest=SecurityIT test`
Expected: 2 tests PASAN.

- [ ] **Step 9: Regresión — el health sigue abierto**

Run: `cd back && ./mvnw -q -Dtest=FlywayMigrationIT,SecurityIT,JwtServiceTest,BaniterioApiApplicationTests test`
Expected: todo verde.

- [ ] **Step 10: Commit**

```bash
git add back/src/main/java/com/baniterio/api/auth/ back/src/main/java/com/baniterio/api/config/SecurityConfig.java back/src/test/java/com/baniterio/api/auth/SecurityIT.java
git commit -m "feat(back): Spring Security stateless con filtro JWT y GET /api/v1/auth/yo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: `POST /api/v1/auth/registro`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/auth/dto/RegistroRequest.java`
- Create: `back/src/main/java/com/baniterio/api/auth/TelefonoNoAutorizadoException.java`
- Create: `back/src/main/java/com/baniterio/api/auth/RegistroConflictoException.java`
- Create: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Create: `back/src/main/java/com/baniterio/api/auth/AuthService.java`
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthController.java` (añade `POST /registro`)
- Test: `back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java`

**Interfaces:**
- Consumes: `TelefonoAutorizadoRepository.findByTelefonoAndUsadoFalse`, `UsuarioRepository.existsByTelefono/existsByEmail/save`, `MembresiaRepository.save`, `PasswordEncoder`, `AppProperties.identidad().telefonoFundador()`.
- Produces: `AuthService.registrar(RegistroRequest) : Usuario` (transaccional).
- Produces: `RegistroRequest(String telefono, String email, String password, String nombre, String apellidos, String mote)`.
- Produces: `POST /api/v1/auth/registro`
  - `201` cuerpo `UsuarioResponse` en éxito.
  - `403` `{ "codigo": "TELEFONO_NO_AUTORIZADO" }` si el teléfono no está autorizado / ya usado.
  - `409` `{ "codigo": "YA_REGISTRADO" }` si el teléfono o el email ya existen.
  - `400` `{ "codigo": "VALIDACION", "errores": { campo: mensaje } }` si el body no valida.

- [ ] **Step 1: `RegistroRequest` con validación**

```java
package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @Pattern(regexp = "^[67]\\d{8}$", message = "El teléfono debe tener 9 dígitos y empezar por 6 o 7")
        String telefono,

        @NotBlank @Email
        String email,

        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        @NotBlank @Size(max = 80)
        String nombre,

        @NotBlank @Size(max = 120)
        String apellidos,

        @Size(max = 60)
        String mote) {
}
```

- [ ] **Step 2: Excepciones**

`TelefonoNoAutorizadoException.java`:

```java
package com.baniterio.api.auth;

public class TelefonoNoAutorizadoException extends RuntimeException {
    public TelefonoNoAutorizadoException() {
        super("El teléfono no está autorizado para registrarse");
    }
}
```

`RegistroConflictoException.java`:

```java
package com.baniterio.api.auth;

public class RegistroConflictoException extends RuntimeException {
    public RegistroConflictoException() {
        super("Ese teléfono o email ya está registrado");
    }
}
```

- [ ] **Step 3: `ApiExceptionHandler`**

```java
package com.baniterio.api.web;

import java.util.HashMap;
import java.util.Map;

import com.baniterio.api.auth.RegistroConflictoException;
import com.baniterio.api.auth.TelefonoNoAutorizadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TelefonoNoAutorizadoException.class)
    ResponseEntity<Map<String, Object>> telefonoNoAutorizado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("codigo", "TELEFONO_NO_AUTORIZADO"));
    }

    @ExceptionHandler(RegistroConflictoException.class)
    ResponseEntity<Map<String, Object>> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "YA_REGISTRADO"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(Map.of("codigo", "VALIDACION", "errores", errores));
    }
}
```

- [ ] **Step 4: Escribir los tests de registro en `AuthControllerIT` (fallan primero)**

`back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java`:

```java
package com.baniterio.api.auth;

import java.util.Map;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class AuthControllerIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    private final RestTemplate http = new RestTemplateBuilder().build();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // 616985168 y 610832295 están sembrados por V6.
    private Map<String, String> registroValido(String telefono, String email) {
        return Map.of("telefono", telefono, "email", email, "password", "secreto1",
                "nombre", "Ana", "apellidos", "López", "mote", "");
    }

    @Test
    void registro_con_telefono_autorizado_crea_el_usuario() {
        var res = http.postForEntity(url("/api/v1/auth/registro"),
                registroValido("610832295", "ana@baniterio.com"), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsKey("id");
        assertThat(res.getBody().get("esSuperadmin")).isEqualTo(false);
    }

    @Test
    void el_fundador_queda_como_superadmin() {
        var res = http.postForEntity(url("/api/v1/auth/registro"),
                registroValido("616985168", "fundador@baniterio.com"), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("esSuperadmin")).isEqualTo(true);
    }

    @Test
    void registro_con_telefono_no_autorizado_devuelve_403_con_codigo() {
        var ex = catchThrowableOfType(
                () -> http.postForEntity(url("/api/v1/auth/registro"),
                        registroValido("600000000", "x@x.com"), Map.class),
                RestClientResponseException.class);

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getResponseBodyAsString()).contains("TELEFONO_NO_AUTORIZADO");
    }

    @Test
    void no_se_puede_registrar_dos_veces_el_mismo_telefono() {
        http.postForEntity(url("/api/v1/auth/registro"),
                registroValido("628215369", "primero@x.com"), Map.class);

        var ex = catchThrowableOfType(
                () -> http.postForEntity(url("/api/v1/auth/registro"),
                        registroValido("628215369", "segundo@x.com"), Map.class),
                RestClientResponseException.class);

        // el teléfono queda 'usado' → el segundo intento cae como no autorizado
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registro_con_telefono_mal_formado_devuelve_400() {
        var ex = catchThrowableOfType(
                () -> http.postForEntity(url("/api/v1/auth/registro"),
                        registroValido("12345", "x@x.com"), Map.class),
                RestClientResponseException.class);

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getResponseBodyAsString()).contains("VALIDACION");
    }
}
```

Nota para quien ejecute: cada test que registra consume un teléfono sembrado (lo marca `usado`). Como el contenedor es único para la clase, usa teléfonos distintos por test (ya está así arriba). Si añades tests, coge otro número de la lista de V6.

- [ ] **Step 5: Ejecutar — fallan (endpoint no existe)**

Run: `cd back && ./mvnw -q -Dtest=AuthControllerIT test`
Expected: 404 / fallos de compilación.

- [ ] **Step 6: Implementar `AuthService.registrar`**

```java
package com.baniterio.api.auth;

import com.baniterio.api.auth.dto.RegistroRequest;
import com.baniterio.api.config.AppProperties;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.TelefonoAutorizado;
import com.baniterio.api.identidad.TelefonoAutorizadoRepository;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final TelefonoAutorizadoRepository telefonosAutorizados;
    private final UsuarioRepository usuarios;
    private final MembresiaRepository membresias;
    private final PasswordEncoder passwordEncoder;
    private final String telefonoFundador;

    public AuthService(TelefonoAutorizadoRepository telefonosAutorizados, UsuarioRepository usuarios,
                       MembresiaRepository membresias, PasswordEncoder passwordEncoder, AppProperties props) {
        this.telefonosAutorizados = telefonosAutorizados;
        this.usuarios = usuarios;
        this.membresias = membresias;
        this.passwordEncoder = passwordEncoder;
        this.telefonoFundador = props.identidad().telefonoFundador();
    }

    @Transactional
    public Usuario registrar(RegistroRequest req) {
        TelefonoAutorizado autorizado = telefonosAutorizados
                .findByTelefonoAndUsadoFalse(req.telefono())
                .orElseThrow(TelefonoNoAutorizadoException::new);

        if (usuarios.existsByTelefono(req.telefono()) || usuarios.existsByEmail(req.email())) {
            throw new RegistroConflictoException();
        }

        boolean esFundador = req.telefono().equals(telefonoFundador);

        Usuario usuario = usuarios.save(Usuario.builder()
                .telefono(req.telefono())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .nombre(req.nombre())
                .apellidos(req.apellidos())
                .mote(StringUtils.hasText(req.mote()) ? req.mote() : null)
                .esSuperadmin(esFundador)
                .activo(true)
                .build());

        membresias.save(Membresia.builder()
                .usuario(usuario)
                .pena(autorizado.getPena())
                .rol(esFundador ? RolMembresia.ADMIN : RolMembresia.MIEMBRO)
                .activa(true)
                .build());

        autorizado.setUsado(true);
        telefonosAutorizados.save(autorizado);

        return usuario;
    }
}
```

- [ ] **Step 7: Añadir `POST /registro` a `AuthController`**

```java
    // añade estos imports:
    // import com.baniterio.api.auth.dto.RegistroRequest;
    // import jakarta.validation.Valid;
    // import org.springframework.http.HttpStatus;
    // import org.springframework.web.bind.annotation.PostMapping;
    // import org.springframework.web.bind.annotation.RequestBody;
    // import org.springframework.web.bind.annotation.ResponseStatus;

    private final AuthService authService;   // añadir al constructor

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registro(@Valid @RequestBody RegistroRequest req) {
        return UsuarioResponse.de(authService.registrar(req));
    }
```

(Actualiza el constructor de `AuthController` para recibir `AuthService authService` además de `UsuarioRepository`.)

- [ ] **Step 8: Ejecutar — pasan**

Run: `cd back && ./mvnw -q -Dtest=AuthControllerIT test`
Expected: los 5 tests de registro PASAN.

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/auth/ back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java
git commit -m "feat(back): POST /api/v1/auth/registro con puerta de teléfono autorizado

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: `POST /api/v1/auth/login`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/auth/dto/LoginRequest.java`
- Create: `back/src/main/java/com/baniterio/api/auth/dto/LoginResponse.java`
- Create: `back/src/main/java/com/baniterio/api/auth/CredencialesInvalidasException.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java` (mapea la nueva excepción a `401`)
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthService.java` (añade `login`)
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthController.java` (añade `POST /login`)
- Test: `back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java` (añade casos de login)

**Interfaces:**
- Consumes: `UsuarioRepository.findByTelefono`, `PasswordEncoder.matches`, `JwtService.generar`.
- Produces: `AuthService.login(LoginRequest) : LoginResponse`.
- Produces: `LoginRequest(String telefono, String password)`.
- Produces: `LoginResponse(String token, UsuarioResponse usuario)`.
- Produces: `POST /api/v1/auth/login` → `200 LoginResponse`; `401 { "codigo": "CREDENCIALES_INVALIDAS" }` si teléfono no existe o contraseña no coincide (sin distinguir cuál).

- [ ] **Step 1: DTOs**

`LoginRequest.java`:

```java
package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String telefono, @NotBlank String password) {
}
```

`LoginResponse.java`:

```java
package com.baniterio.api.auth.dto;

public record LoginResponse(String token, UsuarioResponse usuario) {
}
```

- [ ] **Step 2: Excepción + mapping a 401**

`CredencialesInvalidasException.java`:

```java
package com.baniterio.api.auth;

public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException() {
        super("Teléfono o contraseña incorrectos");
    }
}
```

En `ApiExceptionHandler` añade:

```java
    @ExceptionHandler(com.baniterio.api.auth.CredencialesInvalidasException.class)
    ResponseEntity<Map<String, Object>> credenciales() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("codigo", "CREDENCIALES_INVALIDAS"));
    }
```

- [ ] **Step 3: Escribir los tests de login (fallan primero)**

Añade a `AuthControllerIT`:

```java
    @Test
    void login_correcto_devuelve_token_y_usuario() {
        http.postForEntity(url("/api/v1/auth/registro"),
                registroValido("620330952", "login-ok@x.com"), Map.class);

        var res = http.postForEntity(url("/api/v1/auth/login"),
                Map.of("telefono", "620330952", "password", "secreto1"), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) res.getBody().get("token")).isNotBlank();
        assertThat((Map<?, ?>) res.getBody().get("usuario")).containsKey("id");
    }

    @Test
    void login_con_password_incorrecta_devuelve_401() {
        http.postForEntity(url("/api/v1/auth/registro"),
                registroValido("679874772", "login-bad@x.com"), Map.class);

        var ex = catchThrowableOfType(
                () -> http.postForEntity(url("/api/v1/auth/login"),
                        Map.of("telefono", "679874772", "password", "otra-cosa"), Map.class),
                RestClientResponseException.class);

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponseBodyAsString()).contains("CREDENCIALES_INVALIDAS");
    }

    @Test
    void login_con_telefono_desconocido_devuelve_401() {
        var ex = catchThrowableOfType(
                () -> http.postForEntity(url("/api/v1/auth/login"),
                        Map.of("telefono", "700000000", "password", "loquesea"), Map.class),
                RestClientResponseException.class);

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void el_token_del_login_vale_para_yo() {
        http.postForEntity(url("/api/v1/auth/registro"),
                registroValido("686072485", "yo@x.com"), Map.class);
        var login = http.postForEntity(url("/api/v1/auth/login"),
                Map.of("telefono", "686072485", "password", "secreto1"), Map.class);
        String token = (String) login.getBody().get("token");

        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var res = http.exchange(url("/api/v1/auth/yo"), org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("nombre")).isEqualTo("Ana");
    }
```

- [ ] **Step 4: Ejecutar — fallan**

Run: `cd back && ./mvnw -q -Dtest=AuthControllerIT test`
Expected: los 4 nuevos fallan (404).

- [ ] **Step 5: Implementar `AuthService.login`**

```java
    // imports nuevos:
    // import com.baniterio.api.auth.dto.LoginRequest;
    // import com.baniterio.api.auth.dto.LoginResponse;
    // import com.baniterio.api.auth.dto.UsuarioResponse;

    private final JwtService jwtService;   // añadir al constructor

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        Usuario usuario = usuarios.findByTelefono(req.telefono())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(CredencialesInvalidasException::new);

        String token = jwtService.generar(usuario.getId(), usuario.isEsSuperadmin());
        return new LoginResponse(token, UsuarioResponse.de(usuario));
    }
```

(Añade `JwtService jwtService` al constructor de `AuthService`.)

- [ ] **Step 6: Añadir `POST /login` a `AuthController`**

```java
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
```

- [ ] **Step 7: Ejecutar — todo `AuthControllerIT` pasa**

Run: `cd back && ./mvnw -q -Dtest=AuthControllerIT test`
Expected: los 9 tests PASAN.

- [ ] **Step 8: Suite backend completa**

Run: `cd back && ./mvnw -q clean verify`
Expected: BUILD SUCCESS, todos los tests verdes.

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/ back/src/test/java/com/baniterio/api/auth/AuthControllerIT.java
git commit -m "feat(back): POST /api/v1/auth/login que emite JWT de 7 días

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: Frontend — `HttpClient`, entornos y `AuthService`

**Files:**
- Create: `front/src/environments/environment.ts`
- Create: `front/src/environments/environment.development.ts`
- Modify: `front/angular.json` (fileReplacements en `development`, si no está)
- Modify: `front/src/app/app.config.ts`
- Create: `front/src/app/core/auth.types.ts`
- Create: `front/src/app/core/auth.service.ts`

**Interfaces:**
- Produces: `environment` con `{ apiBaseUrl: string }`.
- Produces: `AuthService` (providedIn root):
  - `registro(body: RegistroBody): Observable<UsuarioDto>`
  - `login(body: LoginBody): Observable<LoginDto>` — como efecto secundario guarda el token en `localStorage`.
  - `token(): string | null`
  - `cerrarSesion(): void`
- Produces (`auth.types.ts`):
  - `interface RegistroBody { telefono; email; password; nombre; apellidos; mote }`
  - `interface LoginBody { telefono; password }`
  - `interface UsuarioDto { id; nombre; apellidos; mote; esSuperadmin }`
  - `interface LoginDto { token: string; usuario: UsuarioDto }`
  - `type CodigoError = 'TELEFONO_NO_AUTORIZADO' | 'YA_REGISTRADO' | 'CREDENCIALES_INVALIDAS' | 'VALIDACION'`

- [ ] **Step 1: Entornos**

`front/src/environments/environment.ts`:

```ts
export const environment = {
  apiBaseUrl: '/api/v1',
};
```

`front/src/environments/environment.development.ts`:

```ts
export const environment = {
  apiBaseUrl: 'http://localhost:8080/api/v1',
};
```

- [ ] **Step 2: fileReplacements**

En `front/angular.json`, en `projects.front.architect.build.configurations.development`, asegúrate de que existe:

```json
"fileReplacements": [
  {
    "replace": "src/environments/environment.ts",
    "with": "src/environments/environment.development.ts"
  }
]
```

Si el bloque `development` ya trae otras claves, añade `fileReplacements` sin borrarlas.

- [ ] **Step 3: Proveer `HttpClient`**

`front/src/app/app.config.ts`:

```ts
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch()),
  ],
};
```

- [ ] **Step 4: Tipos**

`front/src/app/core/auth.types.ts`:

```ts
export interface RegistroBody {
  telefono: string;
  email: string;
  password: string;
  nombre: string;
  apellidos: string;
  mote: string;
}

export interface LoginBody {
  telefono: string;
  password: string;
}

export interface UsuarioDto {
  id: string;
  nombre: string;
  apellidos: string;
  mote: string | null;
  esSuperadmin: boolean;
}

export interface LoginDto {
  token: string;
  usuario: UsuarioDto;
}

export type CodigoError =
  | 'TELEFONO_NO_AUTORIZADO'
  | 'YA_REGISTRADO'
  | 'CREDENCIALES_INVALIDAS'
  | 'VALIDACION';
```

- [ ] **Step 5: `AuthService`**

`front/src/app/core/auth.service.ts`:

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginBody, LoginDto, RegistroBody, UsuarioDto } from './auth.types';

const CLAVE_TOKEN = 'baniterio.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  registro(body: RegistroBody): Observable<UsuarioDto> {
    return this.http.post<UsuarioDto>(`${this.base}/auth/registro`, body);
  }

  login(body: LoginBody): Observable<LoginDto> {
    return this.http
      .post<LoginDto>(`${this.base}/auth/login`, body)
      .pipe(tap((res) => localStorage.setItem(CLAVE_TOKEN, res.token)));
  }

  token(): string | null {
    return localStorage.getItem(CLAVE_TOKEN);
  }

  cerrarSesion(): void {
    localStorage.removeItem(CLAVE_TOKEN);
  }
}
```

- [ ] **Step 6: Verificar build**

Run: `cd front && npx ng build --configuration development`
Expected: build OK.

- [ ] **Step 7: Commit**

```bash
git add front/src/environments/ front/angular.json front/src/app/app.config.ts front/src/app/core/
git commit -m "feat(front): HttpClient, entornos y AuthService

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: Frontend — conectar el login

**Files:**
- Modify: `front/src/app/login/login.ts`
- Modify: `front/src/app/login/login.html`

**Interfaces:**
- Consumes: `AuthService.login`.
- Produces: al hacer login correcto, navega a `/panel` con el token ya en `localStorage`. Signal `estado: 'idle' | 'enviando' | 'error'`.

- [ ] **Step 1: `login.ts`**

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<'idle' | 'enviando' | 'error'>('idle');

  protected readonly form = this.formBuilder.group({
    telefono: ['', [Validators.required, Validators.pattern(/^[67]\d{8}$/)]],
    password: ['', [Validators.required]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.estado.set('enviando');
    const { telefono, password } = this.form.getRawValue();

    this.auth.login({ telefono: telefono!, password: password! }).subscribe({
      next: () => this.router.navigateByUrl('/panel'),
      error: (err: HttpErrorResponse) => {
        this.estado.set('error');
        console.debug('login falló', err.status);
      },
    });
  }
}
```

- [ ] **Step 2: `login.html` — patrón, maxlength y error de servidor**

En el `<input formControlName="telefono">` añade `maxlength="9"` e `inputmode="numeric"`. Cambia el mensaje de error del teléfono a:

```html
@if (form.controls.telefono.invalid && form.controls.telefono.touched) {
  <span class="text-xs font-normal text-red-400">Introduce un teléfono válido (9 dígitos, empieza por 6 o 7).</span>
}
```

Justo antes del `<button type="submit">`, añade:

```html
@if (estado() === 'error') {
  <p class="rounded-xl border border-red-400/30 bg-red-400/10 px-4 py-3 text-sm text-red-300">
    Teléfono o contraseña incorrectos.
  </p>
}
```

Y en el botón, deshabilítalo mientras envía:

```html
<button
  type="submit"
  [disabled]="estado() === 'enviando'"
  class="mt-2 rounded-xl bg-brand px-6 py-3 font-display font-bold text-gold shadow-[0_0_30px_-8px_rgba(248,211,73,0.5)] transition hover:bg-brand-bright disabled:opacity-60"
>
  {{ estado() === 'enviando' ? 'Entrando…' : 'Entrar' }}
</button>
```

- [ ] **Step 3: Verificación manual end-to-end**

1. `docker compose up -d postgres`
2. `cd back && ./mvnw spring-boot:run` (aplica V6)
3. `cd front && npx ng serve`
4. Abre `http://localhost:4200/registro`, regístrate con `616985168` / email cualquiera / `secreto1`.
5. Ve a `/login`, entra con `616985168` / `secreto1` → debe llevarte a `/panel`.
6. DevTools → Application → Local Storage → comprueba `baniterio.token`.
7. Prueba una contraseña mal → aparece el mensaje rojo, no navega.

Expected: todo lo anterior se cumple.

- [ ] **Step 4: Build**

Run: `cd front && npx ng build --configuration development`
Expected: OK.

- [ ] **Step 5: Commit**

```bash
git add front/src/app/login/
git commit -m "feat(front): login real contra POST /api/v1/auth/login

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 9: Frontend — conectar el registro

**Files:**
- Modify: `front/src/app/registro/registro.ts`
- Modify: `front/src/app/registro/registro.html`

**Interfaces:**
- Consumes: `AuthService.registro`.
- Produces: signal `estado: 'idle' | 'enviando' | 'ok' | 'no_autorizado' | 'error'`. En `ok` navega a `/login`. En `no_autorizado` muestra el mensaje de "solicitar acceso" (sin construir el formulario — fuera de alcance).

- [ ] **Step 1: `registro.ts`**

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

type Estado = 'idle' | 'enviando' | 'ok' | 'no_autorizado' | 'error';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-registro',
  styleUrl: './registro.css',
  templateUrl: './registro.html',
})
export class Registro {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<Estado>('idle');

  protected readonly form = this.formBuilder.group({
    nombre: ['', [Validators.required]],
    apellidos: ['', [Validators.required]],
    mote: [''],
    telefono: ['', [Validators.required, Validators.pattern(/^[67]\d{8}$/)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.estado.set('enviando');
    const v = this.form.getRawValue();

    this.auth
      .registro({
        nombre: v.nombre!,
        apellidos: v.apellidos!,
        mote: v.mote ?? '',
        telefono: v.telefono!,
        email: v.email!,
        password: v.password!,
      })
      .subscribe({
        next: () => {
          this.estado.set('ok');
          setTimeout(() => this.router.navigateByUrl('/login'), 1200);
        },
        error: (err: HttpErrorResponse) => {
          this.estado.set(err.error?.codigo === 'TELEFONO_NO_AUTORIZADO' ? 'no_autorizado' : 'error');
        },
      });
  }
}
```

- [ ] **Step 2: `registro.html`**

Reemplaza el bloque `@if (estado() === 'enviado') { ... } @else { ...form... }` por:

```html
@switch (estado()) {
  @case ('ok') {
    <p class="mt-6 rounded-xl border border-brand-bright/30 bg-brand/15 px-4 py-3 text-sm text-gold-soft">
      ¡Cuenta creada! Te llevamos al login…
    </p>
  }
  @case ('no_autorizado') {
    <p class="mt-6 rounded-xl border border-gold/30 bg-gold/10 px-4 py-3 text-sm text-gold-soft">
      Tu teléfono no está en la lista de la peña. Pídele a un miembro que te autorice
      y vuelve a intentarlo. (El formulario de solicitud de acceso llegará pronto.)
    </p>
  }
  @default {
    <form class="mt-6 flex flex-col gap-4" [formGroup]="form" (ngSubmit)="enviar()">
      <!-- ...campos existentes sin cambios... -->
      <!-- en el input de teléfono: maxlength="9" inputmode="numeric" -->
      <!-- mensaje de error del teléfono: -->
      <!--   "Teléfono válido: 9 dígitos, empieza por 6 o 7." -->

      @if (estado() === 'error') {
        <p class="rounded-xl border border-red-400/30 bg-red-400/10 px-4 py-3 text-sm text-red-300">
          No se pudo completar el registro. Revisa los datos e inténtalo de nuevo.
        </p>
      }

      <button
        type="submit"
        [disabled]="estado() === 'enviando'"
        class="mt-2 rounded-xl bg-brand px-6 py-3 font-display font-bold text-gold shadow-[0_0_30px_-8px_rgba(248,211,73,0.5)] transition hover:bg-brand-bright disabled:opacity-60"
      >
        {{ estado() === 'enviando' ? 'Creando…' : 'Crear cuenta' }}
      </button>
    </form>
  }
}
```

Conserva los campos nombre/apellidos/mote/email/password exactamente como están; solo añade `maxlength="9"` e `inputmode="numeric"` al de teléfono y actualiza su mensaje de error.

- [ ] **Step 3: Verificación manual**

Con Postgres + back + `ng serve` levantados:
1. `/registro` con un teléfono **no** sembrado (`600000000`) → mensaje amarillo "no está en la lista".
2. `/registro` con `610832295` y datos válidos → mensaje "cuenta creada", redirige a `/login` en ~1s.
3. Repite con `610832295` → como el teléfono quedó `usado`, sale el mensaje de "no autorizado".
4. `/registro` con teléfono `abc`/corto → el input no deja pasar de 9 y el form marca error sin llamar al server.

Expected: los 4 casos se comportan así.

- [ ] **Step 4: Build**

Run: `cd front && npx ng build --configuration development`
Expected: OK.

- [ ] **Step 5: Commit**

```bash
git add front/src/app/registro/
git commit -m "feat(front): registro real; teléfono no autorizado muestra aviso de acceso

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 10: Cierre — verificación integral y limpieza

**Files:**
- Modify: `README.md` o `back/HELP.md` (sección "Levantar el proyecto en local")

- [ ] **Step 1: Suite backend completa desde cero**

Run: `cd back && ./mvnw -q clean verify`
Expected: BUILD SUCCESS. Deja constancia del número de tests que pasan.

- [ ] **Step 2: Build de producción del front**

Run: `cd front && npx ng build`
Expected: OK (usa `environment.ts` con `/api/v1`).

- [ ] **Step 3: Repaso end-to-end manual con la base limpia**

```bash
docker compose down -v && docker compose up -d postgres
cd back && ./mvnw spring-boot:run   # aplica V1..V6
# en otra terminal:
cd front && npx ng serve
```

Flujo completo: registro del fundador `616985168` → login → `/panel` → token en localStorage → `GET /api/v1/auth/yo` con ese token (desde DevTools o curl) devuelve `esSuperadmin: true`.

- [ ] **Step 4: Documentar el arranque local**

Añade al README una sección corta:

```markdown
## Local

1. `docker compose up -d postgres`
2. `cd back && ./mvnw spring-boot:run` — aplica las migraciones (incluida la siembra de teléfonos)
3. `cd front && npm install && npx ng serve` — http://localhost:4200

Variables opcionales del backend: `JWT_SECRET`, `TELEFONO_FUNDADOR`, `DATABASE_URL`.
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: cómo levantar Bañiterio en local (Postgres + back + front)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Abrir PR**

```bash
git push -u origin feature/registro-login-real
gh pr create --base main --title "Registro y login reales (Postgres + JWT + Angular)" \
  --body "Implementa el spec docs/superpowers/specs/2026-08-28-registro-login-design.md.

- V6: peña Bañiterio + 56 teléfonos autorizados
- POST /api/v1/auth/registro (puerta de teléfono, fundador = superadmin)
- POST /api/v1/auth/login (JWT HS256, 7 días)
- Filtro JWT + Spring Security stateless + GET /api/v1/auth/yo
- Front: AuthService, login y registro reales, validación de teléfono

Tests de integración con Testcontainers (Postgres real).

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

---

## Self-Review

**1. Cobertura del spec:**

| Requisito del spec | Task |
|---|---|
| Levantar Postgres + verificar Flyway V1-V5 | Ya hecho + Task 1 (test que lo blinda) |
| `V6__seed_baniterio.sql` (peña + teléfono fundador) | Task 2 (56 teléfonos) |
| `POST /api/v1/auth/registro` con puerta de teléfono | Task 5 |
| `403` con cuerpo `{ codigo: TELEFONO_NO_AUTORIZADO }` | Task 5 (`ApiExceptionHandler`) |
| Crea usuario (BCrypt) + membresía MIEMBRO + marca `usado` en una transacción | Task 5 (`AuthService.registrar` `@Transactional`) |
| `201` sin JWT | Task 5 (`@ResponseStatus(CREATED)`, devuelve `UsuarioResponse`) |
| `POST /api/v1/auth/login` → JWT (`sub`, `esSuperadmin`, 7 días) | Task 6 + Task 3 (`JwtService`) |
| `401` genérico si teléfono o password fallan | Task 6 (`CredencialesInvalidasException`) |
| Filtro `OncePerRequestFilter` que valida el JWT | Task 4 |
| `/api/v1/auth/**` y `/api/v1/health` públicos, resto tras el filtro | Task 4 (`SecurityConfig`) |
| jjwt (`jjwt-api`/`impl`/`jackson`), versión fijada | Task 1 (`<jjwt.version>0.12.6`) |
| `login.ts` / `registro.ts` con `HttpClient` real | Tasks 7-9 |
| Validación de teléfono en Angular (input no deja teclear de más) y en backend | `maxlength=9` + `pattern` (Tasks 8-9); `@Pattern` (Task 5) |
| Front: token en `localStorage`, navegación a `/panel` | Task 7-8 |
| Front: en `403` mostrar mensaje de solicitar ingreso, sin construir el formulario | Task 9 (`estado 'no_autorizado'`) |
| Test de integración con Testcontainers para los endpoints | Tasks 1, 5, 6 (`AuthControllerIT`) |
| Fuera de alcance: recordar contraseña, panel admin, refresh tokens, formulario de solicitud | No se implementan — respetado |

**2. Placeholders:** No hay `TODO`/`TBD`. Todo step de código lleva código real. El único "rellena tú" es en `registro.html` Task 9 step 2, donde se pide conservar campos existentes ya mostrados literalmente en el spec del fichero — aceptable porque el fichero completo está en el repo y el cambio es aditivo.

**3. Consistencia de tipos:**
- `UsuarioPrincipal(UUID id, boolean esSuperadmin)` — creado en Task 3, usado en Tasks 3/4.
- `JwtService.generar(UUID, boolean)` / `verificar(String): Optional<UsuarioPrincipal>` — Task 3, consumido en Tasks 4/6.
- `UsuarioResponse.de(Usuario)` — Task 4, usado en Tasks 4/5/6.
- `AuthService` constructor va creciendo: Task 5 lo crea con `(telefonosAutorizados, usuarios, membresias, passwordEncoder, props)`, Task 6 le añade `jwtService`. Señalado en cada task.
- `AuthController` constructor: Task 4 `(usuarios)`, Task 5 pasa a `(usuarios, authService)`. Señalado.
- Front: `RegistroBody.mote: string` (no opcional) — el back acepta `""` y lo normaliza a `null` (`AuthService.registrar`). Consistente.
- `environment.apiBaseUrl` sin `/` final; los servicios concatenan `/auth/...`. Consistente.
