# Inventario Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir una sección "Inventario" al panel web que muestra lo almacenado por la peña en cinco categorías, con un modo edición para quien tenga el permiso de área `INVENTARIO`.

**Architecture:** Vertical slice clásico del proyecto. Backend Spring Boot: enum de categoría con sus tamaños, entidad JPA `ArticuloInventario` con FK a `pena`, repositorio, migración Flyway con siembra, servicio + controlador REST bajo `/api/v1/inventario`, excepciones traducidas en `ApiExceptionHandler`. Frontend Angular: servicio HTTP, tipos, componente con vista de solo lectura + modo edición, ruta hija de `/panel`, entradas en el nav, y alta del área nueva en el mapa de permisos.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Flyway, PostgreSQL, Lombok, JUnit 5 + RestTestClient (IT con BBDD real). Angular (standalone components, signals), Vitest (`@angular/build:unit-test`), Tailwind.

**Spec:** `docs/superpowers/specs/2026-09-09-inventario-design.md`

## Global Constraints

- Java sigue en 17 (no subir el nivel de lenguaje).
- Peña única de momento: el backend resuelve la peña por `slug = "baniterio"` (mismo patrón que `CuentaService` / `ServicioPermisos`); no se pasa `penaId` desde el cliente.
- Todos los endpoints cuelgan de `/api/v1/...`.
- El cuerpo de error del backend siempre es `{ "codigo": "<CODIGO>" }` (o `{ "codigo": "VALIDACION", "errores": {...} }` para la validación de bean). Lo produce `ApiExceptionHandler`, no los controladores.
- Los `*IT` comparten una BBDD real; cada test crea sus propios usuarios/datos y no asume ids fijos.
- Textos de cara al usuario en castellano con tildes correctas.
- Commits pequeños y frecuentes, uno por tarea como mínimo. Formato de mensaje: `feat(inventario): ...` / `test(inventario): ...`. Terminar el mensaje con:
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`
- Solo web. Nada de tocar `mobile/`.
- Fuera de alcance: alta/baja de artículos desde la interfaz, historial de movimientos, avisos de stock bajo.

## Comandos de test

- Backend, un IT concreto: `cd back && ./mvnw -q -Dtest=InventarioIT test`
- Backend, un repo IT: `cd back && ./mvnw -q -Dtest=ArticuloInventarioRepositoryIT test`
- Backend, todo: `cd back && ./mvnw -q test`
- Frontend, un spec: `cd front && npx ng test --watch=false --include='**/inventario*.spec.ts'`
- Frontend, todo: `cd front && npx ng test --watch=false`

(En Windows: `mvnw.cmd` en vez de `./mvnw` si `./mvnw` no arranca.)

---

## File Structure

### Backend — paquete nuevo `com.baniterio.api.inventario`

| Fichero | Responsabilidad |
|---|---|
| `identidad/AreaProtegida.java` (modificar) | Añadir el valor `INVENTARIO` al enum de áreas concedibles. |
| `inventario/CategoriaInventario.java` (crear) | Enum de las 5 categorías; cada una lleva su etiqueta legible y su lista fija de tamaños permitidos. Método `permiteTamano`. |
| `inventario/ArticuloInventario.java` (crear) | Entidad JPA de la tabla `articulo_inventario`. |
| `inventario/ArticuloInventarioRepository.java` (crear) | Acceso a BBDD: listar por peña ordenado. |
| `inventario/InventarioService.java` (crear) | Lógica: montar la respuesta de listado; actualizar un artículo con sus validaciones y el chequeo de permiso. |
| `inventario/InventarioController.java` (crear) | `GET /api/v1/inventario`, `PUT /api/v1/inventario/{id}`. |
| `inventario/dto/ArticuloDto.java` (crear) | Un artículo en el JSON. |
| `inventario/dto/CategoriaInventarioDto.java` (crear) | Una categoría con sus tamaños y artículos. |
| `inventario/dto/InventarioResponse.java` (crear) | Raíz de `GET`: `puedoEditar` + `categorias`. |
| `inventario/dto/ActualizarArticuloRequest.java` (crear) | Cuerpo de `PUT`, con anotaciones de validación. |
| `inventario/ArticuloInventarioNoEncontradoException.java` (crear) | 404. |
| `inventario/SinPermisoInventarioException.java` (crear) | 403. |
| `inventario/TamanoInventarioNoValidoException.java` (crear) | 400. |
| `web/ApiExceptionHandler.java` (modificar) | Traducir las 3 excepciones nuevas. |
| `db/migration/V35__inventario.sql` (crear) | Tabla `articulo_inventario` + siembra. |

### Backend — tests

| Fichero | Responsabilidad |
|---|---|
| `inventario/ArticuloInventarioRepositoryIT.java` (crear) | La siembra V35 existe y se lista ordenada. |
| `inventario/InventarioIT.java` (crear) | `GET` (forma + `puedoEditar` por rol/permiso) y `PUT` (ok / 403 / 404 / 400). |

### Frontend — carpeta nueva `front/src/app/panel/inventario/`

| Fichero | Responsabilidad |
|---|---|
| `inventario.types.ts` (crear) | Tipos del contrato con `/api/v1/inventario`. |
| `inventario.service.ts` (crear) | `ver()` y `actualizar()`. |
| `inventario.ts` (crear) | Componente: estado, vista de solo lectura, modo edición. |
| `inventario.html` (crear) | Plantilla. |
| `inventario.service.spec.ts` (crear) | El servicio pega a las URLs correctas. |
| `inventario.spec.ts` (crear) | El componente pinta las categorías, enseña "Editar" solo con permiso, y guarda un `PUT` por fila cambiada. |
| `app.routes.ts` (modificar) | Ruta `panel/inventario`. |
| `panel/panel.ts` + `panel/panel.html` (modificar) | Enlace "Inventario" en el nav de escritorio y en el de móvil. |
| `panel/secciones.ts` (modificar) | Quitar "Inventario" de la lista "Pronto". |
| `panel/panel.spec.ts` (modificar) | "Inventario" es un enlace real, no una sección "Pronto". |
| `admin/admin.types.ts` (modificar) | `INVENTARIO` en el tipo `Area` y en el mapa `AREAS`. |

---

## Task 1: Backend — modelo de datos y siembra

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/identidad/AreaProtegida.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/CategoriaInventario.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/ArticuloInventario.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/ArticuloInventarioRepository.java`
- Create: `back/src/main/resources/db/migration/V35__inventario.sql`
- Test: `back/src/test/java/com/baniterio/api/inventario/ArticuloInventarioRepositoryIT.java`

**Interfaces:**
- Produces:
  - `enum CategoriaInventario { ALCOHOL, CERVEZA, REFRESCOS, LIMPIEZA, COMIDA }` con
    `String etiqueta()`, `List<String> tamanos()`, `boolean permiteTamano(String)`.
  - `class ArticuloInventario` (Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`) con
    `Long getId()`, `Pena getPena()`, `CategoriaInventario getCategoria()`, `String getNombre()`,
    `String getTamano()`, `BigDecimal getCantidad()`, `int getOrden()`, y sus setters.
  - `interface ArticuloInventarioRepository extends JpaRepository<ArticuloInventario, Long>` con
    `List<ArticuloInventario> findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long penaId)`.

- [ ] **Step 1: Añadir el valor al enum de áreas**

En `AreaProtegida.java`, dejar el enum así (mantener el Javadoc que ya hay encima):

```java
public enum AreaProtegida {
    ADMIN_SOLICITUDES,
    ADMIN_PERMISOS,
    INVENTARIO
}
```

- [ ] **Step 2: Crear el enum `CategoriaInventario`**

`back/src/main/java/com/baniterio/api/inventario/CategoriaInventario.java`:

```java
package com.baniterio.api.inventario;

import java.util.List;

/**
 * Las cinco categorías del inventario de la peña. Cada una lleva su etiqueta
 * legible y la lista fija de tamaños que se pueden elegir para un artículo de
 * esa categoría (el desplegable de la interfaz y la validación del PUT salen de
 * aquí). Añadir una categoría = añadir un valor aquí y sembrar sus artículos.
 */
public enum CategoriaInventario {

    ALCOHOL("Alcohol", List.of("70 cl", "1 L", "1,5 L")),
    CERVEZA("Cerveza", List.of("lata", "botellín", "tercio")),
    REFRESCOS("Refrescos", List.of("botella", "lata", "garrafa", "brick")),
    LIMPIEZA("Limpieza", List.of("unidad", "rollo", "paquete", "litro")),
    COMIDA("Comida", List.of("unidad", "paquete", "kg", "lata"));

    private final String etiqueta;
    private final List<String> tamanos;

    CategoriaInventario(String etiqueta, List<String> tamanos) {
        this.etiqueta = etiqueta;
        this.tamanos = tamanos;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public List<String> tamanos() {
        return tamanos;
    }

    /** {@code true} si {@code tamano} es uno de los tamaños permitidos en esta categoría. */
    public boolean permiteTamano(String tamano) {
        return tamanos.contains(tamano);
    }
}
```

- [ ] **Step 3: Crear la entidad `ArticuloInventario`**

`back/src/main/java/com/baniterio/api/inventario/ArticuloInventario.java`:

```java
package com.baniterio.api.inventario;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Pena;

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
 * Un artículo del inventario de la peña (fila de {@code articulo_inventario},
 * ver V35). Una fila por combinación nombre + tamaño: un mismo producto en dos
 * tamaños son dos filas. {@code cantidad} admite decimales (p. ej. 1.5 botellas).
 * Mismo patrón JPA + Lombok que {@link com.baniterio.api.identidad.Evento}.
 */
@Entity
@Table(name = "articulo_inventario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticuloInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pena_id", nullable = false)
    private Pena pena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaInventario categoria;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tamano;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private int orden;
}
```

- [ ] **Step 4: Crear el repositorio**

`back/src/main/java/com/baniterio/api/inventario/ArticuloInventarioRepository.java`:

```java
package com.baniterio.api.inventario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ArticuloInventario}. */
public interface ArticuloInventarioRepository extends JpaRepository<ArticuloInventario, Long> {

    /**
     * Todos los artículos de la peña, ordenados por categoría (orden del enum),
     * luego por {@code orden} dentro de la categoría, luego por nombre.
     */
    List<ArticuloInventario> findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long penaId);
}
```

- [ ] **Step 5: Crear la migración `V35__inventario.sql`**

`back/src/main/resources/db/migration/V35__inventario.sql`:

```sql
-- Inventario de la peña: material y bebida que hay almacenado, agrupado en cinco
-- categorías. Una fila por combinación nombre + tamaño. La cantidad admite
-- decimales (1.5 botellas). El "tamaño" se valida en el servicio contra la lista
-- fija de cada categoría (CategoriaInventario). Siembra idempotente por
-- (pena_id, categoria, nombre, tamano) vía INSERT ... WHERE NOT EXISTS.
CREATE TABLE articulo_inventario (
    id        BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pena_id   BIGINT        NOT NULL REFERENCES pena (id),
    categoria VARCHAR(20)   NOT NULL,
    nombre    VARCHAR(120)  NOT NULL,
    tamano    VARCHAR(20)   NOT NULL,
    cantidad  NUMERIC(8, 2) NOT NULL DEFAULT 0,
    orden     INT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_articulo_inventario_categoria
        CHECK (categoria IN ('ALCOHOL', 'CERVEZA', 'REFRESCOS', 'LIMPIEZA', 'COMIDA')),
    CONSTRAINT ck_articulo_inventario_cantidad CHECK (cantidad >= 0)
);
CREATE INDEX ix_articulo_inventario_pena ON articulo_inventario (pena_id);

INSERT INTO articulo_inventario (pena_id, categoria, nombre, tamano, cantidad, orden)
SELECT p.id, v.categoria, v.nombre, v.tamano, v.cantidad, v.orden
FROM pena p
CROSS JOIN (VALUES
    ('ALCOHOL',   'Tanqueray',            '70 cl',   1.5,  1),
    ('ALCOHOL',   'Seagram''s',           '70 cl',   0.8,  2),
    ('ALCOHOL',   'Beefeater',            '1 L',     0.8,  3),
    ('ALCOHOL',   'Negrita',              '1 L',     1,    4),
    ('ALCOHOL',   'Legendario',           '70 cl',   2.5,  5),
    ('ALCOHOL',   'Puerto de Indias',     '1 L',     0.8,  6),
    ('ALCOHOL',   'Barceló',              '70 cl',   0.5,  7),
    ('ALCOHOL',   'Brugal',               '1 L',     1,    8),
    ('ALCOHOL',   'Four Roses',           '70 cl',   0.8,  9),
    ('ALCOHOL',   'Four Roses',           '1 L',     1,    10),
    ('ALCOHOL',   'Ballantine''s',        '70 cl',   1,    11),
    ('CERVEZA',   'Sin gluten',           'lata',    8,    1),
    ('CERVEZA',   'Mahou Clásica',        'lata',    192,  2),
    ('CERVEZA',   'Mahou 0,0 Tostada',    'lata',    14,   3),
    ('CERVEZA',   'Mixta',                'lata',    3,    4),
    ('CERVEZA',   'Coronita',             'lata',    3,    5),
    ('LIMPIEZA',  'Bayetas',              'unidad',  5,    1),
    ('LIMPIEZA',  'Fregasuelos',          'litro',   0.5,  2),
    ('LIMPIEZA',  'Papel de cocina',      'rollo',   1.5,  3),
    ('LIMPIEZA',  'Platos',               'unidad',  35,   4),
    ('LIMPIEZA',  'Vasos de chupito',     'unidad',  25,   5),
    ('LIMPIEZA',  'Papel higiénico',      'rollo',   12,   6),
    ('LIMPIEZA',  'Vasos de mini',        'unidad',  100,  7),
    ('LIMPIEZA',  'Vasos de sidra',       'unidad',  35,   8),
    ('LIMPIEZA',  'Mantel 1,2 x 5',       'unidad',  1,    9),
    ('REFRESCOS', 'Tónica',               'botella', 3,    1),
    ('REFRESCOS', 'Coca-Cola Zero',       'botella', 15,   2),
    ('REFRESCOS', 'Coca-Cola Zero Zero',  'botella', 5,    3),
    ('REFRESCOS', 'Red Bull',             'lata',    2,    4),
    ('REFRESCOS', 'Gin-tonic Tanqueray',  'lata',    2,    5),
    ('REFRESCOS', 'Aquarius naranja',     'botella', 4,    6),
    ('REFRESCOS', 'Aquarius limón',       'botella', 1,    7),
    ('REFRESCOS', 'Agua',                 'garrafa', 3,    8),
    ('REFRESCOS', 'Coca-Cola Light',      'botella', 5,    9),
    ('REFRESCOS', 'Coca-Cola normal',     'botella', 4,    10),
    ('REFRESCOS', 'Nestea',               'botella', 4,    11),
    ('REFRESCOS', 'Fanta limón',          'botella', 1,    12),
    ('REFRESCOS', 'Tinto de verano',      'botella', 4,    13)
) AS v(categoria, nombre, tamano, cantidad, orden)
WHERE p.slug = 'baniterio'
  AND NOT EXISTS (
      SELECT 1 FROM articulo_inventario a
      WHERE a.pena_id = p.id AND a.categoria = v.categoria
        AND a.nombre = v.nombre AND a.tamano = v.tamano
  );
```

- [ ] **Step 6: Escribir el repo IT (falla al no existir la clase de test aún… escríbela y compílala)**

`back/src/test/java/com/baniterio/api/inventario/ArticuloInventarioRepositoryIT.java`:

```java
package com.baniterio.api.inventario;

import java.util.List;

import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** La siembra V35 del inventario existe y se lista ordenada por categoría/orden. */
class ArticuloInventarioRepositoryIT extends IntegrationTest {

    @Autowired
    ArticuloInventarioRepository articulos;

    @Autowired
    PenaRepository penas;

    Long penaId() {
        return penas.findBySlug("baniterio").orElseThrow().getId();
    }

    @Test
    void la_siembra_incluye_las_cuatro_categorias_con_datos() {
        List<ArticuloInventario> lista =
                articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId());

        assertThat(lista).extracting(ArticuloInventario::getCategoria)
                .contains(CategoriaInventario.ALCOHOL, CategoriaInventario.CERVEZA,
                        CategoriaInventario.LIMPIEZA, CategoriaInventario.REFRESCOS);
        assertThat(lista).filteredOn(a -> a.getCategoria() == CategoriaInventario.CERVEZA)
                .anyMatch(a -> a.getNombre().equals("Mahou Clásica")
                        && a.getCantidad().intValue() == 192
                        && a.getTamano().equals("lata"));
    }

    @Test
    void cada_articulo_tiene_un_tamano_valido_para_su_categoria() {
        List<ArticuloInventario> lista =
                articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId());

        assertThat(lista).isNotEmpty();
        assertThat(lista).allMatch(a -> a.getCategoria().permiteTamano(a.getTamano()));
    }
}
```

- [ ] **Step 7: Ejecutar el repo IT**

Run: `cd back && ./mvnw -q -Dtest=ArticuloInventarioRepositoryIT test`
Expected: PASS (2 tests verdes). Si falla la compilación de Flyway/entidad, arreglar antes de seguir.

- [ ] **Step 8: Commit**

```bash
git add back/src/main/java/com/baniterio/api/identidad/AreaProtegida.java \
        back/src/main/java/com/baniterio/api/inventario/ \
        back/src/main/resources/db/migration/V35__inventario.sql \
        back/src/test/java/com/baniterio/api/inventario/ArticuloInventarioRepositoryIT.java
git commit -m "feat(inventario): modelo, migración y siembra del inventario

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Backend — servicio y API REST

**Files:**
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/ArticuloDto.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/CategoriaInventarioDto.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/InventarioResponse.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/dto/ActualizarArticuloRequest.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/ArticuloInventarioNoEncontradoException.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/SinPermisoInventarioException.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/TamanoInventarioNoValidoException.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/InventarioService.java`
- Create: `back/src/main/java/com/baniterio/api/inventario/InventarioController.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`
- Test: `back/src/test/java/com/baniterio/api/inventario/InventarioIT.java`

**Interfaces:**
- Consumes (de la Task 1): `CategoriaInventario`, `ArticuloInventario`,
  `ArticuloInventarioRepository.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long)`,
  `AreaProtegida.INVENTARIO`.
- Consumes (ya existentes en el repo):
  - `com.baniterio.api.auth.ServicioPermisos` con `boolean puede(Long usuarioId, AreaProtegida area)`.
  - `com.baniterio.api.identidad.PenaRepository` con `Optional<Pena> findBySlug(String)`.
  - `com.baniterio.api.auth.UsuarioPrincipal` con `Long id()`.
- Produces:
  - `record ArticuloDto(Long id, String nombre, String tamano, BigDecimal cantidad)`
    con `static ArticuloDto de(ArticuloInventario a)`.
  - `record CategoriaInventarioDto(String categoria, String etiqueta, List<String> tamanos, List<ArticuloDto> articulos)`.
  - `record InventarioResponse(boolean puedoEditar, List<CategoriaInventarioDto> categorias)`.
  - `record ActualizarArticuloRequest(@NotBlank String nombre, @NotBlank String tamano, @NotNull @PositiveOrZero BigDecimal cantidad)`.
  - `InventarioService` con
    `InventarioResponse ver(Long usuarioId)` y
    `ArticuloDto actualizar(Long usuarioId, Long articuloId, ActualizarArticuloRequest req)`.
  - Endpoints: `GET /api/v1/inventario` → `InventarioResponse`; `PUT /api/v1/inventario/{id}` → `ArticuloDto`.
  - Códigos de error nuevos: `ARTICULO_INVENTARIO_NO_ENCONTRADO` (404), `SIN_PERMISO_INVENTARIO` (403), `TAMANO_INVENTARIO_NO_VALIDO` (400).

- [ ] **Step 1: Crear los DTOs**

`dto/ArticuloDto.java`:

```java
package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import com.baniterio.api.inventario.ArticuloInventario;

/** Un artículo del inventario en el JSON. */
public record ArticuloDto(Long id, String nombre, String tamano, BigDecimal cantidad) {

    public static ArticuloDto de(ArticuloInventario a) {
        return new ArticuloDto(a.getId(), a.getNombre(), a.getTamano(), a.getCantidad());
    }
}
```

`dto/CategoriaInventarioDto.java`:

```java
package com.baniterio.api.inventario.dto;

import java.util.List;

/** Una categoría del inventario con sus tamaños permitidos y sus artículos. */
public record CategoriaInventarioDto(String categoria, String etiqueta,
        List<String> tamanos, List<ArticuloDto> articulos) {
}
```

`dto/InventarioResponse.java`:

```java
package com.baniterio.api.inventario.dto;

import java.util.List;

/**
 * Raíz de {@code GET /api/v1/inventario}: si quien pregunta puede editar
 * ({@code puedoEditar}) y las cinco categorías, cada una con sus artículos
 * (puede venir vacía, p. ej. Comida).
 */
public record InventarioResponse(boolean puedoEditar, List<CategoriaInventarioDto> categorias) {
}
```

`dto/ActualizarArticuloRequest.java`:

```java
package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Cuerpo de {@code PUT /api/v1/inventario/{id}}. La categoría no se puede cambiar. */
public record ActualizarArticuloRequest(
        @NotBlank String nombre,
        @NotBlank String tamano,
        @NotNull @PositiveOrZero BigDecimal cantidad) {
}
```

- [ ] **Step 2: Crear las excepciones**

`ArticuloInventarioNoEncontradoException.java`:

```java
package com.baniterio.api.inventario;

/** No hay artículo con ese id en la peña. La traduce {@code ApiExceptionHandler} a 404. */
public class ArticuloInventarioNoEncontradoException extends RuntimeException {
}
```

`SinPermisoInventarioException.java`:

```java
package com.baniterio.api.inventario;

/** El usuario no tiene el área {@code INVENTARIO}. La traduce {@code ApiExceptionHandler} a 403. */
public class SinPermisoInventarioException extends RuntimeException {
}
```

`TamanoInventarioNoValidoException.java`:

```java
package com.baniterio.api.inventario;

/**
 * El tamaño enviado no es uno de los permitidos en la categoría del artículo.
 * La traduce {@code ApiExceptionHandler} a 400.
 */
public class TamanoInventarioNoValidoException extends RuntimeException {
}
```

- [ ] **Step 3: Crear el servicio**

`InventarioService.java`:

```java
package com.baniterio.api.inventario;

import java.util.List;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.inventario.dto.ActualizarArticuloRequest;
import com.baniterio.api.inventario.dto.ArticuloDto;
import com.baniterio.api.inventario.dto.CategoriaInventarioDto;
import com.baniterio.api.inventario.dto.InventarioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sección Inventario: montar la hoja de listado y actualizar un artículo. La
 * peña es la piloto ({@code slug = "baniterio"}), igual que en
 * {@code CuentaService}. Ver un artículo lo puede cualquier usuario logueado;
 * editarlo exige el área {@link AreaProtegida#INVENTARIO}.
 */
@Service
public class InventarioService {

    private static final String SLUG_PENA = "baniterio";

    private final ArticuloInventarioRepository articulos;
    private final PenaRepository penas;
    private final ServicioPermisos permisos;

    public InventarioService(ArticuloInventarioRepository articulos, PenaRepository penas,
                             ServicioPermisos permisos) {
        this.articulos = articulos;
        this.penas = penas;
        this.permisos = permisos;
    }

    private Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }

    @Transactional(readOnly = true)
    public InventarioResponse ver(Long usuarioId) {
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);
        List<ArticuloInventario> filas =
                articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId());

        List<CategoriaInventarioDto> categorias = java.util.Arrays.stream(CategoriaInventario.values())
                .map(cat -> new CategoriaInventarioDto(
                        cat.name(),
                        cat.etiqueta(),
                        cat.tamanos(),
                        filas.stream()
                                .filter(a -> a.getCategoria() == cat)
                                .map(ArticuloDto::de)
                                .toList()))
                .toList();

        return new InventarioResponse(puedoEditar, categorias);
    }

    @Transactional
    public ArticuloDto actualizar(Long usuarioId, Long articuloId, ActualizarArticuloRequest req) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        ArticuloInventario art = articulos.findById(articuloId)
                .filter(a -> a.getPena().getId().equals(penaId()))
                .orElseThrow(ArticuloInventarioNoEncontradoException::new);

        if (!art.getCategoria().permiteTamano(req.tamano())) {
            throw new TamanoInventarioNoValidoException();
        }

        art.setNombre(req.nombre().trim());
        art.setTamano(req.tamano());
        art.setCantidad(req.cantidad());
        return ArticuloDto.de(articulos.save(art));
    }
}
```

- [ ] **Step 4: Crear el controlador**

`InventarioController.java`:

```java
package com.baniterio.api.inventario;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.inventario.dto.ActualizarArticuloRequest;
import com.baniterio.api.inventario.dto.ArticuloDto;
import com.baniterio.api.inventario.dto.InventarioResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Inventario: consultar lo almacenado (cualquier peñista) y ajustar un
 * artículo (área {@code INVENTARIO}, se comprueba en el servicio). De momento
 * solo se editan artículos que ya existen; el alta/baja llega después.
 */
@Tag(name = "Inventario", description = "Material y bebida almacenados por la peña, por categorías.")
@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    /** El inventario completo agrupado por categoría. {@code puedoEditar} dice si quien pregunta puede tocar. */
    @GetMapping
    public InventarioResponse ver(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.ver(principal.id());
    }

    /** Cambia nombre, tamaño y cantidad de un artículo. Área {@code INVENTARIO}. */
    @PutMapping("/{id}")
    public ArticuloDto actualizar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long id, @Valid @RequestBody ActualizarArticuloRequest req) {
        return service.actualizar(principal.id(), id, req);
    }
}
```

- [ ] **Step 5: Enganchar las excepciones en `ApiExceptionHandler`**

En `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java`, añadir 3 métodos junto a los demás `@ExceptionHandler` (p. ej. tras los de `cuenta`):

```java
    @ExceptionHandler(com.baniterio.api.inventario.ArticuloInventarioNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> articuloInventarioNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "ARTICULO_INVENTARIO_NO_ENCONTRADO");
    }

    @ExceptionHandler(com.baniterio.api.inventario.SinPermisoInventarioException.class)
    ResponseEntity<Map<String, Object>> sinPermisoInventario() {
        return error(HttpStatus.FORBIDDEN, "SIN_PERMISO_INVENTARIO");
    }

    @ExceptionHandler(com.baniterio.api.inventario.TamanoInventarioNoValidoException.class)
    ResponseEntity<Map<String, Object>> tamanoInventarioNoValido() {
        return error(HttpStatus.BAD_REQUEST, "TAMANO_INVENTARIO_NO_VALIDO");
    }
```

- [ ] **Step 6: Escribir el IT de la API**

`back/src/test/java/com/baniterio/api/inventario/InventarioIT.java`:

```java
package com.baniterio.api.inventario;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.Membresia;
import com.baniterio.api.identidad.MembresiaRepository;
import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.identidad.PermisoArea;
import com.baniterio.api.identidad.PermisoAreaRepository;
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

/** GET (forma + puedoEditar) y PUT (ok / 403 / 404 / 400) de la sección Inventario. */
class InventarioIT extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    MembresiaRepository membresias;
    @Autowired
    PenaRepository penas;
    @Autowired
    PermisoAreaRepository permisos;
    @Autowired
    ArticuloInventarioRepository articulos;
    @Autowired
    PasswordEncoder passwordEncoder;

    RestTestClient http;

    @BeforeEach
    void setUp() {
        http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    Pena pena() {
        return penas.findBySlug("baniterio").orElseThrow();
    }

    /** Crea un usuario con el rol dado y opcionalmente el área INVENTARIO, y devuelve su token. */
    String token(RolMembresia rol, boolean conAreaInventario) {
        String tel = "6" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario u = usuarios.save(Usuario.builder()
                .telefono(tel).email(tel + "@inv.test")
                .passwordHash(passwordEncoder.encode("secreto1"))
                .nombre("N" + tel).apellidos("Inv").esSuperadmin(false).activo(true).build());
        membresias.save(Membresia.builder().usuario(u).pena(pena()).rol(rol).activa(true).build());
        if (conAreaInventario) {
            permisos.save(PermisoArea.builder().usuario(u).area(AreaProtegida.INVENTARIO).build());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/v1/auth/login")
                .body(Map.of("telefono", tel, "password", "secreto1"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("token");
    }

    Long idDe(String categoria, String nombre) {
        return articulos.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(pena().getId()).stream()
                .filter(a -> a.getCategoria().name().equals(categoria) && a.getNombre().equals(nombre))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void get_devuelve_las_cinco_categorias_y_puedoEditar_false_para_penista() {
        String token = token(RolMembresia.MIEMBRO, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(body.get("puedoEditar")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cats = (List<Map<String, Object>>) body.get("categorias");
        assertThat(cats).hasSize(5);
        assertThat(cats).extracting(c -> c.get("categoria"))
                .containsExactly("ALCOHOL", "CERVEZA", "REFRESCOS", "LIMPIEZA", "COMIDA");
        Map<String, Object> comida = cats.get(4);
        assertThat((List<?>) comida.get("articulos")).isEmpty();
    }

    @Test
    void get_puedoEditar_true_con_area_inventario() {
        String token = token(RolMembresia.MIEMBRO, true);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.get().uri("/api/v1/inventario")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();

        assertThat(body.get("puedoEditar")).isEqualTo(true);
    }

    @Test
    void put_con_permiso_cambia_la_cantidad() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long id = idDe("CERVEZA", "Mixta");

        http.put().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "Mixta", "tamano", "lata", "cantidad", 9))
                .exchange().expectStatus().isOk();

        assertThat(articulos.findById(id).orElseThrow().getCantidad().intValue()).isEqualTo(9);
    }

    @Test
    void put_sin_permiso_es_403() {
        String token = token(RolMembresia.MIEMBRO, false);
        Long id = idDe("CERVEZA", "Mixta");

        http.put().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "Mixta", "tamano", "lata", "cantidad", 9))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void put_con_tamano_ajeno_a_la_categoria_es_400() {
        String token = token(RolMembresia.MIEMBRO, true);
        Long id = idDe("CERVEZA", "Mixta");

        http.put().uri("/api/v1/inventario/" + id)
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "Mixta", "tamano", "garrafa", "cantidad", 9))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void put_a_id_inexistente_es_404() {
        String token = token(RolMembresia.MIEMBRO, true);

        http.put().uri("/api/v1/inventario/999999")
                .header(AUTHORIZATION, "Bearer " + token)
                .body(Map.of("nombre", "X", "tamano", "lata", "cantidad", 1))
                .exchange().expectStatus().isNotFound();
    }
}
```

- [ ] **Step 7: Ejecutar el IT**

Run: `cd back && ./mvnw -q -Dtest=InventarioIT test`
Expected: PASS (6 tests verdes).

- [ ] **Step 8: Ejecutar toda la suite backend**

Run: `cd back && ./mvnw -q test`
Expected: PASS. (Comprueba que añadir `INVENTARIO` al enum no rompe ningún test de permisos/áreas existente; si algún test asume "exactamente 2 áreas", ajústalo para que cuente las de `AreaProtegida.values()`.)

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/inventario/ \
        back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java \
        back/src/test/java/com/baniterio/api/inventario/InventarioIT.java
git commit -m "feat(inventario): API de listado y edición de artículos

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: Frontend — servicio y tipos

**Files:**
- Create: `front/src/app/panel/inventario/inventario.types.ts`
- Create: `front/src/app/panel/inventario/inventario.service.ts`
- Test: `front/src/app/panel/inventario/inventario.service.spec.ts`

**Interfaces:**
- Consumes: endpoints `GET /api/v1/inventario` y `PUT /api/v1/inventario/{id}` de la Task 2
  (`environment.apiBaseUrl` ya es `.../api/v1`).
- Produces:
  - `type CategoriaClave = 'ALCOHOL' | 'CERVEZA' | 'REFRESCOS' | 'LIMPIEZA' | 'COMIDA'`
  - `interface ArticuloInventario { id: number; nombre: string; tamano: string; cantidad: number }`
  - `interface CategoriaInventario { categoria: CategoriaClave; etiqueta: string; tamanos: string[]; articulos: ArticuloInventario[] }`
  - `interface InventarioResponse { puedoEditar: boolean; categorias: CategoriaInventario[] }`
  - `interface CambioArticulo { nombre: string; tamano: string; cantidad: number }`
  - `class InventarioService` con
    `ver(): Observable<InventarioResponse>` y
    `actualizar(id: number, cambio: CambioArticulo): Observable<ArticuloInventario>`.

- [ ] **Step 1: Crear los tipos**

`front/src/app/panel/inventario/inventario.types.ts`:

```typescript
/**
 * Tipos del contrato con `/api/v1/inventario`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.inventario.dto`). De momento la sección lista el
 * inventario y edita artículos que ya existen; el alta/baja llega después.
 */

export type CategoriaClave = 'ALCOHOL' | 'CERVEZA' | 'REFRESCOS' | 'LIMPIEZA' | 'COMIDA';

export interface ArticuloInventario {
  id: number;
  nombre: string;
  tamano: string;
  cantidad: number;
}

export interface CategoriaInventario {
  categoria: CategoriaClave;
  etiqueta: string;
  tamanos: string[];
  articulos: ArticuloInventario[];
}

export interface InventarioResponse {
  puedoEditar: boolean;
  categorias: CategoriaInventario[];
}

/** Cuerpo del PUT: los tres campos editables de un artículo. */
export interface CambioArticulo {
  nombre: string;
  tamano: string;
  cantidad: number;
}
```

- [ ] **Step 2: Crear el servicio**

`front/src/app/panel/inventario/inventario.service.ts`:

```typescript
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ArticuloInventario, CambioArticulo, InventarioResponse } from './inventario.types';

/**
 * Llamadas de la sección Inventario (`/api/v1/inventario*`). Un método por
 * endpoint; no maneja errores, los deja propagar para que la pantalla traduzca
 * el `codigo` del backend (mismo patrón que `CuentasService`).
 */
@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  ver(): Observable<InventarioResponse> {
    return this.http.get<InventarioResponse>(`${this.base}/inventario`);
  }

  actualizar(id: number, cambio: CambioArticulo): Observable<ArticuloInventario> {
    return this.http.put<ArticuloInventario>(`${this.base}/inventario/${id}`, cambio);
  }
}
```

- [ ] **Step 3: Escribir el spec del servicio**

`front/src/app/panel/inventario/inventario.service.spec.ts`:

```typescript
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { InventarioService } from './inventario.service';

describe('InventarioService', () => {
  let service: InventarioService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InventarioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('ver() pega a GET /inventario', () => {
    service.ver().subscribe();
    const req = httpMock.expectOne(`${base}/inventario`);
    expect(req.request.method).toBe('GET');
    req.flush({ puedoEditar: false, categorias: [] });
  });

  it('actualizar() pega a PUT /inventario/:id con el cambio', () => {
    service.actualizar(7, { nombre: 'Mixta', tamano: 'lata', cantidad: 9 }).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ nombre: 'Mixta', tamano: 'lata', cantidad: 9 });
    req.flush({ id: 7, nombre: 'Mixta', tamano: 'lata', cantidad: 9 });
  });
});
```

- [ ] **Step 4: Ejecutar el spec**

Run: `cd front && npx ng test --watch=false --include='**/inventario.service.spec.ts'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add front/src/app/panel/inventario/inventario.types.ts \
        front/src/app/panel/inventario/inventario.service.ts \
        front/src/app/panel/inventario/inventario.service.spec.ts
git commit -m "feat(inventario): servicio y tipos del front

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: Frontend — pantalla, ruta, nav y permiso

**Files:**
- Create: `front/src/app/panel/inventario/inventario.ts`
- Create: `front/src/app/panel/inventario/inventario.html`
- Test: `front/src/app/panel/inventario/inventario.spec.ts`
- Modify: `front/src/app/app.routes.ts`
- Modify: `front/src/app/panel/panel.ts`
- Modify: `front/src/app/panel/panel.html`
- Modify: `front/src/app/panel/secciones.ts`
- Modify: `front/src/app/panel/panel.spec.ts`
- Modify: `front/src/app/admin/admin.types.ts`

**Interfaces:**
- Consumes (Task 3): `InventarioService.ver()`, `InventarioService.actualizar(id, cambio)`,
  tipos `InventarioResponse`, `CategoriaInventario`, `ArticuloInventario`, `CambioArticulo`.
- Produces: componente `Inventario` (selector `app-inventario`) montado en `/panel/inventario`;
  entrada "Inventario" en el nav; área `'INVENTARIO'` conocida por el front.

- [ ] **Step 1: Crear el componente**

`front/src/app/panel/inventario/inventario.ts`:

```typescript
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InventarioService } from './inventario.service';
import {
  ArticuloInventario,
  CategoriaInventario,
  InventarioResponse,
} from './inventario.types';

/**
 * Sección Inventario: lo que la peña tiene almacenado, en cinco categorías.
 * Cualquier peñista lo ve; si el backend dice `puedoEditar`, aparece el botón
 * "Editar" que convierte las filas en campos (nombre, tamaño de la lista de la
 * categoría, cantidad). "Guardar" manda un PUT por cada fila que haya cambiado.
 *
 * Estado en signals:
 * - `estado`: 'cargando' | 'listo' | 'error' — controla el @switch de la plantilla.
 * - `datos`: la respuesta del backend tal cual (categorías + puedoEditar).
 * - `editando`: `true` mientras se está en modo edición.
 * - `borrador`: mapa id -> {nombre, tamano, cantidad} con los valores que se están tocando.
 * - `guardando`: bloquea el botón mientras van los PUT.
 * - `aviso`: banda de texto arriba (error de guardado, etc.).
 */
@Component({
  selector: 'app-inventario',
  imports: [FormsModule],
  templateUrl: './inventario.html',
  styleUrl: './inventario.css',
})
export class Inventario implements OnInit {
  private readonly inventarioService = inject(InventarioService);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly datos = signal<InventarioResponse | null>(null);
  protected readonly editando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly aviso = signal('');
  protected readonly borrador = signal<Record<number, { nombre: string; tamano: string; cantidad: number }>>(
    {},
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.inventarioService.ver().subscribe({
      next: (datos) => {
        this.datos.set(datos);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected editar(): void {
    const datos = this.datos();
    if (!datos) return;
    const inicial: Record<number, { nombre: string; tamano: string; cantidad: number }> = {};
    for (const cat of datos.categorias) {
      for (const a of cat.articulos) {
        inicial[a.id] = { nombre: a.nombre, tamano: a.tamano, cantidad: a.cantidad };
      }
    }
    this.borrador.set(inicial);
    this.aviso.set('');
    this.editando.set(true);
  }

  protected cancelar(): void {
    this.editando.set(false);
    this.borrador.set({});
  }

  /** Actualiza un campo del borrador de una fila. */
  protected cambiar(id: number, campo: 'nombre' | 'tamano' | 'cantidad', valor: string): void {
    const actual = this.borrador();
    const fila = { ...actual[id] };
    if (campo === 'cantidad') {
      fila.cantidad = Number(valor);
    } else {
      fila[campo] = valor;
    }
    this.borrador.set({ ...actual, [id]: fila });
  }

  /** Filas cuyo borrador difiere del valor cargado. */
  private cambiadas(): ArticuloInventario[] {
    const datos = this.datos();
    const b = this.borrador();
    if (!datos) return [];
    const res: ArticuloInventario[] = [];
    for (const cat of datos.categorias) {
      for (const a of cat.articulos) {
        const d = b[a.id];
        if (d && (d.nombre !== a.nombre || d.tamano !== a.tamano || d.cantidad !== a.cantidad)) {
          res.push(a);
        }
      }
    }
    return res;
  }

  protected guardar(): void {
    const b = this.borrador();
    const pendientes = this.cambiadas();
    if (pendientes.length === 0) {
      this.cancelar();
      return;
    }
    this.guardando.set(true);
    this.aviso.set('');
    let restantes = pendientes.length;
    let huboError = false;
    for (const a of pendientes) {
      this.inventarioService.actualizar(a.id, b[a.id]).subscribe({
        next: () => {
          restantes -= 1;
          if (restantes === 0) this.terminarGuardado(huboError);
        },
        error: () => {
          huboError = true;
          restantes -= 1;
          if (restantes === 0) this.terminarGuardado(huboError);
        },
      });
    }
  }

  private terminarGuardado(huboError: boolean): void {
    this.guardando.set(false);
    this.editando.set(false);
    this.borrador.set({});
    if (huboError) {
      this.aviso.set('Algún cambio no se pudo guardar. Recargo el inventario.');
    }
    this.cargar();
  }

  protected trackCat = (_: number, c: CategoriaInventario) => c.categoria;
  protected trackArt = (_: number, a: ArticuloInventario) => a.id;
}
```

Además crea un fichero vacío `front/src/app/panel/inventario/inventario.css` (los componentes del proyecto declaran `styleUrl`; puede quedar vacío).

- [ ] **Step 2: Crear la plantilla**

`front/src/app/panel/inventario/inventario.html`:

```html
<section class="mx-auto w-full max-w-6xl">
  <div class="flex flex-wrap items-center justify-between gap-3">
    <h1 class="font-display text-2xl font-extrabold">Inventario</h1>
    @if (datos()?.puedoEditar && estado() === 'listo') {
      @if (!editando()) {
        <button type="button" (click)="editar()"
          class="rounded-lg border border-outline px-3 py-1 text-xs font-medium hover:border-gold hover:text-gold">
          Editar
        </button>
      } @else {
        <div class="flex gap-2">
          <button type="button" [disabled]="guardando()" (click)="guardar()"
            class="rounded-lg bg-gold px-3 py-1 text-xs font-semibold text-brand-dark disabled:opacity-50">
            Guardar
          </button>
          <button type="button" (click)="cancelar()"
            class="rounded-lg border border-outline px-3 py-1 text-xs">Cancelar</button>
        </div>
      }
    }
  </div>

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
      <p class="mt-4 text-sm text-red-300">No se ha podido cargar el inventario.</p>
      <button type="button" (click)="cargar()"
        class="mt-2 rounded-lg border border-outline px-3 py-1 text-xs">Reintentar</button>
    }
    @case ('listo') {
      @for (cat of datos()!.categorias; track trackCat($index, cat)) {
        <article class="carta-relieve mt-4 rounded-2xl p-5">
          <h2 class="font-display text-lg font-bold">{{ cat.etiqueta }}</h2>
          <div class="mt-3 overflow-x-auto">
            <table class="w-full border-collapse text-sm [&_td]:border [&_td]:border-outline/40 [&_th]:border [&_th]:border-outline/40">
              <thead class="bg-surface/60 text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th class="px-3 py-2 text-left">Artículo</th>
                  <th class="px-3 py-2 text-left">Tamaño</th>
                  <th class="px-3 py-2 text-right">Cantidad</th>
                </tr>
              </thead>
              <tbody>
                @for (a of cat.articulos; track trackArt($index, a)) {
                  <tr>
                    @if (editando()) {
                      <td class="px-3 py-2">
                        <input type="text" [ngModel]="borrador()[a.id].nombre"
                          (ngModelChange)="cambiar(a.id, 'nombre', $event)"
                          class="w-full rounded border border-outline bg-surface px-2 py-1" />
                      </td>
                      <td class="px-3 py-2">
                        <select [ngModel]="borrador()[a.id].tamano"
                          (ngModelChange)="cambiar(a.id, 'tamano', $event)"
                          class="rounded border border-outline bg-surface px-2 py-1">
                          @for (t of cat.tamanos; track t) {
                            <option [value]="t">{{ t }}</option>
                          }
                        </select>
                      </td>
                      <td class="px-3 py-2 text-right">
                        <input type="number" step="0.1" min="0" [ngModel]="borrador()[a.id].cantidad"
                          (ngModelChange)="cambiar(a.id, 'cantidad', $event)"
                          class="w-24 rounded border border-outline bg-surface px-2 py-1 text-right" />
                      </td>
                    } @else {
                      <td class="px-3 py-2">{{ a.nombre }}</td>
                      <td class="px-3 py-2 text-muted">{{ a.tamano }}</td>
                      <td class="px-3 py-2 text-right font-medium">{{ a.cantidad }}</td>
                    }
                  </tr>
                } @empty {
                  <tr>
                    <td class="px-3 py-3 text-muted" colspan="3">Nada apuntado todavía.</td>
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

- [ ] **Step 3: Registrar la ruta**

En `front/src/app/app.routes.ts`:

1. Añadir el import junto a los demás de `./panel/...`:

```typescript
import { Inventario } from './panel/inventario/inventario';
```

2. Añadir la ruta hija dentro de `children` de `panel`, junto a las de `cuentas`:

```typescript
      { path: 'inventario', component: Inventario, canActivate: [perfilCompletoGuard] },
```

(No lleva `areaGuard`: cualquier peñista ve la sección; el permiso solo controla editar, y eso lo comprueba el backend.)

- [ ] **Step 4: Añadir el enlace al nav**

En `front/src/app/panel/panel.html`, añadir un enlace "Inventario" **después** del de "Cuentas** en los DOS navs (escritorio, ~línea 41; móvil, ~línea 117), copiando exactamente el patrón del enlace de "Cuentas":

Nav de escritorio (tras el `<a ...>Cuentas</a>`):

```html
      <a
        class="rounded-xl px-4 py-2.5 text-muted transition hover:text-gold"
        routerLink="/panel/inventario"
        routerLinkActive="bg-brand/15 text-ink"
        >Inventario</a
      >
```

Nav móvil (tras el `<a ...>Cuentas</a>` de la barra `lg:hidden`):

```html
      <a
        class="shrink-0 rounded-full px-4 py-1.5 text-sm font-medium text-muted transition hover:text-gold"
        routerLink="/panel/inventario"
        routerLinkActive="bg-brand/15 text-ink"
        >Inventario</a
      >
```

`panel.ts` no necesita cambios (los enlaces reales van directos en la plantilla; `seccionesPronto` solo alimenta las etiquetas "Pronto").

- [ ] **Step 5: Quitar "Inventario" de las secciones "Pronto"**

En `front/src/app/panel/secciones.ts`, dejar `SECCIONES` así:

```typescript
export const SECCIONES: Seccion[] = [
  { nombre: 'Ropa', descripcion: 'Pedidos y tallas del vestuario de la peña.' },
];
```

- [ ] **Step 6: Añadir el área al front**

En `front/src/app/admin/admin.types.ts`:

1. Ampliar el tipo `Area`:

```typescript
export type Area = 'ADMIN_SOLICITUDES' | 'ADMIN_PERMISOS' | 'INVENTARIO';
```

2. Ampliar el mapa `AREAS`:

```typescript
export const AREAS: Record<Area, string> = {
  ADMIN_SOLICITUDES: 'Solicitudes',
  ADMIN_PERMISOS: 'Permisos',
  INVENTARIO: 'Inventario',
};
```

(Con esto, el checkbox "Inventario" aparece solo en la pantalla de permisos, que se pinta a partir de `Object.entries(AREAS)`.)

- [ ] **Step 7: Escribir el spec del componente**

`front/src/app/panel/inventario/inventario.spec.ts`:

```typescript
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { Inventario } from './inventario';
import { InventarioResponse } from './inventario.types';

const RESPUESTA = (puedoEditar: boolean): InventarioResponse => ({
  puedoEditar,
  categorias: [
    {
      categoria: 'CERVEZA',
      etiqueta: 'Cerveza',
      tamanos: ['lata', 'botellín', 'tercio'],
      articulos: [
        { id: 1, nombre: 'Mahou Clásica', tamano: 'lata', cantidad: 192 },
        { id: 2, nombre: 'Mixta', tamano: 'lata', cantidad: 3 },
      ],
    },
    { categoria: 'COMIDA', etiqueta: 'Comida', tamanos: ['unidad', 'kg'], articulos: [] },
  ],
});

describe('Inventario', () => {
  let fixture: ComponentFixture<Inventario>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Inventario],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(Inventario);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function cargar(puedoEditar: boolean): void {
    fixture.detectChanges();
    httpMock.expectOne(`${base}/inventario`).flush(RESPUESTA(puedoEditar));
    fixture.detectChanges();
  }

  it('pinta las categorías y sus artículos', () => {
    cargar(false);
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Cerveza');
    expect(txt).toContain('Mahou Clásica');
    expect(txt).toContain('192');
    expect(txt).toContain('Nada apuntado todavía.');
  });

  it('sin permiso no enseña el botón Editar', () => {
    cargar(false);
    const botones = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(botones.some((b) => b.textContent?.includes('Editar'))).toBe(false);
  });

  it('con permiso, editar + guardar manda un PUT por fila cambiada', () => {
    cargar(true);

    const editar = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
      (b) => b.textContent?.includes('Editar'),
    )!;
    editar.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const cantidad = (fixture.nativeElement as HTMLElement).querySelector(
      'input[type="number"]',
    ) as HTMLInputElement;
    cantidad.value = '5';
    cantidad.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const guardar = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
      (b) => b.textContent?.includes('Guardar'),
    )!;
    guardar.dispatchEvent(new Event('click'));

    const put = httpMock.expectOne(`${base}/inventario/1`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.cantidad).toBe(5);
    put.flush({ id: 1, nombre: 'Mahou Clásica', tamano: 'lata', cantidad: 5 });

    // tras guardar, recarga
    httpMock.expectOne(`${base}/inventario`).flush(RESPUESTA(true));
  });
});
```

- [ ] **Step 8: Añadir la comprobación de nav en `panel.spec.ts`**

En `front/src/app/panel/panel.spec.ts`, justo tras el test `'"Cuentas" es un enlace real…'`, añadir el equivalente para Inventario con la MISMA forma que usa ese test (`el.querySelector('a[href="..."]')` + `textContent` recortado, usando el helper `render([])` que ya hay en ese fichero):

```typescript
  it('"Inventario" es un enlace real a /panel/inventario, no una sección "Pronto"', () => {
    const el = render([]);
    const enlace = el.querySelector('a[href="/panel/inventario"]');
    expect(enlace).toBeTruthy();
    expect((enlace?.textContent ?? '').trim()).toBe('Inventario');
  });
```

Nota: hay dos navs (escritorio y móvil); `querySelector` coge el primero (el de escritorio, que va antes en la plantilla), y con eso basta.

- [ ] **Step 9: Ejecutar los specs afectados**

Run: `cd front && npx ng test --watch=false --include='**/inventario*.spec.ts' --include='**/panel.spec.ts'`
Expected: PASS.

- [ ] **Step 10: Ejecutar toda la suite del front**

Run: `cd front && npx ng test --watch=false`
Expected: PASS. (Comprueba que quitar "Inventario" de `SECCIONES` no rompe ningún spec de `inicio`/`panel` que contara las secciones "Pronto"; si alguno lo hace, ajústalo.)

- [ ] **Step 11: Commit**

```bash
git add front/src/app/panel/inventario/ front/src/app/app.routes.ts \
        front/src/app/panel/panel.html front/src/app/panel/panel.ts \
        front/src/app/panel/secciones.ts front/src/app/panel/panel.spec.ts \
        front/src/app/admin/admin.types.ts
git commit -m "feat(inventario): pantalla de inventario en el panel web

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**

- Modelo `articulo_inventario` con categoría/nombre/tamaño/cantidad/orden → Task 1 Step 3+5. ✅
- Enum `CategoriaInventario` + listas de tamaño por categoría → Task 1 Step 2. ✅
- Migración `V35` con siembra de todos los datos → Task 1 Step 5 (incluye los datos de la spec verbatim; COMIDA vacía). ✅
- Permiso de área `INVENTARIO` (enum backend + surge en pantalla de permisos + `/auth/yo`) → Task 1 Step 1 (backend) + Task 4 Step 6 (front). `UsuarioResponse` ya serializa `AreaProtegida.values()` sin cambios. ✅
- `GET /api/v1/inventario` con `puedoEditar` + 5 categorías siempre → Task 2 Step 3-4, IT en Step 6. ✅
- `PUT /api/v1/inventario/{id}` con permiso, 404, 400 por tamaño, 400 por validación, sin cambio de categoría → Task 2 Step 3 (servicio) + Step 1 (validación bean) + IT Step 6. ✅
- Web: entrada en el menú, vista de solo lectura de 5 bloques, botón "Editar" solo con permiso, modo edición con desplegable de tamaño y `PUT` por fila cambiada, "Cancelar" → Task 4 Steps 1-4, spec Step 7. ✅
- Tests backend (GET/PUT con y sin permiso, tamaño inválido, 404) y web (componente + servicio) → Task 2 Step 6, Task 3 Step 3, Task 4 Step 7. ✅
- Fuera de alcance (móvil, alta/baja, historial, alertas) → no hay tareas para eso. ✅

**Placeholder scan:** sin "TBD"/"TODO"/"etc."; cada paso de código lleva el bloque completo. Los pasos "ejecuta la suite entera" piden ajustar un test existente solo *si* falla, con la condición explícita (contar `AreaProtegida.values()` en vez de asumir 2; secciones "Pronto"). ✅

**Type consistency:**

- `ArticuloDto(id, nombre, tamano, cantidad)` — mismo shape en backend (Task 2) y en el tipo `ArticuloInventario` del front (Task 3). ✅
- `InventarioResponse(puedoEditar, categorias)` idéntico back/front. ✅
- `CategoriaInventarioDto.categoria` es `String` (nombre del enum) en backend; en el front `CategoriaClave` union con los mismos 5 literales. ✅
- Repo: `findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc` — mismo nombre en Task 1 (definición), Task 2 (uso en servicio) y ambos IT. ✅
- `InventarioService.actualizar(Long, Long, ActualizarArticuloRequest)` — firma usada igual en el controlador (Task 2 Step 4). ✅
- Front `InventarioService.actualizar(id, cambio)` / `ver()` — usados con esa firma en el componente (Task 4) y el spec (Task 3). ✅
- Códigos de error (`SIN_PERMISO_INVENTARIO`, `ARTICULO_INVENTARIO_NO_ENCONTRADO`, `TAMANO_INVENTARIO_NO_VALIDO`) — definidos en Task 2 Step 5, y los IT comprueban el **status HTTP** (no el string), así que no hay acoplamiento frágil. ✅
