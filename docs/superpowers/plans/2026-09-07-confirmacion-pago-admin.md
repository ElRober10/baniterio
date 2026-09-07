# Confirmación de pago por el administrador — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un administrador confirme el pago de la cuota de San Miguel de cada
asistente (con método: Bizum / Transferencia / Efectivo) desde el modal de
asistentes, y que el peñista vea su pago ya confirmado en el detalle del evento.

**Architecture:** El estado de pago vive en `ficha_bebida` (4 columnas nuevas, sin
tabla `pago`). Dos endpoints nuevos bajo `AsistenciaController`
(`PUT`/`DELETE .../asistencias/{asistenciaId}/pago`), protegidos por el mismo
`puedeGestionar` que "mandar notificación". El listado de asistentes y el bloque
`asistencia.ficha.miFicha` del detalle del evento ganan los campos de pago. Web
(Angular) y móvil (Compose Multiplatform) reflejan el nuevo contrato.

**Tech Stack:** Spring Boot 3 (Java 17), Flyway, JPA/Hibernate, Lombok; Angular 20
(signals, standalone components), Jasmine/Karma; Kotlin Multiplatform + Compose,
Ktor client, kotlin.test + MockEngine.

**Spec:** `docs/superpowers/specs/2026-09-07-confirmacion-pago-admin-design.md`

## Global Constraints

- Java se queda en 17 (no subir el `sourceCompatibility`).
- Rama de trabajo: `feature/cuentas` (ya activa). Commits pequeños y frecuentes.
- Español con tildes en todo el texto de usuario y en los comentarios.
- Códigos de error del backend: constantes estables en el cuerpo `{ "codigo": ... }`;
  el front y el móvil traducen por ese código, nunca por el texto.
- Método de pago: enum de 3 valores **exactos** `TRANSFERENCIA`, `BIZUM`, `EFECTIVO`
  (ya existen así en `eventos.types.ts` y en `HePagadoDialog.kt`).
- El modal del peñista `ModalHePagado` / `HePagadoDialog` (declaración) **no cambia
  su comportamiento**: sigue sin persistir. Esta pieza solo añade la ruta del
  administrador y la vista de "ya confirmado".
- Las 3 suites (`back`, `front`, `mobile`) deben quedar verdes antes del merge.

---

## File Structure

**Backend (`back/src/main/java/com/baniterio/api/`)**
- Crear `identidad/MetodoPago.java` — enum de método de pago.
- Modificar `identidad/FichaBebida.java` — 4 campos de pago.
- Crear `back/src/main/resources/db/migration/V26__ficha_bebida_pago.sql`.
- Crear `evento/FichaSinCuotaException.java`.
- Modificar `web/ApiExceptionHandler.java` — mapear la excepción nueva.
- Crear `evento/dto/ConfirmarPagoRequest.java`.
- Modificar `evento/dto/AsistenteFila.java` — campos de pago + `asistenciaId`.
- Modificar `evento/dto/ListadoAsistentesResponse.java` — `puedoConfirmarPagos`.
- Modificar `evento/dto/FichaBebidaDetalle.java` — campos de pago en `MiFicha`.
- Modificar `evento/AsistenciaService.java` — `confirmarPago`, `deshacerPago`,
  `aFila`, `listadoAsistentes`.
- Modificar `evento/FichaBebidaService.java` — `aMiFicha`.
- Modificar `evento/AsistenciaController.java` — 2 endpoints.
- Crear `back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java`.

**Front (`front/src/app/panel/eventos/`)**
- Modificar `eventos.types.ts` — campos nuevos + código de error.
- Modificar `eventos.service.ts` — `confirmarPago`, `deshacerPago`.
- Modificar `modal-asistentes/modal-asistentes.ts` + `.html` — botón + sub-modal + deshacer.
- Modificar `modal-asistentes/modal-asistentes.spec.ts`.
- Modificar `evento-detalle/evento-detalle.ts` + `.html` — botón "Ya he pagado" + modal info.
- Modificar `evento-detalle/evento-detalle.spec.ts`.

**Móvil (`mobile/shared/src/commonMain/kotlin/com/baniterio/app/`)**
- Modificar `data/dto/EventoDtos.kt` — campos nuevos + `ConfirmarPagoBody`.
- Modificar `data/ResultadoEvento.kt` — `FICHA_SIN_CUOTA`.
- Modificar `data/EventosRepository.kt` + `data/EventosRepositoryImpl.kt` — 2 métodos.
- Modificar `mobile/shared/src/commonTest/.../data/EventosRepositoryImplTest.kt`.
- Modificar `ui/eventos/ListadoAsistentesDialog.kt` — botón + selector + deshacer.
- Modificar `ui/eventos/EventoDetalleScreen.kt` — botón "Ya he pagado" + diálogo info.

---

## Task 1: Migración, enum y campos de pago en la entidad

**Files:**
- Create: `back/src/main/java/com/baniterio/api/identidad/MetodoPago.java`
- Create: `back/src/main/resources/db/migration/V26__ficha_bebida_pago.sql`
- Modify: `back/src/main/java/com/baniterio/api/identidad/FichaBebida.java`
- Test: `back/src/test/java/com/baniterio/api/identidad/FichaBebidaPagoIT.java` (nuevo)

**Interfaces:**
- Produces:
  - `enum MetodoPago { TRANSFERENCIA, BIZUM, EFECTIVO }` (paquete `com.baniterio.api.identidad`).
  - `FichaBebida` con getters/setters Lombok: `boolean isPagado()` / `setPagado(boolean)`,
    `MetodoPago getMetodoPago()` / `setMetodoPago(MetodoPago)`,
    `Usuario getPagadoConfirmadoPor()` / `setPagadoConfirmadoPor(Usuario)`,
    `Instant getPagadoAt()` / `setPagadoAt(Instant)`.

- [ ] **Step 1: Escribir el test de integración que falla**

Crear `back/src/test/java/com/baniterio/api/identidad/FichaBebidaPagoIT.java`:

```java
package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Las 4 columnas de pago de V26 se persisten y se leen en `ficha_bebida`. */
class FichaBebidaPagoIT extends IntegrationTest {

    @Autowired FichaBebidaRepository fichas;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired PenaRepository penas;
    @Autowired BebidaRepository bebidas;

    private Long fichaId;
    private Long eventoId;

    @AfterEach
    void limpiar() {
        if (fichaId != null) fichas.deleteById(fichaId);
        if (eventoId != null) {
            asistencias.deleteAll(asistencias.findByEventoId(eventoId));
            eventos.deleteById(eventoId);
        }
    }

    @Test
    void guarda_y_lee_el_estado_de_pago() {
        Pena pena = penas.findBySlug("baniterio").orElseThrow();
        Cuenta cuenta = cuentas.findByPenaIdAndNombre(pena.getId(), "San Miguel").orElseThrow();
        Evento e = eventos.save(Evento.builder().pena(pena).cuenta(cuenta)
                .nombre("IT-pago-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(10)).build());
        eventoId = e.getId();
        AsistenciaEvento a = asistencias.save(AsistenciaEvento.builder()
                .evento(e).nombre("Invitado").estado(EstadoAsistencia.APUNTADO).build());
        Bebida refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, "Coca-Cola").orElseThrow();

        FichaBebida f = fichas.save(FichaBebida.builder()
                .asistencia(a).refresco(refresco)
                .alternativa(Alternativa.NADA).modalidad(Modalidad.SOLO_CERVEZA)
                .asisteDia1(true).asisteDia2(true)
                .cuota(new BigDecimal("16.00"))
                .pagado(true).metodoPago(MetodoPago.BIZUM).pagadoAt(Instant.now())
                .build());
        fichaId = f.getAsistenciaId();

        FichaBebida leida = fichas.findById(fichaId).orElseThrow();
        assertThat(leida.isPagado()).isTrue();
        assertThat(leida.getMetodoPago()).isEqualTo(MetodoPago.BIZUM);
        assertThat(leida.getPagadoAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Ejecutar el test y ver que no compila / falla**

Run: `cd back && ./gradlew test --tests 'com.baniterio.api.identidad.FichaBebidaPagoIT'`
Expected: FALLO de compilación (`MetodoPago` y los métodos `.pagado(...)` no existen).

- [ ] **Step 3: Crear el enum**

`back/src/main/java/com/baniterio/api/identidad/MetodoPago.java`:

```java
package com.baniterio.api.identidad;

/** Cómo se ha pagado una cuota. Espejo del tipo `MetodoPago` de front y móvil. */
public enum MetodoPago {
    TRANSFERENCIA,
    BIZUM,
    EFECTIVO;

    /** Texto para mostrar al usuario. */
    public String legible() {
        return switch (this) {
            case TRANSFERENCIA -> "Transferencia";
            case BIZUM -> "Bizum";
            case EFECTIVO -> "Efectivo";
        };
    }
}
```

- [ ] **Step 4: Crear la migración**

`back/src/main/resources/db/migration/V26__ficha_bebida_pago.sql`:

```sql
-- Confirmación de pago de la cuota de San Miguel por parte de un administrador
-- (pieza 5 recortada del subsistema Cuentas). El estado vive en la ficha; no hay
-- tabla `pago` todavía. `pagado` lo pone/quita solo el admin desde el modal de
-- asistentes; el modal de declaración del peñista sigue sin persistir.
ALTER TABLE ficha_bebida ADD COLUMN pagado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ficha_bebida ADD COLUMN metodo_pago VARCHAR(16);
ALTER TABLE ficha_bebida ADD COLUMN pagado_confirmado_por BIGINT REFERENCES usuario(id);
ALTER TABLE ficha_bebida ADD COLUMN pagado_at TIMESTAMPTZ;
```

- [ ] **Step 5: Añadir los campos a la entidad**

En `back/src/main/java/com/baniterio/api/identidad/FichaBebida.java`, después del
campo `cuota` (línea ~80) y antes de `createdAt`:

```java
    @Column(nullable = false)
    private boolean pagado;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 16)
    private MetodoPago metodoPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagado_confirmado_por")
    private Usuario pagadoConfirmadoPor;

    @Column(name = "pagado_at")
    private Instant pagadoAt;
```

(Los imports `Instant`, `Column`, `Enumerated`, `EnumType`, `ManyToOne`,
`JoinColumn`, `FetchType` ya están en el fichero.)

- [ ] **Step 6: Ejecutar el test y ver que pasa**

Run: `cd back && ./gradlew test --tests 'com.baniterio.api.identidad.FichaBebidaPagoIT'`
Expected: PASA.

- [ ] **Step 7: Commit**

```bash
git add back/src/main/java/com/baniterio/api/identidad/MetodoPago.java \
        back/src/main/resources/db/migration/V26__ficha_bebida_pago.sql \
        back/src/main/java/com/baniterio/api/identidad/FichaBebida.java \
        back/src/test/java/com/baniterio/api/identidad/FichaBebidaPagoIT.java
git commit -m "feat(cuentas): estado de pago en ficha_bebida (V26 + entidad + MetodoPago)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Endpoints de confirmar / deshacer pago + campos en el listado

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/FichaSinCuotaException.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/ConfirmarPagoRequest.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/dto/AsistenteFila.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/dto/ListadoAsistentesResponse.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaService.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaController.java`
- Test: `back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java` (nuevo)

**Interfaces:**
- Consumes: `MetodoPago`, `FichaBebida` con campos de pago (Task 1);
  `AsistenciaService.puedeGestionar(Long, Evento)` (privado, ya existe);
  `FichaBebidaRepository.findByAsistenciaId(Long)`, `.findByEventoId(Long)` (ya existen);
  `AsistenciaEventoRepository.findByIdAndEventoId(Long, Long)` (ya existe, ver `quitarAMano`).
- Produces:
  - `record ConfirmarPagoRequest(@NotNull MetodoPago metodo)`.
  - `AsistenciaService.confirmarPago(Long usuarioId, Long eventoId, Long asistenciaId, MetodoPago metodo) -> ListadoAsistentesResponse`.
  - `AsistenciaService.deshacerPago(Long usuarioId, Long eventoId, Long asistenciaId) -> ListadoAsistentesResponse`.
  - `AsistenteFila(String nombre, String estado, boolean esManual, BebidaFila bebida, BigDecimal cuota, boolean pagado, Long asistenciaId, String metodoPago, String pagadoPor, Instant pagadoAt)`.
  - `ListadoAsistentesResponse(List<AsistenteFila> asistentes, BigDecimal totalCuotas, BigDecimal totalPagado, List<PersonaPagable> puedoPagarPor, BigDecimal miCuota, boolean puedoConfirmarPagos)`.
  - `PUT /api/v1/eventos/{id}/asistencias/{asistenciaId}/pago`, `DELETE` mismo path.

- [ ] **Step 1: Escribir el IT que falla**

Crear `back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java`. Reusa
el estilo de `FichaBebidaIT` (helpers `crearMiembro`, `sanMiguel`, `fichaBase`,
`putFicha`). Copia esos helpers al nuevo fichero (los `*IT` no comparten clase base
de helpers; `FichaBebidaIT` los tiene privados). Casos:

```java
package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.*;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/** IT de la confirmación de pago por el administrador (pieza 5 recortada). */
class ConfirmacionPagoIT extends IntegrationTest {

    @LocalServerPort int port;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired AsistenciaEventoRepository asistencias;
    @Autowired FichaBebidaRepository fichas;
    @Autowired BebidaRepository bebidas;
    @Autowired PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterEach void limpiar() {
        fichas.deleteAllInBatch();
        asistencias.deleteAllInBatch();
        eventos.deleteAll(eventos.findAll().stream()
                .filter(e -> e.getNombre().startsWith("IT-pagoadm")).toList());
    }

    record Sesion(Long id, String token) {}

    Pena pena() { return penas.findBySlug("baniterio").orElseThrow(); }

    Sesion crearMiembro(RolMembresia rol) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@pagoadm.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Pago").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return new Sesion(u.getId(), (String) body.get("token"));
    }

    Evento sanMiguel(BigDecimal cubatas) {
        CalculadoraCuota.Cuotas c = CalculadoraCuota.derivar(cubatas);
        return eventos.save(Evento.builder().pena(pena())
                .cuenta(cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow())
                .nombre("IT-pagoadm-" + ThreadLocalRandom.current().nextInt(1_000_000))
                .fecha(LocalDate.now().plusDays(20)).fechaFin(LocalDate.now().plusDays(21))
                .cuotaCubatas(c.cubatas()).cuotaCervezas(c.cervezas())
                .cuotaCubatas1Dia(c.cubatas1Dia()).cuotaCervezas1Dia(c.cervezas1Dia())
                .cuotaEmbarazada(c.embarazada()).build());
    }

    /** Apunta a `s` con ficha (solo refresco) y devuelve el id de su asistencia. */
    Long apuntarConFicha(Sesion s, Long eventoId) {
        Long refresco = bebidas.findByTipoAndNombreIgnoreCase(TipoBebida.REFRESCO, "Coca-Cola")
                .orElseThrow().getId();
        Map<String, Object> ficha = new HashMap<>();
        ficha.put("estado", "APUNTADO");
        ficha.put("refrescoBebidaId", refresco);
        ficha.put("alternativa", "NADA");
        ficha.put("asisteDia1", true);
        ficha.put("asisteDia2", true);
        http.put().uri("/api/v1/eventos/" + eventoId + "/ficha-bebida")
                .header(AUTHORIZATION, "Bearer " + s.token()).body(ficha)
                .exchange().expectStatus().isOk();
        return asistencias.findByEventoIdAndUsuarioId(eventoId, s.id()).orElseThrow().getId();
    }

    @Test
    void admin_confirma_el_pago_y_el_listado_lo_refleja() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "BIZUM"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalPagado").isEqualTo(16.0)
                .jsonPath("$.asistentes[0].pagado").isEqualTo(true)
                .jsonPath("$.asistentes[0].metodoPago").isEqualTo("BIZUM")
                .jsonPath("$.asistentes[0].asistenciaId").isEqualTo(asisId.intValue());
    }

    @Test
    void no_admin_no_puede_confirmar_403() {
        Sesion otro = crearMiembro(RolMembresia.MIEMBRO);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + otro.token())
                .body(Map.of("metodo", "BIZUM"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_EVENTO");
    }

    @Test
    void confirmar_sobre_ficha_sin_cuota_es_409_FICHA_SIN_CUOTA() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(null); // 5 cuotas sin poner => ficha.cuota null
        Long asisId = apuntarConFicha(penista, e.getId());

        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "EFECTIVO"))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("FICHA_SIN_CUOTA");
    }

    @Test
    void deshacer_vuelve_a_pendiente() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "BIZUM")).exchange().expectStatus().isOk();

        http.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalPagado").isEqualTo(0.0)
                .jsonPath("$.asistentes[0].pagado").isEqualTo(false);
    }

    @Test
    void el_listado_trae_puedoConfirmarPagos_segun_el_rol() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion miembro = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        apuntarConFicha(miembro, e.getId());

        http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.puedoConfirmarPagos").isEqualTo(true);
        http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
                .header(AUTHORIZATION, "Bearer " + miembro.token())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.puedoConfirmarPagos").isEqualTo(false);
    }
}
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd back && ./gradlew test --tests 'com.baniterio.api.evento.ConfirmacionPagoIT'`
Expected: FALLO de compilación (endpoint y campos nuevos no existen).

- [ ] **Step 3: Excepción + handler**

`back/src/main/java/com/baniterio/api/evento/FichaSinCuotaException.java`:

```java
package com.baniterio.api.evento;

/** Se intenta confirmar el pago de una ficha que aún no tiene cuota calculada. */
public class FichaSinCuotaException extends RuntimeException {
}
```

En `ApiExceptionHandler.java`, junto a `eventoSinFicha()` (línea ~209):

```java
    @ExceptionHandler(com.baniterio.api.evento.FichaSinCuotaException.class)
    ResponseEntity<Map<String, Object>> fichaSinCuota() {
        return error(HttpStatus.CONFLICT, "FICHA_SIN_CUOTA");
    }
```

- [ ] **Step 4: DTO del cuerpo**

`back/src/main/java/com/baniterio/api/evento/dto/ConfirmarPagoRequest.java`:

```java
package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.MetodoPago;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de `PUT /eventos/{id}/asistencias/{asistenciaId}/pago`. */
public record ConfirmarPagoRequest(@NotNull MetodoPago metodo) {
}
```

- [ ] **Step 5: Ampliar `AsistenteFila`**

`back/src/main/java/com/baniterio/api/evento/dto/AsistenteFila.java` — sustituir el
record por:

```java
package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una fila del listado de asistentes. {@code estado} es {@code APUNTADO} o
 * {@code EN_DUDA}. {@code bebida} y {@code cuota} son {@code null} si la
 * asistencia no tiene ficha. Si {@code pagado}, van rellenos {@code metodoPago}
 * (BIZUM|TRANSFERENCIA|EFECTIVO), {@code pagadoPor} (nombre de quien lo confirmó)
 * y {@code pagadoAt}.
 */
public record AsistenteFila(
        String nombre,
        String estado,
        boolean esManual,
        BebidaFila bebida,
        BigDecimal cuota,
        boolean pagado,
        Long asistenciaId,
        String metodoPago,
        String pagadoPor,
        Instant pagadoAt) {
}
```

- [ ] **Step 6: Ampliar `ListadoAsistentesResponse`**

Añadir `boolean puedoConfirmarPagos` al final del record y actualizar el javadoc:

```java
public record ListadoAsistentesResponse(
        List<AsistenteFila> asistentes,
        BigDecimal totalCuotas,
        BigDecimal totalPagado,
        List<PersonaPagable> puedoPagarPor,
        BigDecimal miCuota,
        boolean puedoConfirmarPagos) {
}
```

- [ ] **Step 7: Servicio — `aFila`, `listadoAsistentes`, `confirmarPago`, `deshacerPago`**

En `AsistenciaService.java`:

Cambiar `aFila` (línea ~313) para recibir la ficha y volcar el pago:

```java
    private static AsistenteFila aFila(AsistenciaEvento a, FichaBebida f) {
        BebidaFila bebida = f == null ? null : new BebidaFila(
                f.getAlcohol() != null ? f.getAlcohol().getNombre() : null,
                f.getRefresco().getNombre(),
                f.getAlternativa().name(),
                f.getModalidad().name());
        boolean pagado = f != null && f.isPagado();
        return new AsistenteFila(nombreDe(a), a.getEstado().name(), a.getUsuario() == null,
                bebida, f == null ? null : f.getCuota(), pagado, a.getId(),
                pagado && f.getMetodoPago() != null ? f.getMetodoPago().name() : null,
                pagado && f.getPagadoConfirmadoPor() != null
                        ? f.getPagadoConfirmadoPor().getNombre() : null,
                pagado ? f.getPagadoAt() : null);
    }
```

En `listadoAsistentes` (línea ~305), cambiar el `totalPagado` (hoy `BigDecimal.ZERO`)
y añadir `puedoConfirmarPagos`:

```java
        BigDecimal totalPagado = asistentes.stream()
                .filter(AsistenteFila::pagado)
                .map(AsistenteFila::cuota).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ListadoAsistentesResponse(asistentes, totalCuotas, totalPagado,
                puedoPagarPor(usuarioId, eventoId, fichaPorAsistencia), miCuota,
                puedeGestionar(usuarioId, e));
```

Añadir los dos métodos nuevos (después de `quitarAMano`, línea ~241):

```java
    /**
     * Un administrador confirma el pago de la cuota de una asistencia (pieza 5).
     * Sobrescribe si ya estaba confirmado. 403 si no puede gestionar; 404 si la
     * asistencia no es de ese evento o no tiene ficha; 409 {@code EVENTO_SIN_FICHA}
     * si el evento no es de San Miguel; 409 {@code FICHA_SIN_CUOTA} si la ficha
     * aún no tiene cuota. Devuelve el listado recalculado.
     */
    @Transactional
    public ListadoAsistentesResponse confirmarPago(Long usuarioId, Long eventoId,
            Long asistenciaId, MetodoPago metodo) {
        FichaBebida f = fichaParaPago(usuarioId, eventoId, asistenciaId);
        if (f.getCuota() == null) {
            throw new FichaSinCuotaException();
        }
        f.setPagado(true);
        f.setMetodoPago(metodo);
        f.setPagadoConfirmadoPor(usuarios.findById(usuarioId).orElseThrow());
        f.setPagadoAt(Instant.now());
        fichas.save(f);
        return listadoAsistentes(usuarioId, eventoId);
    }

    /** Deshace {@link #confirmarPago}: vuelve la ficha a "pendiente". */
    @Transactional
    public ListadoAsistentesResponse deshacerPago(Long usuarioId, Long eventoId, Long asistenciaId) {
        FichaBebida f = fichaParaPago(usuarioId, eventoId, asistenciaId);
        f.setPagado(false);
        f.setMetodoPago(null);
        f.setPagadoConfirmadoPor(null);
        f.setPagadoAt(null);
        fichas.save(f);
        return listadoAsistentes(usuarioId, eventoId);
    }

    private FichaBebida fichaParaPago(Long usuarioId, Long eventoId, Long asistenciaId) {
        Evento e = cargar(eventoId);
        if (!puedeGestionar(usuarioId, e)) {
            throw new SinPermisoEventoException();
        }
        if (!e.getCuenta().isLlevaFichaBebida()) {
            throw new EventoSinFichaException();
        }
        AsistenciaEvento a = asistencias.findByIdAndEventoId(asistenciaId, eventoId)
                .orElseThrow(AsistenciaNoEncontradaException::new);
        return fichas.findByAsistenciaId(a.getId())
                .orElseThrow(AsistenciaNoEncontradaException::new);
    }
```

Imports a asegurar en `AsistenciaService.java`: `com.baniterio.api.identidad.MetodoPago`
(el resto —`Instant`, `FichaBebida`— ya están).

- [ ] **Step 8: Controller — dos endpoints**

En `AsistenciaController.java`, añadir (importar `ConfirmarPagoRequest`,
`ListadoAsistentesResponse` ya está, `MetodoPago` no hace falta aquí):

```java
    @PutMapping("/{id}/asistencias/{asistenciaId}/pago")
    public ListadoAsistentesResponse confirmarPago(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId,
            @Valid @RequestBody ConfirmarPagoRequest req) {
        return asistenciaService.confirmarPago(principal.id(), id, asistenciaId, req.metodo());
    }

    @DeleteMapping("/{id}/asistencias/{asistenciaId}/pago")
    public ListadoAsistentesResponse deshacerPago(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @PathVariable Long asistenciaId) {
        return asistenciaService.deshacerPago(principal.id(), id, asistenciaId);
    }
```

- [ ] **Step 9: Ejecutar el IT nuevo + la suite de evento**

Run: `cd back && ./gradlew test --tests 'com.baniterio.api.evento.*'`
Expected: PASA (incluidos `AsistenciaIT`, `FichaBebidaIT`, `ConfirmacionPagoIT`).
Si `AsistenciaIT` o algún test de contrato compara el JSON del listado, ajustar
allí los campos nuevos.

- [ ] **Step 10: Commit**

```bash
git add back/src/main/java/com/baniterio/api/evento back/src/main/java/com/baniterio/api/web \
        back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java
git commit -m "feat(cuentas): endpoints confirmar/deshacer pago + estado de pago en el listado

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: Campos de pago en `asistencia.ficha.miFicha` del detalle

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/evento/dto/FichaBebidaDetalle.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/FichaBebidaService.java`
- Test: `back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java` (añadir un caso)

**Interfaces:**
- Consumes: `FichaBebida` con campos de pago (Task 1); confirmación de pago (Task 2).
- Produces: `FichaBebidaDetalle.MiFicha` con 4 campos nuevos al final:
  `boolean pagado, String metodoPago, String pagadoPor, Instant pagadoAt`.

- [ ] **Step 1: Añadir el caso al IT**

En `ConfirmacionPagoIT.java`:

```java
    @Test
    void el_detalle_del_penista_trae_su_pago_confirmado() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel(new BigDecimal("26"));
        Long asisId = apuntarConFicha(penista, e.getId());
        http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencias/" + asisId + "/pago")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .body(Map.of("metodo", "TRANSFERENCIA")).exchange().expectStatus().isOk();

        http.get().uri("/api/v1/eventos/" + e.getId())
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.asistencia.ficha.miFicha.pagado").isEqualTo(true)
                .jsonPath("$.asistencia.ficha.miFicha.metodoPago").isEqualTo("TRANSFERENCIA")
                .jsonPath("$.asistencia.ficha.miFicha.pagadoPor").isNotEmpty();
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd back && ./gradlew test --tests 'com.baniterio.api.evento.ConfirmacionPagoIT.el_detalle_del_penista_trae_su_pago_confirmado'`
Expected: FALLO (`miFicha.pagado` no existe en el JSON).

- [ ] **Step 3: Ampliar `MiFicha`**

En `FichaBebidaDetalle.java`, añadir al final del record anidado:

```java
    public record MiFicha(Long alcoholBebidaId, String alcohol, Long refrescoBebidaId, String refresco,
                          String alternativa, String cervezaEspecial, boolean embarazada,
                          boolean asisteDia1, boolean asisteDia2, String modalidad,
                          BigDecimal cuota, boolean cuotaPendiente, boolean bebidaPendiente,
                          boolean pagado, String metodoPago, String pagadoPor, Instant pagadoAt) {
    }
```

Añadir el import `java.time.Instant` al fichero.

- [ ] **Step 4: Rellenarlos en `aMiFicha`**

En `FichaBebidaService.java`, método `aMiFicha` (línea ~202), añadir los 4
argumentos al constructor:

```java
        return new FichaBebidaDetalle.MiFicha(
                al != null ? al.getId() : null,
                al != null ? al.getNombre() : "No bebo alcohol",
                re.getId(), re.getNombre(),
                f.getAlternativa().name(), f.getCervezaEspecial(),
                f.isEmbarazada(), f.isAsisteDia1(), f.isAsisteDia2(),
                f.getModalidad().name(), f.getCuota(), f.getCuota() == null, bebidaPendiente,
                f.isPagado(),
                f.isPagado() && f.getMetodoPago() != null ? f.getMetodoPago().name() : null,
                f.isPagado() && f.getPagadoConfirmadoPor() != null
                        ? f.getPagadoConfirmadoPor().getNombre() : null,
                f.isPagado() ? f.getPagadoAt() : null);
```

- [ ] **Step 5: Ejecutar la suite de evento**

Run: `cd back && ./gradlew test --tests 'com.baniterio.api.evento.*'`
Expected: PASA.

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/evento back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java
git commit -m "feat(cuentas): el detalle del evento trae el pago confirmado del peñista

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: Front — tipos y servicio

**Files:**
- Modify: `front/src/app/panel/eventos/eventos.types.ts`
- Modify: `front/src/app/panel/eventos/eventos.service.ts`
- Test: `front/src/app/panel/eventos/eventos.service.spec.ts`

**Interfaces:**
- Consumes: endpoints de Task 2 (`PUT`/`DELETE .../asistencias/{asistenciaId}/pago`).
- Produces:
  - `AsistenteFila` con `asistenciaId: number`, `metodoPago: MetodoPago | null`,
    `pagadoPor: string | null`, `pagadoAt: string | null`.
  - `ListadoAsistentes` con `puedoConfirmarPagos: boolean`.
  - `FichaBebidaMia` con `pagado: boolean`, `metodoPago: MetodoPago | null`,
    `pagadoPor: string | null`, `pagadoAt: string | null`.
  - `EventosService.confirmarPago(eventoId: number, asistenciaId: number, metodo: MetodoPago): Observable<ListadoAsistentes>`.
  - `EventosService.deshacerPago(eventoId: number, asistenciaId: number): Observable<ListadoAsistentes>`.

- [ ] **Step 1: Escribir los tests del servicio que fallan**

En `front/src/app/panel/eventos/eventos.service.spec.ts` añadir (mismo estilo que
los tests existentes con `HttpTestingController`):

```typescript
  it('confirmarPago hace PUT al endpoint de pago con el método', () => {
    let resp: unknown;
    service.confirmarPago(3, 7, 'BIZUM').subscribe((r) => (resp = r));
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/7/pago`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ metodo: 'BIZUM' });
    req.flush({ asistentes: [], totalCuotas: 0, totalPagado: 0, puedoPagarPor: [], miCuota: null, puedoConfirmarPagos: true });
    expect(resp).toBeTruthy();
  });

  it('deshacerPago hace DELETE al endpoint de pago', () => {
    service.deshacerPago(3, 7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/7/pago`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ asistentes: [], totalCuotas: 0, totalPagado: 0, puedoPagarPor: [], miCuota: null, puedoConfirmarPagos: true });
  });
```

(Si el `describe` no tiene `service`/`httpMock`/`base` en scope, copiar el `beforeEach`
del fichero — revisar cómo está montado antes de añadir.)

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npm test -- --watch=false --include='**/eventos.service.spec.ts'`
Expected: FALLO (`confirmarPago` no existe).

- [ ] **Step 3: Tipos**

En `eventos.types.ts`:

En `AsistenteFila` (línea ~216):

```typescript
export interface AsistenteFila {
  nombre: string;
  estado: 'APUNTADO' | 'EN_DUDA';
  esManual: boolean;
  bebida: BebidaFila | null;
  cuota: number | null;
  pagado: boolean;
  asistenciaId: number;
  metodoPago: MetodoPago | null;
  pagadoPor: string | null;
  pagadoAt: string | null;
}
```

En `ListadoAsistentes` (línea ~235) añadir `puedoConfirmarPagos: boolean;`.

En `FichaBebidaMia` (línea ~60) añadir al final:

```typescript
  pagado: boolean;
  metodoPago: MetodoPago | null;
  pagadoPor: string | null;
  pagadoAt: string | null;
```

En `CodigoErrorEvento` (línea ~244) añadir `| 'FICHA_SIN_CUOTA'`.

- [ ] **Step 4: Servicio**

En `eventos.service.ts`, tras `asistentesEvento` (línea ~66):

```typescript
  /** Un administrador confirma el pago de una asistencia (pieza 5). Devuelve el listado recalculado. */
  confirmarPago(
    eventoId: number,
    asistenciaId: number,
    metodo: MetodoPago,
  ): Observable<ListadoAsistentes> {
    return this.http.put<ListadoAsistentes>(
      `${this.base}/eventos/${eventoId}/asistencias/${asistenciaId}/pago`,
      { metodo },
    );
  }

  /** Deshace la confirmación de pago de una asistencia. */
  deshacerPago(eventoId: number, asistenciaId: number): Observable<ListadoAsistentes> {
    return this.http.delete<ListadoAsistentes>(
      `${this.base}/eventos/${eventoId}/asistencias/${asistenciaId}/pago`,
    );
  }
```

Añadir `MetodoPago` a los imports de `./eventos.types`.

- [ ] **Step 5: Ejecutar y ver que pasa**

Run: `cd front && npm test -- --watch=false --include='**/eventos.service.spec.ts'`
Expected: PASA.

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/eventos/eventos.types.ts front/src/app/panel/eventos/eventos.service.ts \
        front/src/app/panel/eventos/eventos.service.spec.ts
git commit -m "feat(cuentas): contrato de front para confirmar/deshacer pago

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Front — modal de asistentes con "Confirmar el pago"

**Files:**
- Modify: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.ts`
- Modify: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.html`
- Test: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.spec.ts`

**Interfaces:**
- Consumes: `EventosService.confirmarPago`, `.deshacerPago` (Task 4);
  `AsistenteFila.asistenciaId`, `.pagado`, `.metodoPago`; `ListadoAsistentes.puedoConfirmarPagos`.
- Produces: (UI interna, sin salidas nuevas).

- [ ] **Step 1: Tests que fallan**

En `modal-asistentes.spec.ts` añadir. El listado flush debe incluir
`puedoConfirmarPagos: true` y filas con `asistenciaId`:

```typescript
  function flushListado(extra: Partial<Record<string, unknown>> = {}) {
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [
        {
          nombre: 'Ana', estado: 'APUNTADO', esManual: false,
          bebida: { alcohol: 'Barceló', refresco: 'Coca-Cola', alternativa: 'NADA', modalidad: 'COMPLETA' },
          cuota: 45, pagado: false, asistenciaId: 11, metodoPago: null, pagadoPor: null, pagadoAt: null,
        },
      ],
      totalCuotas: 45, totalPagado: 0, puedoPagarPor: [], miCuota: 45,
      puedoConfirmarPagos: true, ...extra,
    });
    fixture.detectChanges();
  }

  it('muestra "Confirmar el pago" solo si puedoConfirmarPagos y la fila no está pagada', () => {
    flushListado();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Confirmar el pago');
  });

  it('confirma el pago: abre el método, elige Bizum y llama al servicio', () => {
    flushListado();
    const btn = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Confirmar el pago')!;
    btn.click();
    fixture.detectChanges();
    // el sub-modal ofrece los 3 métodos
    const bizum = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Bizum')!;
    bizum.click();
    fixture.detectChanges();
    const confirmar = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Confirmar')!;
    confirmar.click();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/11/pago`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ metodo: 'BIZUM' });
    req.flush({
      asistentes: [{ nombre: 'Ana', estado: 'APUNTADO', esManual: false, bebida: null,
        cuota: 45, pagado: true, asistenciaId: 11, metodoPago: 'BIZUM', pagadoPor: 'Jefe', pagadoAt: '2026-09-07T10:00:00Z' }],
      totalCuotas: 45, totalPagado: 45, puedoPagarPor: [], miCuota: 45, puedoConfirmarPagos: true,
    });
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('pagado');
    expect(texto).toContain('Bizum');
    expect(texto).not.toContain('Confirmar el pago');
  });

  it('deshacer llama al DELETE', () => {
    flushListado({
      asistentes: [{ nombre: 'Ana', estado: 'APUNTADO', esManual: false, bebida: null,
        cuota: 45, pagado: true, asistenciaId: 11, metodoPago: 'EFECTIVO', pagadoPor: 'Jefe', pagadoAt: '2026-09-07T10:00:00Z' }],
    });
    const deshacer = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'deshacer')!;
    deshacer.click();
    const req = httpMock.expectOne(`${base}/eventos/3/asistencias/11/pago`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ asistentes: [], totalCuotas: 0, totalPagado: 0, puedoPagarPor: [], miCuota: null, puedoConfirmarPagos: true });
  });
```

Actualizar los dos tests que ya existen (el de "carga el listado" y el de "emite
cerrar") para que el `flush` incluya `puedoConfirmarPagos: false` y `asistenciaId`
en las filas (si no, TypeScript se queja del tipo).

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npm test -- --watch=false --include='**/modal-asistentes.spec.ts'`
Expected: FALLO.

- [ ] **Step 3: Componente**

En `modal-asistentes.ts`:

```typescript
import { MetodoPago } from '../eventos.types';

const METODOS: { valor: MetodoPago; texto: string }[] = [
  { valor: 'BIZUM', texto: 'Bizum' },
  { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
];

const METODO_TEXTO: Record<MetodoPago, string> = {
  BIZUM: 'Bizum',
  TRANSFERENCIA: 'Transferencia',
  EFECTIVO: 'Efectivo',
};
```

Dentro de la clase añadir:

```typescript
  protected readonly metodos = METODOS;
  protected readonly metodoTexto = METODO_TEXTO;
  protected readonly filaConfirmando = signal<AsistenteFila | null>(null);
  protected readonly metodoElegido = signal<MetodoPago>('BIZUM');
  protected readonly guardandoPago = signal(false);
  protected readonly errorPago = signal('');

  protected abrirConfirmar(a: AsistenteFila): void {
    this.metodoElegido.set('BIZUM');
    this.errorPago.set('');
    this.filaConfirmando.set(a);
  }

  protected cerrarConfirmar(): void {
    this.filaConfirmando.set(null);
  }

  protected confirmarPago(): void {
    const fila = this.filaConfirmando();
    if (!fila || this.guardandoPago()) {
      return;
    }
    this.guardandoPago.set(true);
    this.eventosService
      .confirmarPago(this.eventoId(), fila.asistenciaId, this.metodoElegido())
      .subscribe({
        next: (d) => {
          this.datos.set(d);
          this.guardandoPago.set(false);
          this.filaConfirmando.set(null);
          this.ajustarColumnas();
        },
        error: () => {
          this.guardandoPago.set(false);
          this.errorPago.set('No se pudo confirmar el pago.');
        },
      });
  }

  protected deshacerPago(a: AsistenteFila): void {
    this.eventosService.deshacerPago(this.eventoId(), a.asistenciaId).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.ajustarColumnas();
      },
      error: () => this.errorPago.set('No se pudo deshacer el pago.'),
    });
  }
```

`ajustarColumnas` es `private`; cambiarlo a `protected` no hace falta (se llama
desde la clase). Sí hace falta que `AsistenteFila` esté importado (ya lo está).

- [ ] **Step 4: Plantilla**

En `modal-asistentes.html`, dentro del `@for` de asistentes, sustituir el bloque
del `<span>` de cuota/estado (líneas ~50-53) y añadir el botón:

```html
                  <div class="flex items-baseline justify-between gap-2">
                    <span class="text-sm font-semibold" [class.text-gold-soft]="a.estado === 'EN_DUDA'">
                      {{ a.nombre }}
                      @if (a.estado === 'EN_DUDA') { <span class="text-xs">(en duda)</span> }
                      @if (a.esManual) { <span class="text-xs text-muted">· invitado</span> }
                    </span>
                    <span class="shrink-0 text-sm font-bold text-gold-soft">
                      {{ a.cuota != null ? a.cuota + ' €' : 'sin cuota' }} ·
                      {{ a.pagado ? 'pagado' : 'pendiente' }}
                      @if (a.pagado && a.metodoPago) {
                        · {{ metodoTexto[a.metodoPago] }}
                      }
                    </span>
                  </div>
                  <p class="mt-1 text-xs text-muted">{{ bebidaTexto(a) }}</p>
                  @if (d.puedoConfirmarPagos && a.cuota != null) {
                    @if (!a.pagado) {
                      <button
                        type="button"
                        (click)="abrirConfirmar(a)"
                        class="mt-2 rounded-lg bg-brand px-3 py-1.5 text-xs font-bold text-gold transition hover:bg-brand-dark"
                      >
                        Confirmar el pago
                      </button>
                    } @else {
                      <button
                        type="button"
                        (click)="deshacerPago(a)"
                        class="mt-2 text-xs font-medium text-muted underline transition hover:text-ink"
                      >
                        deshacer
                      </button>
                    }
                  }
```

Y al final del `<div class="carta-relieve ...">`, antes de cerrarlo, el sub-modal:

```html
    @if (filaConfirmando(); as fila) {
      <div
        class="fixed inset-0 z-[60] flex items-center justify-center bg-surface/90 p-4 backdrop-blur-sm"
        role="dialog"
        aria-label="Confirmar el pago"
      >
        <div class="carta-relieve w-full max-w-sm rounded-2xl p-6">
          <h3 class="font-display text-lg font-extrabold">Confirmar el pago de {{ fila.nombre }}</h3>
          <p class="mt-1 text-sm text-muted">Cuota: {{ fila.cuota }} €</p>
          <p class="mt-4 text-sm font-semibold">¿Cómo lo ha pagado?</p>
          <div class="mt-2 grid grid-cols-3 gap-2">
            @for (m of metodos; track m.valor) {
              <button
                type="button"
                (click)="metodoElegido.set(m.valor)"
                [class]="
                  metodoElegido() === m.valor
                    ? 'rounded-xl border border-brand bg-brand px-2 py-2 text-center text-sm font-bold text-gold'
                    : 'rounded-xl border border-outline px-2 py-2 text-center text-sm font-medium text-ink hover:border-brand-bright/60'
                "
              >
                {{ m.texto }}
              </button>
            }
          </div>
          @if (errorPago()) { <p class="mt-3 text-sm text-red-300">{{ errorPago() }}</p> }
          <div class="mt-5 flex gap-2">
            <button
              type="button"
              (click)="confirmarPago()"
              [disabled]="guardandoPago()"
              class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-gold transition hover:bg-brand-dark disabled:opacity-50"
            >
              Confirmar
            </button>
            <button
              type="button"
              (click)="cerrarConfirmar()"
              class="rounded-xl border border-outline px-4 py-2 text-sm font-medium text-ink"
            >
              Cancelar
            </button>
          </div>
        </div>
      </div>
    }
```

- [ ] **Step 5: Ejecutar los tests del modal**

Run: `cd front && npm test -- --watch=false --include='**/modal-asistentes.spec.ts'`
Expected: PASA.

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/eventos/modal-asistentes
git commit -m "feat(cuentas): el admin confirma el pago desde el modal de asistentes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Front — "Ya he pagado" en el detalle del evento

**Files:**
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.html`
- Test: `front/src/app/panel/eventos/evento-detalle/evento-detalle.spec.ts`

**Interfaces:**
- Consumes: `FichaBebidaMia.pagado`, `.metodoPago`, `.pagadoPor`, `.pagadoAt` (Task 4).
- Produces: (UI interna).

- [ ] **Step 1: Test que falla**

En `evento-detalle.spec.ts`, montar el detalle con `miFicha.pagado = true` y
verificar que aparece "Ya he pagado" y, al pulsarlo, la info. Seguir el patrón del
fichero para el `flush` de `GET /eventos/{id}`. Mínimo:

```typescript
  it('con el pago confirmado muestra "Ya he pagado" y su info', () => {
    // ... flush del detalle con:
    //   asistencia.ficha = { llevaFicha: true, diasEvento: ['2026-09-25'], miFicha: {
    //     ...campos de bebida..., cuota: 26, cuotaPendiente: false, bebidaPendiente: false,
    //     pagado: true, metodoPago: 'BIZUM', pagadoPor: 'Jefe', pagadoAt: '2026-09-07T10:00:00Z' } }
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ya he pagado');
    expect(texto).not.toContain('Confirmar el pago');

    const btn = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Ya he pagado')!;
    btn.click();
    fixture.detectChanges();
    const t2 = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(t2).toContain('Jefe');
    expect(t2).toContain('Bizum');
  });
```

(Revisar el fichero para reproducir su helper de `flush` del detalle; añadir los
campos nuevos de `miFicha` a cualquier fixture existente para que compile.)

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npm test -- --watch=false --include='**/evento-detalle.spec.ts'`
Expected: FALLO.

- [ ] **Step 3: Componente**

En `evento-detalle.ts`:

```typescript
  protected readonly modalPagoInfo = signal(false);

  protected readonly pagoConfirmado = computed(
    () => !!this.evento()?.asistencia.ficha.miFicha?.pagado,
  );

  protected readonly metodoPagoTexto = computed(() => {
    const m = this.evento()?.asistencia.ficha.miFicha?.metodoPago;
    return m === 'BIZUM' ? 'Bizum' : m === 'TRANSFERENCIA' ? 'Transferencia' : m === 'EFECTIVO' ? 'Efectivo' : '';
  });
```

- [ ] **Step 4: Plantilla**

En `evento-detalle.html`, sustituir el botón "Confirmar el pago" (líneas ~128-136):

```html
              @if (e.asistencia.ficha.miFicha?.cuota != null) {
                @if (pagoConfirmado()) {
                  <button
                    type="button"
                    (click)="modalPagoInfo.set(true)"
                    class="rounded-xl bg-brand px-4 py-3 text-center text-sm font-bold text-gold transition hover:bg-brand-dark"
                  >
                    Ya he pagado
                  </button>
                } @else {
                  <button
                    type="button"
                    (click)="modalPago.set(true)"
                    class="rounded-xl bg-brand px-4 py-3 text-center text-sm font-bold text-gold transition hover:bg-brand-dark"
                  >
                    Confirmar el pago
                  </button>
                }
              }
```

Y junto a los otros `@if (modal...)` (líneas ~275-281), el modal informativo:

```html
        @if (modalPagoInfo() && e.asistencia.ficha.miFicha; as f) {
          <div
            class="fixed inset-0 z-50 flex items-center justify-center bg-surface/90 p-4 backdrop-blur-sm"
            role="dialog"
            aria-label="Pago confirmado"
          >
            <div class="carta-relieve w-full max-w-sm rounded-2xl p-6">
              <h3 class="font-display text-lg font-extrabold">Tu pago está confirmado</h3>
              <p class="mt-3 text-sm">
                Lo confirmó <strong>{{ f.pagadoPor }}</strong>
                @if (f.pagadoAt) {
                  el {{ f.pagadoAt | date: 'dd/MM/yyyy' }} a las {{ f.pagadoAt | date: 'HH:mm' }}
                }.
              </p>
              <p class="mt-2 text-sm">Has pagado por <strong>{{ metodoPagoTexto() }}</strong>.</p>
              <button
                type="button"
                (click)="modalPagoInfo.set(false)"
                class="mt-5 rounded-xl bg-brand px-4 py-2 text-sm font-bold text-gold transition hover:bg-brand-dark"
              >
                Cerrar
              </button>
            </div>
          </div>
        }
```

`DatePipe` ya está en `imports` del componente (línea 23).

- [ ] **Step 5: Ejecutar los tests del detalle**

Run: `cd front && npm test -- --watch=false --include='**/evento-detalle.spec.ts'`
Expected: PASA.

- [ ] **Step 6: Suite de front completa**

Run: `cd front && npm test -- --watch=false`
Expected: PASA. Arreglar cualquier fixture de otro spec que instancie
`AsistenteFila` / `FichaBebidaMia` / `ListadoAsistentes` sin los campos nuevos.

- [ ] **Step 7: Commit**

```bash
git add front/src/app/panel/eventos/evento-detalle
git commit -m "feat(cuentas): el peñista ve su pago ya confirmado en el detalle del evento

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: Móvil — DTOs, código de error y repositorio

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/EventoDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoEvento.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/EventosRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/EventosRepositoryImpl.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/EventosRepositoryImplTest.kt`

**Interfaces:**
- Consumes: endpoints de Task 2.
- Produces:
  - `AsistenteFilaDto` con `asistenciaId: Long = 0`, `metodoPago: String? = null`,
    `pagadoPor: String? = null`, `pagadoAt: String? = null`.
  - `ListadoAsistentesDto` con `puedoConfirmarPagos: Boolean = false`.
  - DTO de ficha en el detalle (`MiFicha`) con `pagado: Boolean = false`,
    `metodoPago: String? = null`, `pagadoPor: String? = null`, `pagadoAt: String? = null`.
  - `data class ConfirmarPagoBody(val metodo: String)`.
  - `CodigoErrorEvento.FICHA_SIN_CUOTA`.
  - `EventosRepository.confirmarPago(eventoId: Long, asistenciaId: Long, metodo: String): ResultadoEvento<ListadoAsistentesDto>`.
  - `EventosRepository.deshacerPago(eventoId: Long, asistenciaId: Long): ResultadoEvento<ListadoAsistentesDto>`.

- [ ] **Step 1: Tests que fallan**

En `EventosRepositoryImplTest.kt` añadir:

```kotlin
    @Test
    fun confirmarPago_hace_put_con_el_metodo_en_el_cuerpo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """
            {"asistentes":[],"totalCuotas":0.0,"totalPagado":0.0,"miCuota":null,
             "puedoPagarPor":[],"puedoConfirmarPagos":true}
        """.trimIndent())
        val res = r.confirmarPago(3, 7, "BIZUM")
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("PUT", vistas[0].metodo)
        assertEquals("/api/v1/eventos/3/asistencias/7/pago", vistas[0].path)
        assertTrue(vistas[0].cuerpo.contains("\"metodo\":\"BIZUM\""), vistas[0].cuerpo)
    }

    @Test
    fun deshacerPago_hace_delete() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """
            {"asistentes":[],"totalCuotas":0.0,"totalPagado":0.0,"miCuota":null,
             "puedoPagarPor":[],"puedoConfirmarPagos":true}
        """.trimIndent())
        val res = r.deshacerPago(3, 7)
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("DELETE", vistas[0].metodo)
        assertEquals("/api/v1/eventos/3/asistencias/7/pago", vistas[0].path)
    }

    @Test
    fun confirmarPago_ficha_sin_cuota_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict,
            cuerpoRespuesta = """{"codigo":"FICHA_SIN_CUOTA"}""")
        val res = r.confirmarPago(3, 7, "EFECTIVO")
        assertIs<ResultadoEvento.Error>(res)
        assertEquals(CodigoErrorEvento.FICHA_SIN_CUOTA, res.codigo)
    }
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd mobile && ./gradlew :shared:allTests --tests 'com.baniterio.app.data.EventosRepositoryImplTest'`
Expected: FALLO de compilación.

- [ ] **Step 3: DTOs**

En `EventoDtos.kt`:

`AsistenteFilaDto` (línea ~150):

```kotlin
@Serializable
data class AsistenteFilaDto(
    val nombre: String,
    val estado: String,
    val esManual: Boolean,
    val bebida: BebidaFilaDto? = null,
    val cuota: Double? = null,
    val pagado: Boolean = false,
    val asistenciaId: Long = 0,
    val metodoPago: String? = null,
    val pagadoPor: String? = null,
    val pagadoAt: String? = null,
)
```

`ListadoAsistentesDto` (línea ~171) añadir `val puedoConfirmarPagos: Boolean = false,`.

El DTO de ficha del detalle (el record de líneas ~50-65, comprobar su nombre —
probablemente `MiFichaDto` o anidado en `FichaBebidaBloqueDto`): añadir al final

```kotlin
    val pagado: Boolean = false,
    val metodoPago: String? = null,
    val pagadoPor: String? = null,
    val pagadoAt: String? = null,
```

Al final del fichero, junto a los otros `*Body`:

```kotlin
/** Cuerpo de `PUT /eventos/{id}/asistencias/{asistenciaId}/pago`. */
@Serializable
data class ConfirmarPagoBody(val metodo: String)
```

- [ ] **Step 4: Código de error**

En `ResultadoEvento.kt`: añadir `FICHA_SIN_CUOTA,` al enum, la rama
`"FICHA_SIN_CUOTA" -> FICHA_SIN_CUOTA` en `deCodigoBackend`, y el texto en
`mensaje`:

```kotlin
            FICHA_SIN_CUOTA -> "Esta ficha todavía no tiene cuota que pagar."
```

- [ ] **Step 5: Repositorio**

En `EventosRepository.kt`, tras `asistentes` (línea ~26):

```kotlin
    /** Un administrador confirma el pago de una asistencia (pieza 5). */
    suspend fun confirmarPago(
        eventoId: Long,
        asistenciaId: Long,
        metodo: String,
    ): ResultadoEvento<ListadoAsistentesDto>

    /** Deshace la confirmación de pago de una asistencia. */
    suspend fun deshacerPago(
        eventoId: Long,
        asistenciaId: Long,
    ): ResultadoEvento<ListadoAsistentesDto>
```

En `EventosRepositoryImpl.kt`, tras `asistentes` (línea ~80), e importando
`com.baniterio.app.data.dto.ConfirmarPagoBody`:

```kotlin
    override suspend fun confirmarPago(
        eventoId: Long,
        asistenciaId: Long,
        metodo: String,
    ): ResultadoEvento<ListadoAsistentesDto> = peticion {
        http.put("$API_BASE_URL/eventos/$eventoId/asistencias/$asistenciaId/pago") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ConfirmarPagoBody(metodo))
        }.body()
    }

    override suspend fun deshacerPago(
        eventoId: Long,
        asistenciaId: Long,
    ): ResultadoEvento<ListadoAsistentesDto> = peticion {
        http.delete("$API_BASE_URL/eventos/$eventoId/asistencias/$asistenciaId/pago") { auth() }.body()
    }
```

- [ ] **Step 6: Ejecutar y ver que pasa**

Run: `cd mobile && ./gradlew :shared:allTests --tests 'com.baniterio.app.data.EventosRepositoryImplTest'`
Expected: PASA.

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/EventosRepositoryImplTest.kt
git commit -m "feat(cuentas): repositorio móvil para confirmar/deshacer pago

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: Móvil — `ListadoAsistentesDialog` con confirmación de pago

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/ListadoAsistentesDialog.kt`

**Interfaces:**
- Consumes: `EventosRepository.confirmarPago`, `.deshacerPago` (Task 7);
  `AsistenteFilaDto.asistenciaId`, `.pagado`, `.metodoPago`;
  `ListadoAsistentesDto.puedoConfirmarPagos`.
- Produces: (UI interna).

- [ ] **Step 1: Reescribir el diálogo**

En `ListadoAsistentesDialog.kt`. Añadir helper de método legible y estado local
para la fila que se está confirmando; usar `rememberCoroutineScope` para llamar al
repo. Cambios clave:

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.launch

private fun metodoLegible(m: String?) = when (m) {
    "BIZUM" -> "Bizum"
    "TRANSFERENCIA" -> "Transferencia"
    "EFECTIVO" -> "Efectivo"
    else -> m ?: ""
}

private val METODOS_PAGO = listOf("BIZUM" to "Bizum", "TRANSFERENCIA" to "Transferencia", "EFECTIVO" to "Efectivo")
```

Dentro del composable, junto a `datos`/`error`:

```kotlin
    val scope = rememberCoroutineScope()
    var confirmando by remember { mutableStateOf<AsistenteFilaDto?>(null) }
    var metodoElegido by remember { mutableStateOf("BIZUM") }
    var guardando by remember { mutableStateOf(false) }
```

En el `else ->` que recorre `d.asistentes.forEach { a -> Column { ... } }`, tras
los tres `Text`, añadir:

```kotlin
                                if (d.puedoConfirmarPagos && a.cuota != null) {
                                    if (!a.pagado) {
                                        TextButton(onClick = {
                                            metodoElegido = "BIZUM"
                                            confirmando = a
                                        }) { Text("Confirmar el pago") }
                                    } else {
                                        TextButton(onClick = {
                                            scope.launch {
                                                (repo.deshacerPago(eventoId, a.asistenciaId)
                                                    as? ResultadoEvento.Exito)?.let { datos = it.dato }
                                            }
                                        }) { Text("deshacer") }
                                    }
                                }
```

Y en la línea del `Text` de cuota/estado, añadir el método cuando esté pagado:

```kotlin
                                Text(
                                    (a.cuota?.let { "${formatoImporte(it)} €" } ?: "sin cuota") +
                                        " · " + (if (a.pagado) "pagado" else "pendiente") +
                                        (if (a.pagado && a.metodoPago != null) " · ${metodoLegible(a.metodoPago)}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
```

Después del `AlertDialog` principal, un segundo `AlertDialog` para elegir método
(solo cuando `confirmando != null`):

```kotlin
    confirmando?.let { fila ->
        AlertDialog(
            onDismissRequest = { confirmando = null },
            confirmButton = {
                TextButton(
                    enabled = !guardando,
                    onClick = {
                        guardando = true
                        scope.launch {
                            when (val r = repo.confirmarPago(eventoId, fila.asistenciaId, metodoElegido)) {
                                is ResultadoEvento.Exito -> { datos = r.dato; confirmando = null }
                                is ResultadoEvento.Error -> { /* deja el diálogo abierto */ }
                            }
                            guardando = false
                        }
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmando = null }) { Text("Cancelar") } },
            title = { Text("Confirmar el pago de ${fila.nombre}") },
            text = {
                @OptIn(ExperimentalMaterial3Api::class)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    METODOS_PAGO.forEachIndexed { i, (valor, etiqueta) ->
                        SegmentedButton(
                            selected = metodoElegido == valor,
                            onClick = { metodoElegido = valor },
                            shape = SegmentedButtonDefaults.itemShape(i, METODOS_PAGO.size),
                        ) { Text(etiqueta) }
                    }
                }
            },
        )
    }
```

- [ ] **Step 2: Compilar el módulo shared**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: compila sin errores.

- [ ] **Step 3: Suite de shared**

Run: `cd mobile && ./gradlew :shared:allTests`
Expected: PASA.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/ListadoAsistentesDialog.kt
git commit -m "feat(cuentas): confirmar/deshacer pago en el listado de asistentes (móvil)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 9: Móvil — "Ya he pagado" en `EventoDetalleScreen`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt`

**Interfaces:**
- Consumes: campos de pago en `ev.asistencia.ficha.miFicha` (Task 7).
- Produces: (UI interna).

- [ ] **Step 1: Cambiar el botón y añadir el diálogo**

En `EventoDetalleScreen.kt`. Junto a `var verPago by remember { mutableStateOf(false) }`
(línea ~106) añadir `var verPagoInfo by remember { mutableStateOf(false) }`.

Helper arriba del fichero:

```kotlin
private fun metodoLegible(m: String?) = when (m) {
    "BIZUM" -> "Bizum"
    "TRANSFERENCIA" -> "Transferencia"
    "EFECTIVO" -> "Efectivo"
    else -> m ?: ""
}
```

En el `Row` de botones (líneas ~238-244), sustituir el botón "Confirmar el pago":

```kotlin
                            ev.asistencia.ficha.miFicha?.let { f ->
                                if (f.cuota != null) {
                                    Button(
                                        onClick = { if (f.pagado) verPagoInfo = true else verPago = true },
                                        colors = botonPeña,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            if (f.pagado) "Ya he pagado" else "Confirmar el pago",
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
```

Tras el bloque `if (verPago) { HePagadoDialog(...) }` (línea ~253-264):

```kotlin
                    if (verPagoInfo) {
                        ev.asistencia.ficha.miFicha?.let { f ->
                            AlertDialog(
                                onDismissRequest = { verPagoInfo = false },
                                confirmButton = {
                                    TextButton(onClick = { verPagoInfo = false }) { Text("Cerrar") }
                                },
                                title = { Text("Tu pago está confirmado") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "Lo confirmó ${f.pagadoPor ?: "un administrador"}" +
                                                (f.pagadoAt?.let { " el ${formatoFecha(it.take(10))}" } ?: "") + ".",
                                        )
                                        Text("Has pagado por ${metodoLegible(f.metodoPago)}.")
                                    }
                                },
                            )
                        }
                    }
```

Imports a añadir: `androidx.compose.material3.AlertDialog`,
`androidx.compose.material3.TextButton`.

Nota: `formatoFecha` espera `yyyy-MM-dd`; `pagadoAt` es un instante ISO
(`2026-09-07T10:00:00Z`), por eso `.take(10)` para quedarnos con la fecha. Si
`formatoFecha` no traga ese formato, mostrar `f.pagadoAt.take(10)` tal cual.

- [ ] **Step 2: Compilar shared**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: compila.

- [ ] **Step 3: Suite de shared + build de Android**

Run: `cd mobile && ./gradlew :shared:allTests :androidApp:assembleDebug`
Expected: PASA / BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt
git commit -m "feat(cuentas): el peñista ve su pago confirmado en el detalle del evento (móvil)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 10: Verificación final de las 3 suites

**Files:** (ninguno; solo ejecución)

- [ ] **Step 1: Backend**

Run: `cd back && ./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Front**

Run: `cd front && npm test -- --watch=false && npm run lint`
Expected: todo verde.

- [ ] **Step 3: Móvil**

Run: `cd mobile && ./gradlew :shared:allTests`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Repaso manual pendiente (lo hace Roberto)**

Web: abrir un evento de San Miguel como admin → Listado de asistentes → "Confirmar
el pago" en una fila → elegir método → la fila pasa a "pagado · {método}" y aparece
"deshacer". Entrar como ese peñista → el botón es "Ya he pagado" y abre la info con
nombre, fecha/hora y método. Emulador Android: mismo recorrido.

---

## Self-Review

**Spec coverage:**
- Estado `pagado` + método + autor + fecha en `ficha_bebida` (V26) → Task 1. ✓
- Enum `MetodoPago` en back → Task 1. ✓
- `PUT`/`DELETE .../asistencias/{asistenciaId}/pago`, gated por `puedeGestionar`,
  404/409 `EVENTO_SIN_FICHA`/`FICHA_SIN_CUOTA` → Task 2. ✓
- `AsistenteFila` (+`asistenciaId`, `pagado` real, `metodoPago`, `pagadoPor`,
  `pagadoAt`), `totalPagado` real, `puedoConfirmarPagos` → Task 2. ✓
- `MiFicha` con campos de pago → Task 3. ✓
- Front tipos + servicio (`FICHA_SIN_CUOTA` incluido) → Task 4. ✓
- Modal Asistentes: botón por fila con cuota sin pagar, sub-modal 3 chips, fila
  pagada oculta el botón y muestra método + "deshacer" → Task 5. ✓
- Detalle del evento: `miFicha.pagado` → botón "Ya he pagado" → modal info (autor,
  fecha/hora, método); sin pagar → botón actual abre `ModalHePagado` → Task 6. ✓
- Móvil DTOs + repo + test → Task 7. ✓
- Móvil `ListadoAsistentesDialog` → Task 8. ✓
- Móvil `EventoDetalleScreen` → Task 9. ✓
- 3 suites verdes → Task 10. ✓
- Fuera de alcance (declaración persistida, saldo, movimientos, pantalla admin,
  push) → no hay tareas, correcto.

**Placeholder scan:** los `// ...` en los tests de front (Tasks 5-6) piden
reproducir el helper de `flush` de cada spec; se deja así a propósito porque el
formato exacto depende de cómo esté montado cada fichero, que el implementador
tiene delante. El resto del código es literal.

**Type consistency:** `confirmarPago(usuarioId, eventoId, asistenciaId, metodo)` y
`deshacerPago(usuarioId, eventoId, asistenciaId)` iguales en servicio (Task 2),
controller (Task 2) e IT (Task 2). Front `confirmarPago(eventoId, asistenciaId,
metodo)` / `deshacerPago(eventoId, asistenciaId)` iguales en Tasks 4-6. Móvil
`confirmarPago(eventoId, asistenciaId, metodo)` / `deshacerPago(eventoId,
asistenciaId)` iguales en Tasks 7-9. `AsistenteFila` con el mismo orden de campos
en Task 2 (record Java) y sus DTOs en Task 4 (TS, por nombre) y Task 7 (Kotlin, por
nombre). `puedoConfirmarPagos` consistente. `metodoLegible`/`metodoTexto` mapea los
3 valores igual en front (Task 5, 6) y móvil (Task 8, 9).
