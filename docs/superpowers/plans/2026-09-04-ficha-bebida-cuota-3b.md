# Ficha de bebida + cuota (3b) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que en los eventos de San Miguel, al responder Me apunto / En duda, se pida la ficha de bebida y se calcule y muestre la cuota del peñista; las listas de bebida viven en BBDD con aprobación de admin.

**Architecture:** Catálogo `bebida` (V22) + flag `cuenta.lleva_ficha_bebida` + tabla `ficha_bebida` 1:1 con `asistencia_evento` (V23). `CalculadoraCuota` pura para modalidad+importe. `FichaBebidaService` orquesta (upsert asistencia vía repo, resolver "Otra", calcular, guardar). `BebidaService` para catálogo y aprobación. `EventoService.editar` recalcula cuotas al cambiar `cuota_maxima`. Web y móvil añaden un formulario reutilizable y una pantalla de admin.

**Tech Stack:** Java 17 / Spring Boot 4.1 / Maven / Flyway / Postgres (Testcontainers) · Angular 22 / Vitest · Kotlin Multiplatform + Compose / Ktor.

**Spec:** `docs/superpowers/specs/2026-09-04-ficha-bebida-cuota-3b-design.md`

## Global Constraints

- Backend: Java 17, Spring Boot 4.1.x, Maven. Flyway `V*__descripcion.sql`, siguientes libres **V22, V23**. Hibernate `ddl-auto: validate`, `open-in-view: false`. Peña piloto slug `baniterio`. Errores vía `ApiExceptionHandler` con `codigo` estable.
- Web: Angular 22 standalone + signals + Tailwind. `ng test` (Vitest), `ng build --configuration production` limpio.
- Móvil: KMP + Compose. Navegación `Screen` sellado + `when` en `App.kt`. Repos devuelven `Resultado*`, nunca lanzan por HTTP esperado, relanzan `CancellationException`. `:shared:testAndroidHostTest` verde. Diagnósticos del IDE del módulo móvil rotos (metadata Kotlin) — fiarse de gradle.
- Push: best-effort, no revienta la petición. `AvisoPushEvent(Audiencia, titulo, cuerpo)` publicado con `ApplicationEventPublisher`, consumido por `ManejadorAvisoPush` AFTER_COMMIT. `Audiencia.Administradores` ya existe.
- Reglas de cuota (spec): `M=evento.cuotaMaxima`. Precedencia: embarazada→5 · va exactamente 1 de 2 días→`M/2+1` · (alcohol="No bebo alcohol" y alternativa∈{CERVEZA,CERVEZA_ESPECIAL,TINTO_VERANO})→`max(M-10,0)` · resto→`M`. `M null`→cuota null.
- Modalidad: `EMBARAZADA`/`UN_DIA`/`SOLO_CERVEZA`/`COMPLETA`, misma precedencia.
- Rama: `feature/cuentas` (continúa). Commits en español, cuerpo normal, terminando con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

## File Structure

**Backend (crear salvo indicado):**
- `resources/db/migration/V22__create_bebida.sql` — catálogo + seed + `cuenta.lleva_ficha_bebida`.
- `resources/db/migration/V23__create_ficha_bebida.sql`.
- `identidad/TipoBebida.java`, `EstadoBebida.java`, `Alternativa.java`, `Modalidad.java` — enums.
- `identidad/Bebida.java`, `BebidaRepository.java`.
- `identidad/FichaBebida.java`, `FichaBebidaRepository.java`.
- Modify `identidad/Cuenta.java` (+`llevaFichaBebida`).
- `evento/CalculadoraCuota.java` — lógica pura.
- `evento/BebidaService.java`, `evento/BebidaController.java`.
- `evento/FichaBebidaService.java`; `FichaBebidaController` (endpoints `/eventos/{id}/ficha-bebida`) — puede ir en `AsistenciaController` o nuevo; **decisión: nuevo `FichaBebidaController`**.
- `evento/dto/` — `CatalogoBebidas.java`, `BebidaRef.java`, `FichaBebidaRequest.java`, `FichaBebidaResponse.java`, `FichaBebidaDetalle.java`, `BebidaPendiente.java`.
- `evento/EventoSinFichaException.java`, `evento/BebidaNoEncontradaException.java`.
- Modify `evento/dto/AsistenciaDetalle.java` (+`ficha`), `evento/dto/AsistenciaResumen.java` (+`cuota`,`modalidad`), `evento/dto/AnadirAsistenteRequest.java` (+`ficha`).
- Modify `evento/AsistenciaService.java` (`anadirAMano` acepta ficha), `evento/EventoService.java` (`aDetalle` pasa ficha; `editar` recalcula), `web/ApiExceptionHandler.java` (2 handlers).
- Tests: `evento/CalculadoraCuotaTest.java`, `evento/FichaBebidaIT.java`, `evento/BebidaIT.java`.

**Web (crear salvo indicado):**
- Modify `panel/eventos/eventos.types.ts`, `eventos.service.ts`, `evento-detalle/evento-detalle.ts`+`.html`, `responder/responder.ts`+`.html`, `admin/indice/indice.*`, `app.routes.ts`.
- `panel/eventos/ficha-bebida/ficha-bebida.ts`+`.html` — formulario reutilizable.
- `panel/admin/bebidas/bebidas.ts`+`.html` — aprobación.
- Tests: `eventos.service.spec.ts`, `ficha-bebida.spec.ts`, `evento-detalle.spec.ts`, `bebidas.spec.ts`.

**Móvil (crear salvo indicado):**
- `data/dto/BebidaDtos.kt`; modify `data/dto/EventoDtos.kt`.
- `data/BebidaRepository.kt`+`Impl`, `data/ResultadoBebida.kt`.
- Modify `data/AsistenciaRepository.kt`+`Impl` (+`guardarFicha`, `anadir` con ficha), `data/Dependencias.kt`.
- `ui/eventos/FichaBebidaForm.kt`.
- Modify `ui/eventos/EventoDetalleScreen.kt`, `ui/eventos/ResponderEventoScreen.kt`, `nav/Screen.kt`, `App.kt`, `ui/admin/AdminIndexScreen.kt`.
- `ui/admin/AdminBebidasScreen.kt`.
- Tests: `data/BebidaRepositoryImplTest.kt`, `data/AsistenciaRepositoryImplTest.kt` (+guardarFicha).

---

## Task 1: Backend — enums, tablas, entidades, repos

**Files:**
- Create: `back/src/main/resources/db/migration/V22__create_bebida.sql`, `V23__create_ficha_bebida.sql`
- Create: `back/src/main/java/com/baniterio/api/identidad/{TipoBebida,EstadoBebida,Alternativa,Modalidad,Bebida,BebidaRepository,FichaBebida,FichaBebidaRepository}.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/Cuenta.java`
- Test: `back/src/test/java/com/baniterio/api/identidad/BebidaRepositoryIT.java`

**Interfaces:**
- Produces:
  - `enum TipoBebida { ALCOHOL, REFRESCO }`; `enum EstadoBebida { ACEPTADA, PENDIENTE, RECHAZADA }`; `enum Alternativa { CERVEZA, TINTO_VERANO, NADA, CERVEZA_ESPECIAL }`; `enum Modalidad { COMPLETA, SOLO_CERVEZA, UN_DIA, EMBARAZADA }`.
  - `Bebida` (Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`): `Long id`, `TipoBebida tipo` (`@Enumerated(STRING)`, not null), `String nombre`, `EstadoBebida estado` (`@Enumerated(STRING)`, not null), `Usuario propuestaPor` (`@ManyToOne LAZY`, nullable), `Instant createdAt` (`@CreationTimestamp`), `Instant updatedAt` (`@UpdateTimestamp`).
  - `FichaBebida`: `@Id Long asistenciaId`; `@OneToOne @MapsId @JoinColumn(name="asistencia_id") AsistenciaEvento asistencia`; `Bebida alcohol` (`@ManyToOne LAZY`, nullable, `@JoinColumn(name="alcohol_bebida_id")`); `Bebida refresco` (`@ManyToOne LAZY`, not null, `@JoinColumn(name="refresco_bebida_id")`); `Alternativa alternativa` (`@Enumerated(STRING)`, not null); `String cervezaEspecial`; `boolean embarazada`; `boolean asisteDia1`; `boolean asisteDia2`; `Modalidad modalidad` (`@Enumerated(STRING)`, not null); `BigDecimal cuota`; timestamps.
  - `BebidaRepository extends JpaRepository<Bebida, Long>`:
    - `List<Bebida> findByTipoAndEstadoOrderByNombreAsc(TipoBebida tipo, EstadoBebida estado)`
    - `List<Bebida> findByEstadoOrderByCreatedAtAsc(EstadoBebida estado)`
    - `Optional<Bebida> findByTipoAndNombreIgnoreCase(TipoBebida tipo, String nombre)`
  - `FichaBebidaRepository extends JpaRepository<FichaBebida, Long>`:
    - `Optional<FichaBebida> findByAsistenciaId(Long asistenciaId)`
    - `@Query("select f from FichaBebida f where f.asistencia.evento.id = :eventoId") List<FichaBebida> findByEventoId(@Param("eventoId") Long eventoId)`
  - `Cuenta` gana `@Column(name="lleva_ficha_bebida", nullable=false) private boolean llevaFichaBebida;`

- [ ] **Step 1: V22**

```sql
-- back/src/main/resources/db/migration/V22__create_bebida.sql
-- Catálogo de bebidas para la ficha de San Miguel. "Otra…" en la ficha crea una
-- fila PENDIENTE; un admin la acepta (sale en los desplegables) o la rechaza.
CREATE TABLE bebida (
    id               BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tipo             VARCHAR(16)  NOT NULL,
    nombre           VARCHAR(80)  NOT NULL,
    estado           VARCHAR(16)  NOT NULL DEFAULT 'PENDIENTE',
    propuesta_por_id BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_bebida_tipo   CHECK (tipo IN ('ALCOHOL', 'REFRESCO')),
    CONSTRAINT ck_bebida_estado CHECK (estado IN ('ACEPTADA', 'PENDIENTE', 'RECHAZADA'))
);
CREATE UNIQUE INDEX uq_bebida_tipo_nombre ON bebida (tipo, lower(nombre));

INSERT INTO bebida (tipo, nombre, estado) VALUES
  ('ALCOHOL','Barceló','ACEPTADA'), ('ALCOHOL','White Label','ACEPTADA'),
  ('ALCOHOL','Brugal','ACEPTADA'), ('ALCOHOL','JB','ACEPTADA'),
  ('ALCOHOL','Legendario','ACEPTADA'), ('ALCOHOL','Puerto de Indias','ACEPTADA'),
  ('ALCOHOL','Four Roses','ACEPTADA'), ('ALCOHOL','Negruita','ACEPTADA'),
  ('ALCOHOL','Red Label','ACEPTADA'), ('ALCOHOL','Beefeater','ACEPTADA'),
  ('ALCOHOL','Malibú','ACEPTADA'), ('ALCOHOL','Tanqueray','ACEPTADA'),
  ('ALCOHOL','Negrita','ACEPTADA'), ('ALCOHOL','Ballantines','ACEPTADA'),
  ('ALCOHOL','Absolut','ACEPTADA'), ('ALCOHOL','Seagram''s','ACEPTADA'),
  ('ALCOHOL','DYC','ACEPTADA'), ('ALCOHOL','Larios','ACEPTADA'),
  ('REFRESCO','Coca-Cola','ACEPTADA'), ('REFRESCO','Coca-Cola Zero','ACEPTADA'),
  ('REFRESCO','Coca-Cola Light','ACEPTADA'), ('REFRESCO','Fanta Naranja','ACEPTADA'),
  ('REFRESCO','Fanta Limón','ACEPTADA'), ('REFRESCO','Schweppes Limón','ACEPTADA'),
  ('REFRESCO','Sprite','ACEPTADA'), ('REFRESCO','Sprite Zero','ACEPTADA'),
  ('REFRESCO','Nestea','ACEPTADA'), ('REFRESCO','Tónica','ACEPTADA'),
  ('REFRESCO','Trina Naranja','ACEPTADA'), ('REFRESCO','Aquarius','ACEPTADA');

ALTER TABLE cuenta ADD COLUMN lleva_ficha_bebida BOOLEAN NOT NULL DEFAULT false;
UPDATE cuenta SET lleva_ficha_bebida = true
 WHERE nombre = 'San Miguel'
   AND pena_id = (SELECT id FROM pena WHERE slug = 'baniterio');
```

- [ ] **Step 2: V23**

```sql
-- back/src/main/resources/db/migration/V23__create_ficha_bebida.sql
-- Una ficha por asistencia (solo eventos de San Miguel). modalidad y cuota las
-- calcula el servicio; cuota es NULL si el evento aún no tiene cuota máxima.
CREATE TABLE ficha_bebida (
    asistencia_id      BIGINT       PRIMARY KEY REFERENCES asistencia_evento (id) ON DELETE CASCADE,
    alcohol_bebida_id  BIGINT       REFERENCES bebida (id),
    refresco_bebida_id BIGINT       NOT NULL REFERENCES bebida (id),
    alternativa        VARCHAR(20)  NOT NULL DEFAULT 'NADA',
    cerveza_especial   VARCHAR(80),
    embarazada         BOOLEAN      NOT NULL DEFAULT false,
    asiste_dia_1       BOOLEAN      NOT NULL DEFAULT true,
    asiste_dia_2       BOOLEAN      NOT NULL DEFAULT true,
    modalidad          VARCHAR(16)  NOT NULL,
    cuota              NUMERIC(7,2),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ficha_alternativa CHECK (alternativa IN ('CERVEZA','TINTO_VERANO','NADA','CERVEZA_ESPECIAL')),
    CONSTRAINT ck_ficha_modalidad   CHECK (modalidad IN ('COMPLETA','SOLO_CERVEZA','UN_DIA','EMBARAZADA')),
    CONSTRAINT ck_ficha_algun_dia   CHECK (asiste_dia_1 OR asiste_dia_2)
);
```

- [ ] **Step 3: Enums + entidades + repos + `Cuenta.llevaFichaBebida`** — mirar `identidad/Bebida`... usar `AsistenciaEvento.java` y `Cuenta.java` como patrón. `FichaBebida` usa `@MapsId` (la PK es la FK a asistencia).

- [ ] **Step 4: Test de repo (IT)**

```java
// BebidaRepositoryIT extends IntegrationTest
// - el seed dejó 18 ALCOHOL + 12 REFRESCO ACEPTADAS: findByTipoAndEstadoOrderByNombreAsc(ALCOHOL, ACEPTADA).size() == 18, orden por nombre
// - findByTipoAndNombreIgnoreCase(ALCOHOL, "barceló") encuentra "Barceló"
// - guardar una PENDIENTE y findByEstadoOrderByCreatedAtAsc(PENDIENTE) la trae
// - la cuenta "San Miguel" tiene llevaFichaBebida == true; "Chuletas Santas" == false
// - guardar AsistenciaEvento + FichaBebida (con @MapsId) y findByAsistenciaId la encuentra
// @AfterEach borra las bebidas PENDIENTE creadas y las asistencias/eventos "IT-ficha-repo-"
```

- [ ] **Step 5: Run** `./mvnw -Dsurefire.failIfNoSpecifiedTests=false -Dtest=NoneShouldRun -Dit.test=BebidaRepositoryIT,FlywayMigrationIT verify` — Expected: PASS.

- [ ] **Step 6: Commit** `feat(back): catalogo de bebidas y tabla ficha_bebida`

---

## Task 2: Backend — `CalculadoraCuota` (lógica pura)

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/CalculadoraCuota.java`
- Test: `back/src/test/java/com/baniterio/api/evento/CalculadoraCuotaTest.java`

**Interfaces:**
- Consumes: `Alternativa`, `Modalidad` (Task 1).
- Produces:
  - `CalculadoraCuota` — clase con métodos `static`, sin Spring.
  - `record Resultado(Modalidad modalidad, BigDecimal cuota)`.
  - `static Resultado calcular(BigDecimal cuotaMaxima, boolean embarazada, boolean asisteDia1, boolean asisteDia2, boolean bebeAlcohol, Alternativa alternativa)`:
    - modalidad: `embarazada` → EMBARAZADA; `asisteDia1 ^ asisteDia2` → UN_DIA; `!bebeAlcohol && alternativa ∈ {CERVEZA, CERVEZA_ESPECIAL, TINTO_VERANO}` → SOLO_CERVEZA; resto → COMPLETA.
    - cuota (null si `cuotaMaxima == null`): EMBARAZADA → `5`; UN_DIA → `cuotaMaxima.divide(2, 2, HALF_UP).add(1)`; SOLO_CERVEZA → `cuotaMaxima.subtract(10).max(ZERO)`; COMPLETA → `cuotaMaxima`. Todas `setScale(2, HALF_UP)`.

- [ ] **Step 1: Test**

```java
class CalculadoraCuotaTest {
    private static final BigDecimal M = new BigDecimal("26");

    @Test void completa_paga_M() {
        var r = CalculadoraCuota.calcular(M, false, true, true, true, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.COMPLETA);
        assertThat(r.cuota()).isEqualByComparingTo("26.00");
    }
    @Test void solo_cerveza_paga_M_menos_10() {
        var r = CalculadoraCuota.calcular(M, false, true, true, false, Alternativa.CERVEZA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.SOLO_CERVEZA);
        assertThat(r.cuota()).isEqualByComparingTo("16.00");
    }
    @Test void un_dia_paga_M_partido_2_mas_1() {
        var r = CalculadoraCuota.calcular(M, false, true, false, true, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isEqualByComparingTo("14.00");
    }
    @Test void embarazada_paga_5_aunque_vaya_los_dos_dias() {
        var r = CalculadoraCuota.calcular(M, true, true, true, false, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.EMBARAZADA);
        assertThat(r.cuota()).isEqualByComparingTo("5.00");
    }
    @Test void embarazada_y_un_dia_sigue_siendo_5() {
        var r = CalculadoraCuota.calcular(M, true, true, false, false, Alternativa.NADA);
        assertThat(r.cuota()).isEqualByComparingTo("5.00");
    }
    @Test void solo_cerveza_y_un_dia_gana_un_dia() {
        var r = CalculadoraCuota.calcular(M, false, false, true, false, Alternativa.CERVEZA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isEqualByComparingTo("14.00");
    }
    @Test void no_bebe_nada_y_no_embarazada_paga_completa() {
        var r = CalculadoraCuota.calcular(M, false, true, true, false, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.COMPLETA);
        assertThat(r.cuota()).isEqualByComparingTo("26.00");
    }
    @Test void cuota_maxima_null_da_cuota_null_pero_modalidad_calculada() {
        var r = CalculadoraCuota.calcular(null, false, true, false, true, Alternativa.NADA);
        assertThat(r.modalidad()).isEqualTo(Modalidad.UN_DIA);
        assertThat(r.cuota()).isNull();
    }
    @Test void M_menor_que_10_no_da_cuota_negativa() {
        var r = CalculadoraCuota.calcular(new BigDecimal("8"), false, true, true, false, Alternativa.CERVEZA);
        assertThat(r.cuota()).isEqualByComparingTo("0.00");
    }
}
```

- [ ] **Step 2: Run** — FAIL (no compila).
- [ ] **Step 3: Implementar** `CalculadoraCuota`.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** `feat(back): CalculadoraCuota (modalidad e importe de la cuota)`

---

## Task 3: Backend — `BebidaService` + catálogo + endpoints de admin

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/BebidaService.java`, `BebidaController.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/{CatalogoBebidas,BebidaRef,BebidaPendiente}.java`
- Create: `back/src/main/java/com/baniterio/api/evento/BebidaNoEncontradaException.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Test: `back/src/test/java/com/baniterio/api/evento/BebidaIT.java`

**Interfaces:**
- Consumes: `BebidaRepository`, `UsuarioRepository`, `ServicioPermisos`, `ApplicationEventPublisher`, `Audiencia.Administradores`.
- Produces:
  - `record BebidaRef(Long id, String nombre)`.
  - `record CatalogoBebidas(List<BebidaRef> alcohol, List<BebidaRef> refresco)`.
  - `record BebidaPendiente(Long id, String tipo, String nombre, ProponenteBebida propuestaPor, Instant createdAt)` con `record ProponenteBebida(Long id, String nombre)`.
  - `BebidaNoEncontradaException extends RuntimeException` → 404 `BEBIDA_NO_ENCONTRADA`.
  - `BebidaService`:
    - `CatalogoBebidas catalogo()` — `findByTipoAndEstadoOrderByNombreAsc(ALCOHOL, ACEPTADA)` + idem REFRESCO, mapeados a `BebidaRef`.
    - `Bebida resolverOtra(TipoBebida tipo, String nombreRaw, Long propuestaPorId)` — normaliza (`nombreRaw.trim().replaceAll("\\s+"," ")`); `findByTipoAndNombreIgnoreCase`: si ACEPTADA → devuelve; si PENDIENTE → devuelve; si RECHAZADA → `setEstado(PENDIENTE)`, `save`, publica push, devuelve; si no existe → crea PENDIENTE con `propuestaPor`, `save`, publica push. Push: `new AvisoPushEvent(new Audiencia.Administradores(), "Bebidas", nombreUsuario + " ha propuesto «" + nombre + "» (" + (tipo==ALCOHOL?"alcohol":"refresco") + ")")`.
    - `List<BebidaPendiente> pendientes(Long usuarioId)` — exige `permisos.esAdministrador` (si no, `SinPermisoException` de `com.baniterio.api.admin`); `findByEstadoOrderByCreatedAtAsc(PENDIENTE)`.
    - `void aceptar(Long usuarioId, Long bebidaId)` — exige admin; carga o `BebidaNoEncontradaException`; `setEstado(ACEPTADA)`; `save`.
    - `void rechazar(Long usuarioId, Long bebidaId)` — exige admin; `setEstado(RECHAZADA)`; `save`; si `propuestaPor != null` push `new AvisoPushEvent(new Audiencia.UsuarioUnico(prop.getId()), "Bebidas", "Tu propuesta de bebida «" + nombre + "» no se ha aceptado.")`.
  - `BebidaController` (`/api/v1/bebidas`):
    - `GET /catalogo` → `CatalogoBebidas` (cualquier miembro).
    - `GET ?estado=PENDIENTE` → `List<BebidaPendiente>`.
    - `POST /{id}/aceptar` → 204.
    - `POST /{id}/rechazar` → 204.

- [ ] **Step 1: Tests IT**

```java
// BebidaIT extends IntegrationTest — helpers estilo AsistenciaIT (crearMiembro, http)
@Test void catalogo_trae_solo_aceptadas_ordenadas() {
    // GET /api/v1/bebidas/catalogo -> $.alcohol.length()==18, $.alcohol[0].nombre=="Absolut"
}
@Test void pendientes_sin_ser_admin_403() { /* miembro -> 403 SIN_PERMISO */ }
@Test void admin_acepta_una_bebida_y_pasa_a_salir_en_el_catalogo() {
    // sembrar bebida PENDIENTE ALCOHOL "IT-ron" ; POST /bebidas/{id}/aceptar 204 ; catalogo la incluye
}
@Test void aceptar_inexistente_404() { /* POST /bebidas/99999/aceptar -> 404 BEBIDA_NO_ENCONTRADA */ }
@Test void admin_rechaza_una_bebida() {
    // POST /bebidas/{id}/rechazar 204 ; la bebida queda RECHAZADA ; no sale en catalogo
}
// @AfterEach: bebidas.deleteAll(bebidas.findAll().stream().filter(b -> b.getNombre().startsWith("IT-")).toList())
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementar** DTOs + excepción + handler (`error(HttpStatus.NOT_FOUND, "BEBIDA_NO_ENCONTRADA")`) + `BebidaService` + `BebidaController`.
- [ ] **Step 4: Run** `-Dit.test=BebidaIT` — PASS.
- [ ] **Step 5: Commit** `feat(back): catalogo de bebidas y aprobacion por admin`

---

## Task 4: Backend — `FichaBebidaService` + `PUT /eventos/{id}/ficha-bebida`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/FichaBebidaService.java`, `FichaBebidaController.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/{FichaBebidaRequest,FichaBebidaResponse}.java`
- Create: `back/src/main/java/com/baniterio/api/evento/EventoSinFichaException.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Test: `back/src/test/java/com/baniterio/api/evento/FichaBebidaIT.java`

**Interfaces:**
- Consumes: `AsistenciaEventoRepository`, `FichaBebidaRepository`, `BebidaRepository`, `EventoRepository`, `UsuarioRepository`, `BebidaService.resolverOtra`, `CalculadoraCuota`, `EstadoAsistencia` (Task 1 de 3a).
- Produces:
  - `record FichaBebidaRequest(@NotNull String estado, Long alcoholBebidaId, @Size(max=80) String alcoholOtra, Long refrescoBebidaId, @Size(max=80) String refrescoOtra, @NotNull String alternativa, @Size(max=80) String cervezaEspecial, boolean embarazada, boolean asisteDia1, boolean asisteDia2)`.
  - `record FichaBebidaResponse(String modalidad, BigDecimal cuota, boolean cuotaPendiente)`.
  - `EventoSinFichaException extends RuntimeException` → 409 `EVENTO_SIN_FICHA`.
  - `FichaBebidaService`:
    - `FichaBebidaResponse guardar(Long usuarioId, Long eventoId, FichaBebidaRequest req)`:
      1. `Evento e = eventos.findById(eventoId).orElseThrow(EventoNoEncontradoException::new)`.
      2. `if (!e.getCuenta().isLlevaFichaBebida()) throw new EventoSinFichaException();`
      3. `if (e.getFecha().isBefore(LocalDate.now())) throw new EventoYaPasadoException();`
      4. `EstadoAsistencia estado = parseEstadoApuntadoOEnDuda(req.estado());` (solo APUNTADO/EN_DUDA, si no `IllegalArgumentException` → 400 vía handler existente de `HttpMessageNotReadable`... **no**: lanzar `MetodoNoPermitido`? mejor validar y lanzar `ResponseStatusException`? — **decisión:** `record` valida con `@Pattern(regexp="APUNTADO|EN_DUDA")` en `estado`, así 400 `VALIDACION` gratis).
      5. upsert `AsistenciaEvento` por `(eventoId, usuarioId)` (mismo patrón que `AsistenciaService.responder`: `findByEventoIdAndUsuarioId(...).orElseGet(builder)`, `setEstado(estado)`, `save`).
      6. `Bebida alcohol = resolverAlcohol(req)` — si `alcoholOtra` no vacío → `bebidaService.resolverOtra(ALCOHOL, alcoholOtra, usuarioId)`; si `alcoholBebidaId != null` → `bebidas.findById(...).orElseThrow(BebidaNoEncontradaException::new)`; si ambos → 400 (validar en record con `@AssertTrue`); si ninguno → `null` ("No bebo alcohol").
      7. `Bebida refresco = resolverRefresco(req)` — igual pero obligatorio (ninguno → 400).
      8. `Alternativa alt = Alternativa.valueOf(req.alternativa())` (record con `@Pattern`).
      9. `if (alt == CERVEZA_ESPECIAL) exigir cervezaEspecial no vacío; else cervezaEspecial = null`. Si `embarazada` → forzar `alcohol=null`, `alt=NADA`, `cervezaEspecial=null`.
      10. días: si el evento tiene `fechaFin` y `fechaFin - fecha == 1 día` → usar `req.asisteDia1/2` (validar que al menos uno); si no → ambos `true`.
      11. `var calc = CalculadoraCuota.calcular(e.getCuotaMaxima(), embarazada, dia1, dia2, alcohol != null, alt);`
      12. upsert `FichaBebida` por `asistenciaId` (`findByAsistenciaId().orElseGet(new)`), set todo, `save`.
      13. return `new FichaBebidaResponse(calc.modalidad().name(), calc.cuota(), calc.cuota() == null)`.
    - `void guardarAMano(Long adminId, Long eventoId, AsistenciaEvento asistencia, FichaBebidaRequest req)` — como pasos 6-13 pero sobre una asistencia ya creada (la de "añadir a mano"); no toca estado.
    - `void recalcularCuotas(Long eventoId)` — `Evento e = ...`; `for (FichaBebida f : fichas.findByEventoId(eventoId))` → `f.setCuota(CalculadoraCuota.calcular(e.getCuotaMaxima(), f.isEmbarazada(), f.isAsisteDia1(), f.isAsisteDia2(), f.getAlcohol() != null, f.getAlternativa()).cuota()); fichas.save(f);` (modalidad NO cambia).
    - `FichaBebidaDetalle detalleDe(Long usuarioId, Evento e)` — **la define la Task 5** (necesita más DTOs); en esta Task solo `guardar`.
  - `FichaBebidaController` (`/api/v1/eventos`):
    - `PUT /{id}/ficha-bebida` → `FichaBebidaResponse` (200).

- [ ] **Step 1: Tests IT**

```java
// FichaBebidaIT extends IntegrationTest — helpers de AsistenciaIT; cuenta() = "San Miguel"
// sembrarEventoSanMiguel(nombre, fecha, fechaFin, cuotaMaxima)
@Test void guardar_ficha_calcula_y_devuelve_la_cuota() {
    // evento San Miguel M=26, 2 días; PUT ficha {estado:APUNTADO, refrescoBebidaId: <cocacola>, alternativa:NADA, asisteDia1:true, asisteDia2:true}
    // -> 200 $.modalidad=="COMPLETA" $.cuota==26.00 $.cuotaPendiente==false
    // y hay fila en asistencia_evento (APUNTADO) + ficha_bebida
}
@Test void ficha_en_evento_que_no_es_san_miguel_409_EVENTO_SIN_FICHA() { }
@Test void ficha_en_evento_pasado_409_EVENTO_YA_PASADO() { }
@Test void estado_NO_VOY_en_la_ficha_es_400_VALIDACION() { }
@Test void alcohol_otra_crea_bebida_pendiente_y_no_sale_en_catalogo() {
    // PUT ficha con alcoholOtra:"Ron del abuelo" -> bebida PENDIENTE; GET /bebidas/catalogo no la trae
}
@Test void un_dia_calcula_M_partido_2_mas_1() {
    // asisteDia1:true asisteDia2:false -> $.cuota == 14.00 $.modalidad=="UN_DIA"
}
@Test void embarazada_ignora_alcohol_y_paga_5() {
    // embarazada:true, alcoholBebidaId:<barceló> -> $.cuota==5.00, ficha.alcohol == null
}
@Test void sin_cuota_maxima_guarda_la_ficha_con_cuota_null() {
    // evento San Miguel sin cuotaMaxima -> $.cuota == null $.cuotaPendiente == true
}
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementar** DTOs (con validación) + excepción + handler (`error(HttpStatus.CONFLICT, "EVENTO_SIN_FICHA")`) + `FichaBebidaService.guardar`/`recalcularCuotas`/`guardarAMano` + `FichaBebidaController`.
- [ ] **Step 4: Run** `-Dit.test=FichaBebidaIT,AsistenciaIT` — PASS.
- [ ] **Step 5: Commit** `feat(back): guardar ficha de bebida y calcular la cuota`

---

## Task 5: Backend — `ficha` en `EventoDetalle`, `AsistenciaResumen` con cuota, añadir a mano con ficha, recalcular al editar

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/dto/FichaBebidaDetalle.java`
- Modify: `evento/dto/AsistenciaDetalle.java`, `evento/dto/AsistenciaResumen.java`, `evento/dto/AnadirAsistenteRequest.java`, `evento/FichaBebidaService.java`, `evento/AsistenciaService.java`, `evento/EventoService.java`
- Test: `back/src/test/java/com/baniterio/api/evento/FichaBebidaIT.java` (+casos), `EventoIT.java` (recalcular)

**Interfaces:**
- Consumes: Task 4.
- Produces:
  - `record FichaBebidaDetalle(boolean llevaFicha, List<LocalDate> diasEvento, MiFicha miFicha)` con
    `record MiFicha(Long alcoholBebidaId, String alcohol, Long refrescoBebidaId, String refresco, String alternativa, String cervezaEspecial, boolean embarazada, boolean asisteDia1, boolean asisteDia2, String modalidad, BigDecimal cuota, boolean cuotaPendiente, boolean bebidaPendiente)`.
    `miFicha` null si no hay ficha; `llevaFicha=false` si el evento no es de San Miguel (y `diasEvento` vacío, `miFicha` null).
  - `AsistenciaDetalle` gana un campo final `FichaBebidaDetalle ficha` (tras los 7 actuales).
  - `AsistenciaResumen` gana `BigDecimal cuota` y `String modalidad` (nullables) al final.
  - `AnadirAsistenteRequest` gana `FichaBebidaRequest ficha` (nullable; sin su `estado`, se usa el del request — **decisión:** reutilizar `FichaBebidaRequest` e ignorar su `estado`, el `nombre`/`estado` del alta manda).
  - `FichaBebidaService.detalleDe(Long usuarioId, Evento e) : FichaBebidaDetalle`:
    - `if (!e.getCuenta().isLlevaFichaBebida()) return new FichaBebidaDetalle(false, List.of(), null);`
    - `diasEvento` = `[e.getFecha(), e.getFechaFin()]` si `fechaFin != null && ChronoUnit.DAYS.between(fecha, fechaFin) == 1`, si no `[e.getFecha()]`.
    - `miFicha` = `asistencias.findByEventoIdAndUsuarioId(e.id, usuarioId)` → `fichas.findByAsistenciaId(a.id)` → map a `MiFicha` (`bebidaPendiente` = `alcohol!=null && alcohol.estado != ACEPTADA || refresco.estado != ACEPTADA`).
  - `AsistenciaService.detalleDe` (3a) construye `AsistenciaDetalle` → añade `fichaBebidaService.detalleDe(usuarioId, e)`. Inyectar `FichaBebidaService` en `AsistenciaService` (sin ciclo: `FichaBebidaService` no depende de `AsistenciaService`).
  - `AsistenciaService.anadirAMano(...)` gana parámetro `FichaBebidaRequest ficha` → tras crear la `AsistenciaEvento`, si `e.getCuenta().isLlevaFichaBebida() && ficha != null` → `fichaBebidaService.guardarAMano(adminId, eventoId, asistencia, ficha)`; el `AsistenciaResumen` devuelto lleva `cuota`/`modalidad` de la ficha (o null).
  - `EventoService.editar` — antes de `eventos.save(e)`, capturar `BigDecimal cuotaAntes = e.getCuotaMaxima()`; tras setear la nueva, si `admin && !Objects.equals(cuotaAntes, e.getCuotaMaxima()) && e.getCuenta().isLlevaFichaBebida()` → tras `save`, `fichaBebidaService.recalcularCuotas(e.getId())`. Inyectar `FichaBebidaService` en `EventoService`.

- [ ] **Step 1: Tests**

```java
// FichaBebidaIT
@Test void detalle_del_evento_trae_mi_ficha_y_los_dias() {
    // guardo ficha UN_DIA ; GET /eventos/{id} -> $.asistencia.ficha.llevaFicha==true
    // $.asistencia.ficha.diasEvento.length()==2 ; $.asistencia.ficha.miFicha.modalidad=="UN_DIA"
}
@Test void detalle_de_evento_normal_ficha_llevaFicha_false() {
    // evento de Chuletas -> $.asistencia.ficha.llevaFicha == false, miFicha null
}
@Test void anadir_a_mano_con_ficha_calcula_la_cuota() {
    // POST /eventos/{id}/asistencias {nombre, estado:APUNTADO, ficha:{...NADA, 2 días}} (admin)
    // -> 201 $.cuota == 26.00 $.modalidad == "COMPLETA"
}
// EventoIT
@Test void cambiar_la_cuota_maxima_recalcula_las_fichas() {
    // evento San Miguel M=26 + 1 ficha COMPLETA (cuota 26) ; PUT evento cuotaMaxima=30
    // -> la ficha pasa a cuota 30.00 ; modalidad sigue COMPLETA
}
```
Ajustar en `AsistenciaIT`/`EventoIT` las aserciones de JSON que ya miran `$.asistencia` (campos añadidos, no rompen).

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementar**.
- [ ] **Step 4: Run** `-Dit.test=FichaBebidaIT,AsistenciaIT,EventoIT,BebidaIT verify` — PASS. Luego suite completa `./mvnw verify`.
- [ ] **Step 5: Commit** `feat(back): ficha en el detalle, anadir a mano con ficha y recalculo al cambiar la cuota`

---

## Task 6: Web — servicio, tipos y formulario `ficha-bebida`

**Files:**
- Modify: `front/src/app/panel/eventos/eventos.types.ts`, `eventos.service.ts`
- Create: `front/src/app/panel/eventos/ficha-bebida/ficha-bebida.ts`, `ficha-bebida.html`
- Test: `front/src/app/panel/eventos/eventos.service.spec.ts`, `ficha-bebida/ficha-bebida.spec.ts`

**Interfaces:**
- Produces (tipos):
  - `Alternativa = 'CERVEZA' | 'TINTO_VERANO' | 'NADA' | 'CERVEZA_ESPECIAL'`.
  - `Modalidad = 'COMPLETA' | 'SOLO_CERVEZA' | 'UN_DIA' | 'EMBARAZADA'`.
  - `BebidaRef = { id: number; nombre: string }`.
  - `CatalogoBebidas = { alcohol: BebidaRef[]; refresco: BebidaRef[] }`.
  - `FichaBebidaMia = { alcoholBebidaId: number | null; alcohol: string | null; refrescoBebidaId: number | null; refresco: string | null; alternativa: Alternativa; cervezaEspecial: string | null; embarazada: boolean; asisteDia1: boolean; asisteDia2: boolean; modalidad: Modalidad; cuota: number | null; cuotaPendiente: boolean; bebidaPendiente: boolean }`.
  - `FichaBebidaBloque = { llevaFicha: boolean; diasEvento: string[]; miFicha: FichaBebidaMia | null }`.
  - `EventoDetalle.asistencia.ficha: FichaBebidaBloque`.
  - `FichaBebidaBody = { estado: 'APUNTADO' | 'EN_DUDA'; alcoholBebidaId: number | null; alcoholOtra: string | null; refrescoBebidaId: number | null; refrescoOtra: string | null; alternativa: Alternativa; cervezaEspecial: string | null; embarazada: boolean; asisteDia1: boolean; asisteDia2: boolean }`.
  - `FichaBebidaResponse = { modalidad: Modalidad; cuota: number | null; cuotaPendiente: boolean }`.
  - `AsistenciaResumen` += `cuota: number | null; modalidad: Modalidad | null`.
  - `BebidaPendiente = { id: number; tipo: 'ALCOHOL' | 'REFRESCO'; nombre: string; propuestaPor: { id: number; nombre: string } | null; createdAt: string }`.
  - `CodigoErrorEvento` += `'EVENTO_SIN_FICHA' | 'BEBIDA_NO_ENCONTRADA'`.
  - `EventosService`: `catalogoBebidas(): Observable<CatalogoBebidas>`, `guardarFichaBebida(id, body: FichaBebidaBody): Observable<FichaBebidaResponse>`, `bebidasPendientes(): Observable<BebidaPendiente[]>`, `aceptarBebida(id): Observable<void>`, `rechazarBebida(id): Observable<void>`.
  - Componente `FichaBebida` (`selector: app-ficha-bebida`): inputs `catalogo: CatalogoBebidas`, `dias: string[]`, `fichaActual: FichaBebidaMia | null`, `enDuda: boolean`; output `guardar = EventEmitter<FichaBebidaBody>`; señales para cada campo; al marcar `embarazada` deshabilita alcohol/alternativa; "Otra…" en alcohol/refresco muestra un input y al enviar rellena `alcoholOtra`/`refrescoOtra` (y pone el id a null).

- [ ] **Step 1: Tests de servicio** — un caso por método (URL+verbo+cuerpo), patrón de `eventos.service.spec.ts`.
- [ ] **Step 2: Run FAIL → implementar service + tipos → Step: Run PASS.**
- [ ] **Step 3: Tests de `ficha-bebida`:**
```
- con 2 días pinta el selector de día; con 1 no
- marcar "Embarazada" deshabilita alcohol y alternativa
- elegir "Otra…" en alcohol muestra el input; al guardar el body lleva alcoholOtra y alcoholBebidaId null
- guardar emite el body con los ids elegidos y alternativa
- precarga fichaActual (alcohol, refresco, alternativa, días)
```
- [ ] **Step 4: Implementar** `ficha-bebida.ts` + `.html` (usar `<select>` nativos como `editor-evento`). **Run** `ng test` — PASS.
- [ ] **Step 5: Commit** `feat(front): servicio de bebidas y formulario de ficha reutilizable`

---

## Task 7: Web — ficha en el detalle del evento, en la pantalla bloqueante y en "añadir a mano"

**Files:**
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.ts`+`.html`, `panel/responder/responder.ts`+`.html`
- Test: `evento-detalle/evento-detalle.spec.ts`, `panel/responder/responder.spec.ts`

**Interfaces:**
- Consumes: Task 6 (`FichaBebida` componente, `EventosService.guardarFichaBebida`, `catalogoBebidas`).
- Produces:
  - `evento-detalle`: si `evento().asistencia.ficha.llevaFicha`, tras responder `APUNTADO`/`EN_DUDA` (o si ya hay `miFicha`) mostrar `<app-ficha-bebida>` con `catalogo` (cargado en ngOnInit si `llevaFicha`), `dias = ficha.diasEvento`, `fichaActual = ficha.miFicha`, `enDuda = miAsistencia === 'EN_DUDA'`. Al `(guardar)` → `guardarFichaBebida(id, body)` → refrescar y mostrar `«Tu cuota: {cuota} € — {modalidad legible}»` o `«Cuota pendiente de fijar»`; si `bebidaPendiente` → aviso.
  - "Añadir a mano": si `llevaFicha` y estado ∈ {APUNTADO, EN_DUDA}, mostrar `<app-ficha-bebida>` bajo el nombre; el botón "Añadir" manda `{nombre, estado, ficha}` (el service `anadirAsistente` gana el 3er arg `ficha?: FichaBebidaBody`).
  - `responder`: en un evento con `ficha.llevaFicha`, tras pulsar `APUNTADO`/`EN_DUDA` no avanzar aún: mostrar `<app-ficha-bebida>`; al `(guardar)` → `guardarFichaBebida` → recargar pendientes (y avanzar). "No voy" avanza directo.

- [ ] **Step 1: Tests** (prosa, patrón existente):
```
evento-detalle:
- evento San Miguel: tras "Me apunto" aparece <app-ficha-bebida>; evento normal: no
- al guardar la ficha hace PUT /eventos/5/ficha-bebida y muestra "Tu cuota: 26 €"
- "añadir a mano" en San Miguel manda ficha en el POST
responder:
- evento San Miguel: "Me apunto" muestra la ficha antes de avanzar; al guardarla avanza
- "No voy" avanza sin ficha
```
- [ ] **Step 2: Run FAIL → implementar → Run PASS** (`ng test` + `ng build --configuration production`).
- [ ] **Step 3: Commit** `feat(front): ficha de bebida y cuota en el detalle, la convocatoria y el alta manual`

---

## Task 8: Web — pantalla de admin "Bebidas"

**Files:**
- Create: `front/src/app/panel/admin/bebidas/bebidas.ts`, `bebidas.html`
- Modify: `front/src/app/app.routes.ts`, `front/src/app/panel/admin/indice/indice.ts`+`.html`
- Test: `front/src/app/panel/admin/bebidas/bebidas.spec.ts`

**Interfaces:**
- Consumes: `EventosService.bebidasPendientes/aceptarBebida/rechazarBebida`, `AuthService` (`usuarioActual` → `rol==='ADMIN' || esSuperadmin`).
- Produces:
  - Componente `Bebidas`: en ngOnInit, si no admin → `router.navigateByUrl('/panel')`; si admin → `bebidasPendientes()`. Lista con tipo, nombre, quién la propuso; botones "Aceptar" / "Rechazar" → llamada + quitar de la lista.
  - Ruta `{ path: 'administracion/bebidas', component: Bebidas, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] }`.
  - `admin/indice`: añadir tarjeta/enlace "Bebidas" visible solo si admin/superadmin (como las otras del índice).

- [ ] **Step 1: Test** (`bebidas.spec.ts`): admin ve la lista y "Aceptar" hace `POST /bebidas/3/aceptar`; no-admin es redirigido.
- [ ] **Step 2: Run FAIL → implementar → Run PASS** (`ng test` + build).
- [ ] **Step 3: Commit** `feat(front): pantalla de admin para aceptar bebidas propuestas`

---

## Task 9: Móvil — repos, DTOs y formulario `FichaBebidaForm`

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/BebidaDtos.kt`, `data/BebidaRepository.kt`, `data/BebidaRepositoryImpl.kt`, `data/ResultadoBebida.kt`, `ui/eventos/FichaBebidaForm.kt`
- Modify: `data/dto/EventoDtos.kt`, `data/AsistenciaRepository.kt`, `data/AsistenciaRepositoryImpl.kt`, `data/Dependencias.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/BebidaRepositoryImplTest.kt`, `data/AsistenciaRepositoryImplTest.kt` (+guardarFicha)

**Interfaces:**
- Produces:
  - DTOs `@Serializable`: `BebidaRefDto(id: Long, nombre: String)`; `CatalogoBebidasDto(alcohol: List<BebidaRefDto> = emptyList(), refresco: List<BebidaRefDto> = emptyList())`; `FichaBebidaBody(estado: String, alcoholBebidaId: Long? = null, alcoholOtra: String? = null, refrescoBebidaId: Long? = null, refrescoOtra: String? = null, alternativa: String, cervezaEspecial: String? = null, embarazada: Boolean = false, asisteDia1: Boolean = true, asisteDia2: Boolean = true)`; `FichaBebidaResponseDto(modalidad: String, cuota: Double? = null, cuotaPendiente: Boolean = false)`; `FichaBebidaMiaDto(...campos del spec...)`; `FichaBebidaBloqueDto(llevaFicha: Boolean = false, diasEvento: List<String> = emptyList(), miFicha: FichaBebidaMiaDto? = null)`; `BebidaPendienteDto(id: Long, tipo: String, nombre: String, propuestaPor: ProponenteDto? = null, createdAt: String)`.
  - `EventoDetalle.asistencia` (`AsistenciaDetalleDto`) gana `ficha: FichaBebidaBloqueDto = FichaBebidaBloqueDto()`.
  - `AsistenciaResumenDto` gana `cuota: Double? = null, modalidad: String? = null`.
  - `ResultadoBebida<out T>` sellado (`Exito`/`Error`) + `CodigoErrorBebida { BEBIDA_NO_ENCONTRADA, SIN_PERMISO, SIN_CONEXION, DESCONOCIDO }` con `mensaje`.
  - `BebidaRepository`: `suspend fun catalogo(): ResultadoBebida<CatalogoBebidasDto>`; `suspend fun pendientes(): ResultadoBebida<List<BebidaPendienteDto>>`; `suspend fun aceptar(id: Long): ResultadoBebida<Unit>`; `suspend fun rechazar(id: Long): ResultadoBebida<Unit>`.
  - `AsistenciaRepository` gana `suspend fun guardarFicha(eventoId: Long, body: FichaBebidaBody): ResultadoAsistencia<FichaBebidaResponseDto>`; `anadir` gana `ficha: FichaBebidaBody? = null`.
  - `Dependencias` + `bebidaRepo: BebidaRepository`.
  - `FichaBebidaForm(catalogo, dias: List<String>, fichaActual: FichaBebidaMiaDto?, enDuda: Boolean, onGuardar: (FichaBebidaBody) -> Unit)` — composable con `ExposedDropdownMenuBox` para alcohol/refresco/alternativa, check embarazada, radios de día.

- [ ] **Step 1: Tests** (`BebidaRepositoryImplTest`, `AsistenciaRepositoryImplTest`) con `MockEngine`, patrón de `EventosRepositoryImplTest`: `catalogo` → GET `/api/v1/bebidas/catalogo`; `aceptar` → POST `/api/v1/bebidas/3/aceptar`; `guardarFicha` → PUT `/api/v1/eventos/1/ficha-bebida` con cuerpo `{"estado":"APUNTADO",...}`; 409 `EVENTO_SIN_FICHA` → `ResultadoAsistencia.Error`.
- [ ] **Step 2: Run FAIL → implementar repos + DTOs + Dependencias + `FichaBebidaForm` → Run PASS.**
- [ ] **Step 3: Run** `:shared:testAndroidHostTest :androidApp:compileDebugKotlin` — PASS.
- [ ] **Step 4: Commit** `feat(movil): repos de bebida, DTOs de ficha y formulario FichaBebidaForm`

---

## Task 10: Móvil — ficha en el detalle, la convocatoria y el alta manual + pantalla de admin

**Files:**
- Modify: `ui/eventos/EventoDetalleScreen.kt`, `ui/eventos/ResponderEventoScreen.kt`, `nav/Screen.kt`, `App.kt`, `ui/admin/AdminIndexScreen.kt`
- Create: `ui/admin/AdminBebidasScreen.kt`
- Test: (sin tests de UI; humo manual)

**Interfaces:**
- Consumes: Task 9.
- Produces:
  - `EventoDetalleScreen`: si `ev.asistencia.ficha.llevaFicha`, tras `APUNTADO`/`EN_DUDA` (o si `miFicha != null`) mostrar `FichaBebidaForm` (catálogo cargado con `deps.bebidaRepo.catalogo()` al entrar si `llevaFicha`); al `onGuardar` → `asistenciaRepo.guardarFicha(...)` → mostrar `"Tu cuota: {cuota} € — {modalidad}"`. En "Añadir a mano" de San Miguel, `FichaBebidaForm` embebido y `anadir(..., ficha = body)`.
  - `ResponderEventoScreen`: en evento con `ficha.llevaFicha`, tras `APUNTADO`/`EN_DUDA` mostrar `FichaBebidaForm` antes de recargar; al guardarla, recargar pendientes.
  - `Screen.AdminBebidas` + `AdminBebidasScreen(bebidaRepo, onVolver)` — lista pendientes, botones aceptar/rechazar. Enlace desde `AdminIndexScreen` (visible solo si admin/superadmin, como los demás). `App.kt`: clave, `aClave`/`claveAScreen`, guardia de arranque en frío, rama `is Screen.AdminBebidas ->` con `BackHandler { ir(Screen.AdminIndex) }`.

- [ ] **Step 1:** `FichaBebidaForm` en `EventoDetalleScreen` (tras responder en San Miguel) + mostrar cuota.
- [ ] **Step 2:** lo mismo en `ResponderEventoScreen`.
- [ ] **Step 3:** "Añadir a mano" con ficha en `EventoDetalleScreen`.
- [ ] **Step 4:** `Screen.AdminBebidas` + `AdminBebidasScreen` + `App.kt` + enlace en `AdminIndexScreen`.
- [ ] **Step 5: Run** `:shared:testAndroidHostTest :androidApp:compileDebugKotlin` — PASS.
- [ ] **Step 6: Commit** `feat(movil): ficha de bebida y cuota en el detalle, la convocatoria y admin de bebidas`

---

## Self-Review

**Spec coverage:**
- `bebida` + seed + `cuenta.lleva_ficha_bebida` → Task 1. ✓
- `ficha_bebida` → Task 1. ✓
- Reglas modalidad/cuota + precedencia + edge cases → Task 2 (`CalculadoraCuota` + tests). ✓
- `GET /bebidas/catalogo`, `GET /bebidas?estado`, aceptar/rechazar + push → Task 3. ✓
- `PUT /eventos/{id}/ficha-bebida` + "Otra" + validación → Task 4. ✓
- `ficha` en `EventoDetalle`, `AsistenciaResumen` con cuota, añadir a mano con ficha, recalcular al cambiar M → Task 5. ✓
- Web: servicio + formulario → Task 6; detalle + responder + alta manual → Task 7; admin bebidas → Task 8. ✓
- Móvil: repos + form → Task 9; pantallas + admin → Task 10. ✓
- Errores `EVENTO_SIN_FICHA` (409), `BEBIDA_NO_ENCONTRADA` (404) → Tasks 3 y 4. ✓
- Poder editar la ficha hasta `evento.fecha` → Task 4 (mismo `EventoYaPasadoException`). ✓

**Placeholder scan:** Tasks 6–10 (UI Angular/Compose) llevan la lista de casos en prosa, como el plan de 3a — el patrón ya existe en el repo (`eventos.service.spec.ts`, `EventosRepositoryImplTest.kt`, `EventoDetalleScreen.kt`). Backend (Tasks 1–5) lleva el código clave y los tests completos.

**Type consistency:** `Modalidad`/`Alternativa` enum (back) ↔ string unions (web) ↔ `String` (móvil). `FichaBebidaRequest`/`FichaBebidaBody`/`FichaBebidaBody` mismo juego de campos en las tres capas. `FichaBebidaResponse{modalidad,cuota,cuotaPendiente}` igual en Task 4/6/9. `MiFicha`/`FichaBebidaMia`/`FichaBebidaMiaDto` mismo juego de 13 campos. `EventoDetalle.asistencia.ficha` (Task 5) consumido en Task 7 y Task 10. OK.
