# Inventario de la fiesta — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mover stock del inventario general de la peña al inventario de un evento ("enviar a evento"), verlo en una pantalla por evento y devolverlo al general.

**Architecture:** Tabla nueva `articulo_evento` (una fila por evento + artículo de origen). "Enviar" resta toda la cantidad de la fila del general (que al quedar a 0 deja de listarse) y la suma en `articulo_evento`; "devolver" hace lo inverso. Endpoints nuevos en el módulo `inventario` + uno en `evento`. Web: modal de selección de evento en el listado de categoría y pantalla `InventarioFiesta` en `/panel/eventos/:id/inventario`. Móvil: paridad en `commonMain`.

**Tech Stack:** Spring Boot 3 + Spring Data JPA + Flyway (Postgres) + Lombok; JUnit 5 + `RestTestClient` (maven-failsafe, `*IT`). Angular standalone + signals + control flow; Vitest. KMP + Compose Multiplatform + Ktor + kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-09-09-inventario-evento-design.md`

## Global Constraints

- Java 17. No tocar `back/config/application.yml` (gitignored).
- Peña única: `slug = "baniterio"`, `PenaRepository.findBySlug`.
- Errores de dominio → `{ "codigo": "<CODE>" }` vía `ApiExceptionHandler` (`@RestControllerAdvice`).
- Textos de UI en español, con tildes.
- Web: `environment.apiBaseUrl` = `/api/v1`. Rutas nuevas antes de la comodín `eventos/:id`.
- Web botones estilo corporativo outline: `rounded-lg border border-outline px-3 py-1 text-xs font-medium hover:border-gold hover:text-gold`; CTA dorado: `rounded-xl bg-gold px-4 py-2 text-sm font-semibold text-brand-dark`.
- Móvil: todo en `commonMain`; pantallas en `nav/Screen.kt` + `App.kt` (clave, `when`, guardia de arranque en frío); errores tipados con `ResultadoInventario` / `CodigoErrorInventario`.
- Commits: mensaje termina con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- Rama de trabajo: `feature/inventario` (ya activa; NO hacer merge en este plan).

## Ficheros

### Backend (crear)
- `back/src/main/java/com/baniterio/api/inventario/ArticuloEvento.java` — entidad.
- `back/src/main/java/com/baniterio/api/inventario/ArticuloEventoRepository.java` — repo.
- `back/src/main/java/com/baniterio/api/inventario/NadaQueEnviarException.java`
- `back/src/main/java/com/baniterio/api/inventario/ArticuloEventoNoEncontradoException.java`
- `back/src/main/java/com/baniterio/api/inventario/dto/EnviarAEventoRequest.java`
- `back/src/main/java/com/baniterio/api/inventario/dto/EnviarCategoriaRequest.java`
- `back/src/main/java/com/baniterio/api/inventario/dto/CategoriaEventoDto.java`
- `back/src/main/java/com/baniterio/api/inventario/dto/InventarioEventoResponse.java`
- `back/src/main/java/com/baniterio/api/evento/dto/EventoAbiertoDto.java`
- `back/src/main/resources/db/migration/V37__inventario_evento.sql`
- `back/src/test/java/com/baniterio/api/inventario/ArticuloEventoRepositoryIT.java`

### Backend (modificar)
- `.../inventario/ArticuloInventarioRepository.java` — método de búsqueda por nombre+tamaño.
- `.../inventario/InventarioService.java` — `enviar`, `enviarCategoria`, `verEvento`, `devolver`; filtro de 0 en `ver`; fusión en `crear`.
- `.../inventario/InventarioController.java` — 4 endpoints nuevos.
- `.../evento/EventoService.java` — `abiertos(usuarioId)`.
- `.../evento/EventoController.java` — `GET /eventos/abiertos`.
- `.../web/ApiExceptionHandler.java` — 2 handlers.
- `back/src/test/java/com/baniterio/api/inventario/InventarioIT.java` — tests nuevos.
- `back/src/test/java/com/baniterio/api/evento/EventoIT.java` — test de `abiertos`.

### Web (crear)
- `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.ts`
- `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.html`
- `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.css`
- `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.spec.ts`

### Web (modificar)
- `front/src/app/panel/inventario/inventario.types.ts`
- `front/src/app/panel/inventario/inventario.service.ts` + `.spec.ts`
- `front/src/app/panel/inventario/inventario-categoria.ts` + `.html` + `.spec.ts`
- `front/src/app/app.routes.ts`
- `front/src/app/panel/eventos/evento-detalle/evento-detalle.html` + `.ts` + `.spec.ts`

### Móvil (crear)
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/inventario/InventarioFiestaScreen.kt`

### Móvil (modificar)
- `.../data/dto/InventarioDtos.kt`
- `.../data/InventarioRepository.kt` + `InventarioRepositoryImpl.kt`
- `.../data/ResultadoInventario.kt`
- `.../nav/Screen.kt`
- `.../App.kt`
- `.../ui/inventario/InventarioCategoriaScreen.kt`
- `.../ui/eventos/EventoDetalleScreen.kt`
- `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/InventarioRepositoryImplTest.kt`

---

## Task 1: Entidad `ArticuloEvento`, repo, migración y excepciones

**Files:**
- Create: `back/src/main/java/com/baniterio/api/inventario/ArticuloEvento.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/ArticuloEventoRepository.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/NadaQueEnviarException.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/ArticuloEventoNoEncontradoException.java`
- Create: `back/src/main/resources/db/migration/V37__inventario_evento.sql`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Test: `back/src/test/java/com/baniterio/api/inventario/ArticuloEventoRepositoryIT.java`

**Interfaces:**
- Produces:
  - `ArticuloEvento` entity: getters/setters de `id, evento (Evento), articuloInventario (ArticuloInventario), categoria (CategoriaInventario), nombre (String), tamano (String), cantidad (BigDecimal), orden (int)`; Lombok `@Builder`.
  - `ArticuloEventoRepository`:
    - `List<ArticuloEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId)`
    - `Optional<ArticuloEvento> findByEventoIdAndArticuloInventarioId(Long eventoId, Long articuloInventarioId)`
  - `NadaQueEnviarException extends RuntimeException`
  - `ArticuloEventoNoEncontradoException extends RuntimeException`

- [ ] **Step 1: Escribe el test de repositorio (falla)**

`ArticuloEventoRepositoryIT.java`:

```java
package com.baniterio.api.inventario;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ArticuloEventoRepositoryIT extends IntegrationTest {

    @Autowired ArticuloEventoRepository articulosEvento;
    @Autowired ArticuloInventarioRepository articulos;
    @Autowired EventoRepository eventos;
    @Autowired PenaRepository penas;

    private Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    private Evento algunEvento() {
        return eventos.findAll().stream()
                .filter(e -> e.getPena().getId().equals(penaId()))
                .findFirst().orElseThrow();
    }

    @Test
    void guarda_y_busca_por_evento_y_articulo_de_origen() {
        Evento ev = algunEvento();
        ArticuloInventario origen = articulos
                .findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId()).get(0);

        ArticuloEvento fila = articulosEvento.save(ArticuloEvento.builder()
                .evento(ev)
                .articuloInventario(origen)
                .categoria(origen.getCategoria())
                .nombre(origen.getNombre())
                .tamano(origen.getTamano())
                .cantidad(new BigDecimal("2.00"))
                .orden(1)
                .build());

        assertThat(articulosEvento.findByEventoIdAndArticuloInventarioId(ev.getId(), origen.getId()))
                .get().extracting(ArticuloEvento::getId).isEqualTo(fila.getId());
        assertThat(articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(ev.getId()))
                .extracting(ArticuloEvento::getNombre).contains(origen.getNombre());
    }
}
```

- [ ] **Step 2: Ejecuta el test y comprueba que falla al compilar**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ArticuloEventoRepositoryIT verify`
Expected: fallo de compilación (`ArticuloEvento`, `ArticuloEventoRepository` no existen).

- [ ] **Step 3: Crea la migración `V37__inventario_evento.sql`**

```sql
-- Inventario de la fiesta: material y bebida del inventario general que se ha
-- "enviado" a un evento concreto. Una fila por (evento, artículo de origen);
-- reenviar el mismo artículo suma en la fila existente (UNIQUE). categoria /
-- nombre / tamano son copia congelada del momento del envío. Al "devolver", la
-- cantidad vuelve a articulo_inventario.<articulo_inventario_id> y la fila se
-- borra. Sin siembra.
CREATE TABLE articulo_evento (
    id                     BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    evento_id              BIGINT        NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    articulo_inventario_id BIGINT        NOT NULL REFERENCES articulo_inventario (id),
    categoria              VARCHAR(20)   NOT NULL,
    nombre                 VARCHAR(120)  NOT NULL,
    tamano                 VARCHAR(20)   NOT NULL,
    cantidad               NUMERIC(8, 2) NOT NULL DEFAULT 0,
    orden                  INT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_articulo_evento_categoria
        CHECK (categoria IN ('ALCOHOL', 'CERVEZA', 'REFRESCOS', 'LIMPIEZA', 'COMIDA')),
    CONSTRAINT ck_articulo_evento_cantidad CHECK (cantidad >= 0),
    CONSTRAINT uq_articulo_evento UNIQUE (evento_id, articulo_inventario_id)
);
CREATE INDEX ix_articulo_evento_evento ON articulo_evento (evento_id);
```

- [ ] **Step 4: Crea la entidad `ArticuloEvento.java`**

Copia el patrón de `ArticuloInventario.java` (mismos imports JPA + Lombok). Campos:

```java
@Entity
@Table(name = "articulo_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticuloEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private com.baniterio.api.identidad.Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_inventario_id", nullable = false)
    private ArticuloInventario articuloInventario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaInventario categoria;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tamano;

    @Column(nullable = false, precision = 8, scale = 2)
    private java.math.BigDecimal cantidad;

    @Column(nullable = false)
    private int orden;
}
```

Añade el Javadoc de clase (1-2 frases: "Una línea del inventario de un evento (tabla `articulo_evento`, ver V37)…").

- [ ] **Step 5: Crea `ArticuloEventoRepository.java`**

```java
package com.baniterio.api.inventario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ArticuloEvento} (inventario de la fiesta). */
public interface ArticuloEventoRepository extends JpaRepository<ArticuloEvento, Long> {

    List<ArticuloEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId);

    Optional<ArticuloEvento> findByEventoIdAndArticuloInventarioId(Long eventoId, Long articuloInventarioId);
}
```

- [ ] **Step 6: Crea las dos excepciones**

`NadaQueEnviarException.java`:

```java
package com.baniterio.api.inventario;

/** Se intentó enviar a un evento un artículo cuya cantidad en el general es 0. */
public class NadaQueEnviarException extends RuntimeException {
}
```

`ArticuloEventoNoEncontradoException.java`:

```java
package com.baniterio.api.inventario;

/** No existe esa línea en el inventario de la fiesta (o es de otro evento). */
public class ArticuloEventoNoEncontradoException extends RuntimeException {
}
```

- [ ] **Step 7: Añade los handlers a `ApiExceptionHandler.java`**

Tras el bloque `tamanoInventarioNoValido()` (línea ~282):

```java
    @ExceptionHandler(com.baniterio.api.inventario.NadaQueEnviarException.class)
    ResponseEntity<Map<String, Object>> nadaQueEnviar() {
        return error(HttpStatus.BAD_REQUEST, "NADA_QUE_ENVIAR");
    }

    @ExceptionHandler(com.baniterio.api.inventario.ArticuloEventoNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> articuloEventoNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "ARTICULO_EVENTO_NO_ENCONTRADO");
    }
```

`EVENTO_NO_ENCONTRADO` ya está (línea ~179); no añadir otro.

- [ ] **Step 8: Ejecuta el test y comprueba que pasa**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ArticuloEventoRepositoryIT verify`
Expected: `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/inventario back/src/main/resources/db/migration/V37__inventario_evento.sql back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java back/src/test/java/com/baniterio/api/inventario/ArticuloEventoRepositoryIT.java
git commit -m "feat(inventario): tabla articulo_evento + entidad y repo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Enviar a evento + filtro de 0 + fusión en crear

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/inventario/ArticuloInventarioRepository.java`
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioService.java`
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioController.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/EnviarAEventoRequest.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/EnviarCategoriaRequest.java`
- Test: `back/src/test/java/com/baniterio/api/inventario/InventarioIT.java`

**Interfaces:**
- Consumes: `ArticuloEvento`, `ArticuloEventoRepository`, `NadaQueEnviarException` (Task 1); `EventoRepository`, `EventoNoEncontradoException` (módulo evento).
- Produces:
  - `EnviarAEventoRequest(Long eventoId)` — `@NotNull eventoId`.
  - `EnviarCategoriaRequest(CategoriaInventario categoria, Long eventoId)` — ambos `@NotNull`.
  - `InventarioService.enviar(Long usuarioId, Long articuloId, Long eventoId)` → void.
  - `InventarioService.enviarCategoria(Long usuarioId, CategoriaInventario categoria, Long eventoId)` → void.
  - `POST /api/v1/inventario/{id}/enviar` → 204.
  - `POST /api/v1/inventario/enviar-categoria` → 204.

- [ ] **Step 1: Escribe los tests de `InventarioIT` (fallan)**

Añade al final de `InventarioIT.java` (antes del `}` de cierre). El helper `token(RolMembresia, boolean)`, `idDe(String, String)` y `pena()` ya existen. Necesitas un evento: añade helper.

```java
    Long algunEventoId() {
        return eventos.findAll().stream()
                .filter(e -> e.getPena().getId().equals(pena().getId()) && !e.isOculto())
                .findFirst().orElseThrow().getId();
    }
```

y el autowire (arriba, junto a los otros): `@Autowired com.baniterio.api.identidad.EventoRepository eventos;`

Tests:

```java
    @Test
    void enviar_mueve_toda_la_cantidad_y_la_fila_desaparece_del_general() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long eventoId = algunEventoId();
        Long id = idDe("CERVEZA", "Coronita"); // cantidad 3 en la siembra

        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", eventoId))
                .exchange().expectStatus().isNoContent();

        assertThat(articulos.findById(id).orElseThrow().getCantidad().intValue()).isZero();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) body.get("categorias");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cerveza = (List<Map<String, Object>>) cats.stream()
                .filter(c -> c.get("categoria").equals("CERVEZA")).findFirst().orElseThrow().get("articulos");
        assertThat(cerveza).noneMatch(a -> a.get("nombre").equals("Coronita"));
    }

    @Test
    void enviar_con_cantidad_cero_es_400_nada_que_enviar() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long eventoId = algunEventoId();
        Long id = idDe("CERVEZA", "Mahou Clásica");
        // vaciar primero
        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", eventoId)).exchange().expectStatus().isNoContent();

        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", eventoId))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("NADA_QUE_ENVIAR");
    }

    @Test
    void enviar_sin_area_es_403() {
        String token = token(RolMembresia.MIEMBRO, false);
        Long eventoId = algunEventoId();
        Long id = idDe("ALCOHOL", "Brugal");

        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", eventoId))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void enviar_a_evento_inexistente_es_404() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long id = idDe("ALCOHOL", "Negrita");

        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", 999999))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void enviar_categoria_mueve_todas_las_filas_con_cantidad() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long eventoId = algunEventoId();

        http.post().uri("/api/v1/inventario/enviar-categoria")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("categoria", "REFRESCOS", "eventoId", eventoId))
                .exchange().expectStatus().isNoContent();

        assertThat(articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()))
                .filteredOn(a -> a.getCategoria().name().equals("REFRESCOS"))
                .allMatch(a -> a.getCantidad().signum() == 0);
    }

    @Test
    void crear_con_nombre_y_tamano_ya_existentes_actualiza_la_fila() {
        String token = token(RolMembresia.MIEMBRO, true);
        long antes = articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()).size();

        http.post().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("categoria", "ALCOHOL", "nombre", "Tanqueray", "tamano", "70 cl", "cantidad", 4))
                .exchange().expectStatus().isCreated();

        long despues = articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()).size();
        assertThat(despues).isEqualTo(antes);
        assertThat(articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()).stream()
                .filter(a -> a.getNombre().equals("Tanqueray") && a.getTamano().equals("70 cl"))
                .findFirst().orElseThrow().getCantidad().intValue()).isEqualTo(4);
    }
```

> Nota: estos tests comparten BBDD con los demás de la clase y mueven/vacían
> filas de la siembra. Usan filas que otros tests no tocan (Coronita, Brugal,
> Negrita, REFRESCOS enteros) salvo "Mahou Clásica", que sólo se vacía dentro
> de su propio test. Si al ejecutar la clase completa algún test previo falla
> por falta de una fila, cambia ese test a una fila propia o créala con POST.

- [ ] **Step 2: Ejecuta y comprueba que falla**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=InventarioIT verify`
Expected: fallo de compilación / 404 en las rutas nuevas.

- [ ] **Step 3: Añade el método al repo `ArticuloInventarioRepository.java`**

```java
    Optional<ArticuloInventario> findByPenaIdAndCategoriaAndNombreIgnoreCaseAndTamano(
            Long penaId, CategoriaInventario categoria, String nombre, String tamano);
```

(añade `import java.util.Optional;` si falta).

- [ ] **Step 4: Crea los dos request DTO**

`EnviarAEventoRequest.java`:

```java
package com.baniterio.api.inventario.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/inventario/{id}/enviar}. */
public record EnviarAEventoRequest(@NotNull Long eventoId) {
}
```

`EnviarCategoriaRequest.java`:

```java
package com.baniterio.api.inventario.dto;

import com.baniterio.api.inventario.CategoriaInventario;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/inventario/enviar-categoria}. */
public record EnviarCategoriaRequest(@NotNull CategoriaInventario categoria, @NotNull Long eventoId) {
}
```

- [ ] **Step 5: Modifica `InventarioService.java`**

Añade dependencias al constructor: `ArticuloEventoRepository articulosEvento`, `EventoRepository eventos`. Actualiza el constructor y los campos `final`.

Filtro de 0 en `ver()` — en el `.map(cat -> new CategoriaInventarioDto(...))`, cambia el stream de artículos a:

```java
filas.stream()
        .filter(a -> a.getCategoria() == cat)
        .filter(a -> a.getCantidad().signum() > 0)
        .map(ArticuloDto::de)
        .toList()
```

Fusión en `crear()` — al principio, tras validar permiso y tamaño, antes de calcular `orden`:

```java
Long penaId = penaId();
String nombre = req.nombre().trim();
var existente = articulos.findByPenaIdAndCategoriaAndNombreIgnoreCaseAndTamano(
        penaId, req.categoria(), nombre, req.tamano());
if (existente.isPresent()) {
    ArticuloInventario art = existente.get();
    art.setCantidad(req.cantidad());
    return ArticuloDto.de(articulos.save(art));
}
```

(el resto de `crear` sigue igual; usa la variable `nombre` ya calculada).

Métodos nuevos:

```java
    private Evento eventoAbierto(Long eventoId) {
        return eventos.findById(eventoId)
                .filter(e -> e.getPena().getId().equals(penaId()) && !e.isOculto())
                .orElseThrow(EventoNoEncontradoException::new);
    }

    /** Mueve al evento toda la cantidad de la fila del inventario general. */
    @Transactional
    public void enviar(Long usuarioId, Long articuloId, Long eventoId) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        Evento evento = eventoAbierto(eventoId);
        ArticuloInventario art = articulos.findById(articuloId)
                .filter(a -> a.getPena().getId().equals(penaId()))
                .orElseThrow(ArticuloInventarioNoEncontradoException::new);
        if (art.getCantidad().signum() == 0) {
            throw new NadaQueEnviarException();
        }
        moverAlEvento(evento, art);
    }

    /** "Enviar todo": mueve al evento todas las filas de la categoría con cantidad > 0. */
    @Transactional
    public void enviarCategoria(Long usuarioId, CategoriaInventario categoria, Long eventoId) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        Evento evento = eventoAbierto(eventoId);
        articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId()).stream()
                .filter(a -> a.getCategoria() == categoria && a.getCantidad().signum() > 0)
                .forEach(a -> moverAlEvento(evento, a));
    }

    private void moverAlEvento(Evento evento, ArticuloInventario art) {
        ArticuloEvento fila = articulosEvento
                .findByEventoIdAndArticuloInventarioId(evento.getId(), art.getId())
                .orElse(null);
        if (fila == null) {
            int orden = articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(evento.getId())
                    .stream()
                    .filter(f -> f.getCategoria() == art.getCategoria())
                    .mapToInt(ArticuloEvento::getOrden)
                    .max().orElse(0) + 1;
            fila = ArticuloEvento.builder()
                    .evento(evento)
                    .articuloInventario(art)
                    .categoria(art.getCategoria())
                    .nombre(art.getNombre())
                    .tamano(art.getTamano())
                    .cantidad(art.getCantidad())
                    .orden(orden)
                    .build();
        } else {
            fila.setCantidad(fila.getCantidad().add(art.getCantidad()));
        }
        articulosEvento.save(fila);
        art.setCantidad(java.math.BigDecimal.ZERO);
        articulos.save(art);
    }
```

Imports nuevos: `com.baniterio.api.identidad.Evento`, `com.baniterio.api.identidad.EventoRepository`, `com.baniterio.api.evento.EventoNoEncontradoException`.

- [ ] **Step 6: Modifica `InventarioController.java`**

Añade imports (`EnviarAEventoRequest`, `EnviarCategoriaRequest`) y métodos:

```java
    /** Mueve toda la cantidad de un artículo al inventario de un evento. Área {@code INVENTARIO}. */
    @PostMapping("/{id}/enviar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviar(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody EnviarAEventoRequest req) {
        service.enviar(principal.id(), id, req.eventoId());
    }

    /** "Enviar todo": mueve al evento todas las filas de una categoría con cantidad > 0. Área {@code INVENTARIO}. */
    @PostMapping("/enviar-categoria")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviarCategoria(@AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody EnviarCategoriaRequest req) {
        service.enviarCategoria(principal.id(), req.categoria(), req.eventoId());
    }
```

Cuidado con el orden de mappings: `/{id}/enviar` y `/enviar-categoria` no chocan porque el primero tiene dos segmentos.

- [ ] **Step 7: Ejecuta y comprueba que pasa**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=InventarioIT verify`
Expected: todos verdes. Si algún test viejo de `InventarioIT` peta por falta de una fila que un test nuevo vació, mueve el test nuevo a otra fila.

- [ ] **Step 8: Suite de inventario completa**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Inventario*,ArticuloInventario*,ArticuloEvento*' verify`
Expected: verde.

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/inventario back/src/test/java/com/baniterio/api/inventario/InventarioIT.java
git commit -m "feat(inventario): enviar a evento (mueve stock) + fila a 0 no se lista

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: Ver inventario de la fiesta + devolver + eventos abiertos

**Files:**
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/CategoriaEventoDto.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/InventarioEventoResponse.java`
- Create: `back/src/main/java/com/baniterio/api/evento/dto/EventoAbiertoDto.java`
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioService.java`
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioController.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/EventoService.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/EventoController.java`
- Test: `back/src/test/java/com/baniterio/api/inventario/InventarioIT.java`
- Test: `back/src/test/java/com/baniterio/api/evento/EventoIT.java`

**Interfaces:**
- Consumes: `ArticuloEventoRepository`, `ArticuloEventoNoEncontradoException` (Task 1); `ArticuloDto` (ya existe: `id, nombre, tamano, cantidad`).
- Produces:
  - `CategoriaEventoDto(String categoria, String etiqueta, List<ArticuloDto> articulos)`
  - `InventarioEventoResponse(boolean puedoEditar, List<CategoriaEventoDto> categorias)`
  - `EventoAbiertoDto(Long id, String nombre, java.time.LocalDate fecha)`
  - `InventarioService.verEvento(Long usuarioId, Long eventoId) → InventarioEventoResponse`
  - `InventarioService.devolver(Long usuarioId, Long eventoId, Long articuloEventoId) → void`
  - `EventoService.abiertos(Long usuarioId) → List<EventoAbiertoDto>`
  - `GET /api/v1/inventario/evento/{eventoId}` → `InventarioEventoResponse`
  - `POST /api/v1/inventario/evento/{eventoId}/{articuloEventoId}/devolver` → 204
  - `GET /api/v1/eventos/abiertos` → `List<EventoAbiertoDto>`

- [ ] **Step 1: Escribe los tests (fallan)**

En `InventarioIT.java`, al final:

```java
    @Test
    void ver_evento_devuelve_lo_enviado_agrupado_por_categoria() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long eventoId = algunEventoId();
        Long id = idDe("ALCOHOL", "Ballantine's"); // 1 en la siembra

        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", eventoId)).exchange().expectStatus().isNoContent();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario/evento/" + eventoId)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(body.get("puedoEditar")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) body.get("categorias");
        assertThat(cats).anySatisfy(c -> {
            assertThat(c.get("categoria")).isEqualTo("ALCOHOL");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> arts = (List<Map<String, Object>>) c.get("articulos");
            assertThat(arts).anyMatch(a -> a.get("nombre").equals("Ballantine's"));
        });
    }

    @Test
    void ver_evento_puedoEditar_false_sin_area() {
        String token = token(RolMembresia.MIEMBRO, false);
        Long eventoId = algunEventoId();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario/evento/" + eventoId)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertThat(body.get("puedoEditar")).isEqualTo(false);
    }

    @Test
    void devolver_suma_de_vuelta_al_general_y_borra_la_linea() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long eventoId = algunEventoId();
        Long id = idDe("ALCOHOL", "Barceló"); // 0.5 en la siembra

        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("eventoId", eventoId)).exchange().expectStatus().isNoContent();
        assertThat(articulos.findById(id).orElseThrow().getCantidad().signum()).isZero();

        // el id de la línea del evento
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario/evento/" + eventoId)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) body.get("categorias");
        int lineaId = ((Number) cats.stream()
                .flatMap(c -> ((List<Map<String, Object>>) c.get("articulos")).stream())
                .filter(a -> a.get("nombre").equals("Barceló"))
                .findFirst().orElseThrow().get("id")).intValue();

        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/" + lineaId + "/devolver")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNoContent();

        assertThat(articulos.findById(id).orElseThrow().getCantidad())
                .isEqualByComparingTo(new java.math.BigDecimal("0.5"));
    }

    @Test
    void devolver_sin_area_es_403() {
        String tokenSi = token(RolMembresia.MIEMBRO, true);
        String tokenNo = token(RolMembresia.MIEMBRO, false);
        Long eventoId = algunEventoId();
        Long id = idDe("ALCOHOL", "Legendario");
        http.post().uri("/api/v1/inventario/" + id + "/enviar")
                .header(AUTHORIZATION, "Bearer " + tokenSi)
                .body(Map.of("eventoId", eventoId)).exchange().expectStatus().isNoContent();
        Long lineaId = articulosEvento.findByEventoIdAndArticuloInventarioId(eventoId, id).orElseThrow().getId();

        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/" + lineaId + "/devolver")
                .header(AUTHORIZATION, "Bearer " + tokenNo)
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void devolver_linea_ajena_al_evento_es_404() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long eventoId = algunEventoId();
        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/999999/devolver")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("ARTICULO_EVENTO_NO_ENCONTRADO");
    }
```

Añade el autowire `@Autowired ArticuloEventoRepository articulosEvento;` a `InventarioIT`.

En `EventoIT.java` (mira los helpers que ya tiene: crea eventos vía repo o API; usa el mismo patrón que un test existente de listado):

```java
    @Test
    void abiertos_no_incluye_pasados_ni_ocultos() {
        String token = /* token de un miembro, como en los otros tests de EventoIT */;

        var lista = http.get().uri("/api/v1/eventos/abiertos")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<java.util.List<Map<String, Object>>>() {})
                .returnResult().getResponseBody();

        // todos los devueltos tienen nombre y fecha, y ninguno es de los ocultos/pasados de la siembra de EventoIT
        assertThat(lista).allSatisfy(e -> {
            assertThat(e.get("nombre")).isNotNull();
            assertThat(e.get("fecha")).isNotNull();
        });
    }
```

> Ajusta este test a cómo `EventoIT` monta sus datos (revisa el fichero): si
> `EventoIT` ya crea un evento futuro y uno pasado, comprueba que el futuro
> sale y el pasado no por nombre.

- [ ] **Step 2: Ejecuta y comprueba que falla**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='InventarioIT,EventoIT' verify`
Expected: fallo (rutas nuevas 404 / no compila).

- [ ] **Step 3: Crea los DTO**

`CategoriaEventoDto.java`:

```java
package com.baniterio.api.inventario.dto;

import java.util.List;

/** Una categoría del inventario de un evento con sus artículos (aquí no se editan tamaños). */
public record CategoriaEventoDto(String categoria, String etiqueta, List<ArticuloDto> articulos) {
}
```

`InventarioEventoResponse.java`:

```java
package com.baniterio.api.inventario.dto;

import java.util.List;

/** Raíz de {@code GET /api/v1/inventario/evento/{id}}. Sólo las categorías con algún artículo. */
public record InventarioEventoResponse(boolean puedoEditar, List<CategoriaEventoDto> categorias) {
}
```

`EventoAbiertoDto.java`:

```java
package com.baniterio.api.evento.dto;

import java.time.LocalDate;

/** Ficha mínima de un evento "abierto" (no pasado, no oculto) para el selector de "enviar a evento". */
public record EventoAbiertoDto(Long id, String nombre, LocalDate fecha) {
}
```

- [ ] **Step 4: `EventoService.abiertos(...)`**

```java
    /** Eventos "abiertos" (no pasados, no ocultos), por fecha ascendente. Para el selector de inventario. */
    @Transactional(readOnly = true)
    public List<com.baniterio.api.evento.dto.EventoAbiertoDto> abiertos(Long usuarioId) {
        return eventos.futuros(penaId(), limiteFuturo()).stream()
                .sorted(java.util.Comparator.comparing(Evento::getFecha).thenComparing(Evento::getId))
                .map(e -> new com.baniterio.api.evento.dto.EventoAbiertoDto(
                        e.getId(), e.getNombre(), e.getFecha()))
                .toList();
    }
```

(`eventos.futuros` ya existe en `EventoRepository` y filtra `oculto = false` + no pasado. `usuarioId` no se usa de momento pero se deja por consistencia con el resto de métodos públicos y por si luego hay filtros por permiso.)

- [ ] **Step 5: `EventoController` — `GET /eventos/abiertos`**

```java
    /** Eventos abiertos (no pasados, no ocultos) para elegir a cuál enviar inventario. */
    @GetMapping("/abiertos")
    public java.util.List<com.baniterio.api.evento.dto.EventoAbiertoDto> abiertos(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return eventoService.abiertos(principal.id());
    }
```

Colócalo antes de `@GetMapping("/{id}")` para que `abiertos` no caiga en el detalle.

- [ ] **Step 6: `InventarioService` — `verEvento` y `devolver`**

```java
    @Transactional(readOnly = true)
    public InventarioEventoResponse verEvento(Long usuarioId, Long eventoId) {
        eventoAbierto(eventoId); // valida existencia/pertenencia/oculto → 404
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);
        List<ArticuloEvento> filas =
                articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId);

        List<CategoriaEventoDto> categorias = Arrays.stream(CategoriaInventario.values())
                .map(cat -> new CategoriaEventoDto(
                        cat.name(), cat.etiqueta(),
                        filas.stream()
                                .filter(f -> f.getCategoria() == cat)
                                .map(f -> new ArticuloDto(f.getId(), f.getNombre(), f.getTamano(), f.getCantidad()))
                                .toList()))
                .filter(c -> !c.articulos().isEmpty())
                .toList();

        return new InventarioEventoResponse(puedoEditar, categorias);
    }

    @Transactional
    public void devolver(Long usuarioId, Long eventoId, Long articuloEventoId) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        ArticuloEvento fila = articulosEvento.findById(articuloEventoId)
                .filter(f -> f.getEvento().getId().equals(eventoId))
                .orElseThrow(ArticuloEventoNoEncontradoException::new);
        ArticuloInventario origen = fila.getArticuloInventario();
        origen.setCantidad(origen.getCantidad().add(fila.getCantidad()));
        articulos.save(origen);
        articulosEvento.delete(fila);
    }
```

Imports: `InventarioEventoResponse`, `CategoriaEventoDto`, `ArticuloEventoNoEncontradoException`.

- [ ] **Step 7: `InventarioController` — 2 endpoints**

```java
    /** El inventario que se ha enviado a un evento, agrupado por categoría. Cualquier usuario logueado. */
    @GetMapping("/evento/{eventoId}")
    public InventarioEventoResponse verEvento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.verEvento(principal.id(), eventoId);
    }

    /** Devuelve una línea entera del inventario de la fiesta al inventario general. Área {@code INVENTARIO}. */
    @PostMapping("/evento/{eventoId}/{articuloEventoId}/devolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolver(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long articuloEventoId) {
        service.devolver(principal.id(), eventoId, articuloEventoId);
    }
```

- [ ] **Step 8: Ejecuta y comprueba que pasa**

Run: `cd back && ./mvnw -q -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='InventarioIT,EventoIT' verify`
Expected: verde.

- [ ] **Step 9: Suite completa de backend (regresión)**

Run: `cd back && ./mvnw -q verify`
Expected: verde (mismo baseline que antes del plan).

- [ ] **Step 10: Commit**

```bash
git add back/src/main/java/com/baniterio/api back/src/test/java/com/baniterio/api
git commit -m "feat(inventario): ver inventario de la fiesta, devolver al general, GET /eventos/abiertos

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: Web — modal "Enviar a evento" en el listado de categoría

**Files:**
- Modify: `front/src/app/panel/inventario/inventario.types.ts`
- Modify: `front/src/app/panel/inventario/inventario.service.ts`
- Modify: `front/src/app/panel/inventario/inventario.service.spec.ts`
- Modify: `front/src/app/panel/inventario/inventario-categoria.ts`
- Modify: `front/src/app/panel/inventario/inventario-categoria.html`
- Modify: `front/src/app/panel/inventario/inventario-categoria.spec.ts`

**Interfaces:**
- Consumes: endpoints de Task 2/3 (`POST /inventario/:id/enviar`, `POST /inventario/enviar-categoria`, `GET /eventos/abiertos`).
- Produces (en `inventario.types.ts`):
  - `interface EventoAbierto { id: number; nombre: string; fecha: string; }`
- Produces (en `InventarioService`):
  - `eventosAbiertos(): Observable<EventoAbierto[]>`
  - `enviarAEvento(articuloId: number, eventoId: number): Observable<void>`
  - `enviarCategoria(categoria: CategoriaClave, eventoId: number): Observable<void>`

- [ ] **Step 1: Tipos + servicio + specs de servicio**

En `inventario.types.ts` añade:

```ts
export interface EventoAbierto {
  id: number;
  nombre: string;
  fecha: string;
}
```

En `inventario.service.ts` añade a la clase:

```ts
  eventosAbiertos(): Observable<EventoAbierto[]> {
    return this.http.get<EventoAbierto[]>(`${environment.apiBaseUrl}/eventos/abiertos`);
  }

  enviarAEvento(articuloId: number, eventoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/inventario/${articuloId}/enviar`, { eventoId });
  }

  enviarCategoria(categoria: CategoriaClave, eventoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/inventario/enviar-categoria`, { categoria, eventoId });
  }
```

(importa `EventoAbierto` y `CategoriaClave` de `./inventario.types`; el fichero ya importa cosas de ahí).

En `inventario.service.spec.ts` añade tests:

```ts
  it('eventosAbiertos() pega a GET /eventos/abiertos', () => {
    service.eventosAbiertos().subscribe();
    const req = httpMock.expectOne(`${base}/eventos/abiertos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('enviarAEvento() hace POST /inventario/:id/enviar con { eventoId }', () => {
    service.enviarAEvento(4, 9).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/4/enviar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ eventoId: 9 });
    req.flush(null);
  });

  it('enviarCategoria() hace POST /inventario/enviar-categoria', () => {
    service.enviarCategoria('ALCOHOL', 9).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/enviar-categoria`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ categoria: 'ALCOHOL', eventoId: 9 });
    req.flush(null);
  });
```

- [ ] **Step 2: Ejecuta specs de servicio (fallan → luego pasan)**

Run: `cd front && npx ng test --watch=false`
Expected: los 3 nuevos de `InventarioService` pasan; el resto sigue igual.

- [ ] **Step 3: Lógica del modal en `inventario-categoria.ts`**

Añade señales e imports (`EventoAbierto`):

```ts
  // Modal "Enviar a evento".
  protected readonly enviarAbierto = signal(false);
  // objetivo: un artículo concreto, o 'todo' (toda la categoría).
  protected readonly enviarObjetivo = signal<ArticuloInventario | 'todo' | null>(null);
  protected readonly eventosAbiertos = signal<EventoAbierto[]>([]);
  protected readonly eventoSel = signal<number | null>(null);
  protected readonly enviando = signal(false);
```

Reemplaza los placeholders `enviarAEvento` / `enviarTodoAEvento` por:

```ts
  protected abrirEnviar(objetivo: ArticuloInventario | 'todo'): void {
    this.enviarObjetivo.set(objetivo);
    this.aviso.set('');
    this.eventoSel.set(null);
    this.enviarAbierto.set(true);
    if (this.eventosAbiertos().length === 0) {
      this.inventarioService.eventosAbiertos().subscribe({
        next: (evs) => this.eventosAbiertos.set(evs),
        error: () => this.aviso.set('No se pudieron cargar los eventos.'),
      });
    }
  }

  protected cerrarEnviar(): void {
    this.enviarAbierto.set(false);
    this.enviarObjetivo.set(null);
  }

  protected confirmarEnviar(): void {
    const eventoId = this.eventoSel();
    const objetivo = this.enviarObjetivo();
    const opcion = this.opcion();
    if (eventoId == null || objetivo == null || !opcion) return;
    this.enviando.set(true);
    const obs =
      objetivo === 'todo'
        ? this.inventarioService.enviarCategoria(opcion.clave, eventoId)
        : this.inventarioService.enviarAEvento(objetivo.id, eventoId);
    const nombreEvento = this.eventosAbiertos().find((e) => e.id === eventoId)?.nombre ?? 'el evento';
    obs.subscribe({
      next: () => {
        this.enviando.set(false);
        this.enviarAbierto.set(false);
        this.enviarObjetivo.set(null);
        this.aviso.set(`Enviado a «${nombreEvento}».`);
        this.cargar();
      },
      error: () => {
        this.enviando.set(false);
        this.aviso.set('No se pudo enviar.');
      },
    });
  }

  protected trackEvento = (_: number, e: EventoAbierto) => e.id;
```

Borra `enviarAEvento(a: ArticuloInventario)` y `enviarTodoAEvento()` (los placeholders).

- [ ] **Step 4: Plantilla `inventario-categoria.html`**

Cambia el botón "Enviar a evento" de la fila:

```html
<button
  type="button"
  (click)="abrirEnviar(a)"
  class="whitespace-nowrap rounded-lg border border-outline px-2 py-1 text-xs font-medium hover:border-gold hover:text-gold"
>
  Enviar a evento
</button>
```

Cambia el botón "Enviar todo a evento":

```html
<button
  type="button"
  (click)="abrirEnviar('todo')"
  class="rounded-lg border border-outline px-3 py-1 text-xs font-medium hover:border-gold hover:text-gold"
>
  Enviar todo a evento
</button>
```

Añade el modal al final del fichero, dentro de la `<section>` (junto al modal de "Añadir artículo"):

```html
@if (enviarAbierto()) {
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
    <div class="w-full max-w-sm rounded-2xl bg-surface p-6">
      <h3 class="font-display text-lg font-bold">Enviar a evento</h3>
      <p class="mt-1 text-sm text-muted">
        @if (enviarObjetivo() === 'todo') {
          Se enviará todo lo de «{{ opcion()?.etiqueta }}».
        } @else {
          {{ objetivoNombre() }}
        }
      </p>

      <label class="mt-4 block text-sm">
        <span class="text-xs text-muted">Evento</span>
        <select
          [ngModel]="eventoSel()"
          (ngModelChange)="eventoSel.set($event)"
          class="mt-1 w-full rounded-lg border border-outline bg-surface px-2 py-1"
        >
          <option [ngValue]="null">— Elige un evento —</option>
          @for (e of eventosAbiertos(); track trackEvento($index, e)) {
            <option [ngValue]="e.id">{{ e.nombre }} · {{ e.fecha }}</option>
          }
        </select>
      </label>
      @if (eventosAbiertos().length === 0) {
        <p class="mt-2 text-xs text-muted">No hay eventos abiertos.</p>
      }

      <div class="mt-5 flex justify-end gap-2">
        <button
          type="button"
          [disabled]="enviando() || eventoSel() == null"
          (click)="confirmarEnviar()"
          class="rounded-xl bg-gold px-4 py-2 text-sm font-semibold text-brand-dark disabled:opacity-50"
        >
          Enviar
        </button>
        <button
          type="button"
          (click)="cerrarEnviar()"
          class="rounded-xl border border-outline px-4 py-2 text-sm"
        >
          Cancelar
        </button>
      </div>
    </div>
  </div>
}
```

Añade a `inventario-categoria.ts` el helper `objetivoNombre`:

```ts
  protected objetivoNombre(): string {
    const o = this.enviarObjetivo();
    return o && o !== 'todo' ? `${o.nombre} · ${o.tamano}` : '';
  }
```

`FormsModule` ya está en `imports` del componente (se usa para el modal de añadir).

- [ ] **Step 5: Test de componente en `inventario-categoria.spec.ts`**

```ts
  it('"Enviar a evento" abre modal, elige evento y hace POST /inventario/:id/enviar', () => {
    const { fixture, httpMock } = montar('cerveza');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(true));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Enviar a evento')!
      .dispatchEvent(new Event('click'));

    httpMock
      .expectOne(`${environment.apiBaseUrl}/eventos/abiertos`)
      .flush([{ id: 7, nombre: 'San Miguel', fecha: '2026-09-25' }]);
    fixture.detectChanges();

    const sel = el.querySelector('select') as HTMLSelectElement;
    sel.value = sel.options[1].value; // el evento 7
    sel.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Enviar')!
      .dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${environment.apiBaseUrl}/inventario/1/enviar`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ eventoId: 7 });
    post.flush(null);

    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(true));
    httpMock.verify();
  });

  it('"Enviar todo a evento" usa enviar-categoria', () => {
    const { fixture, httpMock } = montar('cerveza');
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(true));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Enviar todo a evento')!
      .dispatchEvent(new Event('click'));
    httpMock
      .expectOne(`${environment.apiBaseUrl}/eventos/abiertos`)
      .flush([{ id: 7, nombre: 'San Miguel', fecha: '2026-09-25' }]);
    fixture.detectChanges();

    const sel = el.querySelector('select') as HTMLSelectElement;
    sel.value = sel.options[1].value;
    sel.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Enviar')!
      .dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${environment.apiBaseUrl}/inventario/enviar-categoria`);
    expect(post.request.body).toEqual({ categoria: 'CERVEZA', eventoId: 7 });
    post.flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario`).flush(RESPUESTA(true));
    httpMock.verify();
  });
```

Si `RESPUESTA(true)` no incluye el botón "Enviar todo a evento" (sólo sale con artículos y `puedoEditar`), verifica que la categoría CERVEZA de `RESPUESTA` tiene artículos (los tiene: Mahou Clásica, Mixta).

- [ ] **Step 6: Ejecuta la suite web**

Run: `cd front && npx ng test --watch=false`
Expected: todo verde salvo el baseline conocido de errores no relacionados (`editor-evento.spec.ts`, ~5 "errors" preexistentes). Nº de tests sube.

- [ ] **Step 7: Commit**

```bash
git add front/src/app/panel/inventario
git commit -m "feat(inventario): web — modal «Enviar a evento» en el listado de categoría

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Web — pantalla "Inventario de la fiesta" + enlace desde el detalle del evento

**Files:**
- Create: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.ts`
- Create: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.html`
- Create: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.css`
- Create: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.spec.ts`
- Modify: `front/src/app/panel/inventario/inventario.types.ts`
- Modify: `front/src/app/panel/inventario/inventario.service.ts` + `.spec.ts`
- Modify: `front/src/app/app.routes.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.html` + `.ts` + `.spec.ts`

**Interfaces:**
- Consumes: `GET /inventario/evento/:id`, `POST /inventario/evento/:e/:a/devolver` (Task 3).
- Produces (en `inventario.types.ts`):
  - `interface ArticuloFiesta { id: number; nombre: string; tamano: string; cantidad: number; }`
  - `interface CategoriaFiesta { categoria: CategoriaClave; etiqueta: string; articulos: ArticuloFiesta[]; }`
  - `interface InventarioFiestaResponse { puedoEditar: boolean; categorias: CategoriaFiesta[]; }`
- Produces (en `InventarioService`):
  - `inventarioFiesta(eventoId: number): Observable<InventarioFiestaResponse>`
  - `devolverAInventario(eventoId: number, articuloEventoId: number): Observable<void>`
- Produces: componente `InventarioFiesta` (selector `app-inventario-fiesta`), ruta `panel/eventos/:id/inventario`.

- [ ] **Step 1: Tipos + servicio + specs**

`inventario.types.ts`:

```ts
export interface ArticuloFiesta {
  id: number;
  nombre: string;
  tamano: string;
  cantidad: number;
}

export interface CategoriaFiesta {
  categoria: CategoriaClave;
  etiqueta: string;
  articulos: ArticuloFiesta[];
}

export interface InventarioFiestaResponse {
  puedoEditar: boolean;
  categorias: CategoriaFiesta[];
}
```

`inventario.service.ts`:

```ts
  inventarioFiesta(eventoId: number): Observable<InventarioFiestaResponse> {
    return this.http.get<InventarioFiestaResponse>(`${this.base}/inventario/evento/${eventoId}`);
  }

  devolverAInventario(eventoId: number, articuloEventoId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/inventario/evento/${eventoId}/${articuloEventoId}/devolver`,
      {},
    );
  }
```

`inventario.service.spec.ts`:

```ts
  it('inventarioFiesta() pega a GET /inventario/evento/:id', () => {
    service.inventarioFiesta(7).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/evento/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ puedoEditar: false, categorias: [] });
  });

  it('devolverAInventario() hace POST /inventario/evento/:e/:a/devolver', () => {
    service.devolverAInventario(7, 3).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/evento/7/3/devolver`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
```

- [ ] **Step 2: Componente `inventario-fiesta.ts`**

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { InventarioService } from '../../inventario/inventario.service';
import { CategoriaFiesta, ArticuloFiesta } from '../../inventario/inventario.types';

/**
 * Inventario que se ha enviado a un evento ("inventario de la fiesta").
 * `/panel/eventos/:id/inventario`. Lo ve cualquier apuntado; el botón
 * "Devolver a inventario" de cada línea sólo sale con permiso (`puedoEditar`).
 */
@Component({
  selector: 'app-inventario-fiesta',
  imports: [Volver],
  templateUrl: './inventario-fiesta.html',
  styleUrl: './inventario-fiesta.css',
})
export class InventarioFiesta implements OnInit {
  private readonly inventarioService = inject(InventarioService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly puedoEditar = signal(false);
  protected readonly categorias = signal<CategoriaFiesta[]>([]);
  protected readonly aviso = signal('');
  protected readonly devolviendo = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.inventarioService.inventarioFiesta(this.eventoId).subscribe({
      next: (datos) => {
        this.puedoEditar.set(datos.puedoEditar);
        this.categorias.set(datos.categorias);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected devolver(a: ArticuloFiesta): void {
    if (!confirm(`¿Devolver "${a.nombre}" al inventario general?`)) return;
    this.devolviendo.set(true);
    this.aviso.set('');
    this.inventarioService.devolverAInventario(this.eventoId, a.id).subscribe({
      next: () => {
        this.devolviendo.set(false);
        this.cargar();
      },
      error: () => {
        this.devolviendo.set(false);
        this.aviso.set('No se pudo devolver.');
      },
    });
  }

  protected trackCat = (_: number, c: CategoriaFiesta) => c.categoria;
  protected trackArt = (_: number, a: ArticuloFiesta) => a.id;
}
```

- [ ] **Step 3: Plantilla `inventario-fiesta.html`**

```html
<section class="mx-auto w-full max-w-4xl">
  <app-volver [destino]="'/panel/eventos/' + eventoId" etiqueta="Evento" />

  <h1 class="mt-3 font-display text-2xl font-extrabold">Inventario de la fiesta</h1>
  <p class="mt-1 text-sm text-muted">Lo que se ha llevado del inventario de la peña a este evento.</p>

  @if (aviso(); as a) {
    <p class="mt-3 rounded-xl border border-brand-bright/30 bg-brand/15 px-4 py-2 text-sm text-gold-soft">
      {{ a }}
    </p>
  }

  @switch (estado()) {
    @case ('cargando') {
      <p class="mt-4 text-sm text-muted">Cargando…</p>
    }
    @case ('error') {
      <p class="mt-4 text-sm text-red-300">No se ha podido cargar el inventario de la fiesta.</p>
      <button
        type="button"
        (click)="cargar()"
        class="mt-2 rounded-lg border border-outline px-3 py-1 text-xs"
      >
        Reintentar
      </button>
    }
    @case ('listo') {
      @if (categorias().length === 0) {
        <p class="mt-4 text-sm text-muted">Todavía no se ha enviado nada a este evento.</p>
      }
      @for (c of categorias(); track trackCat($index, c)) {
        <article class="carta-relieve mt-4 rounded-2xl p-5">
          <h2 class="font-display text-lg font-bold">{{ c.etiqueta }}</h2>
          <div class="mt-3 overflow-x-auto">
            <table
              class="w-full border-collapse text-sm [&_td]:border [&_td]:border-outline/40 [&_th]:border [&_th]:border-outline/40"
            >
              <thead class="bg-surface/60 text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th class="px-3 py-2 text-left">Artículo</th>
                  <th class="px-3 py-2 text-left">Tamaño</th>
                  <th class="px-3 py-2 text-right">Cantidad</th>
                  <th class="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody>
                @for (a of c.articulos; track trackArt($index, a)) {
                  <tr>
                    <td class="px-3 py-2">{{ a.nombre }}</td>
                    <td class="px-3 py-2 text-muted">{{ a.tamano }}</td>
                    <td class="px-3 py-2 text-right font-medium">{{ a.cantidad }}</td>
                    <td class="px-3 py-2 text-right">
                      @if (puedoEditar()) {
                        <button
                          type="button"
                          [disabled]="devolviendo()"
                          (click)="devolver(a)"
                          class="whitespace-nowrap rounded-lg border border-outline px-2 py-1 text-xs font-medium hover:border-gold hover:text-gold disabled:opacity-50"
                        >
                          Devolver a inventario
                        </button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </article>
      }
    }
  }
</section>
```

`inventario-fiesta.css`: fichero vacío con un comentario (`/* Sin estilos propios: todo con utilidades Tailwind. */`).

- [ ] **Step 4: Ruta en `app.routes.ts`**

Importa `InventarioFiesta` y añade la ruta **antes** de `{ path: 'eventos/:id', ... }`:

```ts
import { InventarioFiesta } from './panel/eventos/inventario-fiesta/inventario-fiesta';
```

```ts
      { path: 'eventos/:id/inventario', component: InventarioFiesta, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id', component: EventoDetalleComponent, canActivate: [perfilCompletoGuard] },
```

(ya hay `eventos/:id/editar` antes de `eventos/:id`; ponla junto a esa.)

- [ ] **Step 5: Enlace desde `evento-detalle`**

En `evento-detalle.html`, el botón "Inventario de la fiesta" (hoy `(click)="proximamente('Inventario de la fiesta')"`) pasa a:

```html
<a
  [routerLink]="['/panel/eventos', e.id, 'inventario']"
  class="rounded-xl border border-brand px-4 py-3 text-center text-sm font-bold text-gold transition hover:bg-brand/20"
>
  Inventario de la fiesta
</a>
```

El botón "Lista de la compra" se queda igual (placeholder). `RouterLink` ya está en los `imports` del componente `EventoDetalleComponent` (se usa para la cuenta). En `evento-detalle.ts` el método `proximamente` se mantiene (lo usa "Lista de la compra"); si el linter marca el string 'Inventario de la fiesta' como no usado no hay problema, es un literal.

- [ ] **Step 6: Test del componente `inventario-fiesta.spec.ts`**

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { InventarioFiesta } from './inventario-fiesta';

const RESPUESTA = (puedoEditar: boolean) => ({
  puedoEditar,
  categorias: [
    {
      categoria: 'ALCOHOL',
      etiqueta: 'Alcohol',
      articulos: [{ id: 5, nombre: 'Tanqueray', tamano: '70 cl', cantidad: 1.5 }],
    },
  ],
});

function montar(): { fixture: ComponentFixture<InventarioFiesta>; httpMock: HttpTestingController } {
  TestBed.configureTestingModule({
    imports: [InventarioFiesta],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } },
    ],
  });
  return {
    fixture: TestBed.createComponent(InventarioFiesta),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

describe('InventarioFiesta', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('pinta lo enviado y, con permiso, "Devolver a inventario" hace el POST', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, httpMock } = montar();
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(true));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Tanqueray');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Devolver a inventario')!
      .dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7/5/devolver`);
    expect(post.request.method).toBe('POST');
    post.flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(true));
    httpMock.verify();
  });

  it('sin permiso no muestra el botón de devolver', () => {
    const { fixture, httpMock } = montar();
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/inventario/evento/7`).flush(RESPUESTA(false));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Devolver a inventario');
    httpMock.verify();
  });
});
```

- [ ] **Step 7: Ajusta `evento-detalle.spec.ts`**

El test "muestra «Lista de la compra» e «Inventario de la fiesta» y son placeholder" (añadido en un commit anterior) ya no vale para "Inventario de la fiesta". Cámbialo:

```ts
  it('muestra "Lista de la compra" (placeholder) e "Inventario de la fiesta" (enlace)', () => {
    crear();
    fixture.detectChanges();
    responder({
      asistencia: {
        ...asistenciaBase,
        ficha: { llevaFicha: true, diasEvento: ['2026-09-25', '2026-09-26'], miFicha: null },
      },
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Lista de la compra');

    const enlace = Array.from(el.querySelectorAll('a')).find(
      (a) => a.textContent?.trim() === 'Inventario de la fiesta',
    );
    expect(enlace?.getAttribute('href')).toContain('/inventario');
  });
```

(el `href` que renderiza `routerLink` en test es `/panel/eventos/<id>/inventario`; comprobar `toContain('/inventario')` es suficiente y no depende del id del mock.)

- [ ] **Step 8: Ejecuta la suite web completa**

Run: `cd front && npx ng test --watch=false`
Expected: verde salvo el baseline de errores no relacionados.

- [ ] **Step 9: Commit**

```bash
git add front/src/app
git commit -m "feat(inventario): web — pantalla «Inventario de la fiesta» + enlace desde el evento

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Móvil — repo + modal "Enviar a evento" en el listado de categoría

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/InventarioDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoInventario.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/InventarioRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/InventarioRepositoryImpl.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/inventario/InventarioCategoriaScreen.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/InventarioRepositoryImplTest.kt`

**Interfaces:**
- Consumes: endpoints de Task 2/3.
- Produces (DTOs en `InventarioDtos.kt`):
  - `EventoAbiertoDto(id: Long, nombre: String, fecha: String)`
  - `EnviarAEventoBody(eventoId: Long)`, `EnviarCategoriaBody(categoria: String, eventoId: Long)`
  - `ArticuloFiestaDto(id: Long, nombre: String, tamano: String, cantidad: Double)`
  - `CategoriaFiestaDto(categoria: String, etiqueta: String, articulos: List<ArticuloFiestaDto>)`
  - `InventarioFiestaResponse(puedoEditar: Boolean, categorias: List<CategoriaFiestaDto>)`
- Produces (en `InventarioRepository`):
  - `suspend fun eventosAbiertos(): ResultadoInventario<List<EventoAbiertoDto>>`
  - `suspend fun enviarAEvento(articuloId: Long, eventoId: Long): ResultadoInventario<Unit>`
  - `suspend fun enviarCategoria(categoria: String, eventoId: Long): ResultadoInventario<Unit>`
  - `suspend fun inventarioFiesta(eventoId: Long): ResultadoInventario<InventarioFiestaResponse>`
  - `suspend fun devolver(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit>`

- [ ] **Step 1: DTOs**

En `InventarioDtos.kt` añade:

```kotlin
@Serializable
data class EventoAbiertoDto(val id: Long, val nombre: String, val fecha: String)

@Serializable
data class EnviarAEventoBody(val eventoId: Long)

@Serializable
data class EnviarCategoriaBody(val categoria: String, val eventoId: Long)

@Serializable
data class ArticuloFiestaDto(val id: Long, val nombre: String, val tamano: String, val cantidad: Double)

@Serializable
data class CategoriaFiestaDto(
    val categoria: String,
    val etiqueta: String,
    val articulos: List<ArticuloFiestaDto> = emptyList(),
)

@Serializable
data class InventarioFiestaResponse(
    val puedoEditar: Boolean = false,
    val categorias: List<CategoriaFiestaDto> = emptyList(),
)
```

- [ ] **Step 2: Códigos de error**

En `ResultadoInventario.kt`, `enum class CodigoErrorInventario` añade valores y mapeo:

```kotlin
    NADA_QUE_ENVIAR,
    ARTICULO_EVENTO_NO_ENCONTRADO,
    EVENTO_NO_ENCONTRADO,
```

en `deCodigoBackend`:

```kotlin
            "NADA_QUE_ENVIAR" -> NADA_QUE_ENVIAR
            "ARTICULO_EVENTO_NO_ENCONTRADO" -> ARTICULO_EVENTO_NO_ENCONTRADO
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
```

en `mensaje`:

```kotlin
            NADA_QUE_ENVIAR -> "No queda nada de ese artículo para enviar."
            ARTICULO_EVENTO_NO_ENCONTRADO -> "Ese artículo ya no está en la fiesta."
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
```

- [ ] **Step 3: Interfaz `InventarioRepository`**

Añade las 5 funciones (firmas de arriba). Imports de los DTOs nuevos.

- [ ] **Step 4: `InventarioRepositoryImpl`**

```kotlin
    override suspend fun eventosAbiertos(): ResultadoInventario<List<EventoAbiertoDto>> = peticion {
        http.get("$API_BASE_URL/eventos/abiertos") { auth() }.body()
    }

    override suspend fun enviarAEvento(articuloId: Long, eventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/$articuloId/enviar") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(EnviarAEventoBody(eventoId))
        }.let { }
    }

    override suspend fun enviarCategoria(categoria: String, eventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/enviar-categoria") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(EnviarCategoriaBody(categoria, eventoId))
        }.let { }
    }

    override suspend fun inventarioFiesta(eventoId: Long): ResultadoInventario<InventarioFiestaResponse> = peticion {
        http.get("$API_BASE_URL/inventario/evento/$eventoId") { auth() }.body()
    }

    override suspend fun devolver(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/evento/$eventoId/$articuloEventoId/devolver") { auth() }.let { }
    }
```

Imports que puedan faltar: `EventoAbiertoDto`, `EnviarAEventoBody`, `EnviarCategoriaBody`, `InventarioFiestaResponse`.

- [ ] **Step 5: Tests del repo**

En `InventarioRepositoryImplTest.kt` añade:

```kotlin
    @Test
    fun eventos_abiertos_hace_get() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """[{"id":7,"nombre":"San Miguel","fecha":"2026-09-25"}]""")
        val res = r.eventosAbiertos()
        assertIs<ResultadoInventario.Exito<*>>(res)
        assertEquals("/api/v1/eventos/abiertos", vistas[0].path)
    }

    @Test
    fun enviar_a_evento_hace_post_con_body() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        r.enviarAEvento(4, 7)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/inventario/4/enviar", vistas[0].path)
        assert(vistas[0].cuerpo.contains("\"eventoId\":7")) { vistas[0].cuerpo }
    }

    @Test
    fun enviar_categoria_hace_post_con_body() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        r.enviarCategoria("ALCOHOL", 7)
        assertEquals("/api/v1/inventario/enviar-categoria", vistas[0].path)
        assert(vistas[0].cuerpo.contains("\"categoria\":\"ALCOHOL\"")) { vistas[0].cuerpo }
    }

    @Test
    fun inventario_fiesta_hace_get() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """{"puedoEditar":true,"categorias":[]}""")
        val res = r.inventarioFiesta(7)
        assertIs<ResultadoInventario.Exito<*>>(res)
        assertEquals("/api/v1/inventario/evento/7", vistas[0].path)
    }

    @Test
    fun devolver_hace_post() = runTest {
        val (r, vistas) = repo(status = HttpStatusCode.NoContent)
        val res = r.devolver(7, 3)
        assertIs<ResultadoInventario.Exito<*>>(res)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/inventario/evento/7/3/devolver", vistas[0].path)
    }

    @Test
    fun devolver_inexistente_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.NotFound,
            cuerpoRespuesta = """{"codigo":"ARTICULO_EVENTO_NO_ENCONTRADO"}""")
        val res = r.devolver(7, 9)
        assertIs<ResultadoInventario.Error>(res)
        assertEquals(CodigoErrorInventario.ARTICULO_EVENTO_NO_ENCONTRADO, res.codigo)
    }
```

- [ ] **Step 6: Modal "Enviar a evento" en `InventarioCategoriaScreen.kt`**

Estado nuevo en el composable `InventarioCategoriaScreen`:

```kotlin
    var enviarObjetivo by remember { mutableStateOf<EnvioObjetivo?>(null) }
    var eventosAbiertos by remember { mutableStateOf<List<EventoAbiertoDto>>(emptyList()) }
```

Tipo auxiliar (arriba del fichero, junto a `NOMBRE_NUEVO`):

```kotlin
private sealed interface EnvioObjetivo {
    data class Uno(val articulo: ArticuloInventarioDto) : EnvioObjetivo
    data object Todo : EnvioObjetivo
}
```

En `FilaArticulo`, el botón "Enviar a evento" (rama `else`, sólo si `puedoEditar`) llama a un nuevo callback `onEnviar` en vez del placeholder `onEnviarAEvento`. Igual el botón "Enviar todo a evento" del pie. Cambia las firmas y el wiring:

- `FilaArticulo(..., onEnviar: () -> Unit, ...)` en vez de `onEnviarAEvento`.
- En el `forEach` del padre: `onEnviar = { enviarObjetivo = EnvioObjetivo.Uno(a) }`.
- Botón del pie: `onClick = { enviarObjetivo = EnvioObjetivo.Todo }`.

Al cambiar `enviarObjetivo` a no-null, si `eventosAbiertos` está vacío, cargar:

```kotlin
    LaunchedEffect(enviarObjetivo) {
        if (enviarObjetivo != null && eventosAbiertos.isEmpty()) {
            when (val r = inventarioRepo.eventosAbiertos()) {
                is ResultadoInventario.Exito -> eventosAbiertos = r.dato
                is ResultadoInventario.Error -> aviso = r.mensaje
            }
        }
    }
```

Diálogo (al final del composable, junto a los otros):

```kotlin
    enviarObjetivo?.let { objetivo ->
        var eventoSel by remember(objetivo) { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { enviarObjetivo = null },
            confirmButton = {
                TextButton(
                    enabled = eventoSel != null,
                    onClick = {
                        val ev = eventoSel ?: return@TextButton
                        enviarObjetivo = null
                        scope.launch {
                            val r = when (objetivo) {
                                is EnvioObjetivo.Uno -> inventarioRepo.enviarAEvento(objetivo.articulo.id, ev)
                                EnvioObjetivo.Todo -> inventarioRepo.enviarCategoria(categoria.clave, ev)
                            }
                            when (r) {
                                is ResultadoInventario.Exito -> {
                                    val n = eventosAbiertos.firstOrNull { it.id == ev }?.nombre ?: "el evento"
                                    aviso = "Enviado a «$n»."
                                }
                                is ResultadoInventario.Error -> aviso = r.mensaje
                            }
                            intento++
                        }
                    },
                ) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { enviarObjetivo = null }) { Text("Cancelar") } },
            title = { Text("Enviar a evento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (objetivo) {
                            is EnvioObjetivo.Uno -> "${objetivo.articulo.nombre} · ${objetivo.articulo.tamano}"
                            EnvioObjetivo.Todo -> "Se enviará todo lo de «${categoria.etiqueta}»."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BaniterioColors.muted,
                    )
                    if (eventosAbiertos.isEmpty()) {
                        Text("No hay eventos abiertos.", color = BaniterioColors.muted)
                    }
                    eventosAbiertos.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { eventoSel = e.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = eventoSel == e.id, onClick = { eventoSel = e.id })
                            Text("${e.nombre} · ${e.fecha}", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            },
        )
    }
```

Imports nuevos: `androidx.compose.material3.RadioButton`, `androidx.compose.foundation.clickable`, `com.baniterio.app.data.dto.EventoAbiertoDto`. (`AlertDialog`, `TextButton`, `Column`, `Row`, `Arrangement`, `Alignment`, `MaterialTheme` ya están.)

- [ ] **Step 7: Compila y pasa tests**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/inventario/InventarioCategoriaScreen.kt mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/InventarioRepositoryImplTest.kt
git commit -m "feat(inventario): móvil — modal «Enviar a evento» en el listado de categoría

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: Móvil — pantalla "Inventario de la fiesta" + enlace desde el detalle del evento

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/inventario/InventarioFiestaScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt`

**Interfaces:**
- Consumes: `InventarioRepository.inventarioFiesta` / `devolver` (Task 6); `Screen` (nav).
- Produces:
  - `Screen.InventarioFiesta` (data object)
  - `@Composable fun InventarioFiestaScreen(inventarioRepo: InventarioRepository, eventoId: Long, onVolver: () -> Unit)`

- [ ] **Step 1: `Screen.InventarioFiesta`**

En `nav/Screen.kt`, junto a `InventarioCategoria`:

```kotlin
    data object InventarioFiesta : Screen()
```

- [ ] **Step 2: Pantalla `InventarioFiestaScreen.kt`**

Copia la estructura de `InventarioCategoriaScreen` (cabecera con "Volver", estados cargando/error/listo, tarjetas `relieveDeCarta` por categoría). Sin edición, sin modal de añadir; cada fila con botón "Devolver" (sólo si `puedoEditar`) que abre un `AlertDialog` de confirmación.

```kotlin
package com.baniterio.app.ui.inventario

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.InventarioRepository
import com.baniterio.app.data.ResultadoInventario
import com.baniterio.app.data.dto.ArticuloFiestaDto
import com.baniterio.app.data.dto.CategoriaFiestaDto
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoFiesta {
    data object Cargando : EstadoFiesta
    data class Cargada(val puedoEditar: Boolean, val categorias: List<CategoriaFiestaDto>) : EstadoFiesta
    data class Error(val mensaje: String) : EstadoFiesta
}

@Composable
fun InventarioFiestaScreen(
    inventarioRepo: InventarioRepository,
    eventoId: Long,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoFiesta>(EstadoFiesta.Cargando) }
    var intento by remember { mutableStateOf(0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf<ArticuloFiestaDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(intento) {
        estado = EstadoFiesta.Cargando
        estado = when (val r = inventarioRepo.inventarioFiesta(eventoId)) {
            is ResultadoInventario.Exito -> EstadoFiesta.Cargada(r.dato.puedoEditar, r.dato.categorias)
            is ResultadoInventario.Error -> EstadoFiesta.Error(r.mensaje)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BaniterioWordmark()
            Text("Volver", style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.brandBright, modifier = Modifier.clickable { onVolver() })
        }
        Text("Inventario de la fiesta", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text("Lo que se ha llevado del inventario de la peña a este evento.",
            style = MaterialTheme.typography.bodyMedium, color = BaniterioColors.muted)

        aviso?.let { Text(it, color = BaniterioColors.gold) }

        when (val e = estado) {
            is EstadoFiesta.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoFiesta.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { intento++ }) { Text("Reintentar") }
            }
            is EstadoFiesta.Cargada -> {
                if (e.categorias.isEmpty()) {
                    Text("Todavía no se ha enviado nada a este evento.", color = BaniterioColors.muted)
                }
                e.categorias.forEach { c ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .relieveDeCarta(RoundedCornerShape(14.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(c.etiqueta, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground)
                        c.articulos.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.nombre, color = MaterialTheme.colorScheme.onBackground)
                                    Text(a.tamano, style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.muted)
                                }
                                Text(fmtFiesta(a.cantidad), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground)
                            }
                            if (e.puedoEditar) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    OutlinedButton(onClick = { confirmar = a }) {
                                        Text("Devolver a inventario")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmar?.let { art ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = null
                    scope.launch {
                        aviso = null
                        when (val r = inventarioRepo.devolver(eventoId, art.id)) {
                            is ResultadoInventario.Exito -> Unit
                            is ResultadoInventario.Error -> aviso = r.mensaje
                        }
                        intento++
                    }
                }) { Text("Devolver") }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
            title = { Text("Devolver a inventario") },
            text = { Text("¿Devolver «${art.nombre}» al inventario general?") },
        )
    }
}

private fun fmtFiesta(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
```

- [ ] **Step 3: Wiring en `App.kt`**

- Constante `CLAVE_INVENTARIO_FIESTA = "InventarioFiesta"`.
- En `aClave()`: `Screen.InventarioFiesta -> CLAVE_INVENTARIO_FIESTA`.
- En `claveAScreen()`: `CLAVE_INVENTARIO_FIESTA -> Screen.InventarioFiesta`.
- En la guardia de arranque en frío añade `|| screen == Screen.InventarioFiesta`.
- Estado: reutiliza `eventoSeleccionado` (ya existe para el detalle) o añade `var inventarioFiestaEventoId by rememberSaveable { mutableStateOf<Long?>(null) }`. Usa uno nuevo para no pisar el del detalle:

```kotlin
    var inventarioFiestaEventoId by rememberSaveable { mutableStateOf<Long?>(null) }
```

- Rama del `when`:

```kotlin
                is Screen.InventarioFiesta -> {
                    BackHandler { ir(Screen.EventoDetalle) }
                    val id = inventarioFiestaEventoId
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.Eventos) }
                    } else {
                        InventarioFiestaScreen(
                            inventarioRepo = deps.inventarioRepo,
                            eventoId = id,
                            onVolver = { ir(Screen.EventoDetalle) },
                        )
                    }
                }
```

- Import `com.baniterio.app.ui.inventario.InventarioFiestaScreen`.

- [ ] **Step 4: Botón en `EventoDetalleScreen.kt`**

`EventoDetalleScreen` recibe callbacks (`onEditar`, `onBorrado`, `onVolver`…). Añade un parámetro `onInventarioFiesta: () -> Unit` y, dentro del bloque `if (ev.asistencia.ficha.llevaFicha) { ... }`, tras el `Row` de "Listado de asistentes" / pago, añade:

```kotlin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onInventarioFiesta,
                                modifier = Modifier.weight(1f),
                            ) { Text("Inventario de la fiesta", fontWeight = FontWeight.Bold) }
                        }
```

En `App.kt`, donde se instancia `EventoDetalleScreen` (rama `Screen.EventoDetalle`), pasa:

```kotlin
                        onInventarioFiesta = {
                            inventarioFiestaEventoId = id
                            ir(Screen.InventarioFiesta)
                        },
```

(`id` ahí es `eventoSeleccionado`, ya no-null en esa rama.)

- [ ] **Step 5: Compila y pasa tests**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app
git commit -m "feat(inventario): móvil — pantalla «Inventario de la fiesta» + botón en el evento

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: Cierre — regresión completa y memoria

**Files:**
- Modify: `C:\Users\rober\.claude\projects\c--Users-rober-Documents-Desarrollo-Ba-iterio\memory\baniterio_inventario.md`

- [ ] **Step 1: Regresión backend**

Run: `cd back && ./mvnw -q verify`
Expected: verde.

- [ ] **Step 2: Regresión web**

Run: `cd front && npx ng test --watch=false`
Expected: verde salvo el baseline conocido (~5 "errors" en `editor-evento.spec.ts`, preexistentes).

- [ ] **Step 3: Regresión móvil**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Actualiza la memoria**

En `memory/baniterio_inventario.md` añade una línea a las decisiones: "Inventario de la fiesta (V37, `articulo_evento`): enviar mueve stock del general al evento y lo deja a 0 (fila oculta); devolver lo inverso. Endpoints `enviar` / `enviar-categoria` / `GET inventario/evento/{id}` / `devolver` / `GET eventos/abiertos`. Pantalla web `/panel/eventos/:id/inventario` y `InventarioFiestaScreen` en móvil." Ajusta la `description` del frontmatter si hace falta. Spec: `docs/superpowers/specs/2026-09-09-inventario-evento-design.md`.

- [ ] **Step 5: Commit**

```bash
git add C:/Users/rober/.claude/projects/c--Users-rober-Documents-Desarrollo-Ba-iterio/memory/baniterio_inventario.md
git commit -m "docs(memoria): inventario de la fiesta implementado

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Finishing the branch**

Announce: "I'm using the finishing-a-development-branch skill to complete this work."
**REQUIRED SUB-SKILL:** Use superpowers:finishing-a-development-branch. Base branch: `main`. NO hacer merge sin que el usuario elija en el menú.

---

## Self-Review

**1. Cobertura del spec:**
- Tabla `articulo_evento` + entidad + repo → Task 1. ✅
- `GET /eventos/abiertos` → Task 3 (steps 3-5). ✅
- `POST /inventario/{id}/enviar` → Task 2. ✅
- `POST /inventario/enviar-categoria` → Task 2. ✅
- `GET /inventario/evento/{id}` → Task 3. ✅
- `POST /inventario/evento/{e}/{a}/devolver` → Task 3. ✅
- Filtro de 0 en `GET /inventario` → Task 2 (step 5). ✅
- Fusión en `POST /inventario` → Task 2 (steps 3, 5). ✅
- Excepciones `NADA_QUE_ENVIAR`, `ARTICULO_EVENTO_NO_ENCONTRADO` → Task 1 (step 7). ✅
- Web: tipos + servicio → Tasks 4, 5. Modal enviar → Task 4. Pantalla `InventarioFiesta` + ruta → Task 5. Enlace desde detalle → Task 5. ✅
- Móvil: DTOs + repo → Task 6. Modal enviar → Task 6. `InventarioFiestaScreen` + nav + botón → Task 7. ✅
- Tests backend/web/móvil → repartidos en cada task + Task 8 regresión. ✅

**2. Placeholders:** sin "TBD"/"TODO". Todos los steps de código llevan bloque real. El único "verifica/ajusta" es en tests que dependen de datos de siembra compartidos (`InventarioIT`, `EventoIT`) — inevitable sin ver el estado exacto de esos ficheros de test en el momento de ejecutar; la instrucción dice qué comprobar.

**3. Consistencia de tipos:**
- `ArticuloDto` (id, nombre, tamano, cantidad) se reutiliza en `CategoriaEventoDto` — coincide con el uso en Task 3 step 6 (`new ArticuloDto(f.getId(), f.getNombre(), f.getTamano(), f.getCantidad())`). ✅
- `CategoriaEventoDto(String categoria, String etiqueta, List<ArticuloDto> articulos)` — mismo shape en interfaz web `CategoriaFiesta` (con `categoria: CategoriaClave` en vez de string; el JSON encaja porque el backend manda `cat.name()`). ✅
- `EventoAbiertoDto(Long id, String nombre, LocalDate fecha)` ↔ web `EventoAbierto { id; nombre; fecha: string }` ↔ móvil `EventoAbiertoDto(Long, String, String)`. La fecha viaja como ISO string; en móvil se deja `String` (no se parsea). ✅
- `InventarioService.enviar/enviarCategoria/verEvento/devolver` — firmas idénticas entre la definición (Task 2/3) y las llamadas del controlador. ✅
- Móvil `InventarioRepository`: 5 firmas nuevas idénticas entre interfaz (Task 6 step 3) e impl (step 4) y tests (step 5). ✅
- Web `InventarioService`: `enviarAEvento(articuloId, eventoId)`, `enviarCategoria(categoria, eventoId)`, `inventarioFiesta(eventoId)`, `devolverAInventario(eventoId, articuloEventoId)` — mismas firmas en servicio (Tasks 4/5) y en componentes (`inventario-categoria.ts`, `inventario-fiesta.ts`). ✅

**4. Riesgos anotados:**
- Tests de `InventarioIT` comparten BBDD y mueven filas de siembra: cada test nuevo usa una fila distinta; anotado en Task 2 step 1.
- La ruta web `eventos/:id/inventario` debe ir antes de `eventos/:id`: anotado en Task 5 step 4.
- `EventoDetalleScreen` móvil quizá no tiene fila de botones extra: Task 7 step 4 la añade.
- El test de `evento-detalle.spec.ts` añadido en un commit anterior se reescribe en Task 5 step 7.
