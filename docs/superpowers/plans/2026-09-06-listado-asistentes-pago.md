# Listado de asistentes + "He pagado" (San Miguel) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir en el detalle de un evento de San Miguel dos botones —"Listado de asistentes" y "He pagado"— con sus modales en web y móvil, servidos por un endpoint de solo lectura; el pago declarado todavía no se persiste.

**Architecture:** Un endpoint nuevo `GET /api/v1/eventos/{id}/asistentes` en `AsistenciaController` que compone la lista de asistencias (con su ficha de bebida y cuota) más el bloque `puedoPagarPor` (familia/invitados que el usuario puede cubrir), calculado reutilizando `VinculoFamiliarService`. Web y móvil consumen ese endpoint en dos modales; el modal "He pagado" es pura UI: valida y emite un objeto, sin llamada de escritura.

**Tech Stack:** Backend Java 17 + Spring Boot 4.1 + Maven + Flyway + JPA (sin migración en esta tanda). Web Angular 22 standalone + signals + Tailwind + Vitest. Móvil Kotlin Multiplatform + Compose Multiplatform + Ktor + kotlinx.serialization; tests `:shared:testAndroidHostTest` con `MockEngine`.

**Spec:** `docs/superpowers/specs/2026-09-06-listado-asistentes-pago-design.md`

## Global Constraints

- **Backend:** Java 17 (NO subir a 21/25). Spring Boot 4.1.x. Hibernate `ddl-auto: validate`, `open-in-view: false` — **no** hay migración nueva en esta tanda; no se añaden columnas ni tablas. Peña piloto por slug `baniterio`. Errores vía `ApiExceptionHandler` con `codigo` estable; no se crean códigos nuevos (`EVENTO_NO_ENCONTRADO`, `EVENTO_SIN_FICHA` ya existen). El id del usuario sale del token (`@AuthenticationPrincipal UsuarioPrincipal principal`), nunca del cuerpo.
- **Web:** Angular standalone, `signal`/`computed`, sin NgModules. Tailwind con los tokens del repo (`brand`, `gold`, `gold-soft`, `outline`, `muted`, `ink`, `surface`, clase `carta-relieve`). Tests con Vitest (`npx ng test --watch=false`). El servicio no traduce errores, los deja propagar. Presupuesto de bundle ya en 550 kB.
- **Móvil:** enums de la API viajan como `String` en los DTOs (igual que `AsistenciaResumenDto.estado`). Repos devuelven `ResultadoEvento<T>`, nunca lanzan por errores HTTP esperados. Navegación por `Screen` sellado + `when` en `App.kt`. Tests `:shared:testAndroidHostTest`.
- **El modal "He pagado" NO persiste nada** en esta tanda: no hay endpoint de escritura, no se llama a ningún repo/servicio al enviarlo. Solo valida, cierra y muestra un aviso.
- **Solo San Miguel:** ambos botones se pintan ⟺ `evento.asistencia.ficha.llevaFicha === true`.

---

### Task 1: Backend — `VinculoFamiliarService.personasParaPago`

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/identidad/VinculoFamiliarService.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/HijoRepository.java` (solo si falta algún método; ver Paso 3)
- Test: `back/src/test/java/com/baniterio/api/identidad/VinculoFamiliarServiceTest.java` (crear)

**Interfaces:**
- Consumes: nada nuevo.
- Produces:
  - `List<VinculoFamiliarService.Persona> personasParaPago(Long actuanteId)` — como `personasQuePuedoResponder` pero **solo** incluye hijos con `mayorDeEdad = true`. Sigue incluyendo la pareja con vínculo `ACEPTADO`. **No** incluye a uno mismo (a diferencia de `personasQuePuedoResponder`, que sí).
  - `record Persona(Long id, String nombre)` ya existe, se reutiliza.

- [ ] **Step 1: Escribir el test que falla**

Crear `back/src/test/java/com/baniterio/api/identidad/VinculoFamiliarServiceTest.java`. Es un `@SpringBootTest` ligero que usa los repos reales (patrón `IntegrationTest` del repo, pero sin `RestTestClient`). Mira `back/src/test/java/com/baniterio/api/support/IntegrationTest.java` para la clase base y copia el estilo de `crearMiembro` de `FichaBebidaIT`.

```java
package com.baniterio.api.identidad;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Unidad de la lógica de "a quién puedo cubrir un pago". */
class VinculoFamiliarServiceTest extends IntegrationTest {

    @Autowired VinculoFamiliarService servicio;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PenaRepository penas;
    @Autowired VinculoParejaRepository vinculos;
    @Autowired HijoRepository hijos;

    private Pena pena() { return penas.findBySlug("baniterio").orElseThrow(); }

    private Usuario nuevoUsuario(String etiqueta) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@vf.test").passwordHash("x")
                .nombre(etiqueta + "-" + tel).apellidos("VF").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(RolMembresia.MIEMBRO).activa(true).build());
        return u;
    }

    @AfterEach
    void limpiar() {
        hijos.deleteAllInBatch();
        vinculos.deleteAllInBatch();
    }

    @Test
    void incluye_pareja_e_hijo_mayor_con_cuenta_pero_no_hijo_menor_ni_a_uno_mismo() {
        Usuario yo = nuevoUsuario("yo");
        Usuario pareja = nuevoUsuario("pareja");
        Usuario hijoMayor = nuevoUsuario("hijoMayor");

        vinculos.save(VinculoPareja.builder()
                .solicitante(yo).parejaUsuario(pareja).estado(EstadoVinculo.ACEPTADO).build());
        hijos.save(Hijo.builder().creador(yo).nombre("Mayor").mayorDeEdad(true)
                .usuario(hijoMayor).visible(true).build());
        hijos.save(Hijo.builder().creador(yo).nombre("Menor").mayorDeEdad(false)
                .visible(true).build());

        List<VinculoFamiliarService.Persona> r = servicio.personasParaPago(yo.getId());

        assertThat(r).extracting(VinculoFamiliarService.Persona::id)
                .containsExactlyInAnyOrder(pareja.getId(), hijoMayor.getId())
                .doesNotContain(yo.getId());
    }
}
```

- [ ] **Step 2: Ejecutar el test — debe fallar**

Run: `cd back && ./mvnw.cmd -q -Dtest=VinculoFamiliarServiceTest test`
Expected: FAIL — `personasParaPago` no existe (no compila).

- [ ] **Step 3: Implementar `personasParaPago`**

En `VinculoFamiliarService.java`, refactorizar para compartir la lógica de recogida y añadir el filtro de edad. La versión actual de `personasQuePuedoResponder` mete a "yo" y todos los hijos con cuenta; la nueva NO mete a "yo" y solo hijos `mayorDeEdad`.

```java
/**
 * A quién puede incluir {@code actuanteId} en un pago además de a sí mismo: su
 * pareja con vínculo {@link EstadoVinculo#ACEPTADO} y los hijos <b>mayores de
 * edad</b> con cuenta propia ({@link Hijo#getUsuario()}) suyos o de la pareja.
 * No incluye a uno mismo.
 */
public List<Persona> personasParaPago(Long actuanteId) {
    Map<Long, Persona> porId = new LinkedHashMap<>();

    Optional<VinculoPareja> vinculo = vinculos
            .findBySolicitanteIdAndEstado(actuanteId, EstadoVinculo.ACEPTADO)
            .or(() -> vinculos.findByParejaUsuarioIdAndEstado(actuanteId, EstadoVinculo.ACEPTADO));
    vinculo.map(VinculoPareja::getParejaUsuario)
            .ifPresent(u -> porId.put(u.getId(), new Persona(u.getId(), u.getNombre())));

    hijos.findByCreadorId(actuanteId).forEach(h -> agregarSiMayorConCuenta(porId, h));
    vinculo.map(VinculoPareja::getId)
            .map(hijos::findByVinculoParejaId)
            .ifPresent(lista -> lista.forEach(h -> agregarSiMayorConCuenta(porId, h)));

    return new ArrayList<>(porId.values());
}

private static void agregarSiMayorConCuenta(Map<Long, Persona> porId, Hijo h) {
    Usuario u = h.getUsuario();
    if (u != null && h.isMayorDeEdad()) {
        porId.put(u.getId(), new Persona(u.getId(), u.getNombre()));
    }
}
```

Comprobar que `Hijo` expone `isMayorDeEdad()` (campo `boolean mayorDeEdad` con Lombok `@Getter` → sí). Comprobar que `HijoRepository` ya tiene `findByCreadorId` y `findByVinculoParejaId` (los usa el método existente → sí, no tocar el repo).

- [ ] **Step 4: Ejecutar el test — debe pasar**

Run: `cd back && ./mvnw.cmd -q -Dtest=VinculoFamiliarServiceTest test`
Expected: PASS.

- [ ] **Step 5: Regresión de lo que ya usaba el servicio**

Run: `cd back && ./mvnw.cmd -q -Dtest=AsistenciaIT test`
Expected: PASS (no se tocó `personasQuePuedoResponder`).

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/identidad/VinculoFamiliarService.java back/src/test/java/com/baniterio/api/identidad/VinculoFamiliarServiceTest.java
git commit -m "feat(cuentas): personasParaPago (pareja + hijos mayores con cuenta)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Backend — `GET /api/v1/eventos/{id}/asistentes`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/evento/dto/ListadoAsistentesResponse.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/AsistenteFila.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/BebidaFila.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/PersonaPagable.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/AsistenciaEventoRepository.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/FichaBebidaRepository.java` (añadir un finder por lista de asistencias)
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaService.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaController.java`
- Test: `back/src/test/java/com/baniterio/api/evento/ListadoAsistentesIT.java` (crear)

**Interfaces:**
- Consumes: `VinculoFamiliarService.personasParaPago(Long)` de Task 1.
- Produces:
  - `AsistenciaService.listadoAsistentes(Long usuarioId, Long eventoId) : ListadoAsistentesResponse`
  - DTOs (records):
    - `ListadoAsistentesResponse(List<AsistenteFila> asistentes, BigDecimal totalCuotas, BigDecimal totalPagado, List<PersonaPagable> puedoPagarPor, BigDecimal miCuota)`
    - `AsistenteFila(String nombre, String estado, boolean esManual, BebidaFila bebida, BigDecimal cuota, boolean pagado)`
    - `BebidaFila(String alcohol, String refresco, String alternativa, String modalidad)`
    - `PersonaPagable(String nombre, BigDecimal cuota, String relacion, Long usuarioId, Long asistenciaId)` — `relacion` ∈ `{"PAREJA","HIJO","INVITADO"}`
  - Endpoint: `GET /api/v1/eventos/{id}/asistentes` → 200 `ListadoAsistentesResponse` · 404 `EVENTO_NO_ENCONTRADO` · 409 `EVENTO_SIN_FICHA`.

- [ ] **Step 1: Escribir el IT que falla**

Crear `back/src/test/java/com/baniterio/api/evento/ListadoAsistentesIT.java`. Copiar la infra de `FichaBebidaIT` (`crearMiembro`, `sanMiguel`, `putFicha`, `@AfterEach`), añadiendo `@Autowired VinculoParejaRepository` y `@Autowired HijoRepository` y limpiándolos en `limpiar()`.

```java
@Test
void lista_apuntados_y_enduda_con_su_cuota_no_los_no_voy() {
    Sesion a = crearMiembro(RolMembresia.MIEMBRO);
    Sesion b = crearMiembro(RolMembresia.MIEMBRO);
    Sesion c = crearMiembro(RolMembresia.MIEMBRO);
    Evento e = sanMiguel(new BigDecimal("26"));

    putFicha(a, e.getId(), fichaBase()).expectStatus().isOk();              // SOLO_CERVEZA → 16
    Map<String, Object> fichaB = fichaBase();
    fichaB.put("estado", "EN_DUDA");
    putFicha(b, e.getId(), fichaB).expectStatus().isOk();
    http.put().uri("/api/v1/eventos/" + e.getId() + "/asistencia")
            .header(AUTHORIZATION, "Bearer " + c.token())
            .body(Map.of("estado", "NO_VOY")).exchange().expectStatus().isOk();

    http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
            .header(AUTHORIZATION, "Bearer " + a.token())
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.asistentes.length()").isEqualTo(2)
            .jsonPath("$.asistentes[?(@.estado == 'NO_VOY')]").doesNotExist()
            .jsonPath("$.totalCuotas").isEqualTo(32.00)
            .jsonPath("$.miCuota").isEqualTo(16.00);
}

@Test
void evento_que_no_es_san_miguel_409() {
    Sesion a = crearMiembro(RolMembresia.MIEMBRO);
    Evento e = eventos.save(Evento.builder().pena(pena()).cuenta(cuenta("Chuletas Santas"))
            .nombre("IT-ficha-chuletas-list").fecha(LocalDate.now().plusDays(20)).build());

    http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
            .header(AUTHORIZATION, "Bearer " + a.token())
            .exchange().expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_SIN_FICHA");
}

@Test
void puedoPagarPor_incluye_pareja_apuntada_con_cuota_y_no_a_uno_mismo() {
    Sesion yo = crearMiembro(RolMembresia.MIEMBRO);
    Sesion pareja = crearMiembro(RolMembresia.MIEMBRO);
    vinculos.save(VinculoPareja.builder()
            .solicitante(usuarios.findById(yo.id()).orElseThrow())
            .parejaUsuario(usuarios.findById(pareja.id()).orElseThrow())
            .estado(EstadoVinculo.ACEPTADO).build());
    Evento e = sanMiguel(new BigDecimal("26"));
    putFicha(yo, e.getId(), fichaBase()).expectStatus().isOk();
    putFicha(pareja, e.getId(), fichaBase()).expectStatus().isOk();

    http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
            .header(AUTHORIZATION, "Bearer " + yo.token())
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.puedoPagarPor.length()").isEqualTo(1)
            .jsonPath("$.puedoPagarPor[0].relacion").isEqualTo("PAREJA")
            .jsonPath("$.puedoPagarPor[0].cuota").isEqualTo(16.00)
            .jsonPath("$.puedoPagarPor[0].usuarioId").isEqualTo(pareja.id());
}

@Test
void puedoPagarPor_incluye_invitado_que_anadi_yo_y_no_el_de_otro() {
    Sesion admin = crearMiembro(RolMembresia.ADMIN);
    Sesion otroAdmin = crearMiembro(RolMembresia.ADMIN);
    Evento e = sanMiguel(new BigDecimal("26"));
    putFicha(admin, e.getId(), fichaBase()).expectStatus().isOk();

    http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
            .header(AUTHORIZATION, "Bearer " + admin.token())
            .body(Map.of("nombre", "Mi primo", "estado", "APUNTADO", "ficha", fichaBase()))
            .exchange().expectStatus().isCreated();
    http.post().uri("/api/v1/eventos/" + e.getId() + "/asistencias")
            .header(AUTHORIZATION, "Bearer " + otroAdmin.token())
            .body(Map.of("nombre", "Primo ajeno", "estado", "APUNTADO", "ficha", fichaBase()))
            .exchange().expectStatus().isCreated();

    http.get().uri("/api/v1/eventos/" + e.getId() + "/asistentes")
            .header(AUTHORIZATION, "Bearer " + admin.token())
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.puedoPagarPor[?(@.relacion == 'INVITADO')].nombre").isEqualTo("Mi primo")
            .jsonPath("$.puedoPagarPor[?(@.nombre == 'Primo ajeno')]").doesNotExist();
}
```

- [ ] **Step 2: Ejecutar el IT — debe fallar**

Run: `cd back && ./mvnw.cmd -q -Dtest=ListadoAsistentesIT test`
Expected: FAIL — 404/405 en `/asistentes` (no existe la ruta).

- [ ] **Step 3: Crear los DTOs**

Cuatro records en `back/src/main/java/com/baniterio/api/evento/dto/`. Estilo: mira `AsistenciaResumen.java` (record con javadoc corto).

```java
// ListadoAsistentesResponse.java
package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Listado de asistentes de un evento de San Miguel: quién va, qué bebe, su cuota
 * y —de momento siempre {@code false}— si ha pagado. {@code puedoPagarPor} es a
 * quién puede cubrir en un pago el usuario que pregunta; {@code miCuota} es su
 * propia cuota ({@code null} si no tiene ficha).
 */
public record ListadoAsistentesResponse(
        List<AsistenteFila> asistentes,
        BigDecimal totalCuotas,
        BigDecimal totalPagado,
        List<PersonaPagable> puedoPagarPor,
        BigDecimal miCuota) {
}
```

```java
// AsistenteFila.java
package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/** Una fila del listado de asistentes. {@code estado} es APUNTADO o EN_DUDA. */
public record AsistenteFila(
        String nombre,
        String estado,
        boolean esManual,
        BebidaFila bebida,
        BigDecimal cuota,
        boolean pagado) {
}
```

```java
// BebidaFila.java
package com.baniterio.api.evento.dto;

/** Lo que bebe un asistente, tal como está en su ficha. {@code alcohol} null = no bebe alcohol. */
public record BebidaFila(String alcohol, String refresco, String alternativa, String modalidad) {
}
```

```java
// PersonaPagable.java
package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/**
 * Alguien a quien el usuario puede incluir en su pago: su pareja, un hijo mayor
 * con cuenta, o un invitado que él añadió. {@code relacion} ∈ {PAREJA, HIJO,
 * INVITADO}. {@code usuarioId} para pareja/hijo, {@code asistenciaId} para invitado.
 */
public record PersonaPagable(
        String nombre,
        BigDecimal cuota,
        String relacion,
        Long usuarioId,
        Long asistenciaId) {
}
```

- [ ] **Step 4: Añadir los finders de repo**

En `AsistenciaEventoRepository.java`:

```java
List<AsistenciaEvento> findByEventoIdAndEstadoIn(Long eventoId, java.util.Collection<EstadoAsistencia> estados);

List<AsistenciaEvento> findByEventoIdAndUsuarioIsNullAndRegistradoPorId(Long eventoId, Long registradoPorId);
```

En `FichaBebidaRepository.java` (mira el fichero para el nombre de la clave; la ficha tiene `asistenciaId`):

```java
List<FichaBebida> findByAsistenciaIdIn(java.util.Collection<Long> asistenciaIds);
```

- [ ] **Step 5: Implementar `AsistenciaService.listadoAsistentes`**

Añadir al `AsistenciaService` (ya tiene inyectado `vinculoFamiliar`, `asistencias`, `eventos`, `usuarios`). Necesita también `FichaBebidaRepository` — inyectarlo por constructor (añadir campo `private final FichaBebidaRepository fichas;` y parámetro; actualizar el `new AsistenciaService(...)` no hace falta, es Spring). Import: `com.baniterio.api.identidad.FichaBebidaRepository`, `com.baniterio.api.identidad.FichaBebida`, los DTOs, `java.util.*`, `java.math.BigDecimal`.

```java
/**
 * Listado de asistentes de un evento de San Miguel (pieza 4). Solo lectura.
 * 404 si no existe; 409 {@code EVENTO_SIN_FICHA} si el evento no lleva ficha.
 */
@Transactional(readOnly = true)
public ListadoAsistentesResponse listadoAsistentes(Long usuarioId, Long eventoId) {
    Evento e = cargar(eventoId);
    if (!e.getCuenta().isLlevaFichaBebida()) {
        throw new EventoSinFichaException();
    }

    List<AsistenciaEvento> filas = asistencias.findByEventoIdAndEstadoIn(
            eventoId, List.of(EstadoAsistencia.APUNTADO, EstadoAsistencia.EN_DUDA));
    Map<Long, FichaBebida> fichaPorAsistencia = fichas
            .findByAsistenciaIdIn(filas.stream().map(AsistenciaEvento::getId).toList())
            .stream().collect(java.util.stream.Collectors.toMap(FichaBebida::getAsistenciaId, f -> f));

    Comparator<AsistenciaEvento> orden = Comparator
            .comparing((AsistenciaEvento a) -> a.getEstado() == EstadoAsistencia.APUNTADO ? 0 : 1)
            .thenComparing(a -> nombreDe(a).toLowerCase());
    List<AsistenteFila> asistentes = filas.stream().sorted(orden)
            .map(a -> aFila(a, fichaPorAsistencia.get(a.getId())))
            .toList();

    BigDecimal totalCuotas = asistentes.stream()
            .map(AsistenteFila::cuota).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal miCuota = asistencias.findByEventoIdAndUsuarioId(eventoId, usuarioId)
            .map(a -> fichaPorAsistencia.get(a.getId()))
            .map(FichaBebida::getCuota).orElse(null);

    return new ListadoAsistentesResponse(asistentes, totalCuotas, BigDecimal.ZERO,
            puedoPagarPor(usuarioId, eventoId, fichaPorAsistencia), miCuota);
}

private static String nombreDe(AsistenciaEvento a) {
    return a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre();
}

private static AsistenteFila aFila(AsistenciaEvento a, FichaBebida f) {
    BebidaFila bebida = f == null ? null : new BebidaFila(
            f.getAlcohol() != null ? f.getAlcohol().getNombre() : null,
            f.getRefresco().getNombre(),
            f.getAlternativa().name(),
            f.getModalidad().name());
    return new AsistenteFila(nombreDe(a), a.getEstado().name(), a.getUsuario() == null,
            bebida, f == null ? null : f.getCuota(), false);
}

private List<PersonaPagable> puedoPagarPor(Long usuarioId, Long eventoId,
        Map<Long, FichaBebida> fichaPorAsistencia) {
    List<PersonaPagable> out = new ArrayList<>();

    // Pareja + hijos mayores con cuenta.
    for (VinculoFamiliarService.Persona p : vinculoFamiliar.personasParaPago(usuarioId)) {
        asistencias.findByEventoIdAndUsuarioId(eventoId, p.id())
                .map(a -> fichaPorAsistencia.get(a.getId()))
                .map(FichaBebida::getCuota)
                .ifPresent(cuota -> out.add(new PersonaPagable(
                        p.nombre(), cuota, relacionDe(usuarioId, p.id()), p.id(), null)));
    }

    // Invitados añadidos a mano por mí.
    for (AsistenciaEvento a : asistencias.findByEventoIdAndUsuarioIsNullAndRegistradoPorId(eventoId, usuarioId)) {
        if (a.getEstado() == EstadoAsistencia.NO_VOY) continue;
        FichaBebida f = fichaPorAsistencia.get(a.getId());
        if (f == null || f.getCuota() == null) continue;
        out.add(new PersonaPagable(a.getNombre(), f.getCuota(), "INVITADO", null, a.getId()));
    }
    return out;
}

private String relacionDe(Long actuanteId, Long personaId) {
    // Si es la pareja del vínculo aceptado → PAREJA; si no, es un hijo mayor con cuenta → HIJO.
    boolean esPareja = vinculoParejaRepository
            .findBySolicitanteIdAndEstado(actuanteId, com.baniterio.api.identidad.EstadoVinculo.ACEPTADO)
            .map(v -> v.getParejaUsuario().getId().equals(personaId))
            .orElse(false)
        || vinculoParejaRepository
            .findByParejaUsuarioIdAndEstado(actuanteId, com.baniterio.api.identidad.EstadoVinculo.ACEPTADO)
            .map(v -> v.getSolicitante().getId().equals(personaId))
            .orElse(false);
    return esPareja ? "PAREJA" : "HIJO";
}
```

Inyectar `VinculoParejaRepository vinculoParejaRepository` en el constructor de `AsistenciaService` (import `com.baniterio.api.identidad.VinculoParejaRepository`). Confirmar que `findBySolicitanteIdAndEstado` / `findByParejaUsuarioIdAndEstado` existen (Task 1 los usa vía `VinculoFamiliarService` → sí).

> Nota de simplicidad: si `relacionDe` resulta enrevesado en revisión, una alternativa es que `VinculoFamiliarService.personasParaPago` devuelva un `record Persona(Long id, String nombre, String relacion)` con la relación ya puesta. Queda a criterio del implementador; el contrato JSON no cambia.

- [ ] **Step 6: Exponer el endpoint en `AsistenciaController`**

```java
@GetMapping("/{id}/asistentes")
public ListadoAsistentesResponse asistentes(@AuthenticationPrincipal UsuarioPrincipal principal,
        @PathVariable Long id) {
    return asistenciaService.listadoAsistentes(principal.id(), id);
}
```

Import `com.baniterio.api.evento.dto.ListadoAsistentesResponse`.

- [ ] **Step 7: Ejecutar el IT — debe pasar**

Run: `cd back && ./mvnw.cmd -q -Dtest=ListadoAsistentesIT test`
Expected: PASS (4/4).

- [ ] **Step 8: Suite backend completa**

Run: `cd back && ./mvnw.cmd -q test`
Expected: BUILD SUCCESS (el `reenviar_antes_de_48h` puede seguir fallando a propósito — ver spec 3c; si falla solo ese, es esperado).

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/evento/ back/src/main/java/com/baniterio/api/identidad/AsistenciaEventoRepository.java back/src/main/java/com/baniterio/api/identidad/FichaBebidaRepository.java back/src/test/java/com/baniterio/api/evento/ListadoAsistentesIT.java
git commit -m "feat(cuentas): endpoint GET /eventos/{id}/asistentes (listado + puedoPagarPor)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Web — tipos, servicio y modal "Listado de asistentes"

**Files:**
- Modify: `front/src/app/panel/eventos/eventos.types.ts`
- Modify: `front/src/app/panel/eventos/eventos.service.ts`
- Modify: `front/src/app/panel/eventos/eventos.service.spec.ts`
- Create: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.ts`
- Create: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.html`
- Create: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.spec.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.html`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.spec.ts`

**Interfaces:**
- Consumes: `GET /api/v1/eventos/{id}/asistentes` de Task 2.
- Produces:
  - Tipos: `MetodoPago = 'TRANSFERENCIA' | 'BIZUM' | 'EFECTIVO'`; `RelacionPago = 'PAREJA' | 'HIJO' | 'INVITADO'`; `BebidaFila`, `AsistenteFila`, `PersonaPagable`, `ListadoAsistentes`.
  - `EventosService.asistentesEvento(id: number): Observable<ListadoAsistentes>`
  - Componente `<app-modal-asistentes [eventoId]="n" (cerrar)="...">`.
  - En `evento-detalle`: señal `modalAsistentes = signal(false)` y botón "Listado de asistentes".

- [ ] **Step 1: Añadir los tipos**

Al final de `eventos.types.ts`, antes del `export type CodigoErrorEvento`:

```typescript
/** Método de pago declarado (pieza 4). */
export type MetodoPago = 'TRANSFERENCIA' | 'BIZUM' | 'EFECTIVO';

/** Relación de una persona que puedo incluir en mi pago. */
export type RelacionPago = 'PAREJA' | 'HIJO' | 'INVITADO';

/** Lo que bebe un asistente, en el listado. `alcohol` null = no bebe alcohol. */
export interface BebidaFila {
  alcohol: string | null;
  refresco: string;
  alternativa: Alternativa;
  modalidad: Modalidad;
}

/** Una fila del listado de asistentes. `estado` es APUNTADO o EN_DUDA. */
export interface AsistenteFila {
  nombre: string;
  estado: 'APUNTADO' | 'EN_DUDA';
  esManual: boolean;
  bebida: BebidaFila | null;
  cuota: number | null;
  pagado: boolean;
}

/** Alguien a quien puedo incluir en mi pago. */
export interface PersonaPagable {
  nombre: string;
  cuota: number;
  relacion: RelacionPago;
  usuarioId: number | null;
  asistenciaId: number | null;
}

/** `GET /api/v1/eventos/{id}/asistentes`. */
export interface ListadoAsistentes {
  asistentes: AsistenteFila[];
  totalCuotas: number;
  totalPagado: number;
  puedoPagarPor: PersonaPagable[];
  miCuota: number | null;
}
```

- [ ] **Step 2: Escribir el test del servicio (falla)**

En `eventos.service.spec.ts`, añadir dentro del `describe` existente (mira cómo montan `TestBed` con `provideHttpClient`/`provideHttpClientTesting` y `httpMock.expectOne`):

```typescript
it('asistentesEvento hace GET al endpoint de asistentes', () => {
  const servicio = TestBed.inject(EventosService);
  servicio.asistentesEvento(7).subscribe();
  const req = httpMock.expectOne(`${base}/eventos/7/asistentes`);
  expect(req.request.method).toBe('GET');
  req.flush({ asistentes: [], totalCuotas: 0, totalPagado: 0, puedoPagarPor: [], miCuota: null });
});
```

- [ ] **Step 3: Ejecutar — falla**

Run: `cd front && npx ng test --watch=false -t "asistentesEvento"`
Expected: FAIL — `asistentesEvento` no existe.

- [ ] **Step 4: Implementar el método del servicio**

En `eventos.service.ts`, importar `ListadoAsistentes` y añadir tras `listarOcultos()`:

```typescript
// --- Listado de asistentes y pago (pieza 4) ---

/** Quién va a un evento de San Miguel, qué bebe, su cuota y su estado de pago. */
asistentesEvento(id: number): Observable<ListadoAsistentes> {
  return this.http.get<ListadoAsistentes>(`${this.base}/eventos/${id}/asistentes`);
}
```

- [ ] **Step 5: Ejecutar — pasa**

Run: `cd front && npx ng test --watch=false -t "asistentesEvento"`
Expected: PASS.

- [ ] **Step 6: Escribir el spec del modal (falla)**

Crear `modal-asistentes/modal-asistentes.spec.ts`:

```typescript
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ModalAsistentes } from './modal-asistentes';

describe('ModalAsistentes', () => {
  let fixture: ComponentFixture<ModalAsistentes>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ModalAsistentes],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ModalAsistentes);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 3);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('carga el listado y pinta una fila por asistente con su cuota', () => {
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [
        { nombre: 'Ana', estado: 'APUNTADO', esManual: false,
          bebida: { alcohol: 'Barceló', refresco: 'Coca-Cola', alternativa: 'NADA', modalidad: 'COMPLETA' },
          cuota: 45, pagado: false },
        { nombre: 'Luis', estado: 'EN_DUDA', esManual: true, bebida: null, cuota: null, pagado: false },
      ],
      totalCuotas: 45, totalPagado: 0, puedoPagarPor: [], miCuota: 45,
    });
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Ana');
    expect(texto).toContain('45');
    expect(texto).toContain('Luis');
    expect(texto).toContain('en duda');
  });
});
```

- [ ] **Step 7: Ejecutar — falla**

Run: `cd front && npx ng test --watch=false -t "ModalAsistentes"`
Expected: FAIL — no existe el componente.

- [ ] **Step 8: Implementar el modal**

`modal-asistentes/modal-asistentes.ts`:

```typescript
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { AsistenteFila, ListadoAsistentes } from '../eventos.types';
import { EventosService } from '../eventos.service';

const MODALIDAD: Record<string, string> = {
  COMPLETA: 'peña completa',
  SOLO_CERVEZA: 'solo cerveza',
  UN_DIA: 'un día',
  EMBARAZADA: 'embarazada',
};
const ALTERNATIVA: Record<string, string> = {
  CERVEZA: 'cerveza',
  TINTO_VERANO: 'tinto de verano',
  NADA: 'nada',
  CERVEZA_ESPECIAL: 'cerveza especial',
};

/**
 * Modal (overlay a pantalla completa, como `ModalRespuestaEvento`) con la lista
 * de quién va a un evento de San Miguel: nombre, estado, qué bebe, su cuota y si
 * ha pagado. Solo lectura; lo abre cualquier miembro desde el detalle del evento.
 */
@Component({
  selector: 'app-modal-asistentes',
  templateUrl: './modal-asistentes.html',
})
export class ModalAsistentes implements OnInit {
  private readonly eventosService = inject(EventosService);

  readonly eventoId = input.required<number>();
  readonly cerrar = output<void>();

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly datos = signal<ListadoAsistentes | null>(null);

  ngOnInit(): void {
    this.eventosService.asistentesEvento(this.eventoId()).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected bebidaTexto(a: AsistenteFila): string {
    if (!a.bebida) return '—';
    const alc = a.bebida.alcohol ?? 'sin alcohol';
    return `${alc} · ${a.bebida.refresco} · ${ALTERNATIVA[a.bebida.alternativa]} (${MODALIDAD[a.bebida.modalidad]})`;
  }
}
```

`modal-asistentes/modal-asistentes.html`:

```html
<div
  class="fixed inset-0 z-50 flex items-center justify-center bg-surface/90 p-4 backdrop-blur-sm"
  role="dialog"
  aria-label="Listado de asistentes"
>
  <div class="carta-relieve flex max-h-[85vh] w-full max-w-lg flex-col rounded-2xl p-6">
    <div class="flex items-start justify-between">
      <h2 class="font-display text-xl font-extrabold">Asistentes</h2>
      <button
        type="button"
        (click)="cerrar.emit()"
        aria-label="Cerrar"
        class="rounded-lg border border-outline px-3 py-1 text-sm text-ink transition hover:border-brand-bright/60"
      >
        Cerrar
      </button>
    </div>

    @switch (estado()) {
      @case ('cargando') {
        <p class="mt-4 text-sm text-muted">Cargando…</p>
      }
      @case ('error') {
        <p class="mt-4 text-sm text-red-300">No se ha podido cargar la lista.</p>
      }
      @case ('listo') {
        @if (datos(); as d) {
          <ul class="mt-4 flex-1 space-y-2 overflow-y-auto">
            @for (a of d.asistentes; track $index) {
              <li class="rounded-xl border border-outline/40 bg-brand/10 p-3">
                <div class="flex items-baseline justify-between gap-2">
                  <span class="text-sm font-semibold" [class.text-gold-soft]="a.estado === 'EN_DUDA'">
                    {{ a.nombre }}
                    @if (a.estado === 'EN_DUDA') { <span class="text-xs">(en duda)</span> }
                    @if (a.esManual) { <span class="text-xs text-muted">· invitado</span> }
                  </span>
                  <span class="shrink-0 text-sm font-bold text-gold-soft">
                    {{ a.cuota != null ? a.cuota + ' €' : 'sin cuota' }}
                    · {{ a.pagado ? 'pagado' : 'pendiente' }}
                  </span>
                </div>
                <p class="mt-1 text-xs text-muted">{{ bebidaTexto(a) }}</p>
              </li>
            }
          </ul>
          <p class="mt-3 border-t border-outline/40 pt-3 text-sm">
            Total cuotas: <strong>{{ d.totalCuotas }} €</strong> ·
            pagado: <strong>{{ d.totalPagado }} €</strong>
          </p>
        }
      }
    }
  </div>
</div>
```

- [ ] **Step 9: Ejecutar — pasa**

Run: `cd front && npx ng test --watch=false -t "ModalAsistentes"`
Expected: PASS.

- [ ] **Step 10: Botón en el detalle del evento**

En `evento-detalle.ts`: importar `ModalAsistentes`, añadirlo a `imports` del `@Component`, y añadir la señal:

```typescript
protected readonly modalAsistentes = signal(false);
```

En `evento-detalle.html`, dentro del `@if (e.asistencia.ficha.miFicha; as f)` **no** — el listado lo ve cualquiera, así que va fuera de ese bloque. Justo después del bloque `@if (e.asistencia.ficha.miFicha; as f) { … }` (línea ~117), y solo si el evento lleva ficha:

```html
@if (e.asistencia.ficha.llevaFicha) {
  <div class="mt-3 flex flex-wrap gap-2">
    <button
      type="button"
      (click)="modalAsistentes.set(true)"
      class="rounded-xl border border-outline px-4 py-2 text-sm font-medium text-ink transition hover:border-brand-bright/60"
    >
      Listado de asistentes
    </button>
  </div>
}
```

Y al final de la `<section>`, fuera de la `<article>`:

```html
@if (modalAsistentes() && evento(); as e) {
  <app-modal-asistentes [eventoId]="e.id" (cerrar)="modalAsistentes.set(false)" />
}
```

- [ ] **Step 11: Test del botón en `evento-detalle.spec.ts`**

Añadir un caso (mira cómo el spec monta `crear()` y hace `httpMock.expectOne(\`${base}/eventos/5\`).flush(...)` con un `EventoDetalle` de prueba; el `asistenciaBase` del fichero necesita `ficha: { llevaFicha: true, diasEvento: [...], miFicha: null }`):

```typescript
it('muestra "Listado de asistentes" en un evento de San Miguel', () => {
  crear();
  httpMock.expectOne(`${base}/eventos/5`).flush(eventoDetalle({
    asistencia: { ...asistenciaBase, ficha: { llevaFicha: true, diasEvento: ['2026-09-25','2026-09-26'], miFicha: null } },
  }));
  fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Listado de asistentes');
});

it('no muestra "Listado de asistentes" si el evento no lleva ficha', () => {
  crear();
  httpMock.expectOne(`${base}/eventos/5`).flush(eventoDetalle({
    asistencia: { ...asistenciaBase, ficha: { llevaFicha: false, diasEvento: [], miFicha: null } },
  }));
  fixture.detectChanges();
  expect(fixture.nativeElement.textContent).not.toContain('Listado de asistentes');
});
```

Si no existe un helper `eventoDetalle(...)` en el spec, usar el objeto literal completo que ya use el fichero para el `flush` y ajustar `asistencia.ficha`.

- [ ] **Step 12: Ejecutar los specs tocados**

Run: `cd front && npx ng test --watch=false -t "EventoDetalle"` y `-t "EventosService"`
Expected: PASS.

- [ ] **Step 13: Commit**

```bash
git add front/src/app/panel/eventos/eventos.types.ts front/src/app/panel/eventos/eventos.service.ts front/src/app/panel/eventos/eventos.service.spec.ts front/src/app/panel/eventos/modal-asistentes/ front/src/app/panel/eventos/evento-detalle/
git commit -m "feat(cuentas): modal listado de asistentes en el detalle del evento (web)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Web — modal "He pagado"

**Files:**
- Create: `front/src/app/panel/eventos/modal-he-pagado/modal-he-pagado.ts`
- Create: `front/src/app/panel/eventos/modal-he-pagado/modal-he-pagado.html`
- Create: `front/src/app/panel/eventos/modal-he-pagado/modal-he-pagado.spec.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.html`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.spec.ts`

**Interfaces:**
- Consumes: `ListadoAsistentes` (para `miCuota` y `puedoPagarPor`) de Task 3; `MetodoPago`, `PersonaPagable`.
- Produces:
  - Componente `<app-modal-he-pagado [eventoId]="n" (cerrar)="..." (enviado)="...">`.
  - Objeto emitido en `enviado`: `{ importe: number; metodo: MetodoPago; cubre: { yo: boolean; usuarioIds: number[]; asistenciaIds: number[] } }` — **el padre solo muestra un aviso, no llama al backend.**

- [ ] **Step 1: Escribir el spec (falla)**

`modal-he-pagado/modal-he-pagado.spec.ts`:

```typescript
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ModalHePagado } from './modal-he-pagado';

describe('ModalHePagado', () => {
  let fixture: ComponentFixture<ModalHePagado>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  function montar() {
    TestBed.configureTestingModule({
      imports: [ModalHePagado],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ModalHePagado);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('eventoId', 3);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/eventos/3/asistentes`).flush({
      asistentes: [], totalCuotas: 0, totalPagado: 0, miCuota: 45,
      puedoPagarPor: [
        { nombre: 'Ana', cuota: 45, relacion: 'PAREJA', usuarioId: 8, asistenciaId: null },
      ],
    });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('Enviar está deshabilitado hasta que hay importe y método', () => {
    montar();
    const boton = () => fixture.nativeElement.querySelector('button[data-test="enviar"]') as HTMLButtonElement;
    expect(boton().disabled).toBe(true);
  });

  it('al enviar emite importe, método y a quién cubre', () => {
    montar();
    const comp = fixture.componentInstance as unknown as {
      importe: { set: (n: number) => void };
      metodo: { set: (m: string) => void };
      marcarUsuario: (id: number) => void;
      enviar: () => void;
    };
    let emitido: unknown;
    fixture.componentInstance.enviado.subscribe((e: unknown) => (emitido = e));
    comp.importe.set(90);
    comp.metodo.set('BIZUM');
    comp.marcarUsuario(8);
    comp.enviar();
    expect(emitido).toEqual({
      importe: 90,
      metodo: 'BIZUM',
      cubre: { yo: true, usuarioIds: [8], asistenciaIds: [] },
    });
  });
});
```

- [ ] **Step 2: Ejecutar — falla**

Run: `cd front && npx ng test --watch=false -t "ModalHePagado"`
Expected: FAIL — no existe.

- [ ] **Step 3: Implementar el componente**

`modal-he-pagado/modal-he-pagado.ts`:

```typescript
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { MetodoPago, PersonaPagable } from '../eventos.types';
import { EventosService } from '../eventos.service';

interface PagoDeclarado {
  importe: number;
  metodo: MetodoPago;
  cubre: { yo: boolean; usuarioIds: number[]; asistenciaIds: number[] };
}

const METODOS: { valor: MetodoPago; texto: string }[] = [
  { valor: 'TRANSFERENCIA', texto: 'Transferencia a la cuenta de la peña' },
  { valor: 'BIZUM', texto: 'Bizum al administrador' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
];
const RELACION: Record<string, string> = { PAREJA: 'pareja', HIJO: 'hijo/a', INVITADO: 'invitado/a' };

/**
 * Modal para declarar un pago: importe, a quién cubre (uno mismo siempre, más
 * pareja / hijos mayores con cuenta / invitados propios) y método. En esta tanda
 * NO persiste: valida y emite `(enviado)` con el objeto; el detalle solo muestra
 * un aviso. La lógica de confirmación por el admin es una tanda posterior.
 */
@Component({
  selector: 'app-modal-he-pagado',
  templateUrl: './modal-he-pagado.html',
})
export class ModalHePagado implements OnInit {
  private readonly eventosService = inject(EventosService);

  readonly eventoId = input.required<number>();
  readonly cerrar = output<void>();
  readonly enviado = output<PagoDeclarado>();

  protected readonly metodos = METODOS;
  protected readonly relacionTexto = RELACION;
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly miCuota = signal<number | null>(null);
  protected readonly personas = signal<PersonaPagable[]>([]);

  protected readonly importe = signal<number>(0);
  protected readonly metodo = signal<MetodoPago | null>(null);
  protected readonly usuariosMarcados = signal<Set<number>>(new Set());
  protected readonly asistenciasMarcadas = signal<Set<number>>(new Set());

  /** Suma sugerida: mi cuota + la de cada persona marcada. */
  protected readonly sugerido = computed(() => {
    let total = this.miCuota() ?? 0;
    for (const p of this.personas()) {
      if (p.usuarioId != null && this.usuariosMarcados().has(p.usuarioId)) total += p.cuota;
      if (p.asistenciaId != null && this.asistenciasMarcadas().has(p.asistenciaId)) total += p.cuota;
    }
    return total;
  });

  protected readonly puedeEnviar = computed(() => this.importe() > 0 && this.metodo() != null);

  ngOnInit(): void {
    this.eventosService.asistentesEvento(this.eventoId()).subscribe({
      next: (d) => {
        this.miCuota.set(d.miCuota);
        this.personas.set(d.puedoPagarPor);
        this.importe.set(d.miCuota ?? 0);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected cambiarImporte(e: Event): void {
    this.importe.set(Number((e.target as HTMLInputElement).value) || 0);
  }

  protected marcarUsuario(id: number): void {
    this.usuariosMarcados.update((s) => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
    this.importe.set(this.sugerido());
  }

  protected marcarAsistencia(id: number): void {
    this.asistenciasMarcadas.update((s) => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
    this.importe.set(this.sugerido());
  }

  protected enviar(): void {
    if (!this.puedeEnviar()) return;
    this.enviado.emit({
      importe: this.importe(),
      metodo: this.metodo()!,
      cubre: {
        yo: true,
        usuarioIds: [...this.usuariosMarcados()],
        asistenciaIds: [...this.asistenciasMarcadas()],
      },
    });
  }
}
```

`modal-he-pagado/modal-he-pagado.html`:

```html
<div
  class="fixed inset-0 z-50 flex items-center justify-center bg-surface/90 p-4 backdrop-blur-sm"
  role="dialog"
  aria-label="He pagado"
>
  <div class="carta-relieve flex max-h-[85vh] w-full max-w-md flex-col rounded-2xl p-6">
    <div class="flex items-start justify-between">
      <h2 class="font-display text-xl font-extrabold">He pagado</h2>
      <button
        type="button"
        (click)="cerrar.emit()"
        aria-label="Cerrar"
        class="rounded-lg border border-outline px-3 py-1 text-sm text-ink transition hover:border-brand-bright/60"
      >
        Cerrar
      </button>
    </div>

    @switch (estado()) {
      @case ('cargando') { <p class="mt-4 text-sm text-muted">Cargando…</p> }
      @case ('error') { <p class="mt-4 text-sm text-red-300">No se ha podido cargar.</p> }
      @case ('listo') {
        <div class="mt-4 flex-1 space-y-4 overflow-y-auto">
          <div>
            <p class="text-sm font-semibold">¿A quién pagas?</p>
            <label class="mt-2 flex items-center gap-2 text-sm">
              <input type="checkbox" checked disabled />
              Yo @if (miCuota() != null) { <span class="text-muted">({{ miCuota() }} €)</span> }
            </label>
            @for (p of personas(); track $index) {
              <label class="mt-1 flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  (change)="p.usuarioId != null ? marcarUsuario(p.usuarioId) : marcarAsistencia(p.asistenciaId!)"
                />
                {{ p.nombre }}
                <span class="text-muted">({{ relacionTexto[p.relacion] }} · {{ p.cuota }} €)</span>
              </label>
            }
          </div>

          <div>
            <label class="text-sm font-semibold" for="importe-pago">Importe pagado (€)</label>
            <input
              id="importe-pago"
              type="number"
              min="0"
              step="0.01"
              [value]="importe()"
              (input)="cambiarImporte($event)"
              class="mt-1 w-full rounded-xl border border-outline bg-transparent px-3 py-2 text-sm"
            />
          </div>

          <div>
            <p class="text-sm font-semibold">Cómo lo has pagado</p>
            @for (m of metodos; track m.valor) {
              <label class="mt-1 flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  name="metodo-pago"
                  [checked]="metodo() === m.valor"
                  (change)="metodo.set(m.valor)"
                />
                {{ m.texto }}
              </label>
            }
          </div>
        </div>

        <div class="mt-4 flex gap-2 border-t border-outline/40 pt-4">
          <button
            type="button"
            data-test="enviar"
            (click)="enviar()"
            [disabled]="!puedeEnviar()"
            class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-gold transition hover:bg-brand-dark disabled:opacity-50"
          >
            Enviar
          </button>
          <button
            type="button"
            (click)="cerrar.emit()"
            class="rounded-xl border border-outline px-4 py-2 text-sm font-medium text-ink"
          >
            Cancelar
          </button>
        </div>
      }
    }
  </div>
</div>
```

- [ ] **Step 4: Ejecutar — pasa**

Run: `cd front && npx ng test --watch=false -t "ModalHePagado"`
Expected: PASS.

- [ ] **Step 5: Botón en el detalle**

En `evento-detalle.ts` añadir `import { ModalHePagado }`, meterlo en `imports`, y:

```typescript
protected readonly modalPago = signal(false);

protected onPagoEnviado(): void {
  this.modalPago.set(false);
  this.aviso.set('Pago enviado. Un administrador lo confirmará.');
}
```

En `evento-detalle.html`, dentro del `@if (e.asistencia.ficha.miFicha; as f)`, junto al botón "Cambiar lo que voy a beber" (solo si hay cuota) — envolver ambos en un contenedor flex:

```html
@if (f.cuota != null) {
  <button
    type="button"
    (click)="modalPago.set(true)"
    class="mt-2 rounded-xl border border-outline px-4 py-2 text-sm font-medium text-ink transition hover:border-brand-bright/60"
  >
    He pagado
  </button>
}
```

Y al final de la `<section>`, junto al de asistentes:

```html
@if (modalPago() && evento(); as e) {
  <app-modal-he-pagado
    [eventoId]="e.id"
    (cerrar)="modalPago.set(false)"
    (enviado)="onPagoEnviado()"
  />
}
```

- [ ] **Step 6: Test del botón en `evento-detalle.spec.ts`**

```typescript
it('muestra "He pagado" solo si tengo cuota', () => {
  crear();
  httpMock.expectOne(`${base}/eventos/5`).flush(eventoDetalle({
    asistencia: { ...asistenciaBase, ficha: { llevaFicha: true, diasEvento: ['2026-09-25','2026-09-26'],
      miFicha: { ...fichaMiaBase, cuota: 45, cuotaPendiente: false } } },
  }));
  fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('He pagado');
});
```

(`fichaMiaBase` = un `FichaBebidaMia` de prueba; si no existe en el spec, escribir el literal completo con todos los campos de la interfaz `FichaBebidaMia`.)

- [ ] **Step 7: Suite web completa + build**

Run: `cd front && npx ng test --watch=false` y luego `npx ng build`
Expected: todos los tests PASS; build sin superar el presupuesto (si lo supera por poco, subir el budget en `angular.json` como se hizo en `859c6e3`, comentándolo en el commit).

- [ ] **Step 8: Commit**

```bash
git add front/src/app/panel/eventos/modal-he-pagado/ front/src/app/panel/eventos/evento-detalle/ front/angular.json
git commit -m "feat(cuentas): modal \"He pagado\" en el detalle del evento (web, sin persistir)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Móvil — DTOs y `EventosRepository.asistentes`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/EventoDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/EventosRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/EventosRepositoryImpl.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/EventosRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `GET /api/v1/eventos/{id}/asistentes` de Task 2.
- Produces:
  - DTOs `@Serializable`: `ListadoAsistentesDto`, `AsistenteFilaDto`, `BebidaFilaDto`, `PersonaPagableDto` (enums como `String`).
  - `EventosRepository.asistentes(eventoId: Long): ResultadoEvento<ListadoAsistentesDto>`

- [ ] **Step 1: Escribir el test (falla)**

En `EventosRepositoryImplTest.kt`, añadir:

```kotlin
@Test
fun asistentes_hace_get_con_bearer_y_deserializa() = runTest {
    val (r, vistas) = repo(cuerpoRespuesta = """
        {"asistentes":[
          {"nombre":"Ana","estado":"APUNTADO","esManual":false,
           "bebida":{"alcohol":"Barceló","refresco":"Coca-Cola","alternativa":"NADA","modalidad":"COMPLETA"},
           "cuota":45.0,"pagado":false}],
         "totalCuotas":45.0,"totalPagado":0.0,"miCuota":45.0,
         "puedoPagarPor":[
          {"nombre":"Luis","cuota":45.0,"relacion":"HIJO","usuarioId":9,"asistenciaId":null}]}
    """.trimIndent())
    val res = r.asistentes(3)
    assertIs<ResultadoEvento.Exito<*>>(res)
    assertEquals("GET", vistas[0].metodo)
    assertEquals("/api/v1/eventos/3/asistentes", vistas[0].path)
    assertEquals("Bearer jwt-x", vistas[0].auth)
    val dto = (res as ResultadoEvento.Exito).valor
    assertEquals(1, dto.asistentes.size)
    assertEquals("Ana", dto.asistentes[0].nombre)
    assertEquals("HIJO", dto.puedoPagarPor[0].relacion)
}
```

- [ ] **Step 2: Ejecutar — falla**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest --tests "*EventosRepositoryImplTest.asistentes*"`
Expected: FAIL — no compila (`asistentes` no existe).

- [ ] **Step 3: Añadir los DTOs**

En `EventoDtos.kt`, tras `AsistenciaResumenDto` (línea ~128):

```kotlin
/** Lo que bebe un asistente en el listado. `alcohol` null = no bebe alcohol. */
@Serializable
data class BebidaFilaDto(
    val alcohol: String? = null,
    val refresco: String,
    val alternativa: String,
    val modalidad: String,
)

/** Una fila del listado de asistentes. `estado` = APUNTADO | EN_DUDA. */
@Serializable
data class AsistenteFilaDto(
    val nombre: String,
    val estado: String,
    val esManual: Boolean,
    val bebida: BebidaFilaDto? = null,
    val cuota: Double? = null,
    val pagado: Boolean = false,
)

/** Alguien a quien puedo incluir en mi pago. `relacion` = PAREJA | HIJO | INVITADO. */
@Serializable
data class PersonaPagableDto(
    val nombre: String,
    val cuota: Double,
    val relacion: String,
    val usuarioId: Long? = null,
    val asistenciaId: Long? = null,
)

/** `GET /eventos/{id}/asistentes` (pieza 4). */
@Serializable
data class ListadoAsistentesDto(
    val asistentes: List<AsistenteFilaDto> = emptyList(),
    val totalCuotas: Double = 0.0,
    val totalPagado: Double = 0.0,
    val puedoPagarPor: List<PersonaPagableDto> = emptyList(),
    val miCuota: Double? = null,
)
```

- [ ] **Step 4: Método en la interfaz y en la impl**

`EventosRepository.kt` — añadir a la interfaz (mira dónde está `listarOcultos`):

```kotlin
/** Listado de asistentes de un evento de San Miguel (pieza 4). Solo lectura. */
suspend fun asistentes(eventoId: Long): ResultadoEvento<ListadoAsistentesDto>
```

`EventosRepositoryImpl.kt` — añadir tras `listarOcultos()`:

```kotlin
override suspend fun asistentes(eventoId: Long): ResultadoEvento<ListadoAsistentesDto> = peticion {
    http.get("$API_BASE_URL/eventos/$eventoId/asistentes") { auth() }.body()
}
```

Import `com.baniterio.app.data.dto.ListadoAsistentesDto` en ambos.

- [ ] **Step 5: Ejecutar — pasa**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest --tests "*EventosRepositoryImplTest.asistentes*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/
git commit -m "feat(cuentas): DTOs y repo del listado de asistentes (móvil)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Móvil — diálogos y botones en `EventoDetalleScreen`

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/ListadoAsistentesDialog.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/HePagadoDialog.kt`

**Interfaces:**
- Consumes: `EventosRepository.asistentes(Long)` de Task 5; DTOs de Task 5.
- Produces: dos `@Composable` de diálogo; dos botones en `EventoDetalleScreen` visibles ⟺ `detalle.asistencia.ficha.llevaFicha` (y "He pagado" además ⟺ `miFicha?.cuota != null`).

- [ ] **Step 1: Leer el sitio donde van los botones**

Abrir `EventoDetalleScreen.kt` y localizar el bloque de "Cambiar lo que voy a beber" (busca `llevaFicha` / `miFicha` / el texto del botón). Los dos botones nuevos van en esa misma zona. Anotar cómo se obtiene el `EventosRepository` (parámetro de la pantalla o `Dependencias`) y el patrón de estado (`var ... by remember { mutableStateOf(...) }` + `LaunchedEffect`).

- [ ] **Step 2: `ListadoAsistentesDialog.kt`**

```kotlin
package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.AsistenteFilaDto
import com.baniterio.app.data.dto.ListadoAsistentesDto

private fun modalidadTexto(m: String) = when (m) {
    "COMPLETA" -> "peña completa"; "SOLO_CERVEZA" -> "solo cerveza"
    "UN_DIA" -> "un día"; "EMBARAZADA" -> "embarazada"; else -> m
}

private fun bebidaTexto(a: AsistenteFilaDto): String {
    val b = a.bebida ?: return "—"
    val alc = b.alcohol ?: "sin alcohol"
    return "$alc · ${b.refresco} (${modalidadTexto(b.modalidad)})"
}

/** Diálogo de solo lectura con la lista de asistentes a un evento de San Miguel. */
@Composable
fun ListadoAsistentesDialog(
    eventoId: Long,
    repo: EventosRepository,
    onCerrar: () -> Unit,
) {
    var datos by remember { mutableStateOf<ListadoAsistentesDto?>(null) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(eventoId) {
        when (val r = repo.asistentes(eventoId)) {
            is ResultadoEvento.Exito -> datos = r.valor
            is ResultadoEvento.Error -> error = true
        }
    }

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        title = { Text("Asistentes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    error -> Text("No se ha podido cargar la lista.")
                    datos == null -> Text("Cargando…")
                    else -> {
                        datos!!.asistentes.forEach { a ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    buildString {
                                        append(a.nombre)
                                        if (a.estado == "EN_DUDA") append(" (en duda)")
                                        if (a.esManual) append(" · invitado")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    (a.cuota?.let { "$it €" } ?: "sin cuota") +
                                        " · " + (if (a.pagado) "pagado" else "pendiente"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(bebidaTexto(a), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Total cuotas: ${datos!!.totalCuotas} € · pagado: ${datos!!.totalPagado} €")
                    }
                }
            }
        },
    )
}
```

Ajustar imports/tipos al patrón real del repo (`ResultadoEvento.Exito.valor` vs `.data` — comprobar en `ResultadoEvento.kt`).

- [ ] **Step 3: `HePagadoDialog.kt`**

```kotlin
package com.baniterio.app.ui.eventos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.ListadoAsistentesDto

/** El objeto que se enviaría (en esta tanda NO se manda a ningún sitio). */
data class PagoDeclarado(
    val importe: Double,
    val metodo: String,
    val cubreUsuarioIds: List<Long>,
    val cubreAsistenciaIds: List<Long>,
)

private val METODOS = listOf(
    "TRANSFERENCIA" to "Transferencia a la cuenta de la peña",
    "BIZUM" to "Bizum al administrador",
    "EFECTIVO" to "Efectivo",
)
private fun relacionTexto(r: String) = when (r) {
    "PAREJA" -> "pareja"; "HIJO" -> "hijo/a"; "INVITADO" -> "invitado/a"; else -> r
}

/**
 * Diálogo para declarar un pago. En esta tanda no persiste: valida y llama a
 * [onEnviar] con el objeto; la pantalla solo muestra un aviso.
 */
@Composable
fun HePagadoDialog(
    eventoId: Long,
    repo: EventosRepository,
    onEnviar: (PagoDeclarado) -> Unit,
    onCerrar: () -> Unit,
) {
    var datos by remember { mutableStateOf<ListadoAsistentesDto?>(null) }
    var importe by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf<String?>(null) }
    val usuarioIds = remember { mutableStateListOf<Long>() }
    val asistenciaIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(eventoId) {
        (repo.asistentes(eventoId) as? ResultadoEvento.Exito)?.valor?.let {
            datos = it
            importe = (it.miCuota ?: 0.0).toString()
        }
    }

    val puedeEnviar = (importe.toDoubleOrNull() ?: 0.0) > 0.0 && metodo != null

    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(
                enabled = puedeEnviar,
                onClick = {
                    onEnviar(
                        PagoDeclarado(
                            importe = importe.toDouble(),
                            metodo = metodo!!,
                            cubreUsuarioIds = usuarioIds.toList(),
                            cubreAsistenciaIds = asistenciaIds.toList(),
                        )
                    )
                },
            ) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
        title = { Text("He pagado") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val d = datos
                if (d == null) {
                    Text("Cargando…")
                } else {
                    Text("¿A quién pagas?", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = true, onCheckedChange = null, enabled = false)
                        Text("Yo" + (d.miCuota?.let { " ($it €)" } ?: ""))
                    }
                    d.puedoPagarPor.forEach { p ->
                        val id = p.usuarioId ?: p.asistenciaId ?: return@forEach
                        val lista = if (p.usuarioId != null) usuarioIds else asistenciaIds
                        Row(
                            Modifier.toggleable(
                                value = lista.contains(id),
                                onValueChange = { on -> if (on) lista.add(id) else lista.remove(id) },
                            ),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = lista.contains(id), onCheckedChange = null)
                            Text("${p.nombre} (${relacionTexto(p.relacion)} · ${p.cuota} €)")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importe,
                        onValueChange = { importe = it },
                        label = { Text("Importe pagado (€)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Cómo lo has pagado", style = MaterialTheme.typography.bodyMedium)
                    METODOS.forEach { (valor, texto) ->
                        Row(
                            Modifier.selectable(selected = metodo == valor, onClick = { metodo = valor }),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = metodo == valor, onClick = null)
                            Text(texto)
                        }
                    }
                }
            }
        },
    )
}
```

Ajustar `ResultadoEvento.Exito` (`.valor` vs `.data`), `Alignment` import y la firma de `KeyboardOptions` al estilo del repo (mirar `FichaBebidaForm.kt`, que ya usa `OutlinedTextField` y dropdowns).

- [ ] **Step 4: Botones en `EventoDetalleScreen`**

En la zona de la ficha, tras el botón "Cambiar lo que voy a beber":

```kotlin
if (detalle.asistencia.ficha.llevaFicha) {
    var verAsistentes by remember { mutableStateOf(false) }
    var verPago by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { verAsistentes = true }) { Text("Listado de asistentes") }
    if (detalle.asistencia.ficha.miFicha?.cuota != null) {
        OutlinedButton(onClick = { verPago = true }) { Text("He pagado") }
    }

    if (verAsistentes) {
        ListadoAsistentesDialog(detalle.id, repo, onCerrar = { verAsistentes = false })
    }
    if (verPago) {
        HePagadoDialog(
            eventoId = detalle.id,
            repo = repo,
            onEnviar = {
                verPago = false
                // TODO(pagos): cuando se defina el modelo de pagos, mandar 'it' al backend.
                aviso = "Pago enviado. Un administrador lo confirmará."
            },
            onCerrar = { verPago = false },
        )
    }
}
```

Usar el nombre real del repo (`eventosRepo`, `repo`…) y de la variable de aviso/snackbar que ya tenga la pantalla. Si la pantalla no recibe el `EventosRepository`, añadirlo como parámetro y pasarlo desde `App.kt` (mira cómo se le pasan `asistenciaRepo`/`bebidaRepo` a otras pantallas).

- [ ] **Step 5: Compilar y pasar los tests**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest :shared:compileCommonMainKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/
git commit -m "feat(cuentas): diálogos de listado de asistentes y \"He pagado\" (móvil)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: Verificación final y notas

**Files:** ninguno (solo comprobación).

- [ ] **Step 1: Las tres suites**

```bash
cd back && ./mvnw.cmd -q test
cd ../front && npx ng test --watch=false && npx ng build
cd ../mobile && ./gradlew :shared:testAndroidHostTest
```

Expected: todo verde (salvo el `reenviar_antes_de_48h` del backend, fallo conocido y a propósito).

- [ ] **Step 2: Humo manual (lo hace el usuario)**

Dejar anotado para el usuario:
- Web (`localhost:4200`) y emulador Android: abrir un evento de **San Miguel** futuro, responder "Me apunto", rellenar la ficha.
- Comprobar que salen "Listado de asistentes" (siempre) y "He pagado" (solo con cuota).
- "Listado de asistentes" → modal con la lista, cuotas y totales; los "no voy" no aparecen.
- "He pagado" → marcar pareja/hijo/invitado si los hay, el importe se ajusta, elegir método, Enviar → aviso "un administrador lo confirmará", nada se guarda (recargar y sigue "pendiente").
- En un evento que **no** es de San Miguel: los dos botones no aparecen.

- [ ] **Step 3: Actualizar memoria**

Añadir a `memory/baniterio_cuentas.md` una línea bajo la pieza 4: hecho el listado de asistentes + modal "He pagado" (web + móvil + endpoint de lectura), sin persistir el pago, en `feature/cuentas`; pendiente el modelo de pagos y la confirmación del admin.

---

## Self-Review

**1. Cobertura del spec:**
- Listado de asistentes (endpoint + modal web + diálogo móvil) → Tasks 2, 3, 6. ✓
- "He pagado" (modal web + diálogo móvil, sin persistir) → Tasks 4, 6. ✓
- `puedoPagarPor` (pareja / hijos ≥18 con cuenta / invitados propios) → Tasks 1, 2. ✓
- Solo San Miguel, botones ⟺ `llevaFicha` → Tasks 3, 4, 6. ✓
- "He pagado" solo con cuota → Tasks 4, 6. ✓
- 409 `EVENTO_SIN_FICHA` → Task 2 (IT). ✓
- Métodos de pago transferencia/bizum/efectivo → Tasks 4, 6. ✓
- Totales del pie → Tasks 2, 3. ✓
- Casos límite (sin cuotas, sin ficha, `puedoPagarPor` vacío, hijo menor) → Tasks 1, 2 (IT) + UI tolera `null`. ✓
- Sin migración / sin entidad / sin escritura → respetado en todas las tareas.

**2. Placeholders:** el `TODO(pagos)` de la Task 6 Step 4 es intencionado (marca el punto de enganche de la tanda siguiente), no un hueco del plan. El resto lleva código concreto.

**3. Consistencia de tipos:** `ListadoAsistentes`/`ListadoAsistentesResponse`/`ListadoAsistentesDto` con los mismos campos (`asistentes`, `totalCuotas`, `totalPagado`, `puedoPagarPor`, `miCuota`) en las tres capas. `PersonaPagable(nombre, cuota, relacion, usuarioId, asistenciaId)` idéntico en back/web/móvil. `asistentesEvento` (web) ↔ `asistentes` (móvil) ↔ `GET /eventos/{id}/asistentes` (back). Objeto de pago: web `{importe, metodo, cubre:{yo, usuarioIds, asistenciaIds}}`, móvil `PagoDeclarado(importe, metodo, cubreUsuarioIds, cubreAsistenciaIds)` — equivalentes (móvil aplana `cubre`), `yo` siempre implícito true.
