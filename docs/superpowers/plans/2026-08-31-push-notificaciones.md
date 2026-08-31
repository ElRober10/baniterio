# Notificaciones push — plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development
> (recomendado) o superpowers:executing-plans para ejecutar este plan tarea a
> tarea. Los pasos usan casillas (`- [ ]`).

**Goal:** cuando entra una solicitud de acceso a la peña, enviar una
notificación push a los administradores; infraestructura genérica reutilizable
para futuros avisos.

**Architecture:** el backend guarda los tokens FCM de cada usuario
(`dispositivo`), y un `@TransactionalEventListener` traduce un evento de dominio
(`AvisoPushEvent`) en envíos vía Firebase Cloud Messaging, eligiendo la
implementación real o de log según `app.push.modo`. El móvil (Android)
registra/borra su token al iniciar/cerrar sesión.

**Tech Stack:** Java 17 · Spring Boot 4.1 · Flyway · Firebase Admin SDK ·
Kotlin Multiplatform · Ktor 3.5 · Firebase Cloud Messaging (Android).

**Spec:** `docs/superpowers/specs/2026-08-31-push-notificaciones-design.md`

## Global Constraints

- **Backend:** Java 17, Spring Boot 4.1.x, Maven (`back/`). Migraciones Flyway
  `back/src/main/resources/db/migration/V*__desc.sql`; la siguiente es `V9`.
  Hibernate `ddl-auto: validate` (las `@Entity` deben cuadrar con lo que crea
  Flyway). Entidades con Lombok `@Getter @Setter @NoArgsConstructor
  @AllArgsConstructor @Builder`.
- **Config propia** en `AppProperties` (record, `@ConfigurationProperties("app")`)
  + `application.yml` con `${VARIABLE:por-defecto}`.
- **Sin credenciales para compilar/testear:** `app.push.modo` por defecto `log`
  → `PushEnLog`, no toca Firebase. Nunca añadir credenciales al repo ni a los
  tests.
- **Seguridad:** las rutas nuevas `/api/v1/**` quedan autenticadas por defecto
  (`SecurityConfig` ya hace `anyRequest().authenticated()`); NO tocar la lista
  `permitAll`. Principal: `@AuthenticationPrincipal UsuarioPrincipal principal`
  (`principal.id()`, `principal.esSuperadmin()`); puede ser `null`.
- **Tests backend:** unitarios `*Test` (JUnit 5 + Mockito + AssertJ, sin Spring,
  patrón de `AdminServiceTest`); integración `*IT extends IntegrationTest`
  (`@SpringBootTest(RANDOM_PORT)` + Testcontainers Postgres + Flyway real,
  patrón de `AdminSolicitudesIT`, token vía crear usuario por repos + login
  HTTP). `mvn test` corre `*Test`; `mvn verify` corre `*IT`.
- **Móvil:** `commonMain` sin dependencias de plataforma. Ktor con
  `expectSuccess = true` (4xx/5xx lanza `ResponseException`). Patrón `peticion {}`
  con `try` acotado (nunca `runCatching`; relanzar `CancellationException`).
  Token JWT solo en memoria (`SesionHolder.token`); cabecera Bearer vía
  `sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }`.
- **Android:** `applicationId com.baniterio.app`, `minSdk 24`, `targetSdk 36`,
  AGP 9, Kotlin 2.4. Grafo `Dependencias` construido en `BaniterioApp` (proceso).
- **iOS:** NO se ejecuta ni compila en este plan (necesita Mac). La Tarea 11
  deja el código escrito y marcado.
- **Idioma:** código, comentarios y mensajes de commit en español, siguiendo el
  estilo del repo.

---

## File Structure

**Backend — nuevos:**
- `identidad/Dispositivo.java` — entidad token↔usuario.
- `identidad/PlataformaDispositivo.java` — enum `ANDROID`/`IOS`.
- `identidad/DispositivoRepository.java` — acceso a BBDD.
- `db/migration/V9__create_dispositivo.sql` — tabla.
- `push/` (paquete nuevo `com.baniterio.api.push`):
  - `ServicioPush.java` — interfaz de envío.
  - `PushEnLog.java` — impl de log (por defecto).
  - `PushFirebase.java` — impl FCM (`modo=fcm`).
  - `ConfiguracionFirebase.java` — inicializa `FirebaseApp` + bean `EnvioMulticast`.
  - `EnvioMulticast.java` — costura testeable sobre `FirebaseMessaging`.
  - `Audiencia.java` — sealed: `UsuarioUnico`/`TodaLaPena`/`Administradores`.
  - `ResolutorAudiencia.java` — audiencia → `List<Long>` usuarioId.
  - `AvisoPushEvent.java` — evento de dominio.
  - `ManejadorAvisoPush.java` — listener AFTER_COMMIT → envío.
  - `DispositivoService.java` — alta/baja/poda de dispositivos.
  - `DispositivoController.java` — `POST`/`DELETE /api/v1/dispositivos`.
  - `dto/RegistrarDispositivoRequest.java`.

**Backend — modificados:**
- `config/AppProperties.java` — record `Push` nuevo.
- `application.yml` — bloque `app.push`.
- `pom.xml` — dependencia `com.google.firebase:firebase-admin`.
- `identidad/MembresiaRepository.java` — `findByPenaIdAndActivaTrue`.
- `auth/AuthService.java` — `ApplicationEventPublisher` + publicar el evento.

**Móvil — nuevos:**
- `shared/commonMain/.../data/DispositivoRepository.kt` — interfaz.
- `shared/commonMain/.../data/DispositivoRepositoryImpl.kt` — impl Ktor.
- `shared/commonMain/.../data/dto/DispositivoDtos.kt` — `RegistrarDispositivoRequest`.
- `shared/commonTest/.../data/DispositivoRepositoryImplTest.kt`.
- `androidApp/.../BaniterioMessagingService.kt` — servicio FCM.

**Móvil — modificados:**
- `shared/commonMain/.../data/Dependencias.kt` — `dispositivoRepo`.
- `shared/commonMain/.../App.kt` — callbacks `alIniciarSesion`/`alCerrarSesion`.
- `shared/build.gradle.kts` — `ktor-client-mock` en `commonTest`.
- `mobile/gradle/libs.versions.toml` — Firebase BOM + messaging + plugin.
- `mobile/build.gradle.kts` — plugin `google-services` (apply false).
- `mobile/androidApp/build.gradle.kts` — plugin + deps Firebase.
- `mobile/androidApp/src/main/AndroidManifest.xml` — permiso + servicio.
- `mobile/androidApp/src/main/kotlin/.../BaniterioApp.kt` — canal de notificación.
- `mobile/androidApp/src/main/kotlin/.../MainActivity.kt` — permiso + callbacks.
- `mobile/.gitignore` — `google-services.json`.
- `mobile/README.md` — pasos de Firebase.

**iOS — Tarea 11, escrito y NO ejecutado.**

---

## Task 1: Entidad y tabla `dispositivo`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/identidad/PlataformaDispositivo.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/Dispositivo.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/DispositivoRepository.java`
- Create: `back/src/main/resources/db/migration/V9__create_dispositivo.sql`
- Test: `back/src/test/java/com/baniterio/api/identidad/DispositivoRepositoryIT.java`

**Interfaces:**
- Produces:
  - `enum PlataformaDispositivo { ANDROID, IOS }`
  - `Dispositivo` (Lombok builder) con `Long getId()`, `Usuario getUsuario()`,
    `String getToken()`, `PlataformaDispositivo getPlataforma()`,
    `Instant getActualizadoEn()`, setters correspondientes.
  - `DispositivoRepository extends JpaRepository<Dispositivo, Long>`:
    - `Optional<Dispositivo> findByToken(String token)`
    - `List<Dispositivo> findByUsuarioIdIn(Collection<Long> usuarioIds)`
    - `void deleteByToken(String token)`
    - `void deleteByTokenIn(Collection<String> tokens)`
    - `boolean existsByToken(String token)`

- [ ] **Step 1: Migración Flyway**

Crear `V9__create_dispositivo.sql`:

```sql
-- Token de notificaciones push (FCM/APNs) de cada instalación de la app.
-- Un token identifica un dispositivo+app; si otra persona inicia sesión en ese
-- móvil, el token pasa a ser suyo (UPDATE de usuario_id), de ahí el UNIQUE.
CREATE TABLE dispositivo (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token          TEXT NOT NULL UNIQUE,
    plataforma     TEXT NOT NULL,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dispositivo_usuario ON dispositivo (usuario_id);
```

- [ ] **Step 2: Enum**

```java
package com.baniterio.api.identidad;

/** Sistema de push del dispositivo. Se guarda como texto en {@code dispositivo.plataforma}. */
public enum PlataformaDispositivo {
    ANDROID,
    IOS
}
```

- [ ] **Step 3: Entidad**

```java
package com.baniterio.api.identidad;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
 * Token de notificaciones push de una instalación concreta de la app, ligado al
 * usuario que tiene sesión en ese dispositivo. El token es único: cuando otra
 * persona entra en el mismo móvil, se reasigna {@code usuario} (ver
 * {@code DispositivoService.registrar}).
 */
@Entity
@Table(name = "dispositivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlataformaDispositivo plataforma;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
```

- [ ] **Step 4: Repositorio**

```java
package com.baniterio.api.identidad;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Dispositivo} (token push ↔ usuario). */
public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    Optional<Dispositivo> findByToken(String token);

    boolean existsByToken(String token);

    List<Dispositivo> findByUsuarioIdIn(Collection<Long> usuarioIds);

    void deleteByToken(String token);

    void deleteByTokenIn(Collection<String> tokens);
}
```

- [ ] **Step 5: Escribir el IT (falla primero)**

`DispositivoRepositoryIT.java` — patrón `AdminSolicitudesIT` (extiende
`IntegrationTest`, usa `@Autowired`). Crea un `Usuario` por repo y prueba el
guardado:

```java
package com.baniterio.api.identidad;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DispositivoRepositoryIT extends IntegrationTest {

    @Autowired
    DispositivoRepository dispositivos;

    @Autowired
    UsuarioRepository usuarios;

    private Usuario nuevoUsuario() {
        String t = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        return usuarios.save(Usuario.builder()
                .telefono(t).email(t + "@disp.test").passwordHash("x")
                .nombre("D").apellidos("T").esSuperadmin(false).activo(true).build());
    }

    @Test
    void guarda_y_busca_por_token() {
        Usuario u = nuevoUsuario();
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token("tok-" + u.getId()).plataforma(PlataformaDispositivo.ANDROID).build());

        assertThat(dispositivos.findByToken("tok-" + u.getId())).isPresent();
        assertThat(dispositivos.findByUsuarioIdIn(List.of(u.getId()))).hasSize(1);
    }

    @Test
    void borra_por_token_en_lote() {
        Usuario u = nuevoUsuario();
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token("a-" + u.getId()).plataforma(PlataformaDispositivo.ANDROID).build());
        dispositivos.save(Dispositivo.builder()
                .usuario(u).token("b-" + u.getId()).plataforma(PlataformaDispositivo.IOS).build());

        dispositivos.deleteByTokenIn(List.of("a-" + u.getId(), "b-" + u.getId()));

        assertThat(dispositivos.findByUsuarioIdIn(List.of(u.getId()))).isEmpty();
    }
}
```

- [ ] **Step 6: Verificar RED**

Run: `cd back && ./mvnw -q verify -Dit.test=DispositivoRepositoryIT -Dtest=nada`
Expected: falla a compilar (clases nuevas) o el IT falla.
(Nota: si el runner de Windows usa `mvnw.cmd`, usar ese. Failsafe = `verify`.)

- [ ] **Step 7: Verificar GREEN**

Con los ficheros de los pasos 1-4 creados:
Run: `cd back && ./mvnw -q verify -Dit.test=DispositivoRepositoryIT`
Expected: PASS. Flyway aplica `V9`; Hibernate `validate` no protesta.

- [ ] **Step 8: Commit**

```bash
git add back/src/main/java/com/baniterio/api/identidad/Dispositivo.java \
        back/src/main/java/com/baniterio/api/identidad/PlataformaDispositivo.java \
        back/src/main/java/com/baniterio/api/identidad/DispositivoRepository.java \
        back/src/main/resources/db/migration/V9__create_dispositivo.sql \
        back/src/test/java/com/baniterio/api/identidad/DispositivoRepositoryIT.java
git commit -m "feat(push): tabla y entidad dispositivo (token push ↔ usuario)"
```

---

## Task 2: Registro y baja de dispositivos (servicio + endpoints)

**Files:**
- Create: `back/src/main/java/com/baniterio/api/push/DispositivoService.java`
- Create: `back/src/main/java/com/baniterio/api/push/DispositivoController.java`
- Create: `back/src/main/java/com/baniterio/api/push/dto/RegistrarDispositivoRequest.java`
- Test: `back/src/test/java/com/baniterio/api/push/DispositivoServiceTest.java`
- Test: `back/src/test/java/com/baniterio/api/push/DispositivoControllerIT.java`

**Interfaces:**
- Consumes: `DispositivoRepository`, `UsuarioRepository`, `PlataformaDispositivo`,
  `Dispositivo` (Task 1); `UsuarioPrincipal` (`auth`).
- Produces:
  - `DispositivoService`:
    - `void registrar(Long usuarioId, String token, PlataformaDispositivo plataforma)`
      — `@Transactional`; upsert por token (reasigna `usuario` y `plataforma` si
      ya existía).
    - `void darDeBaja(Long usuarioId, String token)` — `@Transactional`; borra la
      fila SOLO si su `usuario.id == usuarioId`.
    - `void podar(Collection<String> tokens)` — `@Transactional`; `deleteByTokenIn`
      (lo usa Task 6 desde el listener AFTER_COMMIT).
  - `RegistrarDispositivoRequest(@NotBlank String token, @NotNull PlataformaDispositivo plataforma)`.

- [ ] **Step 1: DTO**

```java
package com.baniterio.api.push.dto;

import com.baniterio.api.identidad.PlataformaDispositivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/dispositivos}: el token de push del móvil y su plataforma. */
public record RegistrarDispositivoRequest(
        @NotBlank String token,
        @NotNull PlataformaDispositivo plataforma) {
}
```

- [ ] **Step 2: Escribir `DispositivoServiceTest` (falla primero)**

```java
package com.baniterio.api.push;

import java.util.Optional;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispositivoServiceTest {

    private final DispositivoRepository dispositivos = mock(DispositivoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final DispositivoService servicio = new DispositivoService(dispositivos, usuarios);

    @Test
    void registrar_inserta_si_el_token_es_nuevo() {
        when(dispositivos.findByToken("tk")).thenReturn(Optional.empty());
        when(usuarios.findById(5L)).thenReturn(Optional.of(Usuario.builder().id(5L).build()));

        servicio.registrar(5L, "tk", PlataformaDispositivo.ANDROID);

        verify(dispositivos).save(any(Dispositivo.class));
    }

    @Test
    void registrar_reasigna_usuario_si_el_token_ya_existia() {
        Dispositivo existente = Dispositivo.builder()
                .id(1L).usuario(Usuario.builder().id(9L).build())
                .token("tk").plataforma(PlataformaDispositivo.ANDROID).build();
        when(dispositivos.findByToken("tk")).thenReturn(Optional.of(existente));
        when(usuarios.findById(5L)).thenReturn(Optional.of(Usuario.builder().id(5L).build()));

        servicio.registrar(5L, "tk", PlataformaDispositivo.IOS);

        assertThat(existente.getUsuario().getId()).isEqualTo(5L);
        assertThat(existente.getPlataforma()).isEqualTo(PlataformaDispositivo.IOS);
        verify(dispositivos).save(existente);
    }

    @Test
    void darDeBaja_no_borra_el_dispositivo_de_otro() {
        Dispositivo ajeno = Dispositivo.builder()
                .usuario(Usuario.builder().id(9L).build()).token("tk").build();
        when(dispositivos.findByToken("tk")).thenReturn(Optional.of(ajeno));

        servicio.darDeBaja(5L, "tk");

        verify(dispositivos, never()).deleteByToken(any());
    }
}
```

- [ ] **Step 3: `DispositivoService`**

```java
package com.baniterio.api.push;

import java.util.Collection;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, baja y poda de {@link Dispositivo}. Un token identifica una instalación
 * de la app: si al registrarlo ya existe, se reasigna a quien lo registra ahora
 * (otra persona ha iniciado sesión en ese móvil).
 */
@Service
public class DispositivoService {

    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;

    public DispositivoService(DispositivoRepository dispositivos, UsuarioRepository usuarios) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
    }

    @Transactional
    public void registrar(Long usuarioId, String token, PlataformaDispositivo plataforma) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("usuario " + usuarioId + " no existe"));
        Dispositivo d = dispositivos.findByToken(token).orElseGet(Dispositivo::new);
        d.setUsuario(usuario);
        d.setToken(token);
        d.setPlataforma(plataforma);
        dispositivos.save(d);
    }

    @Transactional
    public void darDeBaja(Long usuarioId, String token) {
        dispositivos.findByToken(token)
                .filter(d -> d.getUsuario().getId().equals(usuarioId))
                .ifPresent(d -> dispositivos.deleteByToken(token));
    }

    /** Borra tokens que el proveedor de push ha marcado como muertos. */
    @Transactional
    public void podar(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            dispositivos.deleteByTokenIn(tokens);
        }
    }
}
```

- [ ] **Step 4: `DispositivoController`**

```java
package com.baniterio.api.push;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.push.dto.RegistrarDispositivoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro del token de push del móvil del usuario que tiene sesión. Ruta
 * autenticada (sin token JWT → 401, lo pone {@code SecurityConfig}). El
 * {@code DELETE} es idempotente y solo afecta al dispositivo del propio usuario.
 */
@RestController
@RequestMapping("/api/v1/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivos;

    public DispositivoController(DispositivoService dispositivos) {
        this.dispositivos = dispositivos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@AuthenticationPrincipal UsuarioPrincipal principal,
                          @Valid @RequestBody RegistrarDispositivoRequest req) {
        dispositivos.registrar(principal.id(), req.token(), req.plataforma());
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void darDeBaja(@AuthenticationPrincipal UsuarioPrincipal principal,
                          @PathVariable String token) {
        dispositivos.darDeBaja(principal.id(), token);
    }
}
```

- [ ] **Step 5: Escribir `DispositivoControllerIT`**

Patrón `AdminSolicitudesIT`: helper que crea usuario + login HTTP para el token.
Copiar `telefonoLibre()` y un `tokenDeUsuario()` reducido (rol `MIEMBRO` basta;
la ruta no exige área).

```java
package com.baniterio.api.push;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.DispositivoRepository;
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

class DispositivoControllerIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DispositivoRepository dispositivos;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private record Sesion(Long usuarioId, String token) {}

    private Sesion nuevaSesion() {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@d.test").passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("D").apellidos("T").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder()
                .usuario(u).pena(penas.findBySlug("baniterio").orElseThrow())
                .rol(RolMembresia.MIEMBRO).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    @Test
    void sin_token_jwt_devuelve_401() {
        http.post().uri("/api/v1/dispositivos")
                .body(Map.of("token", "tk", "plataforma", "ANDROID"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void registra_y_luego_da_de_baja_el_propio_dispositivo() {
        Sesion s = nuevaSesion();
        String tk = "tk-" + s.usuarioId();

        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + s.token())
                .body(Map.of("token", tk, "plataforma", "ANDROID"))
                .exchange().expectStatus().isNoContent();
        assertThat(dispositivos.findByToken(tk)).isPresent();

        http.delete().uri("/api/v1/dispositivos/" + tk).header(AUTHORIZATION, "Bearer " + s.token())
                .exchange().expectStatus().isNoContent();
        assertThat(dispositivos.findByToken(tk)).isEmpty();
    }

    @Test
    void un_segundo_registro_del_mismo_token_reasigna_sin_duplicar() {
        Sesion a = nuevaSesion();
        Sesion b = nuevaSesion();
        String tk = "compartido-" + a.usuarioId();

        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + a.token())
                .body(Map.of("token", tk, "plataforma", "ANDROID"))
                .exchange().expectStatus().isNoContent();
        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + b.token())
                .body(Map.of("token", tk, "plataforma", "IOS"))
                .exchange().expectStatus().isNoContent();

        assertThat(dispositivos.findByUsuarioIdIn(java.util.List.of(a.usuarioId()))).isEmpty();
        assertThat(dispositivos.findByToken(tk)).get()
                .extracting(d -> d.getUsuario().getId()).isEqualTo(b.usuarioId());
    }

    @Test
    void baja_de_un_token_ajeno_no_lo_borra() {
        Sesion dueno = nuevaSesion();
        Sesion otro = nuevaSesion();
        String tk = "de-" + dueno.usuarioId();
        http.post().uri("/api/v1/dispositivos").header(AUTHORIZATION, "Bearer " + dueno.token())
                .body(Map.of("token", tk, "plataforma", "ANDROID"))
                .exchange().expectStatus().isNoContent();

        http.delete().uri("/api/v1/dispositivos/" + tk).header(AUTHORIZATION, "Bearer " + otro.token())
                .exchange().expectStatus().isNoContent();

        assertThat(dispositivos.findByToken(tk)).isPresent();
    }
}
```

- [ ] **Step 6: Verificar RED → implementar → GREEN**

Run: `cd back && ./mvnw -q test -Dtest=DispositivoServiceTest` → PASS tras el paso 3.
Run: `cd back && ./mvnw -q verify -Dit.test=DispositivoControllerIT` → PASS tras el paso 4.

- [ ] **Step 7: Commit**

```bash
git add back/src/main/java/com/baniterio/api/push/ back/src/test/java/com/baniterio/api/push/
git commit -m "feat(push): endpoints de alta/baja de dispositivo + servicio"
```

---

## Task 3: `ServicioPush` + `PushEnLog` + config + dependencia Firebase

**Files:**
- Modify: `back/pom.xml` (dependencia `firebase-admin`)
- Modify: `back/src/main/java/com/baniterio/api/config/AppProperties.java`
- Modify: `back/src/main/resources/application.yml`
- Create: `back/src/main/java/com/baniterio/api/push/ServicioPush.java`
- Create: `back/src/main/java/com/baniterio/api/push/PushEnLog.java`
- Test: `back/src/test/java/com/baniterio/api/push/PushEnLogTest.java`

**Interfaces:**
- Produces:
  - `interface ServicioPush { List<String> enviar(List<String> tokens, String titulo, String cuerpo); }`
    — devuelve los tokens muertos a podar; NUNCA lanza.
  - `AppProperties.Push(String modo, String credencialesJson)` accesible como
    `props.push()`.

- [ ] **Step 1: Dependencia en `pom.xml`**

Añadir dentro de `<dependencies>` (antes de `</dependencies>`, línea ~135):

```xml
		<dependency>
			<groupId>com.google.firebase</groupId>
			<artifactId>firebase-admin</artifactId>
			<version>9.4.3</version>
		</dependency>
```

- [ ] **Step 2: Verificar que el contexto sigue arrancando**

Run: `cd back && ./mvnw -q dependency:tree | grep -i "conflict\|netty\|guava" || true`
Run: `cd back && ./mvnw -q test -Dtest=BaniterioApiApplicationTests` (o el test de
contexto que exista; si no, `./mvnw -q compile`).
Expected: compila y el contexto carga. Si hay choque de versiones (Netty, Guava,
`google-http-client`), acotarlo con `<exclusions>` en la dependencia y volver a
probar. Anotar en el informe qué se excluyó.

- [ ] **Step 3: `AppProperties.Push`**

En `AppProperties.java`, añadir `Push push` al record y el sub-record:

```java
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Identidad identidad, Cors cors, Email email, Push push) {
    // ... records existentes ...

    /**
     * Config del envío de notificaciones push. {@code modo}: {@code "log"}
     * (por defecto, solo traza en el log — no requiere credenciales) o
     * {@code "fcm"} (envía de verdad vía Firebase). {@code credencialesJson} es
     * la ruta al fichero de cuenta de servicio de Firebase (solo modo fcm).
     */
    public record Push(String modo, String credencialesJson) {
    }
}
```

- [ ] **Step 4: `application.yml`**

Bajo `app:` (tras el bloque `email:`), añadir:

```yaml
  push:
    modo: ${PUSH_MODO:log}                    # log = solo traza · fcm = envía por Firebase
    credenciales-json: ${PUSH_CREDENCIALES:}  # ruta al service-account.json (solo modo fcm)
```

- [ ] **Step 5: Interfaz `ServicioPush`**

```java
package com.baniterio.api.push;

import java.util.List;

/**
 * Envía una notificación push a una lista de tokens de dispositivo.
 *
 * <p>Implementación según {@code app.push.modo}: {@link PushEnLog} (por defecto)
 * o {@link PushFirebase} ({@code fcm}).
 *
 * <p>Contrato: NUNCA lanza — un fallo de envío se registra, no revienta el flujo
 * que lo disparó. Devuelve los tokens que el proveedor ha rechazado por estar
 * muertos (desinstalados / caducados), para que quien llama los borre.
 */
public interface ServicioPush {

    List<String> enviar(List<String> tokens, String titulo, String cuerpo);
}
```

- [ ] **Step 6: Escribir `PushEnLogTest` (falla primero)**

```java
package com.baniterio.api.push;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PushEnLogTest {

    private final PushEnLog push = new PushEnLog();

    @Test
    void no_lanza_y_no_devuelve_tokens_muertos() {
        assertThatCode(() -> push.enviar(List.of("a", "b"), "T", "C")).doesNotThrowAnyException();
        assertThat(push.enviar(List.of("a", "b"), "T", "C")).isEmpty();
    }

    @Test
    void tolera_lista_vacia() {
        assertThat(push.enviar(List.of(), "T", "C")).isEmpty();
    }
}
```

- [ ] **Step 7: `PushEnLog`**

```java
package com.baniterio.api.push;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación por defecto de {@link ServicioPush}: no envía nada, solo deja
 * traza. Activa salvo que {@code app.push.modo=fcm}. Permite desarrollar y
 * testear sin credenciales de Firebase.
 */
@Component
@ConditionalOnProperty(name = "app.push.modo", havingValue = "log", matchIfMissing = true)
public class PushEnLog implements ServicioPush {

    private static final Logger log = LoggerFactory.getLogger(PushEnLog.class);

    @Override
    public List<String> enviar(List<String> tokens, String titulo, String cuerpo) {
        log.info("[push:log] {} dispositivo(s) · \"{}\" — \"{}\"", tokens.size(), titulo, cuerpo);
        return List.of();
    }
}
```

- [ ] **Step 8: Verificar**

Run: `cd back && ./mvnw -q test -Dtest=PushEnLogTest` → PASS.
Run: `cd back && ./mvnw -q verify` → toda la suite sigue verde (el contexto de
los `*IT` carga con `modo=log` y un único `ServicioPush`).

- [ ] **Step 9: Commit**

```bash
git add back/pom.xml back/src/main/java/com/baniterio/api/config/AppProperties.java \
        back/src/main/resources/application.yml back/src/main/java/com/baniterio/api/push/ \
        back/src/test/java/com/baniterio/api/push/PushEnLogTest.java
git commit -m "feat(push): ServicioPush + impl de log + config app.push + dep firebase-admin"
```

---

## Task 4: `PushFirebase` (envío real vía FCM)

**Files:**
- Create: `back/src/main/java/com/baniterio/api/push/EnvioMulticast.java`
- Create: `back/src/main/java/com/baniterio/api/push/ConfiguracionFirebase.java`
- Create: `back/src/main/java/com/baniterio/api/push/PushFirebase.java`
- Test: `back/src/test/java/com/baniterio/api/push/PushFirebaseTest.java`

**Interfaces:**
- Consumes: `ServicioPush` (Task 3), `AppProperties.Push`.
- Produces: `PushFirebase implements ServicioPush` (activa con `app.push.modo=fcm`).
  Costura `EnvioMulticast` para poder testear sin Firebase real.

- [ ] **Step 1: Costura `EnvioMulticast`**

```java
package com.baniterio.api.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;

/**
 * Lo único que {@link PushFirebase} necesita de Firebase: enviar un mensaje a
 * varios tokens. Aislarlo en una interfaz permite testear {@code PushFirebase}
 * con un doble, sin inicializar un {@code FirebaseApp} real.
 */
@FunctionalInterface
public interface EnvioMulticast {

    BatchResponse enviar(MulticastMessage mensaje) throws FirebaseMessagingException;
}
```

- [ ] **Step 2: `ConfiguracionFirebase` (bean real, solo modo fcm)**

```java
package com.baniterio.api.push;

import java.io.FileInputStream;
import java.io.IOException;

import com.baniterio.api.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializa el {@link FirebaseApp} a partir del fichero de cuenta de servicio
 * ({@code app.push.credenciales-json}) y expone la costura {@link EnvioMulticast}.
 * Solo se activa con {@code app.push.modo=fcm}; en {@code log} nada de esto se
 * carga y no hacen falta credenciales.
 */
@Configuration
@ConditionalOnProperty(name = "app.push.modo", havingValue = "fcm")
public class ConfiguracionFirebase {

    @Bean
    FirebaseApp firebaseApp(AppProperties props) throws IOException {
        String ruta = props.push().credencialesJson();
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalStateException(
                    "app.push.modo=fcm requiere app.push.credenciales-json (ruta al service-account.json)");
        }
        try (FileInputStream credenciales = new FileInputStream(ruta)) {
            FirebaseOptions opciones = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credenciales))
                    .build();
            return FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(opciones)
                    : FirebaseApp.getInstance();
        }
    }

    @Bean
    EnvioMulticast envioMulticast(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app)::sendEachForMulticast;
    }
}
```

- [ ] **Step 3: Escribir `PushFirebaseTest` (falla primero)**

Testea SOLO la lógica pura (troceo en lotes de 500 y selección de tokens
muertos). El `BatchResponse` se hace con Mockito (`mock(BatchResponse.class)`,
`mock(SendResponse.class)`).

```java
package com.baniterio.api.push;

import java.util.List;
import java.util.stream.IntStream;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushFirebaseTest {

    private SendResponse ok() {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(true);
        return r;
    }

    private SendResponse fallo(MessagingErrorCode codigo) {
        SendResponse r = mock(SendResponse.class);
        when(r.isSuccessful()).thenReturn(false);
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(codigo);
        when(r.getException()).thenReturn(ex);
        return r;
    }

    @Test
    void devuelve_los_tokens_con_error_unregistered_o_invalid_argument() throws Exception {
        BatchResponse resp = mock(BatchResponse.class);
        when(resp.getResponses()).thenReturn(List.of(
                ok(), fallo(MessagingErrorCode.UNREGISTERED),
                fallo(MessagingErrorCode.INVALID_ARGUMENT), fallo(MessagingErrorCode.INTERNAL)));
        EnvioMulticast envio = m -> resp;
        PushFirebase push = new PushFirebase(envio);

        List<String> muertos = push.enviar(List.of("a", "b", "c", "d"), "T", "C");

        assertThat(muertos).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void trocea_en_lotes_de_500() throws Exception {
        int[] llamadas = {0};
        EnvioMulticast envio = m -> {
            llamadas[0]++;
            BatchResponse r = mock(BatchResponse.class);
            when(r.getResponses()).thenReturn(List.of());
            return r;
        };
        PushFirebase push = new PushFirebase(envio);
        List<String> tokens = IntStream.range(0, 1200).mapToObj(i -> "t" + i).toList();

        push.enviar(tokens, "T", "C");

        assertThat(llamadas[0]).isEqualTo(3); // 500 + 500 + 200
    }

    @Test
    void un_fallo_de_firebase_no_propaga_excepcion() {
        EnvioMulticast envio = m -> { throw mock(FirebaseMessagingException.class); };
        PushFirebase push = new PushFirebase(envio);

        assertThat(push.enviar(List.of("a"), "T", "C")).isEmpty();
    }
}
```

- [ ] **Step 4: `PushFirebase`**

```java
package com.baniterio.api.push;

import java.util.ArrayList;
import java.util.List;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envío real de push vía Firebase Cloud Messaging. Activa con
 * {@code app.push.modo=fcm}. Trocea en lotes de {@value #LOTE} (límite de FCM
 * para multicast) y devuelve los tokens que FCM marca como muertos
 * ({@code UNREGISTERED} / {@code INVALID_ARGUMENT}) para podarlos.
 */
@Component
@ConditionalOnProperty(name = "app.push.modo", havingValue = "fcm")
public class PushFirebase implements ServicioPush {

    private static final Logger log = LoggerFactory.getLogger(PushFirebase.class);
    private static final int LOTE = 500;

    private final EnvioMulticast envio;

    public PushFirebase(EnvioMulticast envio) {
        this.envio = envio;
    }

    @Override
    public List<String> enviar(List<String> tokens, String titulo, String cuerpo) {
        List<String> muertos = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += LOTE) {
            List<String> lote = tokens.subList(i, Math.min(i + LOTE, tokens.size()));
            try {
                MulticastMessage mensaje = MulticastMessage.builder()
                        .setNotification(Notification.builder().setTitle(titulo).setBody(cuerpo).build())
                        .addAllTokens(lote)
                        .build();
                BatchResponse resp = envio.enviar(mensaje);
                List<SendResponse> respuestas = resp.getResponses();
                for (int j = 0; j < respuestas.size(); j++) {
                    if (esTokenMuerto(respuestas.get(j))) {
                        muertos.add(lote.get(j));
                    }
                }
            } catch (Exception e) {
                log.error("Fallo enviando lote de push ({} tokens): {}", lote.size(), e.toString());
            }
        }
        return muertos;
    }

    private boolean esTokenMuerto(SendResponse r) {
        if (r.isSuccessful() || r.getException() == null) {
            return false;
        }
        MessagingErrorCode codigo = r.getException().getMessagingErrorCode();
        return codigo == MessagingErrorCode.UNREGISTERED || codigo == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
```

- [ ] **Step 5: Verificar**

Run: `cd back && ./mvnw -q test -Dtest=PushFirebaseTest` → PASS.
Run: `cd back && ./mvnw -q verify` → toda la suite verde (el `modo` sigue en
`log`, `PushFirebase` y `ConfiguracionFirebase` no se instancian en los tests).

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/push/EnvioMulticast.java \
        back/src/main/java/com/baniterio/api/push/ConfiguracionFirebase.java \
        back/src/main/java/com/baniterio/api/push/PushFirebase.java \
        back/src/test/java/com/baniterio/api/push/PushFirebaseTest.java
git commit -m "feat(push): envío real vía Firebase Cloud Messaging (modo fcm)"
```

---

## Task 5: Audiencia y su resolutor

**Files:**
- Create: `back/src/main/java/com/baniterio/api/push/Audiencia.java`
- Create: `back/src/main/java/com/baniterio/api/push/ResolutorAudiencia.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/MembresiaRepository.java`
- Test: `back/src/test/java/com/baniterio/api/push/ResolutorAudienciaTest.java`

**Interfaces:**
- Consumes: `MembresiaRepository`, `UsuarioRepository`, `PenaRepository`,
  `RolMembresia`.
- Produces:
  - `sealed interface Audiencia permits Audiencia.UsuarioUnico, Audiencia.TodaLaPena, Audiencia.Administradores`
    con los tres `record`.
  - `ResolutorAudiencia` (`@Service`): `List<Long> resolver(Audiencia a)` —
    lista de `usuario.id` destinatarios, sin duplicados.
  - `MembresiaRepository.findByPenaIdAndActivaTrue(Long penaId)`.

- [ ] **Step 1: `Audiencia`**

```java
package com.baniterio.api.push;

/**
 * A quién va dirigido un {@link AvisoPushEvent}. Sellada: hoy solo se usa
 * {@link Administradores} (la solicitud de acceso), pero las otras dos quedan
 * listas para los avisos que vengan (pagos, mensajes a toda la peña, etc.).
 */
public sealed interface Audiencia {

    /** Una sola persona. */
    record UsuarioUnico(Long usuarioId) implements Audiencia {}

    /** Todos los miembros activos de la peña. */
    record TodaLaPena() implements Audiencia {}

    /** Administradores y superadministradores activos de la peña. */
    record Administradores() implements Audiencia {}
}
```

- [ ] **Step 2: Método nuevo en `MembresiaRepository`**

```java
    List<Membresia> findByPenaIdAndActivaTrue(Long penaId);
```

- [ ] **Step 3: Escribir `ResolutorAudienciaTest` (falla primero)**

```java
package com.baniterio.api.push;

import java.util.List;
import java.util.Optional;

import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolutorAudienciaTest {

    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PenaRepository penas = mock(PenaRepository.class);
    private final ResolutorAudiencia resolutor = new ResolutorAudiencia(membresias, usuarios, penas);

    {
        when(penas.findBySlug("baniterio")).thenReturn(Optional.of(Pena.builder().id(1L).build()));
    }

    private Membresia membresia(long usuarioId, RolMembresia rol, boolean superadmin) {
        return Membresia.builder()
                .usuario(Usuario.builder().id(usuarioId).esSuperadmin(superadmin).build())
                .rol(rol).activa(true).build();
    }

    @Test
    void usuario_unico_devuelve_ese_id() {
        assertThat(resolutor.resolver(new Audiencia.UsuarioUnico(42L))).containsExactly(42L);
    }

    @Test
    void toda_la_pena_devuelve_los_miembros_activos() {
        when(membresias.findByPenaIdAndActivaTrue(1L)).thenReturn(List.of(
                membresia(1L, RolMembresia.MIEMBRO, false),
                membresia(2L, RolMembresia.ADMIN, false)));

        assertThat(resolutor.resolver(new Audiencia.TodaLaPena())).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void administradores_une_admins_y_superadmins_sin_duplicar() {
        when(membresias.findByPenaIdAndActivaTrue(1L)).thenReturn(List.of(
                membresia(1L, RolMembresia.MIEMBRO, false),   // fuera
                membresia(2L, RolMembresia.ADMIN, false),      // dentro (rol)
                membresia(3L, RolMembresia.MIEMBRO, true),     // dentro (superadmin)
                membresia(4L, RolMembresia.ADMIN, true)));     // dentro, sin duplicar

        assertThat(resolutor.resolver(new Audiencia.Administradores()))
                .containsExactlyInAnyOrder(2L, 3L, 4L);
    }
}
```

- [ ] **Step 4: `ResolutorAudiencia`**

```java
package com.baniterio.api.push;

import java.util.List;
import java.util.stream.Stream;

import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduce una {@link Audiencia} a la lista de {@code usuario.id} que deben
 * recibir el aviso. Peña piloto: se resuelve por slug, igual que
 * {@code ServicioPermisos}.
 */
@Service
public class ResolutorAudiencia {

    private static final String SLUG_PENA = "baniterio";

    private final MembresiaRepository membresias;
    private final UsuarioRepository usuarios;
    private final PenaRepository penas;

    public ResolutorAudiencia(MembresiaRepository membresias, UsuarioRepository usuarios,
                              PenaRepository penas) {
        this.membresias = membresias;
        this.usuarios = usuarios;
        this.penas = penas;
    }

    @Transactional(readOnly = true)
    public List<Long> resolver(Audiencia audiencia) {
        return switch (audiencia) {
            case Audiencia.UsuarioUnico u -> List.of(u.usuarioId());
            case Audiencia.TodaLaPena ignored -> activas().map(m -> m.getUsuario().getId()).distinct().toList();
            case Audiencia.Administradores ignored -> activas()
                    .filter(m -> m.getRol() == RolMembresia.ADMIN || m.getUsuario().isEsSuperadmin())
                    .map(m -> m.getUsuario().getId())
                    .distinct()
                    .toList();
        };
    }

    private Stream<Membresia> activas() {
        Long penaId = penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
        return membresias.findByPenaIdAndActivaTrue(penaId).stream();
    }
}
```

Nota: `UsuarioRepository` se deja inyectado aunque `Administradores` mire
`m.getUsuario().isEsSuperadmin()` desde la membresía; si el análisis del
reviewer indica que un superadmin puede no tener membresía activa (red de
seguridad, como en `ServicioPermisos.esAdministrador`), añadir un
`usuarios.findAll().stream().filter(Usuario::isEsSuperadmin)...` a la unión y
cubrirlo con un test. Decisión del implementador con lo que vea en el modelo.

- [ ] **Step 5: Verificar**

Run: `cd back && ./mvnw -q test -Dtest=ResolutorAudienciaTest` → PASS.

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/push/Audiencia.java \
        back/src/main/java/com/baniterio/api/push/ResolutorAudiencia.java \
        back/src/main/java/com/baniterio/api/identidad/MembresiaRepository.java \
        back/src/test/java/com/baniterio/api/push/ResolutorAudienciaTest.java
git commit -m "feat(push): audiencia (usuario/peña/admins) y su resolutor"
```

---

## Task 6: Evento de dominio + listener de envío

**Files:**
- Create: `back/src/main/java/com/baniterio/api/push/AvisoPushEvent.java`
- Create: `back/src/main/java/com/baniterio/api/push/ManejadorAvisoPush.java`
- Test: `back/src/test/java/com/baniterio/api/push/ManejadorAvisoPushTest.java`

**Interfaces:**
- Consumes: `Audiencia`, `ResolutorAudiencia`, `ServicioPush` (Tasks 3-5);
  `DispositivoRepository`, `DispositivoService.podar` (Tasks 1-2).
- Produces:
  - `record AvisoPushEvent(Audiencia audiencia, String titulo, String cuerpo)`.
  - `ManejadorAvisoPush` — `@Component`, `@TransactionalEventListener(AFTER_COMMIT)`.

- [ ] **Step 1: Evento**

```java
package com.baniterio.api.push;

/**
 * Evento de dominio: "ha pasado algo que hay que avisar por push". Lo publican
 * los servicios (hoy {@code AuthService} al crear una solicitud de acceso) y lo
 * consume {@link ManejadorAvisoPush} tras confirmar la transacción, de modo que
 * un fallo de envío no revierta la operación que lo disparó.
 */
public record AvisoPushEvent(Audiencia audiencia, String titulo, String cuerpo) {
}
```

- [ ] **Step 2: Escribir `ManejadorAvisoPushTest` (falla primero)**

```java
package com.baniterio.api.push;

import java.util.List;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManejadorAvisoPushTest {

    private final ResolutorAudiencia resolutor = mock(ResolutorAudiencia.class);
    private final DispositivoRepository dispositivos = mock(DispositivoRepository.class);
    private final ServicioPush servicioPush = mock(ServicioPush.class);
    private final DispositivoService dispositivoService = mock(DispositivoService.class);
    private final ManejadorAvisoPush manejador =
            new ManejadorAvisoPush(resolutor, dispositivos, servicioPush, dispositivoService);

    private Dispositivo disp(String token) {
        return Dispositivo.builder().usuario(Usuario.builder().id(1L).build())
                .token(token).plataforma(PlataformaDispositivo.ANDROID).build();
    }

    private AvisoPushEvent evento() {
        return new AvisoPushEvent(new Audiencia.Administradores(), "T", "C");
    }

    @Test
    void envia_a_los_tokens_de_la_audiencia() {
        when(resolutor.resolver(any())).thenReturn(List.of(1L, 2L));
        when(dispositivos.findByUsuarioIdIn(List.of(1L, 2L))).thenReturn(List.of(disp("a"), disp("b")));
        when(servicioPush.enviar(any(), eq("T"), eq("C"))).thenReturn(List.of());

        manejador.alAviso(evento());

        verify(servicioPush).enviar(List.of("a", "b"), "T", "C");
        verify(dispositivoService, never()).podar(any());
    }

    @Test
    void poda_los_tokens_que_el_envio_marca_muertos() {
        when(resolutor.resolver(any())).thenReturn(List.of(1L));
        when(dispositivos.findByUsuarioIdIn(any())).thenReturn(List.of(disp("a"), disp("b")));
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of("b"));

        manejador.alAviso(evento());

        verify(dispositivoService).podar(List.of("b"));
    }

    @Test
    void sin_dispositivos_no_llama_al_envio() {
        when(resolutor.resolver(any())).thenReturn(List.of(1L));
        when(dispositivos.findByUsuarioIdIn(any())).thenReturn(List.of());

        manejador.alAviso(evento());

        verify(servicioPush, never()).enviar(any(), any(), any());
    }

    @Test
    void un_fallo_resolviendo_no_propaga() {
        when(resolutor.resolver(any())).thenThrow(new RuntimeException("boom"));
        // no debe lanzar
        manejador.alAviso(evento());
    }
}
```

- [ ] **Step 3: `ManejadorAvisoPush`**

```java
package com.baniterio.api.push;

import java.util.List;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce un {@link AvisoPushEvent} en envíos de push, DESPUÉS de que la
 * transacción que lo publicó confirme (igual patrón que
 * {@code ManejadorCorreoSolicitud}): así un fallo de push no revierte la
 * operación de dominio. Nunca propaga excepción.
 */
@Component
public class ManejadorAvisoPush {

    private static final Logger log = LoggerFactory.getLogger(ManejadorAvisoPush.class);

    private final ResolutorAudiencia resolutor;
    private final DispositivoRepository dispositivos;
    private final ServicioPush servicioPush;
    private final DispositivoService dispositivoService;

    public ManejadorAvisoPush(ResolutorAudiencia resolutor, DispositivoRepository dispositivos,
                              ServicioPush servicioPush, DispositivoService dispositivoService) {
        this.resolutor = resolutor;
        this.dispositivos = dispositivos;
        this.servicioPush = servicioPush;
        this.dispositivoService = dispositivoService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alAviso(AvisoPushEvent e) {
        try {
            List<Long> usuarios = resolutor.resolver(e.audiencia());
            List<Dispositivo> disp = dispositivos.findByUsuarioIdIn(usuarios);
            if (disp.isEmpty()) {
                return;
            }
            List<String> tokens = disp.stream().map(Dispositivo::getToken).toList();
            List<String> muertos = servicioPush.enviar(tokens, e.titulo(), e.cuerpo());
            if (!muertos.isEmpty()) {
                dispositivoService.podar(muertos);
            }
        } catch (Exception ex) {
            log.error("No se pudo enviar el aviso push \"{}\": {}", e.titulo(), ex.toString());
        }
    }
}
```

- [ ] **Step 4: Verificar**

Run: `cd back && ./mvnw -q test -Dtest=ManejadorAvisoPushTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/push/AvisoPushEvent.java \
        back/src/main/java/com/baniterio/api/push/ManejadorAvisoPush.java \
        back/src/test/java/com/baniterio/api/push/ManejadorAvisoPushTest.java
git commit -m "feat(push): evento AvisoPushEvent + listener que envía tras el commit"
```

---

## Task 7: Disparar el aviso al crear una solicitud de acceso

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/auth/AuthService.java`
- Test: `back/src/test/java/com/baniterio/api/auth/SolicitudDisparaAvisoPushIT.java`

**Interfaces:**
- Consumes: `AvisoPushEvent`, `Audiencia.Administradores` (Task 6);
  `org.springframework.context.ApplicationEventPublisher`.
- Produces: al llamar a `POST /api/v1/auth/solicitudes`, tras confirmar, se
  publica `AvisoPushEvent(Administradores, "Nueva solicitud de acceso",
  "<nombre> <apellidos> quiere entrar en la peña")`.

- [ ] **Step 1: Escribir `SolicitudDisparaAvisoPushIT` (falla primero)**

`@MockitoBean ServicioPush` para espiar el envío sin Firebase. Un admin con
dispositivo registrado; se crea una solicitud; se verifica el envío.

```java
package com.baniterio.api.auth;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.Dispositivo;
import com.baniterio.api.identidad.DispositivoRepository;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PlataformaDispositivo;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;
import com.baniterio.api.identidad.UsuarioRepository;
import com.baniterio.api.push.ServicioPush;
import com.baniterio.api.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static java.time.Duration.ofSeconds;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SolicitudDisparaAvisoPushIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired DispositivoRepository dispositivos;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean ServicioPush servicioPush;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        when(servicioPush.enviar(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void crear_solicitud_envia_push_a_los_admins_con_dispositivo() {
        // admin con dispositivo
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario admin = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@a.test").passwordHash("x")
                .nombre("A").apellidos("D").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(admin)
                .pena(penas.findBySlug("baniterio").orElseThrow())
                .rol(RolMembresia.ADMIN).activa(true).build());
        dispositivos.save(Dispositivo.builder().usuario(admin)
                .token("tok-admin-" + admin.getId()).plataforma(PlataformaDispositivo.ANDROID).build());

        String solicitante = "7" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        http.post().uri("/api/v1/auth/solicitudes").body(Map.of(
                "telefono", solicitante, "email", solicitante + "@x.com",
                "nombre", "Marta", "apellidos", "García",
                "motivo", "Soy de la peña de siempre y quiero volver.",
                "relacion", "Tres generaciones de mi familia en la peña.",
                "conocidos", "Conozco a medio mundo de la peña."))
                .exchange().expectStatus().isCreated();

        Awaitility.await().atMost(ofSeconds(5)).untilAsserted(() ->
                verify(servicioPush).enviar(
                        contains("tok-admin-" + admin.getId()) == null ? any() : any(),
                        eq("Nueva solicitud de acceso"),
                        contains("Marta García")));
    }
}
```

Nota para el implementador: el `contains(...)` de arriba está mal usado sobre un
`List<String>`; sustituir por un `ArgumentCaptor<List<String>>` y comprobar que
la lista contiene `"tok-admin-..."`, o por
`verify(servicioPush).enviar(eq(List.of("tok-admin-" + admin.getId())), eq("Nueva solicitud de acceso"), eq("Marta García quiere entrar en la peña"))`.
Elegir la forma exacta al implementar; el objetivo del test es: **crear la
solicitud → se llama a `ServicioPush.enviar` con el token del admin y el texto
correcto**. Añadir `org.awaitility:awaitility` como dependencia `test` en
`pom.xml` si no está (el listener es AFTER_COMMIT, mismo hilo, pero el `verify`
inmediato suele bastar; usar Awaitility solo si sale flaky).

- [ ] **Step 2: Verificar RED**

Run: `cd back && ./mvnw -q verify -Dit.test=SolicitudDisparaAvisoPushIT`
Expected: falla (no se publica el evento todavía).

- [ ] **Step 3: Modificar `AuthService`**

Añadir el campo y parámetro de constructor:

```java
import org.springframework.context.ApplicationEventPublisher;
import com.baniterio.api.push.AvisoPushEvent;
import com.baniterio.api.push.Audiencia;
// ...
    private final ApplicationEventPublisher eventos;

    public AuthService(TelefonoAutorizadoRepository telefonosAutorizados, UsuarioRepository usuarios,
                       MembresiaRepository membresias, PasswordEncoder passwordEncoder, AppProperties props,
                       JwtService jwtService, SolicitudIngresoRepository solicitudes, PenaRepository penas,
                       ServicioPermisos servicioPermisos, ApplicationEventPublisher eventos) {
        // ... asignaciones existentes ...
        this.eventos = eventos;
    }
```

Al final de `solicitarIngreso`, tras `SolicitudIngreso guardada = solicitudes.save(solicitud);`
(renombrar el `return` para tener la referencia):

```java
        SolicitudIngreso guardada = solicitudes.save(solicitud);
        eventos.publishEvent(new AvisoPushEvent(
                new Audiencia.Administradores(),
                "Nueva solicitud de acceso",
                req.nombre() + " " + req.apellidos() + " quiere entrar en la peña"));
        return guardada;
```

- [ ] **Step 4: Verificar GREEN + suite completa**

Run: `cd back && ./mvnw -q verify -Dit.test=SolicitudDisparaAvisoPushIT` → PASS.
Run: `cd back && ./mvnw -q verify` → toda la suite verde (ojo a `AuthControllerIT`
y cualquier test que construya `AuthService` a mano — no debería haber ninguno).

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/auth/AuthService.java \
        back/src/test/java/com/baniterio/api/auth/SolicitudDisparaAvisoPushIT.java back/pom.xml
git commit -m "feat(push): la solicitud de acceso dispara un aviso push a los admins"
```

---

## Task 8: Móvil — `DispositivoRepository` (shared)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/DispositivoRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/DispositivoRepositoryImpl.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/DispositivoDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/Dependencias.kt`
- Modify: `mobile/shared/build.gradle.kts` (ktor-client-mock en commonTest)
- Modify: `mobile/gradle/libs.versions.toml` (ktor-client-mock)
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/DispositivoRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `HttpClient`, `SesionHolder`, `API_BASE_URL` (existentes).
- Produces:
  - `interface DispositivoRepository { suspend fun registrar(token: String, plataforma: String): Boolean; suspend fun eliminar(token: String): Boolean }`
  - `Dependencias.dispositivoRepo: DispositivoRepository`
  - `@Serializable data class RegistrarDispositivoRequest(val token: String, val plataforma: String)`

- [ ] **Step 1: DTO**

`data/dto/DispositivoDtos.kt`:

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

/** Cuerpo de `POST /api/v1/dispositivos`. `plataforma` = "ANDROID" | "IOS". */
@Serializable
data class RegistrarDispositivoRequest(val token: String, val plataforma: String)
```

- [ ] **Step 2: Interfaz**

`data/DispositivoRepository.kt`:

```kotlin
package com.baniterio.app.data

/**
 * Registra o da de baja el token de push de este dispositivo en el backend.
 * Devuelve `true` si la llamada fue bien; `false` si falló (se ignora — el
 * backend poda los tokens muertos por su cuenta al enviar).
 */
interface DispositivoRepository {
    suspend fun registrar(token: String, plataforma: String): Boolean
    suspend fun eliminar(token: String): Boolean
}
```

- [ ] **Step 3: Añadir `ktor-client-mock` al catálogo y a `commonTest`**

`libs.versions.toml`, sección `[libraries]`:

```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

`shared/build.gradle.kts`, `commonTest.dependencies`:

```kotlin
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
        }
```

- [ ] **Step 4: Escribir `DispositivoRepositoryImplTest` (falla primero)**

```kotlin
package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DispositivoRepositoryImplTest {

    private fun repo(handler: MockEngine.() -> Unit): Pair<DispositivoRepositoryImpl, MutableList<String>> {
        val vistas = mutableListOf<String>()
        val engine = MockEngine { req ->
            vistas += "${req.method.value} ${req.url.encodedPath}"
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return DispositivoRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun registrar_hace_post_y_devuelve_true_en_2xx() = runTest {
        val (r, vistas) = repo { }
        assertTrue(r.registrar("tok", "ANDROID"))
        assertEquals(listOf("POST /api/v1/dispositivos"), vistas.map { it.substringBefore('?') })
    }

    @Test
    fun eliminar_hace_delete_del_token() = runTest {
        val (r, vistas) = repo { }
        assertTrue(r.eliminar("tok-123"))
        assertTrue(vistas.any { it.startsWith("DELETE ") && it.endsWith("/dispositivos/tok-123") })
    }

    @Test
    fun registrar_devuelve_false_si_el_backend_responde_500() = runTest {
        val engine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.InternalServerError) }
        val http = HttpClient(engine) { configComun() }
        val r = DispositivoRepositoryImpl(http, SesionHolder().apply { token = "x" })
        assertEquals(false, r.registrar("tok", "ANDROID"))
    }
}
```

(Ajustar imports/`API_BASE_URL` — `encodedPath` incluye el `/api/v1` del host
según cómo esté formado `API_BASE_URL`. Si `API_BASE_URL` es
`http://.../api/v1`, el `encodedPath` de la petición será `/api/v1/dispositivos`.)

- [ ] **Step 5: Implementación**

`data/DispositivoRepositoryImpl.kt`:

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.RegistrarDispositivoRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class DispositivoRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : DispositivoRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun registrar(token: String, plataforma: String): Boolean = intentar {
        http.post("$API_BASE_URL/dispositivos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(RegistrarDispositivoRequest(token, plataforma))
        }
    }

    override suspend fun eliminar(token: String): Boolean = intentar {
        http.delete("$API_BASE_URL/dispositivos/$token") { auth() }
    }

    /** `true` si el bloque no lanza; `false` ante cualquier error. Relanza la
     *  cancelación para no romper la concurrencia estructurada. */
    private suspend inline fun intentar(bloque: () -> Unit): Boolean =
        try {
            bloque()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            false
        } catch (e: Exception) {
            false
        }
}
```

Nota: `bloque` es `suspend` de hecho (llama a `http.post`), marcar la lambda y
la `inline fun` como `suspend` correctamente (`private suspend fun intentar(bloque: suspend () -> Unit)`),
sin `inline` si da problemas con `suspend`.

- [ ] **Step 6: Cablear en `Dependencias`**

```kotlin
class Dependencias(
    val repo: AuthRepository,
    val almacen: AlmacenCredenciales,
    val adminRepo: AdminRepository,
    val dispositivoRepo: DispositivoRepository,
)

fun crearDependencias(almacen: AlmacenCredenciales): Dependencias {
    val http = crearHttpClient()
    val sesion = SesionHolder()
    return Dependencias(
        repo = AuthRepositoryImpl(http, sesion),
        almacen = almacen,
        adminRepo = AdminRepositoryImpl(http, sesion),
        dispositivoRepo = DispositivoRepositoryImpl(http, sesion),
    )
}
```

- [ ] **Step 7: Verificar**

Run: `cd mobile && ./gradlew :shared:testDebugUnitTest --tests "*DispositivoRepositoryImplTest*"`
(o `:shared:allTests` / `:shared:iosSimulatorArm64Test` está deshabilitado en
Windows — basta el de Android host).
Run: `cd mobile && ./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/DispositivoRepository.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/DispositivoRepositoryImpl.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/DispositivoDtos.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/Dependencias.kt \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/DispositivoRepositoryImplTest.kt \
        mobile/shared/build.gradle.kts mobile/gradle/libs.versions.toml
git commit -m "feat(movil): DispositivoRepository (registrar/eliminar token push)"
```

---

## Task 9: Móvil — costura de callbacks en `App.kt`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`

**Interfaces:**
- Produces: `App(deps, alIniciarSesion: () -> Unit = {}, alCerrarSesion: () -> Unit = {})`.
  `alIniciarSesion()` se invoca en `onLoginSuccess` (LoginScreen) y
  `onDesbloqueado` (DesbloqueoScreen). `alCerrarSesion()` se invoca en
  `onCerrarSesion` (PanelScreen) ANTES de `deps.repo.logout()`.

- [ ] **Step 1: Firma y cableado**

En `App.kt`, cambiar la firma:

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    deps: Dependencias,
    alIniciarSesion: () -> Unit = {},
    alCerrarSesion: () -> Unit = {},
) {
```

En la rama `Screen.Desbloqueo`:

```kotlin
                is Screen.Desbloqueo -> DesbloqueoScreen(
                    repo = deps.repo,
                    almacen = deps.almacen,
                    onDesbloqueado = { alIniciarSesion(); ir(Screen.Panel) },
                    onUsarOtraCuenta = { ir(Screen.Login) },
                )
```

En la rama `Screen.Login`:

```kotlin
                is Screen.Login -> LoginScreen(
                    repo = deps.repo,
                    almacen = deps.almacen,
                    onLoginSuccess = { alIniciarSesion(); ir(Screen.Panel) },
                    onIrARegistro = { ir(Screen.Registro) },
                )
```

En la rama `Screen.Panel`, `onCerrarSesion`:

```kotlin
                    onCerrarSesion = {
                        alCerrarSesion()
                        deps.repo.logout()
                        deps.almacen.borrar()
                        ir(Screen.Login)
                    },
```

- [ ] **Step 2: Verificar que no rompe nada**

`MainActivity` y `MainViewController` llaman a `App(deps)` sin los nuevos
parámetros → compilan por el valor por defecto.

Run: `cd mobile && ./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt
git commit -m "feat(movil): callbacks alIniciarSesion/alCerrarSesion en App (no-op por defecto)"
```

---

## Task 10: Móvil — integración Firebase en Android

**Files:**
- Modify: `mobile/gradle/libs.versions.toml`
- Modify: `mobile/build.gradle.kts`
- Modify: `mobile/settings.gradle.kts` (repos ya incluyen `com.google`, verificar)
- Modify: `mobile/androidApp/build.gradle.kts`
- Modify: `mobile/androidApp/src/main/AndroidManifest.xml`
- Modify: `mobile/androidApp/src/main/kotlin/com/baniterio/app/BaniterioApp.kt`
- Modify: `mobile/androidApp/src/main/kotlin/com/baniterio/app/MainActivity.kt`
- Create: `mobile/androidApp/src/main/kotlin/com/baniterio/app/BaniterioMessagingService.kt`
- Modify: `mobile/.gitignore`
- Modify: `mobile/README.md`

**Interfaces:**
- Consumes: `Dependencias.dispositivoRepo` (Task 8), `App(..., alIniciarSesion,
  alCerrarSesion)` (Task 9).
- Produces: al iniciar sesión en Android se hace `POST /api/v1/dispositivos`
  con el token FCM; al cerrar sesión, `DELETE`. Push entrante → notificación
  del sistema.

**Paso manual (guiado por el asistente):** crear el proyecto Firebase, registrar
la app Android `com.baniterio.app`, descargar `google-services.json` a
`mobile/androidApp/`. Este paso lo hace el usuario; las tareas de código no
dependen de que el fichero esté presente para compilar SALVO el plugin
`google-services`, que aborta el build si falta. Por eso: **el implementador
crea un `google-services.json` de marcador** (estructura mínima válida) si el
real no está, y deja el `assembleDebug` verde; el usuario lo reemplaza luego.
(Alternativa: aplicar el plugin `google-services` de forma condicional a que el
fichero exista. Decidir al implementar; preferible el marcador + `.gitignore`.)

- [ ] **Step 1: Catálogo de versiones**

`libs.versions.toml`:

```toml
[versions]
# ...
googleServices = "4.4.2"
firebaseBom = "34.1.0"

[libraries]
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }

[plugins]
googleServices = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Plugin en el build raíz**

`mobile/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.googleServices) apply false
}
```

- [ ] **Step 3: `androidApp/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}
// ...
dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.core)   // si no estaba ya, para lanzar corrutinas
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
```

- [ ] **Step 4: `.gitignore` + marcador `google-services.json`**

`mobile/.gitignore`: añadir `androidApp/google-services.json`.

Crear `mobile/androidApp/google-services.json` de marcador (el usuario lo
reemplaza con el real de la consola):

```json
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "baniterio-marcador",
    "storage_bucket": "baniterio-marcador.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": { "package_name": "com.baniterio.app" }
      },
      "api_key": [ { "current_key": "MARCADOR-REEMPLAZAR-CON-EL-REAL" } ],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
```

- [ ] **Step 5: Manifest**

`mobile/androidApp/src/main/AndroidManifest.xml` — dentro de `<manifest>`:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Dentro de `<application>`:

```xml
        <service
            android:name=".BaniterioMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <meta-data
            android:name="com.google.firebase.messaging.default_notification_channel_id"
            android:value="avisos" />
```

- [ ] **Step 6: Canal de notificación en `BaniterioApp`**

```kotlin
class BaniterioApp : Application() {
    val deps: Dependencias by lazy { crearDependencias(AlmacenCredenciales(this)) }

    override fun onCreate() {
        super.onCreate()
        val canal = NotificationChannel(
            "avisos",
            "Avisos de la peña",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Solicitudes y cosas por atender" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
```

(`NotificationChannel` existe desde API 26; `minSdk` es 24 → envolver en
`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)`.)

- [ ] **Step 7: `BaniterioMessagingService`**

```kotlin
package com.baniterio.app

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe los eventos de Firebase Cloud Messaging. `onNewToken` mantiene el
 * backend al día si el token cambia estando con sesión. `onMessageReceived`
 * solo se invoca con la app en primer plano (en segundo plano el sistema pinta
 * la notificación solo, por el bloque `notification` del mensaje).
 */
class BaniterioMessagingService : FirebaseMessagingService() {

    private val deps get() = (application as BaniterioApp).deps
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (deps.repo.usuarioActual != null) {
            scope.launch { deps.dispositivoRepo.registrar(token, "ANDROID") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        val aviso = NotificationCompat.Builder(this, "avisos")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(message.messageId?.hashCode() ?: 0, aviso)
    }
}
```

(Icono: usar el del launcher / un drawable propio si existe; `ic_dialog_info` es
un marcador. Anotarlo como mejora menor.)

- [ ] **Step 8: `MainActivity` — permiso + callbacks**

```kotlin
class MainActivity : FragmentActivity() {

    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deps = (application as BaniterioApp).deps
        val scope = CoroutineScope(Dispatchers.Main)

        fun sincronizarToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { deps.dispositivoRepo.registrar(token, "ANDROID") }
            }
        }
        fun borrarToken() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { deps.dispositivoRepo.eliminar(token) }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            App(
                deps = deps,
                alIniciarSesion = { sincronizarToken() },
                alCerrarSesion = { borrarToken() },
            )
        }
    }
}
```

Notas: `borrarToken()` se llama ANTES de `deps.repo.logout()` porque el callback
`alCerrarSesion` en `App.kt` (Task 9) está puesto antes; el token JWT sigue en
memoria cuando la corrutina hace el `DELETE`. Si sale carrera (la corrutina
tarda y el token ya se limpió), capturar `deps.repo` no ayuda — alternativa:
hacer el `DELETE` de forma síncrona-bloqueante corta o mover el `logout()` a un
callback posterior. Decisión del implementador; lo aceptable es que en el caso
normal el `DELETE` salga con el Bearer válido.

- [ ] **Step 9: Verificar**

Run: `cd mobile && ./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL (con
el `google-services.json` marcador).
Run: `cd mobile && ./gradlew :androidApp:installDebug` si hay dispositivo.

- [ ] **Step 10: `mobile/README.md` — sección Firebase**

Añadir una sección "Notificaciones push (Android)" con los pasos:
1. Consola Firebase → crear proyecto (o usar el existente).
2. Añadir app Android, package `com.baniterio.app`.
3. Descargar `google-services.json` → `mobile/androidApp/` (reemplaza el marcador; está en `.gitignore`).
4. `./gradlew :androidApp:installDebug`.
5. Backend con `PUSH_MODO=fcm` y `PUSH_CREDENCIALES=/ruta/service-account.json`
   (Consola Firebase → Configuración del proyecto → Cuentas de servicio →
   Generar nueva clave privada).

- [ ] **Step 11: Commit**

```bash
git add mobile/gradle/libs.versions.toml mobile/build.gradle.kts \
        mobile/androidApp/ mobile/.gitignore mobile/README.md
git commit -m "feat(movil): integración Firebase Cloud Messaging en Android"
```

- [ ] **Step 12: Verificación manual de punta a punta (con el usuario)**

1. Usuario crea el proyecto Firebase y coloca el `google-services.json` real.
2. Reinstalar: `./gradlew :androidApp:installDebug`.
3. Backend local con `PUSH_MODO=fcm` + credenciales.
4. Iniciar sesión en el móvil como admin (superadmin 600000001).
5. Desde otro teléfono / navegador incógnito, enviar una solicitud de acceso.
6. Comprobar que llega la notificación "Nueva solicitud de acceso" al móvil,
   con la app cerrada.
7. Comprobar en BBDD que hay una fila en `dispositivo` para el admin.

---

## Task 11: iOS — DEFERIDA (requiere Mac, NO EJECUTAR)

> **NO EJECUTAR en este plan.** Requiere macOS + Xcode + iPhone físico. Ver
> memoria `baniterio_push_ios_pendiente`. Se deja escrita para una sesión corta
> en Mac. El implementador de este plan crea SOLO el archivo de notas de abajo y
> NO toca el proyecto Xcode.

**Files:**
- Create: `docs/superpowers/notas/push-ios-pasos.md` (guion para la sesión en Mac)

- [ ] **Step 1: Escribir el guion iOS** en `docs/superpowers/notas/push-ios-pasos.md`:

```markdown
# Push iOS — pasos para la sesión en Mac

Prerrequisitos: Mac con Xcode, cuenta Apple Developer, iPhone físico.

## Consola Apple / Firebase
1. Apple Developer → Certificates, IDs & Profiles → Keys → crear una APNs Auth
   Key (.p8). Apuntar Key ID y Team ID.
2. Firebase → añadir app iOS, bundle id `com.baniterio.app.Baniterio`.
   Descargar `GoogleService-Info.plist`.
3. Firebase → Configuración del proyecto → Cloud Messaging → subir la .p8 con
   su Key ID y Team ID.

## Xcode
4. Añadir `GoogleService-Info.plist` a `mobile/iosApp/iosApp/` (target iosApp).
5. Target iosApp → Signing & Capabilities → + Capability:
   - Push Notifications
   - Background Modes → Remote notifications
6. Añadir el paquete SPM `https://github.com/firebase/firebase-ios-sdk` →
   producto `FirebaseMessaging`.

## Código
7. `iOSApp.swift`:
   - `FirebaseApp.configure()` en `init()`.
   - `UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])`.
   - `UIApplication.shared.registerForRemoteNotifications()`.
   - Conformar un `AppDelegate` a `MessagingDelegate`; en
     `messaging(_:didReceiveRegistrationToken:)` llamar a un método del
     framework `Shared` que registre el token (crear
     `PushBridge.registrar(token:)` en `iosMain` que use
     `deps.dispositivoRepo.registrar(token, "IOS")`).
8. `iosMain`: crear `PushBridge.kt` con acceso al `dispositivoRepo` (mismo
   patrón de `deps` que `MainViewController.kt`).
9. Implementar `alIniciarSesion` / `alCerrarSesion` al construir `App(...)` en
   `MainViewController()` — obtener el token de `Messaging.messaging().token`.

## Verificar
10. Ejecutar en iPhone físico, iniciar sesión como admin, mandar una solicitud
    desde otro sitio, comprobar la notificación con la app cerrada.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/notas/push-ios-pasos.md
git commit -m "docs(push): guion de la parte iOS para retomar en Mac"
```

---

## Self-Review (hecho al escribir el plan)

- **Cobertura del spec:** tabla `dispositivo` (T1) · endpoints (T2) ·
  `ServicioPush`/log/config/dep (T3) · FCM real (T4) · audiencias (T5) · evento
  + listener + poda (T6) · disparo desde solicitud (T7) · repo móvil (T8) ·
  costura App.kt (T9) · Android Firebase + README + verificación manual (T10) ·
  iOS escrito y diferido (T11). Todo el spec tiene tarea.
- **Sin placeholders:** los tests llevan código real. Dos puntos marcados como
  "decisión del implementador" son elecciones de forma (ArgumentCaptor vs
  matcher; marcador de `google-services.json` vs plugin condicional; carrera del
  token en logout) con la opción recomendada indicada — no huecos.
- **Consistencia de tipos:** `ServicioPush.enviar(List<String>, String, String)
  : List<String>` igual en T3/T4/T6. `DispositivoService.registrar(Long, String,
  PlataformaDispositivo)` y `.podar(Collection<String>)` igual en T2/T6.
  `Audiencia.Administradores` (record) igual en T5/T6/T7.
  `DispositivoRepository` (Kotlin) `registrar/eliminar` igual en T8/T9/T10.
  `App(deps, alIniciarSesion, alCerrarSesion)` igual en T9/T10.
- **Riesgo vivo:** choque de dependencias `firebase-admin` ↔ Spring Boot 4
  (T3 step 2 lo verifica y acota). Carrera del token en logout (T10 step 8, con
  mitigación). Ninguno bloquea el diseño.
