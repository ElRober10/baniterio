# Asistencia a eventos 3a — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que cada evento futuro pueda tener una convocatoria: un admin u organizador manda una notificación push con texto libre, cada persona responde Me apunto / No voy / En duda, y quien no ha respondido se encuentra una pantalla bloqueante al abrir la app.

**Architecture:** Dos tablas nuevas (`asistencia_evento`, `notificacion_evento`) en el paquete `identidad`; un `AsistenciaService` + `AsistenciaController` en `com.baniterio.api.evento`; una `Audiencia.SinRespuestaEvento` en el módulo push. `EventoService.aDetalle` delega en `AsistenciaService` para los campos nuevos de `EventoDetalle`. Web y móvil añaden los controles al detalle del evento y una pantalla bloqueante enganchada como el "perfil obligatorio".

**Tech Stack:** Java 17 / Spring Boot 4.1 / Maven / Flyway / Postgres (Testcontainers) · Angular 22 / Vitest · Kotlin Multiplatform + Compose / Ktor.

**Spec:** `docs/superpowers/specs/2026-09-03-asistencia-evento-3a-design.md`

## Global Constraints

- Backend: Java 17, Spring Boot 4.1.x, Maven. Flyway `V*__descripcion.sql`, siguientes libres **V20, V21**. Hibernate `ddl-auto: validate`, `open-in-view: false`. Peña piloto por slug `baniterio`. Errores vía `ApiExceptionHandler` con `codigo` estable.
- Web: Angular 22 standalone + signals + Tailwind. `ng test` (Vitest), `ng build --configuration production` limpio.
- Móvil: KMP + Compose. Navegación `Screen` sellado + `when` en `App.kt`. Repos devuelven `Resultado*`, nunca lanzan por HTTP esperado, relanzan `CancellationException`. `:shared:testAndroidHostTest` verde.
- Push: envío best-effort; si falla no revienta la petición.
- Decisiones tomadas: "hasta el día del evento" = `evento.fecha` (inicio); endpoints en un `AsistenciaController` nuevo; push sin deep-link; ventana de 48 h desde el último envío.
- Rama: `feature/cuentas` (continúa).
- Commits en español, cuerpo normal, terminando con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

## File Structure

**Backend (crear salvo indicado):**
- `identidad/EstadoAsistencia.java` — enum APUNTADO / NO_VOY / EN_DUDA.
- `identidad/AsistenciaEvento.java` — entidad.
- `identidad/AsistenciaEventoRepository.java`.
- `identidad/NotificacionEvento.java` — entidad.
- `identidad/NotificacionEventoRepository.java`.
- `resources/db/migration/V20__create_asistencia_evento.sql`.
- `resources/db/migration/V21__create_notificacion_evento.sql`.
- `evento/AsistenciaService.java` — toda la lógica.
- `evento/AsistenciaController.java` — endpoints `/api/v1/eventos/{id}/asistencia*`, `/notificacion`, `/pendientes-respuesta`.
- `evento/dto/ResponderAsistenciaRequest.java`, `AnadirAsistenteRequest.java`, `MandarNotificacionRequest.java`, `AsistenciaResumen.java`, `PendientesRespuestaResponse.java`, `AsistenciaDetalle.java` (el bloque que se mete en `EventoDetalle`).
- `evento/EventoYaPasadoException.java`, `NotificacionReenvioProntoException.java`, `AsistenciaNoEncontradaException.java`, `AsistenciaNoManualException.java`.
- Modificar: `evento/dto/EventoDetalle.java` (campos nuevos), `evento/EventoService.java` (`aDetalle` delega), `web/ApiExceptionHandler.java` (4 handlers), `push/Audiencia.java` (+`SinRespuestaEvento`), `push/ResolutorAudiencia.java` (+rama).
- Tests: `evento/AsistenciaIT.java`, `identidad/AsistenciaEventoRepositoryIT.java` (opcional si el IT ya cubre).

**Web (crear salvo indicado):**
- Modificar: `panel/eventos/eventos.types.ts` (tipos), `panel/eventos/eventos.service.ts` (métodos), `panel/eventos/evento-detalle/evento-detalle.ts` + `.html` (controles), `app.routes.ts` (ruta + guard).
- `panel/responder/responder.ts` + `.html` + `.css` — pantalla bloqueante.
- `panel/responder/respuesta-pendiente.guard.ts` — guard.
- Tests: `evento-detalle.spec.ts` (ampliar), `responder.spec.ts`, `respuesta-pendiente.guard.spec.ts`, `eventos.service.spec.ts` (ampliar).

**Móvil (crear salvo indicado):**
- `data/AsistenciaRepository.kt` + `data/AsistenciaRepositoryImpl.kt`.
- `data/ResultadoAsistencia.kt`.
- Modificar: `data/dto/EventoDtos.kt` (EventoDetalle + DTOs nuevos), `data/Dependencias.kt` (+`asistenciaRepo`), `nav/Screen.kt` (+`ResponderEvento`), `App.kt` (claves + gate tras `CargandoSesion`), `ui/eventos/EventoDetalleScreen.kt` (controles).
- `ui/eventos/ResponderEventoScreen.kt` — pantalla bloqueante.
- Tests: `data/AsistenciaRepositoryImplTest.kt`.

---

## Task 1: Backend — entidades, migraciones, repos

**Files:**
- Create: `back/src/main/resources/db/migration/V20__create_asistencia_evento.sql`
- Create: `back/src/main/resources/db/migration/V21__create_notificacion_evento.sql`
- Create: `back/src/main/java/com/baniterio/api/identidad/EstadoAsistencia.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/AsistenciaEvento.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/AsistenciaEventoRepository.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/NotificacionEvento.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/NotificacionEventoRepository.java`
- Test: `back/src/test/java/com/baniterio/api/identidad/AsistenciaEventoRepositoryIT.java`

**Interfaces:**
- Produces:
  - `EstadoAsistencia { APUNTADO, NO_VOY, EN_DUDA }`.
  - `AsistenciaEvento` (Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`): `Long id`, `Evento evento` (ManyToOne LAZY, not null), `Usuario usuario` (ManyToOne LAZY, nullable), `String nombre`, `EstadoAsistencia estado` (`@Enumerated(STRING)`, not null), `Usuario registradoPor` (ManyToOne LAZY, nullable), `Instant createdAt`, `Instant updatedAt`.
  - `NotificacionEvento`: `Long id`, `Evento evento` (ManyToOne LAZY, not null), `String texto`, `Usuario enviadaPor` (ManyToOne LAZY, not null), `Instant enviadaAt` (`@CreationTimestamp`).
  - `AsistenciaEventoRepository extends JpaRepository<AsistenciaEvento, Long>`:
    - `Optional<AsistenciaEvento> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId)`
    - `List<AsistenciaEvento> findByEventoId(Long eventoId)`
    - `long countByEventoIdAndEstado(Long eventoId, EstadoAsistencia estado)`
    - `boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId)`
    - `@Query("select a.usuario.id from AsistenciaEvento a where a.evento.id = :eventoId and a.usuario.id is not null") Set<Long> idsUsuariosConRespuesta(@Param("eventoId") Long eventoId)`
  - `NotificacionEventoRepository extends JpaRepository<NotificacionEvento, Long>`:
    - `Optional<NotificacionEvento> findFirstByEventoIdOrderByEnviadaAtDesc(Long eventoId)`
    - `boolean existsByEventoId(Long eventoId)`

- [ ] **Step 1: V20 migration**

```sql
-- back/src/main/resources/db/migration/V20__create_asistencia_evento.sql
-- Respuesta de una persona a un evento (Me apunto / No voy / En duda). usuario_id
-- NULL = persona sin app (invitado o añadida a mano por un admin/organizador);
-- en ese caso nombre es obligatorio (lo valida el servicio). registrado_por = el
-- admin/organizador que creó la fila; NULL si respondió la propia persona.
CREATE TABLE asistencia_evento (
    id                BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    evento_id         BIGINT       NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    usuario_id        BIGINT       REFERENCES usuario (id) ON DELETE CASCADE,
    nombre            VARCHAR(120),
    estado            VARCHAR(16)  NOT NULL,
    registrado_por_id BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_asistencia_estado CHECK (estado IN ('APUNTADO', 'NO_VOY', 'EN_DUDA'))
);

-- Una persona con app solo tiene una respuesta por evento; las añadidas a mano
-- (usuario_id NULL) no chocan entre sí.
CREATE UNIQUE INDEX uq_asistencia_evento_usuario
    ON asistencia_evento (evento_id, usuario_id) WHERE usuario_id IS NOT NULL;
CREATE INDEX ix_asistencia_evento ON asistencia_evento (evento_id);
```

- [ ] **Step 2: V21 migration**

```sql
-- back/src/main/resources/db/migration/V21__create_notificacion_evento.sql
-- Cada vez que un admin u organizador pulsa "Mandar notificación" se guarda una
-- fila. Sirve para la regla de reenvío (>= 48 h desde el último envío) y para la
-- pantalla bloqueante (hay notificación + no he respondido).
CREATE TABLE notificacion_evento (
    id              BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    evento_id       BIGINT      NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    texto           VARCHAR(500),
    enviada_por_id  BIGINT      NOT NULL REFERENCES usuario (id),
    enviada_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_notificacion_evento ON notificacion_evento (evento_id, enviada_at DESC);
```

- [ ] **Step 3: Enum + entidades + repos** — escribir los 5 ficheros Java según el bloque *Interfaces*. Mirar `identidad/Evento.java` y `identidad/SolicitudEvento.java` como patrón (Lombok, `@Entity`, `@Table`, `@JoinColumn`, `@CreationTimestamp`/`@UpdateTimestamp`).

- [ ] **Step 4: Test de repo (IT)**

```java
// AsistenciaEventoRepositoryIT — extends IntegrationTest
// - guarda una AsistenciaEvento con usuario -> findByEventoIdAndUsuarioId la encuentra
// - guarda dos con usuario_id NULL y mismo nombre -> no lanza (unicidad parcial)
// - guarda dos con el mismo (evento, usuario) -> saveAndFlush la segunda lanza
// - idsUsuariosConRespuesta devuelve solo los usuario_id no nulos
// - NotificacionEvento: findFirstByEventoIdOrderByEnviadaAtDesc devuelve la más reciente
```
Usar el patrón de `EventoRepositoryIT` (autowire repos, `penas.findBySlug("baniterio")`, crear `Evento` con `cuenta()` = `cuentas.findByPenaIdAndNombre(penaId,"San Miguel")`).

- [ ] **Step 5: Run** `./mvnw -Dsurefire.failIfNoSpecifiedTests=false -Dtest=NoneShouldRun -Dit.test=AsistenciaEventoRepositoryIT,FlywayMigrationIT verify` — Expected: PASS (Flyway aplica V20/V21).

- [ ] **Step 6: Commit** `feat(back): tablas asistencia_evento y notificacion_evento`

---

## Task 2: Backend — responder asistencia

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/AsistenciaService.java`
- Create: `back/src/main/java/com/baniterio/api/evento/AsistenciaController.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/ResponderAsistenciaRequest.java`
- Create: `back/src/main/java/com/baniterio/api/evento/EventoYaPasadoException.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Test: `back/src/test/java/com/baniterio/api/evento/AsistenciaIT.java`

**Interfaces:**
- Consumes: `AsistenciaEventoRepository`, `EventoRepository`, `UsuarioRepository`, `ServicioPermisos`, `EstadoAsistencia`.
- Produces:
  - `ResponderAsistenciaRequest(@NotNull EstadoAsistencia estado)` (record).
  - `EventoYaPasadoException extends RuntimeException` → 409 `EVENTO_YA_PASADO`.
  - `AsistenciaService.responder(Long usuarioId, Long eventoId, EstadoAsistencia estado)` — upsert de `(eventoId, usuarioId)`, `registradoPor` NULL; lanza `EventoNoEncontradoException` si no existe, `EventoYaPasadoException` si `evento.fecha < LocalDate.now()`.
  - `AsistenciaController`: `PUT /api/v1/eventos/{id}/asistencia` → llama a `responder` y devuelve `eventoService.detalle(principal.id(), id)` (200).

- [ ] **Step 1: Test IT (fallará al no existir el endpoint)**

```java
// AsistenciaIT extends IntegrationTest — helpers como EventoIT (crearMiembro, cuenta(), sembrarEvento)
@Test void responder_crea_la_fila_APUNTADO() {
    Sesion s = crearMiembro(RolMembresia.MIEMBRO);
    Evento e = sembrarEvento("IT-asis-1", LocalDate.now().plusDays(30), null);
    http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
        .header(AUTHORIZATION, "Bearer " + s.token())
        .body(Map.of("estado", "APUNTADO"))
        .exchange().expectStatus().isOk()
        .expectBody().jsonPath("$.miAsistencia").isEqualTo("APUNTADO");
}
@Test void responder_de_nuevo_actualiza_el_estado() { /* APUNTADO -> EN_DUDA, sigue 1 fila */ }
@Test void responder_evento_pasado_es_409_EVENTO_YA_PASADO() {
    // sembrarEvento con fecha = LocalDate.now().minusDays(1) -> 409
}
```
> `$.miAsistencia` lo añade la Task 5 a `EventoDetalle`. Para que esta Task compile su test, añade el campo `miAsistencia` a `EventoDetalle` **aquí** (con el resto de campos nuevos como `null`/0 provisional) o mueve la aserción de `miAsistencia` a la Task 5. **Decisión:** en esta Task el test solo comprueba `expectStatus().isOk()` y que `asistencias.findByEventoIdAndUsuarioId(...)` tiene la fila; la aserción sobre `$.miAsistencia` va en la Task 5.

- [ ] **Step 2: Run** el test — Expected: FAIL (404 / no endpoint).

- [ ] **Step 3: Implementar** `EventoYaPasadoException`, handler en `ApiExceptionHandler` (`@ExceptionHandler(EventoYaPasadoException.class)` → `error(HttpStatus.CONFLICT, "EVENTO_YA_PASADO")`), `ResponderAsistenciaRequest`, `AsistenciaService.responder`, `AsistenciaController`.

```java
// AsistenciaService.responder
@Transactional
public void responder(Long usuarioId, Long eventoId, EstadoAsistencia estado) {
    Evento e = eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
    if (e.getFecha().isBefore(LocalDate.now())) throw new EventoYaPasadoException();
    AsistenciaEvento a = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
        .orElseGet(() -> AsistenciaEvento.builder()
            .evento(e).usuario(usuarios.findById(usuarioId).orElseThrow()).build());
    a.setEstado(estado);
    asistencias.save(a);
}
```

- [ ] **Step 4: Run** — Expected: PASS. Luego `./mvnw ... -Dit.test=AsistenciaIT,EventoIT verify` para no romper Eventos.

- [ ] **Step 5: Commit** `feat(back): responder asistencia a un evento`

---

## Task 3: Backend — mandar / reenviar notificación

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/dto/MandarNotificacionRequest.java`
- Create: `back/src/main/java/com/baniterio/api/evento/NotificacionReenvioProntoException.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaService.java`, `AsistenciaController.java`, `web/ApiExceptionHandler.java`, `push/Audiencia.java`, `push/ResolutorAudiencia.java`
- Test: `back/src/test/java/com/baniterio/api/evento/AsistenciaIT.java` (+casos), `push/ResolutorAudienciaIT` si existe (ampliar)

**Interfaces:**
- Consumes: `NotificacionEventoRepository`, `ApplicationEventPublisher`, `ServicioPermisos`, Task 2.
- Produces:
  - `Audiencia.SinRespuestaEvento(Long eventoId)` implements `Audiencia`.
  - `ResolutorAudiencia.resolver`: rama `instanceof Audiencia.SinRespuestaEvento s` → miembros activos de la peña cuyo id no está en `asistenciaEventoRepository.idsUsuariosConRespuesta(s.eventoId())`.
  - `MandarNotificacionRequest(@Size(max = 500) String texto)` (record).
  - `NotificacionReenvioProntoException` → 409 `NOTIFICACION_REENVIO_PRONTO`.
  - `AsistenciaService.mandarNotificacion(Long usuarioId, Long eventoId, String texto)` — 403 si no admin ni `evento.creadoPor`; 409 `EVENTO_YA_PASADO`; 409 `NOTIFICACION_REENVIO_PRONTO` si `ultima.enviadaAt > now - 48h`; inserta `NotificacionEvento`; publica `AvisoPushEvent(new Audiencia.SinRespuestaEvento(eventoId), "Eventos", cuerpo)`.
  - `AsistenciaController`: `POST /api/v1/eventos/{id}/notificacion` → 204.

- [ ] **Step 1: Tests IT**

```java
@Test void mandar_notificacion_primera_vez_204_y_registra() {
    // admin u organizador -> 204; notificaciones.existsByEventoId(id) == true
}
@Test void reenviar_antes_de_48h_es_409() {
    // guardar NotificacionEvento con enviadaAt = now(); POST -> 409 NOTIFICACION_REENVIO_PRONTO
}
@Test void reenviar_pasadas_48h_204() {
    // guardar NotificacionEvento con enviadaAt = now().minus(49h); POST -> 204
}
@Test void mandar_notificacion_sin_permiso_403() {
    // miembro que no creó el evento -> 403 SIN_PERMISO_EVENTO
}
@Test void audiencia_sin_respuesta_excluye_a_quien_ya_respondio() {
    // en un ResolutorAudienciaIT o con el repo: usuario A responde, B no ->
    // resolver(new Audiencia.SinRespuestaEvento(id)) contiene B y no A
}
```

- [ ] **Step 2: Run** — FAIL.

- [ ] **Step 3: Implementar**. `Audiencia.SinRespuestaEvento` + rama en `ResolutorAudiencia` (inyectar `AsistenciaEventoRepository` ahí, o pasar los ids ya resueltos — preferir inyectar el repo). `AsistenciaService.mandarNotificacion`:

```java
@Transactional
public void mandarNotificacion(Long usuarioId, Long eventoId, String texto) {
    Evento e = eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new);
    boolean puede = permisos.esAdministrador(usuarioId)
        || (e.getCreadoPor() != null && e.getCreadoPor().getId().equals(usuarioId));
    if (!puede) throw new SinPermisoEventoException();
    if (e.getFecha().isBefore(LocalDate.now())) throw new EventoYaPasadoException();
    notificaciones.findFirstByEventoIdOrderByEnviadaAtDesc(eventoId).ifPresent(n -> {
        if (n.getEnviadaAt().isAfter(Instant.now().minus(48, ChronoUnit.HOURS)))
            throw new NotificacionReenvioProntoException();
    });
    String limpio = (texto == null || texto.isBlank()) ? null : texto.trim();
    notificaciones.save(NotificacionEvento.builder()
        .evento(e).texto(limpio).enviadaPor(usuarios.findById(usuarioId).orElseThrow()).build());
    String cuerpo = limpio != null ? limpio
        : "«" + e.getNombre() + "» — ¿te apuntas? Entra en la app y responde.";
    publisher.publishEvent(new AvisoPushEvent(
        new Audiencia.SinRespuestaEvento(eventoId), "Eventos", cuerpo));
}
```

- [ ] **Step 4: Run** `-Dit.test=AsistenciaIT,ResolutorAudienciaIT` — PASS.

- [ ] **Step 5: Commit** `feat(back): mandar y reenviar notificacion de evento`

---

## Task 4: Backend — añadir / quitar asistentes a mano

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/dto/AnadirAsistenteRequest.java`, `dto/AsistenciaResumen.java`
- Create: `back/src/main/java/com/baniterio/api/evento/AsistenciaNoEncontradaException.java`, `AsistenciaNoManualException.java`
- Modify: `AsistenciaService.java`, `AsistenciaController.java`, `web/ApiExceptionHandler.java`
- Test: `AsistenciaIT.java` (+casos)

**Interfaces:**
- Produces:
  - `AnadirAsistenteRequest(@NotBlank @Size(max = 120) String nombre, @NotNull EstadoAsistencia estado)`.
  - `AsistenciaResumen(Long id, String nombre, EstadoAsistencia estado, boolean esManual)`.
  - `AsistenciaNoEncontradaException` → 404 `ASISTENCIA_NO_ENCONTRADA`; `AsistenciaNoManualException` → 409 `ASISTENCIA_NO_MANUAL`.
  - `AsistenciaService.anadirAMano(Long usuarioId, Long eventoId, String nombre, EstadoAsistencia estado) : AsistenciaResumen` — 403 si no admin ni organizador; crea fila `usuario` NULL, `nombre`, `registradoPor` = usuarioId.
  - `AsistenciaService.quitarAMano(Long usuarioId, Long eventoId, Long asistenciaId)` — 403 si no admin ni organizador; 404 si no existe o no es de ese evento; 409 `ASISTENCIA_NO_MANUAL` si `usuario_id` no es NULL; borra.
  - `AsistenciaController`: `POST /api/v1/eventos/{id}/asistencias` → 201 `AsistenciaResumen`; `DELETE /api/v1/eventos/{id}/asistencias/{asistenciaId}` → 204.

- [ ] **Step 1: Tests IT**

```java
@Test void anadir_a_mano_crea_fila_sin_usuario() {
    // admin -> 201, $.nombre == "Primo de Juan", $.esManual == true
    // asistencias.findById(id).getUsuario() == null
}
@Test void anadir_a_mano_sin_permiso_403() {}
@Test void quitar_a_mano_ok_204() {}
@Test void quitar_la_respuesta_de_un_usuario_es_409_ASISTENCIA_NO_MANUAL() {
    // crear asistencia con usuario (responder) -> DELETE .../asistencias/{id} -> 409
}
@Test void quitar_inexistente_404() {}
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementar** los dos métodos + DTOs + excepciones + 2 handlers.
- [ ] **Step 4: Run** `-Dit.test=AsistenciaIT` — PASS.
- [ ] **Step 5: Commit** `feat(back): anadir y quitar asistentes a mano`

---

## Task 5: Backend — `pendientes-respuesta` + campos de `EventoDetalle`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/dto/PendientesRespuestaResponse.java`, `dto/AsistenciaDetalle.java`
- Modify: `evento/dto/EventoDetalle.java`, `evento/EventoService.java`, `evento/AsistenciaService.java`, `AsistenciaController.java`
- Test: `AsistenciaIT.java` (+casos), `EventoIT.java` (aserción de recuento)

**Interfaces:**
- Produces:
  - `AsistenciaDetalle(String miAsistencia, boolean puedeNotificar, Instant notificacionReenviableAt, int apuntados, int noVoy, int enDuda, int sinContestar)` — `miAsistencia` es el `name()` del enum o `null`.
  - `EventoDetalle` gana un campo `AsistenciaDetalle asistencia` (o se aplanan los 7 campos; **decisión: campo anidado `asistencia`** para no ensanchar el record). Ajustar `EventoService.aDetalle` para construirlo vía `asistenciaService.detalleDe(usuarioId, evento)`.
  - `AsistenciaService.detalleDe(Long usuarioId, Evento e) : AsistenciaDetalle`:
    - `miAsistencia` = `findByEventoIdAndUsuarioId(e.id, usuarioId).map(a -> a.getEstado().name()).orElse(null)`.
    - `puedeNotificar` = (admin || `e.creadoPor` == usuarioId) && `!e.getFecha().isBefore(hoy)`.
    - `notificacionReenviableAt` = `ultima.enviadaAt + 48h` o `null` si nunca.
    - recuentos por `countByEventoIdAndEstado`; `sinContestar` = nº miembros activos − (apuntados+noVoy+enDuda con usuario) ... **simplificación:** `sinContestar` = `resolutorAudiencia.resolver(new Audiencia.SinRespuestaEvento(e.id)).size()`. Reutiliza la lógica y evita duplicar el conteo.
  - `PendientesRespuestaResponse(List<EventoResumen> eventos)`.
  - `AsistenciaService.pendientesRespuesta(Long usuarioId) : List<Evento>` — eventos de la peña con `fecha >= hoy`, con `notificaciones.existsByEventoId(id)` y sin `asistencias.existsByEventoIdAndUsuarioId(id, usuarioId)`, ordenados por `fecha` asc.
  - `AsistenciaController`: `GET /api/v1/eventos/pendientes-respuesta` → `PendientesRespuestaResponse` (usa `EventoService` para mapear a `EventoResumen`).

- [ ] **Step 1: Tests**

```java
// AsistenciaIT
@Test void pendientes_respuesta_trae_eventos_con_notificacion_sin_respuesta_mia() {
    // evento A: notificación enviada, no respondo -> sale
    // evento B: notificación enviada, respondo NO_VOY -> no sale
    // evento C: sin notificación -> no sale
}
@Test void detalle_trae_recuento_y_mi_asistencia() {
    // 2 miembros responden APUNTADO, yo EN_DUDA -> GET /eventos/{id}:
    // $.asistencia.apuntados == 2, $.asistencia.enDuda == 1, $.miAsistencia == "EN_DUDA"
}
@Test void detalle_puedeNotificar_true_para_admin() {}
```
Ajustar en `EventoIT` los tests de detalle que ya existan si el JSON nuevo les molesta (no debería: campos añadidos).

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementar**. Actualizar el constructor de `EventoDetalle` en `EventoService.aDetalle` (pasa `asistenciaService.detalleDe(usuarioId, e)`). Inyectar `AsistenciaService` en `EventoService` (ojo dependencia circular: `AsistenciaService` no debe depender de `EventoService`; el mapeo a `EventoResumen` en `pendientesRespuesta` se hace en el `Controller` llamando a un método estático/helper de `EventoService`, o el controller arma la respuesta). **Decisión:** `AsistenciaController.pendientes()` recibe `EventoService` y `AsistenciaService`; pide los `Evento` a `AsistenciaService` y los mapea con un `EventoService.aResumen(e)` hecho `public static` o package-private.
- [ ] **Step 4: Run** `-Dit.test=AsistenciaIT,EventoIT,CuentaIT verify` — PASS.
- [ ] **Step 5: Commit** `feat(back): pendientes de respuesta y recuento de asistencia en el detalle`

---

## Task 6: Web — servicio, tipos y controles en el detalle del evento

**Files:**
- Modify: `front/src/app/panel/eventos/eventos.types.ts`, `eventos.service.ts`, `evento-detalle/evento-detalle.ts`, `evento-detalle/evento-detalle.html`
- Test: `front/src/app/panel/eventos/eventos.service.spec.ts`, `evento-detalle/evento-detalle.spec.ts`

**Interfaces:**
- Produces (tipos):
  - `EstadoAsistencia = 'APUNTADO' | 'NO_VOY' | 'EN_DUDA'`.
  - `EventoDetalle` gana `asistencia: { miAsistencia: EstadoAsistencia | null; puedeNotificar: boolean; notificacionReenviableAt: string | null; apuntados: number; noVoy: number; enDuda: number; sinContestar: number }`.
  - `AsistenciaResumen = { id: number; nombre: string; estado: EstadoAsistencia; esManual: boolean }`.
  - `CodigoErrorEvento` += `'EVENTO_YA_PASADO' | 'NOTIFICACION_REENVIO_PRONTO' | 'ASISTENCIA_NO_ENCONTRADA' | 'ASISTENCIA_NO_MANUAL'`.
  - `EventosService`: `responder(id, estado): Observable<EventoDetalle>`, `mandarNotificacion(id, texto?): Observable<void>`, `anadirAsistente(id, nombre, estado): Observable<AsistenciaResumen>`, `quitarAsistente(id, asistenciaId): Observable<void>`, `pendientesRespuesta(): Observable<{ eventos: EventoResumen[] }>`.

- [ ] **Step 1: Tests de servicio** — un caso por método (URL + verbo + cuerpo), patrón de `eventos.service.spec.ts` actual.
- [ ] **Step 2: Run** — FAIL. **Step 3: Implementar** service + tipos. **Step 4: Run** — PASS.
- [ ] **Step 5: Tests de `evento-detalle`**:

```
- pinta 3 botones (Me apunto / No voy / En duda) y marca el actual segun asistencia.miAsistencia
- al pulsar "No voy" hace PUT /eventos/5/asistencia { estado: 'NO_VOY' } y refresca
- con asistencia.puedeNotificar: aparece "Mandar notificación"; al enviar con texto hace POST .../notificacion
- notificacionReenviableAt en el futuro: el boton "Mandar notificación" se ve como "Reenviar disponible el ..." deshabilitado
- bloque "Añadir a mano": nombre + estado -> POST .../asistencias
- muestra el recuento "{apuntados} apuntados · {enDuda} en duda · {sinContestar} sin contestar"
```

- [ ] **Step 6: Implementar** `evento-detalle.ts` (signals: `enviandoNotif`, `dialogoNotif`, `textoNotif`, `nombreManual`, `estadoManual`) + `.html`. Reutilizar estilos de botones de `eventos.html`.
- [ ] **Step 7: Run** `ng test` + `ng build --configuration production` — PASS / limpio.
- [ ] **Step 8: Commit** `feat(front): RSVP y notificacion en el detalle del evento`

---

## Task 7: Web — pantalla bloqueante + guard

**Files:**
- Create: `front/src/app/panel/responder/responder.ts`, `responder.html`, `responder.css`, `respuesta-pendiente.guard.ts`
- Modify: `front/src/app/app.routes.ts`
- Test: `front/src/app/panel/responder/responder.spec.ts`, `respuesta-pendiente.guard.spec.ts`

**Interfaces:**
- Consumes: `EventosService.pendientesRespuesta`, `EventosService.responder`.
- Produces:
  - `respuestaPendienteGuard: CanActivateFn` — llama a `pendientesRespuesta()`; si `eventos.length > 0` → `router.parseUrl('/panel/responder')`; si no → `true`. Se aplica a las hijas de `/panel` **excepto** `responder` y `miembros/editar` (para no chocar con `perfilCompletoGuard`; el orden es: perfil primero, luego respuesta).
  - `Responder` component — carga `pendientesRespuesta()`, muestra el primer evento (nombre + fecha + texto de la última notificación si lo devolviéramos; **v1: solo nombre/fecha**) y 3 botones; al responder, recarga; cuando `eventos` queda vacío → `router.navigateByUrl('/panel')`.

- [ ] **Step 1: Test del guard** (redirige si hay pendientes, deja pasar si no). **Step 2: Run FAIL. Step 3: Implementar. Step 4: PASS.**
- [ ] **Step 5: Test del componente** (pinta el primer evento, al pulsar "Me apunto" hace PUT y avanza; sin pendientes navega a /panel).
- [ ] **Step 6: Implementar** componente + ruta `{ path: 'responder', component: Responder, canActivate: [perfilCompletoGuard] }` y añadir `respuestaPendienteGuard` a las demás hijas de `/panel`.
- [ ] **Step 7: Run** `ng test` + build — PASS.
- [ ] **Step 8: Commit** `feat(front): pantalla bloqueante de respuesta a eventos`

---

## Task 8: Móvil — repo, DTOs y controles en el detalle

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AsistenciaRepository.kt`, `AsistenciaRepositoryImpl.kt`, `ResultadoAsistencia.kt`
- Modify: `data/dto/EventoDtos.kt`, `data/Dependencias.kt`, `ui/eventos/EventoDetalleScreen.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/AsistenciaRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - DTOs `@Serializable`: `AsistenciaDetalleDto(miAsistencia: String? = null, puedeNotificar: Boolean, notificacionReenviableAt: String? = null, apuntados: Int, noVoy: Int, enDuda: Int, sinContestar: Int)`; `EventoDetalle` gana `asistencia: AsistenciaDetalleDto`. `AsistenciaResumenDto(id: Long, nombre: String, estado: String, esManual: Boolean)`. `ResponderAsistenciaBody(estado: String)`, `AnadirAsistenteBody(nombre: String, estado: String)`, `MandarNotificacionBody(texto: String? = null)`, `PendientesRespuestaDto(eventos: List<EventoResumen> = emptyList())`.
  - `ResultadoAsistencia<out T>` sellado (`Exito`/`Error`) + `CodigoErrorAsistencia` (EVENTO_YA_PASADO, NOTIFICACION_REENVIO_PRONTO, SIN_PERMISO_EVENTO, ASISTENCIA_NO_MANUAL, ASISTENCIA_NO_ENCONTRADA, SIN_CONEXION, DESCONOCIDO) con `mensaje`.
  - `AsistenciaRepository`: `suspend fun responder(eventoId, estado): ResultadoAsistencia<EventoDetalle>`; `suspend fun mandarNotificacion(eventoId, texto: String?): ResultadoAsistencia<Unit>`; `suspend fun anadir(eventoId, nombre, estado): ResultadoAsistencia<AsistenciaResumenDto>`; `suspend fun quitar(eventoId, asistenciaId): ResultadoAsistencia<Unit>`; `suspend fun pendientes(): ResultadoAsistencia<List<EventoResumen>>`.
  - `Dependencias` + `asistenciaRepo`.

- [ ] **Step 1: Test** `AsistenciaRepositoryImplTest` con `MockEngine` (patrón de `EventosRepositoryImplTest`): responder → PUT `/api/v1/eventos/1/asistencia` con Bearer y cuerpo `{"estado":"APUNTADO"}`; `mandarNotificacion` 409 `NOTIFICACION_REENVIO_PRONTO` → `ResultadoAsistencia.Error` con ese código; `pendientes` → GET `/api/v1/eventos/pendientes-respuesta`.
- [ ] **Step 2: Run FAIL. Step 3: Implementar** repo + DTOs + Dependencias. **Step 4: Run PASS.**
- [ ] **Step 5: `EventoDetalleScreen`** — 3 botones de estado (marca el actual con color brand), bloque "Mandar notificación" (si `asistencia.puedeNotificar`) con `AlertDialog` + `OutlinedTextField`, bloque "Añadir a mano" (`OutlinedTextField` nombre + 3 chips estado), texto de recuento. Sin test de UI (el proyecto no tiene tests de Compose).
- [ ] **Step 6: Run** `:shared:testAndroidHostTest` + `:androidApp:compileDebugKotlin` — PASS.
- [ ] **Step 7: Commit** `feat(movil): RSVP y notificacion en el detalle del evento`

---

## Task 9: Móvil — `ResponderEventoScreen` + gate en `App.kt`

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/ResponderEventoScreen.kt`
- Modify: `nav/Screen.kt`, `App.kt`
- Test: (ninguno de UI; el flujo se cubre en humo manual)

**Interfaces:**
- Consumes: `AsistenciaRepository.pendientes`, `AsistenciaRepository.responder`.
- Produces:
  - `Screen.ResponderEvento : Screen()`.
  - `ResponderEventoScreen(asistenciaRepo, onTerminado: () -> Unit)` — al entrar pide `pendientes()`; muestra el primero (nombre + fecha) + 3 botones grandes; al responder recarga; cuando la lista queda vacía llama `onTerminado()`. **Sin `BackHandler`** (bloqueante).
- Cableado en `App.kt`: en `CargandoSesionScreen.onPerfilCompleto`, en vez de ir directo a `Panel`, comprobar pendientes. **Decisión de implementación:** `CargandoSesion` ya carga el perfil; añadir un paso: al `onPerfilCompleto`, ir a `Screen.ResponderEvento` siempre, y que `ResponderEventoScreen` con lista vacía llame `onTerminado()` → `ir(Screen.Panel)` inmediatamente (evita duplicar la llamada de red en `App.kt`). Guardia de arranque en frío: añadir `Screen.ResponderEvento` a la lista de screens que requieren sesión.

- [ ] **Step 1: `Screen.ResponderEvento`** + claves en `App.kt` (`CLAVE_RESPONDER_EVENTO`, `aClave`, `claveAScreen`, lista de la guardia en frío).
- [ ] **Step 2: `ResponderEventoScreen`** — Compose, patrón de `HistoriaScreen`/`EventosScreen` (Column + estado sellado Cargando/Lista/Error).
- [ ] **Step 3: Cablear** `App.kt`: `is Screen.CargandoSesion -> onPerfilCompleto = { ir(Screen.ResponderEvento) }`; `is Screen.ResponderEvento -> ResponderEventoScreen(deps.asistenciaRepo, onTerminado = { ir(Screen.Panel) })`.
- [ ] **Step 4: Run** `:shared:testAndroidHostTest :androidApp:compileDebugKotlin` — PASS.
- [ ] **Step 5: Commit** `feat(movil): pantalla bloqueante de respuesta a eventos`

---

## Self-Review

**Spec coverage:**
- `asistencia_evento` / `notificacion_evento` → Task 1. ✓
- `Audiencia.SinRespuestaEvento` → Task 3. ✓
- `PUT /asistencia` → Task 2; `POST /notificacion` → Task 3; `POST/DELETE /asistencias` → Task 4; `GET /pendientes-respuesta` → Task 5. ✓
- `EventoDetalle` campos nuevos → Task 5 (back), Task 6 (web), Task 8 (móvil). ✓
- Web detalle + guard + pantalla → Tasks 6, 7. ✓
- Móvil detalle + pantalla + gate → Tasks 8, 9. ✓
- 48 h desde último envío → Task 3. ✓
- Cambiar hasta `evento.fecha` → Task 2 (`responder` valida `fecha >= hoy`). ✓
- "No voy" silencia push → cubierto por `SinRespuestaEvento` (los NO_VOY tienen fila) → Task 3. ✓
- Fuera de alcance (bebida/cuota, lista completa+pago, invitados/pareja/hijos, deep-link, campanita) → no hay tareas, correcto.

**Placeholder scan:** los tests de Tasks 6–9 están descritos en prosa con la lista de casos, no con código completo — aceptable para specs de UI Angular/Compose donde el patrón ya existe en el repo (`eventos.service.spec.ts`, `EventosRepositoryImplTest.kt`); el implementador copia el patrón. Backend (Tasks 1–5) sí llevan el código clave.

**Type consistency:** `EstadoAsistencia` (enum back) ↔ `'APUNTADO'|'NO_VOY'|'EN_DUDA'` (web) ↔ `String` (móvil). `AsistenciaDetalle`/`AsistenciaDetalleDto`/`asistencia:{...}` — mismo juego de 7 campos en las tres capas. `miAsistencia` siempre nullable string. OK.
