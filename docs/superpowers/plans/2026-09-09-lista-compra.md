# Lista de la compra por evento — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cada evento tiene una lista de la compra que se calcula sola a partir de los apuntados y los días de fiesta, sale del botón "Lista de la compra" del detalle del evento, y un admin con área `INVENTARIO` la ajusta desde la pantalla "Cantidades para eventos".

**Architecture:** Plantilla global `regla_compra` (sembrada con las fórmulas actuales) → se copia a `regla_compra_evento` la primera vez que se abre la lista de un evento. Un motor puro (`CalculadoraListaCompra`) aplica la fórmula tipada de cada regla sobre los datos del evento y devuelve líneas brutas; el servicio redondea hacia arriba y aplica el override manual del admin. API de lectura para cualquier peñista y API de administración para el área `INVENTARIO`. Web (4 componentes) y móvil (repo + 3 pantallas) en paridad.

**Tech Stack:** Spring Boot 3 + Spring Data JPA + Flyway (Postgres) + Lombok; JUnit 5 + `RestTestClient` (`*IT` bajo maven-failsafe); Angular standalone + signals + control flow + Vitest; KMP + Compose Multiplatform + Ktor + kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-09-09-lista-compra-design.md`

## Global Constraints

- Java 17.
- Peña piloto única, se resuelve por `slug = "baniterio"` (`PenaRepository.findBySlug`).
- Errores de dominio → `{ "codigo": "<CODE>" }` vía `ApiExceptionHandler` (`@RestControllerAdvice`).
- Área de permiso: `INVENTARIO` (`AreaProtegida.INVENTARIO`), `ServicioPermisos.puede(usuarioId, AreaProtegida.INVENTARIO)`. Admins/superadmins la tienen implícita.
- Ver la lista de la compra: cualquier peñista logueado. Todo `/admin/lista-compra/**`: área `INVENTARIO`.
- Bloque 1 (esta entrega): cálculo bruto con `ceil`. El descuento del inventario de la fiesta (`articulo_evento`) y el umbral del fregasuelos son el bloque 2 — NO se implementan; el DTO ya lleva `cantidadCalculada` para no necesitar migración entonces.
- Reglas dinámicas (`ALCOHOL_SELECCIONADO`, `REFRESCO_SELECCIONADO`): solo existen en la plantilla, no se pueden crear a mano, y no admiten `cantidadAjustada`.
- Móvil: errores tipados con enum + `deCodigoBackend()`, patrón de `ResultadoInventario`.
- IT comparten BBDD sin rollback (`RestTestClient.bindToServer`): cada test usa filas propias o las crea y borra. Nunca mutar filas de siembra compartidas.
- Commits terminan con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- NO tocar ni commitear `mobile/shared/src/androidMain/kotlin/com/baniterio/app/data/ApiConfig.android.kt` ni `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/cuentas/CuentaDetalleScreen.kt` (mods locales ajenos). Al hacer `git add`, añadir ficheros concretos, nunca `git add -A` sobre `mobile/`.
- El texto de commits, comentarios y docs va en prosa normal (no estilo caveman).

## Nombres y tipos compartidos (contrato entre tareas)

**Paquete backend nuevo:** `com.baniterio.api.compra`.

**Enums:**
```java
// com.baniterio.api.compra.TipoFormulaCompra
public enum TipoFormulaCompra {
    POR_PENISTA, POR_PENISTA_DIA, POR_DIA, POR_EVENTO, POR_CADA_N_PENISTAS,
    CERVEZA_ALTERNATIVA, TINTO_ALTERNATIVA, ALCOHOL_SELECCIONADO, REFRESCO_SELECCIONADO;

    public boolean esDinamica() {
        return this == ALCOHOL_SELECCIONADO || this == REFRESCO_SELECCIONADO;
    }
}
// com.baniterio.api.compra.OrigenReglaCompra
public enum OrigenReglaCompra { PLANTILLA, MANUAL }
```

**Entidades** (`categoria` es `com.baniterio.api.inventario.CategoriaInventario`):
- `ReglaCompra`: `Long id`, `Pena pena` (`@ManyToOne LAZY`, `pena_id`), `CategoriaInventario categoria` (`@Enumerated STRING`, len 20), `String nombre` (len 120), `String tamano` (len 20), `TipoFormulaCompra tipoFormula` (`@Enumerated STRING`, len 30, col `tipo_formula`), `BigDecimal factor` (precision 8 scale 3), `Integer porCada` (nullable, col `por_cada`), `int orden`.
- `ReglaCompraEvento`: mismos campos que `ReglaCompra` salvo `pena`, más `Evento evento` (`@ManyToOne LAZY`, `evento_id`), `OrigenReglaCompra origen` (`@Enumerated STRING`, len 10), `BigDecimal cantidadAjustada` (nullable, precision 8 scale 2, col `cantidad_ajustada`), `boolean activa`.

Ambas con Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

**Repos:**
```java
public interface ReglaCompraRepository extends JpaRepository<ReglaCompra, Long> {
    List<ReglaCompra> findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long penaId);
}
public interface ReglaCompraEventoRepository extends JpaRepository<ReglaCompraEvento, Long> {
    List<ReglaCompraEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId);
    boolean existsByEventoId(Long eventoId);
    Optional<ReglaCompraEvento> findByIdAndEventoId(Long id, Long eventoId);
}
```

**Motor** (`com.baniterio.api.compra.CalculadoraListaCompra`, clase con métodos `static`):
```java
public record PersonaCompra(int diasQueVa, boolean tieneFicha, String alcohol, String refresco,
                            com.baniterio.api.identidad.Alternativa alternativa) {}
public record DatosEvento(int apuntados, int diasFiesta, boolean llevaFicha, List<PersonaCompra> personas) {}
public record LineaCalculada(String categoria, String nombre, String tamano,
                             BigDecimal bruto, boolean dinamica, boolean necesitaFicha, int orden) {}

// Devuelve 0..N líneas para una regla (N>1 solo en las dinámicas).
public static List<LineaCalculada> lineasDe(ReglaCompraEvento regla, DatosEvento datos);
// ceil hacia arriba a entero.
public static BigDecimal ceil(BigDecimal v);  // v.setScale(0, RoundingMode.CEILING)
```

**DTOs backend** (`com.baniterio.api.compra.dto`):
```java
record LineaCompraDto(String nombre, String tamano, BigDecimal cantidad, BigDecimal cantidadCalculada,
                      boolean ajustada, boolean dinamica, boolean necesitaFicha) {}
record CategoriaListaCompraDto(String categoria, String etiqueta, List<LineaCompraDto> lineas) {}
record ListaCompraResponse(boolean puedoEditar, boolean llevaFicha, int apuntados, int diasFiesta,
                           List<CategoriaListaCompraDto> categorias) {}
record EventoListaCompraDto(Long id, String nombre, LocalDate fecha, LocalDate fechaFin) {}
record ReglaCompraEventoDto(Long id, String categoria, String etiqueta, String nombre, String tamano,
                            String tipoFormula, BigDecimal factor, Integer porCada, String origen,
                            BigDecimal cantidadCalculada, BigDecimal cantidadAjustada,
                            BigDecimal cantidadFinal, boolean activa) {}
record ListaCompraAdminResponse(EventoListaCompraDto evento, int apuntados, int diasFiesta,
                                List<ReglaCompraEventoDto> reglas) {}
record AjustarReglaRequest(@jakarta.validation.constraints.PositiveOrZero BigDecimal cantidadAjustada,
                           boolean activa) {}  // cantidadAjustada puede ser null
record CrearReglaRequest(@NotNull CategoriaInventario categoria, @NotBlank String nombre,
                         @NotBlank String tamano, @NotNull TipoFormulaCompra tipoFormula,
                         @NotNull @PositiveOrZero BigDecimal factor, Integer porCada) {}
```

**Excepciones backend** (`com.baniterio.api.compra`, todas `extends RuntimeException`, vacías salvo indicación):
`ReglaCompraNoEncontradaException` (404 `REGLA_COMPRA_NO_ENCONTRADA`), `ReglaCompraNoBorrableException` (409 `REGLA_COMPRA_NO_BORRABLE`), `ReglaCompraDuplicadaException` (409 `REGLA_COMPRA_DUPLICADA`), `AjusteNoAplicaException` (400 `AJUSTE_NO_APLICA`), `PorCadaNoAplicaException` (400 `POR_CADA_NO_APLICA`), `FormulaNoCreableException` (400 `FORMULA_NO_CREABLE`). Se reutilizan `com.baniterio.api.evento.EventoNoEncontradoException` y `com.baniterio.api.inventario.SinPermisoInventarioException`.

**Endpoints:**
- `GET /api/v1/eventos/{eventoId}/lista-compra` → `ListaCompraResponse` (cualquier peñista).
- `GET /api/v1/admin/lista-compra/eventos` → `List<EventoListaCompraDto>`.
- `GET /api/v1/admin/lista-compra/eventos/{eventoId}` → `ListaCompraAdminResponse`.
- `PUT /api/v1/admin/lista-compra/eventos/{eventoId}/reglas/{reglaId}` (body `AjustarReglaRequest`) → 204.
- `POST /api/v1/admin/lista-compra/eventos/{eventoId}/reglas` (body `CrearReglaRequest`) → 201 `ReglaCompraEventoDto`.
- `DELETE /api/v1/admin/lista-compra/eventos/{eventoId}/reglas/{reglaId}` → 204.

**Servicio** `com.baniterio.api.compra.ListaCompraService`, métodos públicos:
```java
ListaCompraResponse verLista(Long usuarioId, Long eventoId);
List<EventoListaCompraDto> eventos(Long usuarioId);
ListaCompraAdminResponse verAdmin(Long usuarioId, Long eventoId);
void ajustarRegla(Long usuarioId, Long eventoId, Long reglaId, AjustarReglaRequest req);
ReglaCompraEventoDto crearRegla(Long usuarioId, Long eventoId, CrearReglaRequest req);
void borrarRegla(Long usuarioId, Long eventoId, Long reglaId);
```

**Web** (`front/src/app/panel/eventos/lista-compra/` para lectura, `front/src/app/admin/lista-compra/` para admin):
- `ListaCompraService` (Angular) métodos: `lista(eventoId: number)`, `adminEventos()`, `adminEvento(eventoId: number)`, `ajustarRegla(eventoId, reglaId, body)`, `crearRegla(eventoId, body)`, `borrarRegla(eventoId, reglaId)`.
- Rutas: `panel/eventos/:id/lista-compra` → `ListaCompra`; `panel/administracion/lista-compra` → `ListaCompraAdmin`; `panel/administracion/lista-compra/:id` → `ListaCompraAdminEvento` (con `areaGuard('INVENTARIO')`).

**Móvil** (`mobile/shared/src/commonMain/kotlin/com/baniterio/app/`):
- DTOs en `data/dto/ListaCompraDtos.kt`; `ResultadoListaCompra` + `CodigoErrorListaCompra` en `data/ResultadoListaCompra.kt`; `ListaCompraRepository` / `ListaCompraRepositoryImpl` en `data/`.
- `Screen`: `ListaCompra`, `ListaCompraAdmin`, `ListaCompraAdminEvento`.
- Pantallas: `ui/compra/ListaCompraScreen.kt`, `ui/compra/ListaCompraAdminScreen.kt`, `ui/compra/ListaCompraAdminEventoScreen.kt`.

---

## Task 1: Migración V38, entidades, enums y repos

**Files:**
- Create: `back/src/main/resources/db/migration/V38__lista_compra.sql`
- Create: `back/src/main/java/com/baniterio/api/compra/TipoFormulaCompra.java`
- Create: `back/src/main/java/com/baniterio/api/compra/OrigenReglaCompra.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompra.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompraEvento.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompraRepository.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompraEventoRepository.java`
- Test: `back/src/test/java/com/baniterio/api/compra/ReglaCompraRepositoryIT.java`

**Interfaces:**
- Produces: `TipoFormulaCompra`, `OrigenReglaCompra`, `ReglaCompra`, `ReglaCompraEvento`, `ReglaCompraRepository`, `ReglaCompraEventoRepository` (firmas en "Nombres y tipos compartidos").

- [ ] **Step 1: Escribir la migración V38**

`back/src/main/resources/db/migration/V38__lista_compra.sql`:

```sql
-- Lista de la compra por evento. `regla_compra` es la plantilla global de la
-- peña (una fila por artículo, con su fórmula tipada). `regla_compra_evento` son
-- las instancias por evento: se copian de la plantilla la primera vez que se
-- abre la lista de un evento y a partir de ahí se editan solo en ese evento.
-- El descuento del inventario de la fiesta (bloque 2) NO está aquí.
CREATE TABLE regla_compra (
    id           BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pena_id      BIGINT        NOT NULL REFERENCES pena (id),
    categoria    VARCHAR(20)   NOT NULL,
    nombre       VARCHAR(120)  NOT NULL,
    tamano       VARCHAR(20)   NOT NULL,
    tipo_formula VARCHAR(30)   NOT NULL,
    factor       NUMERIC(8, 3) NOT NULL,
    por_cada     INT,
    orden        INT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_regla_compra_categoria
        CHECK (categoria IN ('ALCOHOL', 'CERVEZA', 'REFRESCOS', 'LIMPIEZA', 'COMIDA')),
    CONSTRAINT ck_regla_compra_tipo CHECK (tipo_formula IN (
        'POR_PENISTA', 'POR_PENISTA_DIA', 'POR_DIA', 'POR_EVENTO', 'POR_CADA_N_PENISTAS',
        'CERVEZA_ALTERNATIVA', 'TINTO_ALTERNATIVA', 'ALCOHOL_SELECCIONADO', 'REFRESCO_SELECCIONADO')),
    CONSTRAINT ck_regla_compra_factor CHECK (factor >= 0),
    CONSTRAINT ck_regla_compra_por_cada CHECK (por_cada IS NULL OR por_cada > 0),
    CONSTRAINT uq_regla_compra UNIQUE (pena_id, categoria, nombre, tipo_formula)
);

CREATE TABLE regla_compra_evento (
    id                BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    evento_id         BIGINT        NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    categoria         VARCHAR(20)   NOT NULL,
    nombre            VARCHAR(120)  NOT NULL,
    tamano            VARCHAR(20)   NOT NULL,
    tipo_formula      VARCHAR(30)   NOT NULL,
    factor            NUMERIC(8, 3) NOT NULL,
    por_cada          INT,
    orden             INT           NOT NULL DEFAULT 0,
    origen            VARCHAR(10)   NOT NULL,
    cantidad_ajustada NUMERIC(8, 2),
    activa            BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_regla_compra_evento_categoria
        CHECK (categoria IN ('ALCOHOL', 'CERVEZA', 'REFRESCOS', 'LIMPIEZA', 'COMIDA')),
    CONSTRAINT ck_regla_compra_evento_tipo CHECK (tipo_formula IN (
        'POR_PENISTA', 'POR_PENISTA_DIA', 'POR_DIA', 'POR_EVENTO', 'POR_CADA_N_PENISTAS',
        'CERVEZA_ALTERNATIVA', 'TINTO_ALTERNATIVA', 'ALCOHOL_SELECCIONADO', 'REFRESCO_SELECCIONADO')),
    CONSTRAINT ck_regla_compra_evento_origen CHECK (origen IN ('PLANTILLA', 'MANUAL')),
    CONSTRAINT ck_regla_compra_evento_factor CHECK (factor >= 0),
    CONSTRAINT ck_regla_compra_evento_por_cada CHECK (por_cada IS NULL OR por_cada > 0),
    CONSTRAINT ck_regla_compra_evento_ajustada CHECK (cantidad_ajustada IS NULL OR cantidad_ajustada >= 0),
    CONSTRAINT uq_regla_compra_evento UNIQUE (evento_id, categoria, nombre, tipo_formula)
);
CREATE INDEX ix_regla_compra_evento_evento ON regla_compra_evento (evento_id);

-- Siembra de la plantilla con las fórmulas actuales de la peña. Idempotente por
-- (pena_id, categoria, nombre, tipo_formula).
INSERT INTO regla_compra (pena_id, categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
SELECT p.id, v.categoria, v.nombre, v.tamano, v.tipo_formula, v.factor, v.por_cada, v.orden
FROM pena p
CROSS JOIN (VALUES
    ('ALCOHOL',   '',                            '70 cl',   'ALCOHOL_SELECCIONADO',  0.5, NULL, 1),
    ('CERVEZA',   'Cerveza',                     'lata',    'CERVEZA_ALTERNATIVA',   5,   NULL, 1),
    ('REFRESCOS', '',                            'botella', 'REFRESCO_SELECCIONADO', 1,   NULL, 1),
    ('REFRESCOS', 'Tinto de verano',             'botella', 'TINTO_ALTERNATIVA',     1,   NULL, 2),
    ('REFRESCOS', 'Hielos',                      'bolsa',   'POR_PENISTA',           1,   NULL, 3),
    ('LIMPIEZA',  'Bayetas',                     'unidad',  'POR_CADA_N_PENISTAS',   2,   30,   1),
    ('LIMPIEZA',  'Fregasuelos',                 'litro',   'POR_CADA_N_PENISTAS',   0.2, 50,   2),
    ('LIMPIEZA',  'Papel de cocina',             'rollo',   'POR_DIA',               1,   NULL, 3),
    ('LIMPIEZA',  'Platos',                      'unidad',  'POR_PENISTA',           3,   NULL, 4),
    ('LIMPIEZA',  'Vasos de chupito',            'unidad',  'POR_PENISTA',           3,   NULL, 5),
    ('LIMPIEZA',  'Papel higiénico',             'rollo',   'POR_CADA_N_PENISTAS',   6,   30,   6),
    ('LIMPIEZA',  'Vasos de mini',               'unidad',  'POR_PENISTA_DIA',       3,   NULL, 7),
    ('LIMPIEZA',  'Vasos de sidra',              'unidad',  'POR_PENISTA_DIA',       3,   NULL, 8),
    ('LIMPIEZA',  'Mantel',                      'unidad',  'POR_DIA',               1,   NULL, 9),
    ('LIMPIEZA',  'Vasos de invitar',            'unidad',  'POR_EVENTO',            50,  NULL, 10),
    ('LIMPIEZA',  'Film transparente',           'rollo',   'POR_EVENTO',            1,   NULL, 11),
    ('COMIDA',    'Paletilla ibérica',           'unidad',  'POR_EVENTO',            1,   NULL, 1),
    ('COMIDA',    'Salchichón ibérico',          'unidad',  'POR_EVENTO',            1,   NULL, 2),
    ('COMIDA',    'Chorizo ibérico',             'unidad',  'POR_EVENTO',            1,   NULL, 3),
    ('COMIDA',    'Nocilla grande',              'bote',    'POR_CADA_N_PENISTAS',   1,   12,   4),
    ('COMIDA',    'Nocilla pequeña sin gluten',  'bote',    'POR_EVENTO',            1,   NULL, 5),
    ('COMIDA',    'Pan de molde sin gluten',     'paquete', 'POR_EVENTO',            1,   NULL, 6),
    ('COMIDA',    'Picos',                       'paquete', 'POR_CADA_N_PENISTAS',   2,   10,   7),
    ('COMIDA',    'Pan de molde',                'paquete', 'POR_CADA_N_PENISTAS',   2,   12,   8)
) AS v(categoria, nombre, tamano, tipo_formula, factor, por_cada, orden)
WHERE p.slug = 'baniterio'
  AND NOT EXISTS (
      SELECT 1 FROM regla_compra r
      WHERE r.pena_id = p.id AND r.categoria = v.categoria
        AND r.nombre = v.nombre AND r.tipo_formula = v.tipo_formula
  );
```

- [ ] **Step 2: Escribir los enums**

`TipoFormulaCompra.java` y `OrigenReglaCompra.java` exactamente como en "Nombres y tipos compartidos". `TipoFormulaCompra` lleva el método `esDinamica()`.

- [ ] **Step 3: Escribir las entidades**

`ReglaCompra.java`:

```java
package com.baniterio.api.compra;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.inventario.CategoriaInventario;
import jakarta.persistence.*;
import lombok.*;

/** Plantilla global de reglas de compra de la peña (ver V38). Una fila por artículo. */
@Entity
@Table(name = "regla_compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReglaCompra {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_formula", nullable = false, length = 30)
    private TipoFormulaCompra tipoFormula;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal factor;

    @Column(name = "por_cada")
    private Integer porCada;

    @Column(nullable = false)
    private int orden;
}
```

`ReglaCompraEvento.java`: igual estructura, sin `pena`, con:

```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private com.baniterio.api.identidad.Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenReglaCompra origen;

    @Column(name = "cantidad_ajustada", precision = 8, scale = 2)
    private BigDecimal cantidadAjustada;

    @Column(nullable = false)
    private boolean activa;
```

- [ ] **Step 4: Escribir los repos**

`ReglaCompraRepository.java` y `ReglaCompraEventoRepository.java` exactamente como en "Nombres y tipos compartidos".

- [ ] **Step 5: Escribir el test de integración**

`back/src/test/java/com/baniterio/api/compra/ReglaCompraRepositoryIT.java`. Mira `back/src/test/java/com/baniterio/api/inventario/ArticuloEventoRepositoryIT.java` para el andamiaje (`@SpringBootTest`, `@Autowired`, `@Transactional` del test de repo). Contenido:

```java
package com.baniterio.api.compra;

import static org.assertj.core.api.Assertions.assertThat;

import com.baniterio.api.identidad.PenaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReglaCompraRepositoryIT {

    @Autowired ReglaCompraRepository reglas;
    @Autowired PenaRepository penas;

    @Test
    void la_plantilla_se_siembra_con_las_24_reglas() {
        Long penaId = penas.findBySlug("baniterio").orElseThrow().getId();
        var todas = reglas.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId);
        assertThat(todas).hasSize(24);
        assertThat(todas).anyMatch(r -> r.getTipoFormula() == TipoFormulaCompra.ALCOHOL_SELECCIONADO);
        assertThat(todas).anyMatch(r ->
                r.getNombre().equals("Bayetas") && r.getPorCada() == 30
                && r.getTipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS);
    }
}
```

- [ ] **Step 6: Ejecutar el test**

Run: `cd back && ./mvnw -q -Dtest=ReglaCompraRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -30`
Espera: la migración V38 aplica y el test pasa. Si Flyway se queja del checksum, es que ya existía otra V38 — renumerar a la siguiente libre.

- [ ] **Step 7: Commit**

```bash
git add back/src/main/resources/db/migration/V38__lista_compra.sql \
        back/src/main/java/com/baniterio/api/compra/ \
        back/src/test/java/com/baniterio/api/compra/ReglaCompraRepositoryIT.java
git commit -m "feat(lista-compra): tablas regla_compra y regla_compra_evento (V38)"
```

---

## Task 2: Excepciones y handlers HTTP

**Files:**
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompraNoEncontradaException.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompraNoBorrableException.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ReglaCompraDuplicadaException.java`
- Create: `back/src/main/java/com/baniterio/api/compra/AjusteNoAplicaException.java`
- Create: `back/src/main/java/com/baniterio/api/compra/PorCadaNoAplicaException.java`
- Create: `back/src/main/java/com/baniterio/api/compra/FormulaNoCreableException.java`
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java` (añadir handlers tras `articuloEventoNoEncontrado`, línea ~292)

**Interfaces:**
- Produces: las 6 excepciones y sus códigos HTTP/string (ver "Nombres y tipos compartidos").

- [ ] **Step 1: Escribir las 6 excepciones**

Cada fichero, patrón de `back/src/main/java/com/baniterio/api/inventario/NadaQueEnviarException.java`:

```java
package com.baniterio.api.compra;

/** La regla no existe o no es de ese evento. */
public class ReglaCompraNoEncontradaException extends RuntimeException {
}
```

Repetir para `ReglaCompraNoBorrableException` ("Una regla copiada de la plantilla no se borra, se desactiva."), `ReglaCompraDuplicadaException` ("Ya hay una regla con esa categoría, nombre y fórmula en el evento."), `AjusteNoAplicaException` ("Las reglas dinámicas no admiten cantidad fija."), `PorCadaNoAplicaException` ("`porCada` solo vale con POR_CADA_N_PENISTAS."), `FormulaNoCreableException` ("Las fórmulas dinámicas no se crean a mano.").

- [ ] **Step 2: Añadir un test al handler (opcional rápido: se cubre en los IT de Task 5). Saltar a Step 3.**

- [ ] **Step 3: Añadir los handlers en `ApiExceptionHandler.java`**

Tras el método `articuloEventoNoEncontrado()` (~línea 292):

```java
    @ExceptionHandler(com.baniterio.api.compra.ReglaCompraNoEncontradaException.class)
    ResponseEntity<Map<String, Object>> reglaCompraNoEncontrada() {
        return error(HttpStatus.NOT_FOUND, "REGLA_COMPRA_NO_ENCONTRADA");
    }

    @ExceptionHandler(com.baniterio.api.compra.ReglaCompraNoBorrableException.class)
    ResponseEntity<Map<String, Object>> reglaCompraNoBorrable() {
        return error(HttpStatus.CONFLICT, "REGLA_COMPRA_NO_BORRABLE");
    }

    @ExceptionHandler(com.baniterio.api.compra.ReglaCompraDuplicadaException.class)
    ResponseEntity<Map<String, Object>> reglaCompraDuplicada() {
        return error(HttpStatus.CONFLICT, "REGLA_COMPRA_DUPLICADA");
    }

    @ExceptionHandler(com.baniterio.api.compra.AjusteNoAplicaException.class)
    ResponseEntity<Map<String, Object>> ajusteNoAplica() {
        return error(HttpStatus.BAD_REQUEST, "AJUSTE_NO_APLICA");
    }

    @ExceptionHandler(com.baniterio.api.compra.PorCadaNoAplicaException.class)
    ResponseEntity<Map<String, Object>> porCadaNoAplica() {
        return error(HttpStatus.BAD_REQUEST, "POR_CADA_NO_APLICA");
    }

    @ExceptionHandler(com.baniterio.api.compra.FormulaNoCreableException.class)
    ResponseEntity<Map<String, Object>> formulaNoCreable() {
        return error(HttpStatus.BAD_REQUEST, "FORMULA_NO_CREABLE");
    }
```

- [ ] **Step 4: Compilar**

Run: `cd back && ./mvnw -q compile 2>&1 | tail -20`
Espera: compila sin errores.

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/ back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java
git commit -m "feat(lista-compra): excepciones de dominio y handlers HTTP"
```

---

## Task 3: Motor de cálculo `CalculadoraListaCompra`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/compra/CalculadoraListaCompra.java`
- Test: `back/src/test/java/com/baniterio/api/compra/CalculadoraListaCompraTest.java`

**Interfaces:**
- Consumes: `ReglaCompraEvento` (Task 1), `TipoFormulaCompra` (Task 1), `com.baniterio.api.identidad.Alternativa` (`CERVEZA`, `TINTO_VERANO`, `NADA`, `CERVEZA_ESPECIAL`).
- Produces: `CalculadoraListaCompra.PersonaCompra`, `.DatosEvento`, `.LineaCalculada`, `.lineasDe(regla, datos)`, `.ceil(v)` (firmas en "Nombres y tipos compartidos").

- [ ] **Step 1: Escribir los tests que fallan**

`back/src/test/java/com/baniterio/api/compra/CalculadoraListaCompraTest.java`:

```java
package com.baniterio.api.compra;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.baniterio.api.compra.CalculadoraListaCompra.DatosEvento;
import com.baniterio.api.compra.CalculadoraListaCompra.LineaCalculada;
import com.baniterio.api.compra.CalculadoraListaCompra.PersonaCompra;
import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.inventario.CategoriaInventario;
import org.junit.jupiter.api.Test;

class CalculadoraListaCompraTest {

    private static ReglaCompraEvento regla(TipoFormulaCompra tipo, double factor, Integer porCada,
                                           CategoriaInventario cat, String nombre, String tamano) {
        return ReglaCompraEvento.builder()
                .tipoFormula(tipo).factor(BigDecimal.valueOf(factor)).porCada(porCada)
                .categoria(cat).nombre(nombre).tamano(tamano).orden(1)
                .origen(OrigenReglaCompra.PLANTILLA).activa(true).build();
    }

    private static PersonaCompra p(int dias, boolean ficha, String alcohol, String refresco, Alternativa alt) {
        return new PersonaCompra(dias, ficha, alcohol, refresco, alt);
    }

    private static DatosEvento sanMiguel(List<PersonaCompra> personas) {
        return new DatosEvento(personas.size(), 2, true, personas);
    }

    @Test
    void por_penista() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA),
                                  p(1, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_PENISTA, 3, null, CategoriaInventario.LIMPIEZA, "Platos", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("6"); // 3 * 2 apuntados
    }

    @Test
    void por_penista_dia_usa_dias_que_va() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA),
                                  p(1, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_PENISTA_DIA, 3, null, CategoriaInventario.LIMPIEZA, "Vasos de mini", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("9"); // 3 * (2+1)
    }

    @Test
    void por_dia() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_DIA, 1, null, CategoriaInventario.LIMPIEZA, "Mantel", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("2"); // 1 * 2 dias
    }

    @Test
    void por_evento() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.POR_EVENTO, 50, null, CategoriaInventario.LIMPIEZA, "Vasos de invitar", "unidad");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("50");
    }

    @Test
    void por_cada_n_penistas_redondea_los_grupos_hacia_arriba() {
        var personas = new java.util.ArrayList<PersonaCompra>();
        for (int i = 0; i < 31; i++) personas.add(p(2, true, null, "Fanta", Alternativa.NADA));
        var d = sanMiguel(personas);
        var r = regla(TipoFormulaCompra.POR_CADA_N_PENISTAS, 2, 30, CategoriaInventario.LIMPIEZA, "Bayetas", "unidad");
        // ceil(31/30) = 2 grupos -> 2 * 2 = 4
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("4");
    }

    @Test
    void cerveza_alternativa_solo_cuenta_a_quien_bebe_cerveza() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.CERVEZA),
                                  p(1, true, null, "Fanta", Alternativa.CERVEZA),
                                  p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.CERVEZA_ALTERNATIVA, 5, null, CategoriaInventario.CERVEZA, "Cerveza", "lata");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("15"); // 5 * (2+1)
    }

    @Test
    void tinto_alternativa() {
        var d = sanMiguel(List.of(p(2, true, null, "Fanta", Alternativa.TINTO_VERANO)));
        var r = regla(TipoFormulaCompra.TINTO_ALTERNATIVA, 1, null, CategoriaInventario.REFRESCOS, "Tinto de verano", "botella");
        assertThat(CalculadoraListaCompra.lineasDe(r, d).get(0).bruto()).isEqualByComparingTo("2");
    }

    @Test
    void alcohol_seleccionado_una_linea_por_marca_y_1L_si_es_una_botella() {
        var d = sanMiguel(List.of(
                p(2, true, "Legendario", "Fanta", Alternativa.NADA),
                p(2, true, "Legendario", "Fanta", Alternativa.NADA),
                p(2, true, "Legendario", "Fanta", Alternativa.NADA), // Legendario: 0.5*6 = 3 -> 3 x 70 cl
                p(2, true, "Barceló", "Fanta", Alternativa.NADA)));   // Barceló: 0.5*2 = 1 -> 1 x 1 L
        var r = regla(TipoFormulaCompra.ALCOHOL_SELECCIONADO, 0.5, null, CategoriaInventario.ALCOHOL, "", "70 cl");
        List<LineaCalculada> lineas = CalculadoraListaCompra.lineasDe(r, d);
        assertThat(lineas).extracting(LineaCalculada::nombre).containsExactly("Barceló", "Legendario"); // alfabético
        assertThat(lineas.get(0).tamano()).isEqualTo("1 L");
        assertThat(lineas.get(0).bruto()).isEqualByComparingTo("1");
        assertThat(lineas.get(1).tamano()).isEqualTo("70 cl");
        assertThat(lineas.get(1).bruto()).isEqualByComparingTo("3");
    }

    @Test
    void refresco_seleccionado_una_linea_por_marca_siempre_en_botella() {
        var d = sanMiguel(List.of(
                p(2, true, null, "Coca-Cola Zero", Alternativa.NADA),
                p(1, true, null, "Coca-Cola Zero", Alternativa.NADA),
                p(2, true, null, "Fanta", Alternativa.NADA)));
        var r = regla(TipoFormulaCompra.REFRESCO_SELECCIONADO, 1, null, CategoriaInventario.REFRESCOS, "", "botella");
        List<LineaCalculada> lineas = CalculadoraListaCompra.lineasDe(r, d);
        assertThat(lineas).extracting(LineaCalculada::nombre).containsExactly("Coca-Cola Zero", "Fanta");
        assertThat(lineas.get(0).bruto()).isEqualByComparingTo("3"); // 1 * (2+1)
        assertThat(lineas.get(0).tamano()).isEqualTo("botella");
    }

    @Test
    void evento_sin_ficha_las_reglas_de_bebida_dan_cero_y_marcan_necesitaFicha() {
        var d = new DatosEvento(3, 1, false,
                List.of(p(1, false, null, null, null), p(1, false, null, null, null), p(1, false, null, null, null)));
        var cerveza = regla(TipoFormulaCompra.CERVEZA_ALTERNATIVA, 5, null, CategoriaInventario.CERVEZA, "Cerveza", "lata");
        var alcohol = regla(TipoFormulaCompra.ALCOHOL_SELECCIONADO, 0.5, null, CategoriaInventario.ALCOHOL, "", "70 cl");
        var lc = CalculadoraListaCompra.lineasDe(cerveza, d).get(0);
        assertThat(lc.bruto()).isEqualByComparingTo("0");
        assertThat(lc.necesitaFicha()).isTrue();
        var la = CalculadoraListaCompra.lineasDe(alcohol, d).get(0);
        assertThat(la.bruto()).isEqualByComparingTo("0");
        assertThat(la.necesitaFicha()).isTrue();
        assertThat(la.nombre()).isEqualTo("Alcohol"); // etiqueta de fila vacía de la regla dinámica
    }

    @Test
    void ceil_redondea_hacia_arriba() {
        assertThat(CalculadoraListaCompra.ceil(new BigDecimal("3.01"))).isEqualByComparingTo("4");
        assertThat(CalculadoraListaCompra.ceil(new BigDecimal("3.00"))).isEqualByComparingTo("3");
    }
}
```

- [ ] **Step 2: Ejecutar los tests y ver que fallan**

Run: `cd back && ./mvnw -q -Dtest=CalculadoraListaCompraTest -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -20`
Espera: FALLA con "cannot find symbol CalculadoraListaCompra".

- [ ] **Step 3: Escribir el motor**

`back/src/main/java/com/baniterio/api/compra/CalculadoraListaCompra.java`:

```java
package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baniterio.api.identidad.Alternativa;

/**
 * Motor puro de la lista de la compra: aplica la fórmula tipada de una regla
 * sobre los datos de un evento y devuelve una o varias líneas con la cantidad
 * bruta (antes de redondear y antes de restar el inventario de la fiesta, que es
 * el bloque 2). El redondeo hacia arriba lo hace {@link #ceil} y lo aplica el
 * servicio.
 */
public final class CalculadoraListaCompra {

    private CalculadoraListaCompra() {
    }

    /** Una persona apuntada al evento, con lo que hace falta para las fórmulas. */
    public record PersonaCompra(int diasQueVa, boolean tieneFicha, String alcohol, String refresco,
                                Alternativa alternativa) {
    }

    /** Datos agregados del evento para una tanda de cálculo. */
    public record DatosEvento(int apuntados, int diasFiesta, boolean llevaFicha, List<PersonaCompra> personas) {
    }

    /** Una línea de resultado de una regla. */
    public record LineaCalculada(String categoria, String nombre, String tamano, BigDecimal bruto,
                                 boolean dinamica, boolean necesitaFicha, int orden) {
    }

    public static BigDecimal ceil(BigDecimal v) {
        return v.setScale(0, RoundingMode.CEILING);
    }

    public static List<LineaCalculada> lineasDe(ReglaCompraEvento r, DatosEvento d) {
        String cat = r.getCategoria().name();
        TipoFormulaCompra tipo = r.getTipoFormula();
        BigDecimal f = r.getFactor();

        return switch (tipo) {
            case POR_PENISTA -> uno(r, f.multiply(BigDecimal.valueOf(d.apuntados())), false);
            case POR_PENISTA_DIA -> uno(r, f.multiply(BigDecimal.valueOf(sumaDias(d.personas(), p -> true))), false);
            case POR_DIA -> uno(r, f.multiply(BigDecimal.valueOf(d.diasFiesta())), false);
            case POR_EVENTO -> uno(r, f, false);
            case POR_CADA_N_PENISTAS -> {
                int grupos = (int) Math.ceil((double) d.apuntados() / r.getPorCada());
                yield uno(r, f.multiply(BigDecimal.valueOf(grupos)), false);
            }
            case CERVEZA_ALTERNATIVA -> reglaBebida(r, d,
                    f.multiply(BigDecimal.valueOf(sumaDias(d.personas(),
                            p -> p.tieneFicha() && p.alternativa() == Alternativa.CERVEZA))));
            case TINTO_ALTERNATIVA -> reglaBebida(r, d,
                    f.multiply(BigDecimal.valueOf(sumaDias(d.personas(),
                            p -> p.tieneFicha() && p.alternativa() == Alternativa.TINTO_VERANO))));
            case ALCOHOL_SELECCIONADO -> dinamica(r, d, cat, true);
            case REFRESCO_SELECCIONADO -> dinamica(r, d, cat, false);
        };
    }

    private static List<LineaCalculada> uno(ReglaCompraEvento r, BigDecimal bruto, boolean necesitaFicha) {
        return List.of(new LineaCalculada(r.getCategoria().name(), r.getNombre(), r.getTamano(),
                bruto, false, necesitaFicha, r.getOrden()));
    }

    /** Regla de cerveza / tinto: si el evento no lleva ficha, línea a 0 con necesitaFicha. */
    private static List<LineaCalculada> reglaBebida(ReglaCompraEvento r, DatosEvento d, BigDecimal bruto) {
        if (!d.llevaFicha()) {
            return uno(r, BigDecimal.ZERO, true);
        }
        return uno(r, bruto, false);
    }

    private static int sumaDias(List<PersonaCompra> personas, java.util.function.Predicate<PersonaCompra> filtro) {
        return personas.stream().filter(filtro).mapToInt(PersonaCompra::diasQueVa).sum();
    }

    /**
     * Reglas ALCOHOL/REFRESCO_SELECCIONADO: una línea por marca elegida. Sin ficha
     * en el evento, una única línea a 0 con necesitaFicha y nombre "Alcohol" / "Refrescos".
     */
    private static List<LineaCalculada> dinamica(ReglaCompraEvento r, DatosEvento d, String cat, boolean esAlcohol) {
        if (!d.llevaFicha()) {
            String etiqueta = esAlcohol ? "Alcohol" : "Refrescos";
            return List.of(new LineaCalculada(cat, etiqueta, r.getTamano(), BigDecimal.ZERO, true, true, r.getOrden()));
        }
        Map<String, Integer> diasPorMarca = new LinkedHashMap<>();
        for (PersonaCompra p : d.personas()) {
            if (!p.tieneFicha()) {
                continue;
            }
            String marca = esAlcohol ? p.alcohol() : p.refresco();
            if (marca == null) {
                continue;
            }
            diasPorMarca.merge(marca, p.diasQueVa(), Integer::sum);
        }
        List<LineaCalculada> out = new ArrayList<>();
        diasPorMarca.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(e -> {
                    BigDecimal bruto = r.getFactor().multiply(BigDecimal.valueOf(e.getValue()));
                    BigDecimal n = ceil(bruto);
                    String tamano = r.getTamano();
                    if (esAlcohol) {
                        tamano = n.compareTo(BigDecimal.ONE) == 0 ? "1 L" : "70 cl";
                    }
                    out.add(new LineaCalculada(cat, e.getKey(), tamano, n, true, false, r.getOrden()));
                });
        return out;
    }
}
```

Nota: en las líneas dinámicas el `bruto` que se devuelve ya viene redondeado (`ceil`), porque la regla de "1 L si es 1 botella" necesita el entero. El servicio vuelve a aplicar `ceil` sobre él (idempotente).

- [ ] **Step 4: Ejecutar los tests y ver que pasan**

Run: `cd back && ./mvnw -q -Dtest=CalculadoraListaCompraTest -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -20`
Espera: PASA (13 tests).

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/CalculadoraListaCompra.java \
        back/src/test/java/com/baniterio/api/compra/CalculadoraListaCompraTest.java
git commit -m "feat(lista-compra): motor de cálculo con fórmulas tipadas"
```

---

## Task 4: Servicio, DTOs y API de lectura

**Files:**
- Create: `back/src/main/java/com/baniterio/api/compra/dto/LineaCompraDto.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/CategoriaListaCompraDto.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/ListaCompraResponse.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/EventoListaCompraDto.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/ReglaCompraEventoDto.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/ListaCompraAdminResponse.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/AjustarReglaRequest.java`
- Create: `back/src/main/java/com/baniterio/api/compra/dto/CrearReglaRequest.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ListaCompraService.java`
- Create: `back/src/main/java/com/baniterio/api/compra/ListaCompraController.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/EventoRepository.java` (añadir `noOcultos`)
- Test: `back/src/test/java/com/baniterio/api/compra/ListaCompraIT.java`

**Interfaces:**
- Consumes: `CalculadoraListaCompra.*` (Task 3), repos y entidades (Task 1), excepciones (Task 2), `ServicioPermisos.puede`, `AsistenciaEventoRepository.findByEventoIdAndEstadoIn`, `FichaBebidaRepository.findByEventoId`, `PenaRepository.findBySlug`, `EventoRepository`.
- Produces: `ListaCompraService.verLista/eventos/verAdmin/ajustarRegla/crearRegla/borrarRegla`, DTOs, endpoint `GET /api/v1/eventos/{eventoId}/lista-compra`, `EventoRepository.noOcultos(penaId)`.

- [ ] **Step 1: Escribir los DTOs**

Los 8 records, exactamente como en "Nombres y tipos compartidos". `LocalDate` de `java.time`. Validaciones: `AjustarReglaRequest` — `@PositiveOrZero BigDecimal cantidadAjustada` (permite null), `boolean activa`. `CrearReglaRequest` — `@NotNull CategoriaInventario categoria`, `@NotBlank String nombre`, `@NotBlank String tamano`, `@NotNull TipoFormulaCompra tipoFormula`, `@NotNull @PositiveOrZero BigDecimal factor`, `Integer porCada`.

- [ ] **Step 2: Añadir `noOcultos` a `EventoRepository`**

Tras `ocultos(...)` (~línea 77):

```java
    /** Todos los eventos no ocultos de la peña, con la cuenta cargada. Más recientes primero. Para "Cantidades para eventos". */
    @Query("""
            select e from Evento e
            join fetch e.cuenta
            where e.pena.id = :penaId and e.oculto = false
            order by e.fecha desc, e.id desc
            """)
    List<Evento> noOcultos(@Param("penaId") Long penaId);
```

- [ ] **Step 3: Escribir el IT de lectura (parte que ya se puede)**

`back/src/test/java/com/baniterio/api/compra/ListaCompraIT.java`. Andamiaje de `back/src/test/java/com/baniterio/api/inventario/InventarioIT.java` (extiende el mismo base de IT, `RestTestClient`, helper de login). Este test crea un evento propio para no chocar con nadie:

```java
package com.baniterio.api.compra;

// imports: ver InventarioIT para el patrón exacto (base IT, RestTestClient, jsonPath)

class ListaCompraIT extends /* base de IT del proyecto, ver InventarioIT */ {

    // Helpers disponibles en la base: tokenAdmin(), tokenMiembroSinAreas(), client (RestTestClient).
    // Crear un evento de un día sin ficha vía POST /api/v1/eventos como admin, guardar su id.

    @Test
    void lectura_materializa_las_reglas_y_agrupa_por_categoria() {
        long eventoId = crearEventoDeUnDia("Lista compra IT ver");
        client.get().uri("/api/v1/eventos/{id}/lista-compra", eventoId)
                .header("Authorization", "Bearer " + tokenMiembroSinAreas())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.puedoEditar").isEqualTo(false)
                .jsonPath("$.llevaFicha").isEqualTo(false)
                .jsonPath("$.categorias[?(@.categoria=='LIMPIEZA')]").exists();
    }

    @Test
    void evento_inexistente_es_404() {
        client.get().uri("/api/v1/eventos/{id}/lista-compra", 999999)
                .header("Authorization", "Bearer " + tokenMiembroSinAreas())
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
    }
}
```

(El resto de tests del IT —ajustes, POST, DELETE— se añaden en Task 5, cuando existan esos endpoints.)

- [ ] **Step 4: Escribir el servicio**

`back/src/main/java/com/baniterio/api/compra/ListaCompraService.java`:

```java
package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baniterio.api.auth.ServicioPermisos;
import com.baniterio.api.compra.CalculadoraListaCompra.DatosEvento;
import com.baniterio.api.compra.CalculadoraListaCompra.LineaCalculada;
import com.baniterio.api.compra.CalculadoraListaCompra.PersonaCompra;
import com.baniterio.api.compra.dto.*;
import com.baniterio.api.evento.EventoNoEncontradoException;
import com.baniterio.api.identidad.*;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.inventario.SinPermisoInventarioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de la compra por evento: montar la lista calculada (cualquier peñista) y
 * el editor de "Cantidades para eventos" (área INVENTARIO). Las reglas de cada
 * evento se materializan copiando la plantilla global la primera vez que se pide
 * la lista. Bloque 1: cálculo bruto con redondeo hacia arriba; el descuento del
 * inventario de la fiesta es el bloque 2.
 */
@Service
public class ListaCompraService {

    private static final String SLUG_PENA = "baniterio";

    private final ReglaCompraRepository plantilla;
    private final ReglaCompraEventoRepository reglasEvento;
    private final EventoRepository eventos;
    private final AsistenciaEventoRepository asistencias;
    private final FichaBebidaRepository fichas;
    private final PenaRepository penas;
    private final ServicioPermisos permisos;

    public ListaCompraService(ReglaCompraRepository plantilla, ReglaCompraEventoRepository reglasEvento,
                              EventoRepository eventos, AsistenciaEventoRepository asistencias,
                              FichaBebidaRepository fichas, PenaRepository penas, ServicioPermisos permisos) {
        this.plantilla = plantilla;
        this.reglasEvento = reglasEvento;
        this.eventos = eventos;
        this.asistencias = asistencias;
        this.fichas = fichas;
        this.penas = penas;
        this.permisos = permisos;
    }

    private Long penaId() {
        return penas.findBySlug(SLUG_PENA)
                .orElseThrow(() -> new IllegalStateException("Falta la peña piloto '" + SLUG_PENA + "'"))
                .getId();
    }

    private Evento eventoAbierto(Long eventoId) {
        return eventos.findById(eventoId)
                .filter(e -> e.getPena().getId().equals(penaId()) && !e.isOculto())
                .orElseThrow(EventoNoEncontradoException::new);
    }

    private void exigirArea(Long usuarioId) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
    }

    /** Copia la plantilla al evento si aún no tiene reglas propias. */
    @Transactional
    void materializar(Evento evento) {
        if (reglasEvento.existsByEventoId(evento.getId())) {
            return;
        }
        for (ReglaCompra r : plantilla.findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(penaId())) {
            reglasEvento.save(ReglaCompraEvento.builder()
                    .evento(evento).categoria(r.getCategoria()).nombre(r.getNombre()).tamano(r.getTamano())
                    .tipoFormula(r.getTipoFormula()).factor(r.getFactor()).porCada(r.getPorCada())
                    .orden(r.getOrden()).origen(OrigenReglaCompra.PLANTILLA).cantidadAjustada(null)
                    .activa(true).build());
        }
    }

    private DatosEvento datosDe(Evento e) {
        boolean llevaFicha = e.getCuenta().isLlevaFichaBebida();
        int diasFiesta = e.getFechaFin() != null
                ? (int) ChronoUnit.DAYS.between(e.getFecha(), e.getFechaFin()) + 1 : 1;
        boolean dosDiasFicha = llevaFicha && diasFiesta == 2;

        List<AsistenciaEvento> apuntadas = asistencias.findByEventoIdAndEstadoIn(
                e.getId(), List.of(EstadoAsistencia.APUNTADO));
        Map<Long, FichaBebida> fichaPorAsistencia = fichas.findByEventoId(e.getId()).stream()
                .collect(Collectors.toMap(FichaBebida::getAsistenciaId, Function.identity()));

        List<PersonaCompra> personas = new ArrayList<>();
        for (AsistenciaEvento a : apuntadas) {
            FichaBebida f = fichaPorAsistencia.get(a.getId());
            int diasQueVa;
            if (dosDiasFicha && f != null) {
                diasQueVa = (f.isAsisteDia1() ? 1 : 0) + (f.isAsisteDia2() ? 1 : 0);
            } else {
                diasQueVa = diasFiesta;
            }
            personas.add(new PersonaCompra(
                    diasQueVa,
                    f != null,
                    f != null && f.getAlcohol() != null ? f.getAlcohol().getNombre() : null,
                    f != null ? f.getRefresco().getNombre() : null,
                    f != null ? f.getAlternativa() : null));
        }
        return new DatosEvento(apuntadas.size(), diasFiesta, llevaFicha, personas);
    }

    @Transactional
    public ListaCompraResponse verLista(Long usuarioId, Long eventoId) {
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        DatosEvento datos = datosDe(e);
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);

        List<ReglaCompraEvento> activas = reglasEvento
                .findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(ReglaCompraEvento::isActiva).toList();

        // categoria -> lineas
        var porCategoria = new java.util.LinkedHashMap<CategoriaInventario, List<LineaCompraDto>>();
        for (CategoriaInventario cat : CategoriaInventario.values()) {
            porCategoria.put(cat, new ArrayList<>());
        }
        for (ReglaCompraEvento r : activas) {
            for (LineaCalculada lc : CalculadoraListaCompra.lineasDe(r, datos)) {
                BigDecimal calculada = CalculadoraListaCompra.ceil(lc.bruto());
                boolean ajustada = !r.getTipoFormula().esDinamica() && r.getCantidadAjustada() != null;
                BigDecimal cantidad = ajustada ? r.getCantidadAjustada() : calculada;
                porCategoria.get(r.getCategoria()).add(new LineaCompraDto(
                        lc.nombre(), lc.tamano(), cantidad, calculada, ajustada, lc.dinamica(), lc.necesitaFicha()));
            }
        }

        List<CategoriaListaCompraDto> categorias = new ArrayList<>();
        porCategoria.forEach((cat, lineas) -> {
            if (!lineas.isEmpty()) {
                categorias.add(new CategoriaListaCompraDto(cat.name(), cat.etiqueta(), lineas));
            }
        });
        return new ListaCompraResponse(puedoEditar, datos.llevaFicha(), datos.apuntados(),
                datos.diasFiesta(), categorias);
    }

    // --- Administración -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EventoListaCompraDto> eventos(Long usuarioId) {
        exigirArea(usuarioId);
        return eventos.noOcultos(penaId()).stream()
                .map(e -> new EventoListaCompraDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()))
                .toList();
    }

    @Transactional
    public ListaCompraAdminResponse verAdmin(Long usuarioId, Long eventoId) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        DatosEvento datos = datosDe(e);

        List<ReglaCompraEventoDto> reglas = reglasEvento
                .findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .map(r -> {
                    BigDecimal calculada = CalculadoraListaCompra.lineasDe(r, datos).stream()
                            .map(lc -> CalculadoraListaCompra.ceil(lc.bruto()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal ajustada = r.getTipoFormula().esDinamica() ? null : r.getCantidadAjustada();
                    BigDecimal fin = ajustada != null ? ajustada : calculada;
                    return new ReglaCompraEventoDto(r.getId(), r.getCategoria().name(), r.getCategoria().etiqueta(),
                            r.getNombre(), r.getTamano(), r.getTipoFormula().name(), r.getFactor(), r.getPorCada(),
                            r.getOrigen().name(), calculada, ajustada, fin, r.isActiva());
                })
                .toList();

        return new ListaCompraAdminResponse(
                new EventoListaCompraDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()),
                datos.apuntados(), datos.diasFiesta(), reglas);
    }

    @Transactional
    public void ajustarRegla(Long usuarioId, Long eventoId, Long reglaId, AjustarReglaRequest req) {
        exigirArea(usuarioId);
        eventoAbierto(eventoId);
        ReglaCompraEvento r = reglasEvento.findByIdAndEventoId(reglaId, eventoId)
                .orElseThrow(ReglaCompraNoEncontradaException::new);
        if (r.getTipoFormula().esDinamica() && req.cantidadAjustada() != null) {
            throw new AjusteNoAplicaException();
        }
        r.setCantidadAjustada(req.cantidadAjustada());
        r.setActiva(req.activa());
        reglasEvento.save(r);
    }

    @Transactional
    public ReglaCompraEventoDto crearRegla(Long usuarioId, Long eventoId, CrearReglaRequest req) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        if (req.tipoFormula().esDinamica()) {
            throw new FormulaNoCreableException();
        }
        boolean esPorCada = req.tipoFormula() == TipoFormulaCompra.POR_CADA_N_PENISTAS;
        if (esPorCada == (req.porCada() == null) || (req.porCada() != null && req.porCada() <= 0)) {
            throw new PorCadaNoAplicaException();
        }
        int orden = reglasEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(x -> x.getCategoria() == req.categoria())
                .mapToInt(ReglaCompraEvento::getOrden).max().orElse(0) + 1;
        ReglaCompraEvento r = ReglaCompraEvento.builder()
                .evento(e).categoria(req.categoria()).nombre(req.nombre().trim()).tamano(req.tamano().trim())
                .tipoFormula(req.tipoFormula()).factor(req.factor()).porCada(esPorCada ? req.porCada() : null)
                .orden(orden).origen(OrigenReglaCompra.MANUAL).cantidadAjustada(null).activa(true).build();
        try {
            r = reglasEvento.saveAndFlush(r);
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaCompraDuplicadaException();
        }
        DatosEvento datos = datosDe(e);
        BigDecimal calculada = CalculadoraListaCompra.lineasDe(r, datos).stream()
                .map(lc -> CalculadoraListaCompra.ceil(lc.bruto())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReglaCompraEventoDto(r.getId(), r.getCategoria().name(), r.getCategoria().etiqueta(),
                r.getNombre(), r.getTamano(), r.getTipoFormula().name(), r.getFactor(), r.getPorCada(),
                r.getOrigen().name(), calculada, null, calculada, true);
    }

    @Transactional
    public void borrarRegla(Long usuarioId, Long eventoId, Long reglaId) {
        exigirArea(usuarioId);
        eventoAbierto(eventoId);
        ReglaCompraEvento r = reglasEvento.findByIdAndEventoId(reglaId, eventoId)
                .orElseThrow(ReglaCompraNoEncontradaException::new);
        if (r.getOrigen() == OrigenReglaCompra.PLANTILLA) {
            throw new ReglaCompraNoBorrableException();
        }
        reglasEvento.delete(r);
    }
}
```

Nota `PorCadaNoAplicaException`: la condición `esPorCada == (porCada == null)` es verdadera cuando (es POR_CADA y falta porCada) o (no es POR_CADA y sobra porCada); en ambos casos hay que fallar.

- [ ] **Step 5: Escribir el controlador de lectura**

`back/src/main/java/com/baniterio/api/compra/ListaCompraController.java`:

```java
package com.baniterio.api.compra;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.compra.dto.ListaCompraResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Lista de la compra calculada de un evento. La ve cualquier peñista logueado. */
@Tag(name = "Lista de la compra", description = "Lista de la compra calculada por evento.")
@RestController
@RequestMapping("/api/v1/eventos")
public class ListaCompraController {

    private final ListaCompraService service;

    public ListaCompraController(ListaCompraService service) {
        this.service = service;
    }

    @GetMapping("/{eventoId}/lista-compra")
    public ListaCompraResponse verLista(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.verLista(principal.id(), eventoId);
    }
}
```

- [ ] **Step 6: Ejecutar los tests**

Run: `cd back && ./mvnw -q -Dtest=CalculadoraListaCompraTest,ReglaCompraRepositoryIT test -Dit.test=ListaCompraIT verify -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -30`
Espera: verde. Si `ListaCompraIT` no encuentra helpers, ajustar a la base de IT real (ver `InventarioIT` imports/extends).

- [ ] **Step 7: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/ back/src/main/java/com/baniterio/api/identidad/EventoRepository.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraIT.java
git commit -m "feat(lista-compra): servicio, DTOs y GET /eventos/{id}/lista-compra"
```

---

## Task 5: API de administración

**Files:**
- Create: `back/src/main/java/com/baniterio/api/compra/ListaCompraAdminController.java`
- Modify: `back/src/test/java/com/baniterio/api/compra/ListaCompraIT.java` (añadir tests de admin)

**Interfaces:**
- Consumes: `ListaCompraService.eventos/verAdmin/ajustarRegla/crearRegla/borrarRegla`, DTOs (Task 4).
- Produces: endpoints `/api/v1/admin/lista-compra/**`.

- [ ] **Step 1: Escribir el controlador de administración**

`back/src/main/java/com/baniterio/api/compra/ListaCompraAdminController.java`:

```java
package com.baniterio.api.compra;

import java.util.List;

import com.baniterio.api.auth.UsuarioPrincipal;
import com.baniterio.api.compra.dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** "Cantidades para eventos": el admin ajusta las cantidades y añade/quita reglas. Área INVENTARIO. */
@Tag(name = "Lista de la compra (admin)", description = "Ajuste de cantidades y reglas de la lista de la compra por evento.")
@RestController
@RequestMapping("/api/v1/admin/lista-compra")
public class ListaCompraAdminController {

    private final ListaCompraService service;

    public ListaCompraAdminController(ListaCompraService service) {
        this.service = service;
    }

    @GetMapping("/eventos")
    public List<EventoListaCompraDto> eventos(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return service.eventos(principal.id());
    }

    @GetMapping("/eventos/{eventoId}")
    public ListaCompraAdminResponse verEvento(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId) {
        return service.verAdmin(principal.id(), eventoId);
    }

    @PutMapping("/eventos/{eventoId}/reglas/{reglaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ajustar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long reglaId,
            @Valid @RequestBody AjustarReglaRequest req) {
        service.ajustarRegla(principal.id(), eventoId, reglaId, req);
    }

    @PostMapping("/eventos/{eventoId}/reglas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReglaCompraEventoDto crear(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @Valid @RequestBody CrearReglaRequest req) {
        return service.crearRegla(principal.id(), eventoId, req);
    }

    @DeleteMapping("/eventos/{eventoId}/reglas/{reglaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long reglaId) {
        service.borrarRegla(principal.id(), eventoId, reglaId);
    }
}
```

- [ ] **Step 2: Añadir tests de administración al IT**

En `ListaCompraIT.java`, añadir (usan un evento propio creado en cada test o en un `@BeforeEach`):

```java
    @Test
    void admin_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Lista compra IT 403");
        client.get().uri("/api/v1/admin/lista-compra/eventos/{id}", eventoId)
                .header("Authorization", "Bearer " + tokenMiembroSinAreas())
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }

    @Test
    void ajustar_cambia_la_cantidad_final_y_desactivar_quita_la_linea_de_la_lectura() {
        long eventoId = crearEventoDeUnDia("Lista compra IT ajuste");
        // materializa y coge la regla de "Platos"
        var reglas = client.get().uri("/api/v1/admin/lista-compra/eventos/{id}", eventoId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .exchange().expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        long platosId = idDeRegla(reglas, "Platos");

        client.put().uri("/api/v1/admin/lista-compra/eventos/{e}/reglas/{r}", eventoId, platosId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("cantidadAjustada", 99, "activa", true))
                .exchange().expectStatus().isNoContent();

        client.get().uri("/api/v1/eventos/{id}/lista-compra", eventoId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.categorias[?(@.categoria=='LIMPIEZA')].lineas[?(@.nombre=='Platos')].cantidad")
                .isEqualTo(99);

        client.put().uri("/api/v1/admin/lista-compra/eventos/{e}/reglas/{r}", eventoId, platosId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("activa", false))
                .exchange().expectStatus().isNoContent();

        client.get().uri("/api/v1/eventos/{id}/lista-compra", eventoId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.categorias[?(@.categoria=='LIMPIEZA')].lineas[?(@.nombre=='Platos')]").doesNotExist();
    }

    @Test
    void ajustar_dinamica_con_cantidad_es_400() {
        long eventoId = crearEventoDeUnDia("Lista compra IT dinamica");
        var reglas = pedirAdmin(eventoId);
        long alcoholId = idDeReglaPorFormula(reglas, "ALCOHOL_SELECCIONADO");
        client.put().uri("/api/v1/admin/lista-compra/eventos/{e}/reglas/{r}", eventoId, alcoholId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("cantidadAjustada", 5, "activa", true))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("AJUSTE_NO_APLICA");
    }

    @Test
    void crear_regla_manual_aparece_en_la_lectura_y_se_puede_borrar() {
        long eventoId = crearEventoDeUnDia("Lista compra IT manual");
        String nuevaId = client.post().uri("/api/v1/admin/lista-compra/eventos/{id}/reglas", eventoId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("categoria", "COMIDA", "nombre", "Servilletas", "tamano", "paquete",
                        "tipoFormula", "POR_EVENTO", "factor", 2))
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.origen").isEqualTo("MANUAL")
                .jsonPath("$.cantidadFinal").isEqualTo(2)
                .jsonPath("$.id").value(v -> {}).returnResult().getResponseBody() != null ? "" : "";
        // (extraer el id del cuerpo con jsonPath y usarlo)
    }

    @Test
    void borrar_regla_de_plantilla_es_409() {
        long eventoId = crearEventoDeUnDia("Lista compra IT no borrable");
        var reglas = pedirAdmin(eventoId);
        long platosId = idDeRegla(reglas, "Platos");
        client.delete().uri("/api/v1/admin/lista-compra/eventos/{e}/reglas/{r}", eventoId, platosId)
                .header("Authorization", "Bearer " + tokenAdmin())
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.codigo").isEqualTo("REGLA_COMPRA_NO_BORRABLE");
    }
```

Los helpers `crearEventoDeUnDia`, `idDeRegla`, `idDeReglaPorFormula`, `pedirAdmin` se implementan como privados en el propio IT (POST a `/api/v1/eventos`, parseo del JSON de `verAdmin`). Ajustar `tokenAdmin()` / `tokenMiembroSinAreas()` a los helpers reales de la base de IT.

- [ ] **Step 3: Ejecutar el IT completo**

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraIT verify -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -40`
Espera: verde.

- [ ] **Step 4: Ejecutar toda la suite de backend**

Run: `cd back && ./mvnw -q verify 2>&1 | tee /tmp/mvn.log | tail -15; echo "MVN_EXIT=${PIPESTATUS[0]}"`
Espera: `MVN_EXIT=0`.

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/ListaCompraAdminController.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraIT.java
git commit -m "feat(lista-compra): API de administración (Cantidades para eventos)"
```

---

## Task 6: Web — servicio Angular, tipos y spec

**Files:**
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.types.ts`
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.service.ts`
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.service.spec.ts`

**Interfaces:**
- Consumes: los endpoints de Tasks 4-5.
- Produces: `ListaCompraService` (Angular) + tipos, para las Tasks 7-8.

- [ ] **Step 1: Escribir los tipos**

`lista-compra.types.ts`:

```ts
export type CategoriaClave = 'ALCOHOL' | 'CERVEZA' | 'REFRESCOS' | 'LIMPIEZA' | 'COMIDA';

export type TipoFormula =
  | 'POR_PENISTA' | 'POR_PENISTA_DIA' | 'POR_DIA' | 'POR_EVENTO' | 'POR_CADA_N_PENISTAS'
  | 'CERVEZA_ALTERNATIVA' | 'TINTO_ALTERNATIVA' | 'ALCOHOL_SELECCIONADO' | 'REFRESCO_SELECCIONADO';

export interface LineaCompra {
  nombre: string;
  tamano: string;
  cantidad: number;
  cantidadCalculada: number;
  ajustada: boolean;
  dinamica: boolean;
  necesitaFicha: boolean;
}

export interface CategoriaListaCompra {
  categoria: CategoriaClave;
  etiqueta: string;
  lineas: LineaCompra[];
}

export interface ListaCompraResponse {
  puedoEditar: boolean;
  llevaFicha: boolean;
  apuntados: number;
  diasFiesta: number;
  categorias: CategoriaListaCompra[];
}

export interface EventoListaCompra {
  id: number;
  nombre: string;
  fecha: string;
  fechaFin: string | null;
}

export interface ReglaCompraEvento {
  id: number;
  categoria: CategoriaClave;
  etiqueta: string;
  nombre: string;
  tamano: string;
  tipoFormula: TipoFormula;
  factor: number;
  porCada: number | null;
  origen: 'PLANTILLA' | 'MANUAL';
  cantidadCalculada: number;
  cantidadAjustada: number | null;
  cantidadFinal: number;
  activa: boolean;
}

export interface ListaCompraAdminResponse {
  evento: EventoListaCompra;
  apuntados: number;
  diasFiesta: number;
  reglas: ReglaCompraEvento[];
}

export interface AjustarRegla {
  cantidadAjustada: number | null;
  activa: boolean;
}

export interface CrearRegla {
  categoria: CategoriaClave;
  nombre: string;
  tamano: string;
  tipoFormula: TipoFormula;
  factor: number;
  porCada?: number | null;
}
```

- [ ] **Step 2: Escribir el spec del servicio (falla)**

`lista-compra.service.spec.ts` — patrón de `front/src/app/panel/inventario/inventario.service.spec.ts` (`provideHttpClient`, `provideHttpClientTesting`, `HttpTestingController`). Import de `environment` desde `'../../../../environments/environment'` (4 niveles, como `inventario-fiesta.spec.ts`). Un test por método:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { environment } from '../../../../environments/environment';
import { ListaCompraService } from './lista-compra.service';

describe('ListaCompraService', () => {
  let service: ListaCompraService;
  let http: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ListaCompraService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ListaCompraService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lista() hace GET /eventos/:id/lista-compra', () => {
    service.lista(7).subscribe();
    const r = http.expectOne(`${base}/eventos/7/lista-compra`);
    expect(r.request.method).toBe('GET');
    r.flush({ puedoEditar: false, llevaFicha: false, apuntados: 0, diasFiesta: 1, categorias: [] });
  });

  it('adminEventos() hace GET /admin/lista-compra/eventos', () => {
    service.adminEventos().subscribe();
    const r = http.expectOne(`${base}/admin/lista-compra/eventos`);
    expect(r.request.method).toBe('GET');
    r.flush([]);
  });

  it('adminEvento() hace GET /admin/lista-compra/eventos/:id', () => {
    service.adminEvento(7).subscribe();
    const r = http.expectOne(`${base}/admin/lista-compra/eventos/7`);
    expect(r.request.method).toBe('GET');
    r.flush({ evento: { id: 7, nombre: 'x', fecha: '2026-09-25', fechaFin: null }, apuntados: 0, diasFiesta: 1, reglas: [] });
  });

  it('ajustarRegla() hace PUT con body', () => {
    service.ajustarRegla(7, 3, { cantidadAjustada: 12, activa: true }).subscribe();
    const r = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas/3`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ cantidadAjustada: 12, activa: true });
    r.flush(null);
  });

  it('crearRegla() hace POST con body', () => {
    const body = { categoria: 'COMIDA', nombre: 'Servilletas', tamano: 'paquete', tipoFormula: 'POR_EVENTO', factor: 2 } as const;
    service.crearRegla(7, body).subscribe();
    const r = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual(body);
    r.flush({});
  });

  it('borrarRegla() hace DELETE', () => {
    service.borrarRegla(7, 3).subscribe();
    const r = http.expectOne(`${base}/admin/lista-compra/eventos/7/reglas/3`);
    expect(r.request.method).toBe('DELETE');
    r.flush(null);
  });
});
```

- [ ] **Step 3: Ejecutar el spec y ver que falla**

Run: `cd front && npx vitest run src/app/panel/eventos/lista-compra/lista-compra.service.spec.ts 2>&1 | tail -20`
Espera: FALLA (no existe `ListaCompraService`).

- [ ] **Step 4: Escribir el servicio**

`lista-compra.service.ts` — patrón de `inventario.service.ts`:

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AjustarRegla, CrearRegla, ListaCompraAdminResponse, ListaCompraResponse, EventoListaCompra, ReglaCompraEvento,
} from './lista-compra.types';

/** Llamadas de la lista de la compra por evento. Un método por endpoint; los errores se dejan propagar. */
@Injectable({ providedIn: 'root' })
export class ListaCompraService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  lista(eventoId: number): Observable<ListaCompraResponse> {
    return this.http.get<ListaCompraResponse>(`${this.base}/eventos/${eventoId}/lista-compra`);
  }

  adminEventos(): Observable<EventoListaCompra[]> {
    return this.http.get<EventoListaCompra[]>(`${this.base}/admin/lista-compra/eventos`);
  }

  adminEvento(eventoId: number): Observable<ListaCompraAdminResponse> {
    return this.http.get<ListaCompraAdminResponse>(`${this.base}/admin/lista-compra/eventos/${eventoId}`);
  }

  ajustarRegla(eventoId: number, reglaId: number, body: AjustarRegla): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/lista-compra/eventos/${eventoId}/reglas/${reglaId}`, body);
  }

  crearRegla(eventoId: number, body: CrearRegla): Observable<ReglaCompraEvento> {
    return this.http.post<ReglaCompraEvento>(`${this.base}/admin/lista-compra/eventos/${eventoId}/reglas`, body);
  }

  borrarRegla(eventoId: number, reglaId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/lista-compra/eventos/${eventoId}/reglas/${reglaId}`);
  }
}
```

- [ ] **Step 5: Ejecutar el spec y ver que pasa**

Run: `cd front && npx vitest run src/app/panel/eventos/lista-compra/lista-compra.service.spec.ts 2>&1 | tail -20`
Espera: PASA (6 tests).

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/eventos/lista-compra/
git commit -m "feat(lista-compra): web — servicio Angular y tipos"
```

---

## Task 7: Web — pantalla de lectura y botón del evento

**Files:**
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.ts`
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.html`
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.css`
- Create: `front/src/app/panel/eventos/lista-compra/lista-compra.spec.ts`
- Modify: `front/src/app/app.routes.ts` (import + ruta antes de `eventos/:id`)
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.html:156-163` (botón)
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.spec.ts` (test del enlace)

**Interfaces:**
- Consumes: `ListaCompraService.lista` (Task 6).

- [ ] **Step 1: Escribir el spec del componente (falla)**

`lista-compra.spec.ts` — patrón de `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.spec.ts` (mismo `montar()` con `{ provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } }`, `provideHttpClient` + testing). Tests:

```ts
// 1. pinta una categoría con sus líneas
it('pinta las categorías y sus líneas', async () => {
  const { fixture, http } = montar();
  http.expectOne(`${base}/eventos/7/lista-compra`).flush({
    puedoEditar: false, llevaFicha: true, apuntados: 10, diasFiesta: 2,
    categorias: [{ categoria: 'LIMPIEZA', etiqueta: 'Limpieza y utensilios', lineas: [
      { nombre: 'Platos', tamano: 'unidad', cantidad: 30, cantidadCalculada: 30, ajustada: false, dinamica: false, necesitaFicha: false },
    ]}],
  });
  fixture.detectChanges();
  const txt = (fixture.nativeElement as HTMLElement).textContent!;
  expect(txt).toContain('Platos');
  expect(txt).toContain('30');
});

// 2. muestra la nota cuando necesitaFicha
it('marca las líneas que necesitan ficha de bebida', async () => {
  const { fixture, http } = montar();
  http.expectOne(`${base}/eventos/7/lista-compra`).flush({
    puedoEditar: false, llevaFicha: false, apuntados: 3, diasFiesta: 1,
    categorias: [{ categoria: 'CERVEZA', etiqueta: 'Cerveza', lineas: [
      { nombre: 'Cerveza', tamano: 'lata', cantidad: 0, cantidadCalculada: 0, ajustada: false, dinamica: false, necesitaFicha: true },
    ]}],
  });
  fixture.detectChanges();
  expect((fixture.nativeElement as HTMLElement).textContent).toContain('necesita ficha de bebida');
});
```

- [ ] **Step 2: Ejecutar y ver que falla**

Run: `cd front && npx vitest run src/app/panel/eventos/lista-compra/lista-compra.spec.ts 2>&1 | tail -20`
Espera: FALLA.

- [ ] **Step 3: Escribir el componente**

`lista-compra.ts` — copia la estructura de `inventario-fiesta.ts`:

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { ListaCompraService } from './lista-compra.service';
import { CategoriaListaCompra } from './lista-compra.types';

/**
 * Lista de la compra calculada de un evento, `/panel/eventos/:id/lista-compra`.
 * Solo lectura; la ve cualquier peñista. El ajuste de cantidades está en
 * "Cantidades para eventos" (administración).
 */
@Component({
  selector: 'app-lista-compra',
  imports: [Volver],
  templateUrl: './lista-compra.html',
  styleUrl: './lista-compra.css',
})
export class ListaCompra implements OnInit {
  private readonly service = inject(ListaCompraService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly apuntados = signal(0);
  protected readonly diasFiesta = signal(0);
  protected readonly categorias = signal<CategoriaListaCompra[]>([]);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.lista(this.eventoId).subscribe({
      next: (d) => {
        this.apuntados.set(d.apuntados);
        this.diasFiesta.set(d.diasFiesta);
        this.categorias.set(d.categorias);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected trackCat = (_: number, c: CategoriaListaCompra) => c.categoria;
}
```

`lista-compra.html` — patrón de `inventario-fiesta.html`:

```html
<section class="mx-auto w-full max-w-5xl">
  <div class="mb-4"><app-volver [destino]="'/panel/eventos/' + eventoId" etiqueta="Evento" /></div>

  <h1 class="font-display text-2xl font-extrabold leading-tight">Lista de la compra</h1>

  @switch (estado()) {
    @case ('cargando') { <p class="mt-4 text-sm text-muted">Cargando…</p> }
    @case ('error') {
      <p class="mt-4 text-sm text-red-300">No se ha podido cargar la lista.</p>
      <button type="button" (click)="cargar()" class="mt-2 rounded-lg border border-outline px-3 py-1 text-sm">Reintentar</button>
    }
    @case ('listo') {
      <p class="mt-1 text-sm text-muted">{{ apuntados() }} apuntados · {{ diasFiesta() }} día(s) de fiesta</p>
      @if (categorias().length === 0) {
        <p class="mt-6 text-sm text-muted">Todavía no hay nada que comprar para este evento.</p>
      } @else {
        @for (cat of categorias(); track trackCat($index, cat)) {
          <article class="carta-relieve mt-4 rounded-2xl p-5">
            <h2 class="font-display text-lg font-bold text-gold-soft">{{ cat.etiqueta }}</h2>
            <table class="mt-3 w-full text-sm">
              <thead>
                <tr class="text-left text-xs uppercase tracking-wide text-muted">
                  <th class="pb-1">Artículo</th><th class="pb-1">Tamaño</th><th class="pb-1 text-right">Cantidad</th>
                </tr>
              </thead>
              <tbody>
                @for (l of cat.lineas; track l.nombre + l.tamano) {
                  <tr class="border-t border-outline/30">
                    <td class="py-1">
                      {{ l.nombre }}
                      @if (l.necesitaFicha) { <span class="text-xs text-muted">· necesita ficha de bebida</span> }
                      @if (l.ajustada) { <span class="text-xs text-gold-soft">· ajustado</span> }
                    </td>
                    <td class="py-1">{{ l.tamano }}</td>
                    <td class="py-1 text-right font-semibold">{{ l.cantidad }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </article>
        }
      }
    }
  }
</section>
```

`lista-compra.css`: `/* Sin estilos propios. */`

Verificar la firma real de `app-volver` (mirar `inventario-fiesta.html`: usa `[destino]` y `etiqueta`).

- [ ] **Step 4: Añadir la ruta**

`app.routes.ts`: import `import { ListaCompra } from './panel/eventos/lista-compra/lista-compra';` y ruta **antes** de `eventos/:id` (junto a `eventos/:id/inventario`):

```ts
      { path: 'eventos/:id/lista-compra', component: ListaCompra, canActivate: [perfilCompletoGuard] },
```

- [ ] **Step 5: Cambiar el botón del detalle del evento**

`evento-detalle.html:156-163`: sustituir el `<button (click)="proximamente('Lista de la compra')" ...>` por un enlace igual que el de "Inventario de la fiesta":

```html
              <a
                [routerLink]="['/panel/eventos', e.id, 'lista-compra']"
                class="rounded-xl border border-brand px-4 py-3 text-center text-sm font-bold text-gold transition hover:bg-brand/20"
              >
                Lista de la compra
              </a>
```

Si `proximamente(...)` ya no lo usa nadie más en `evento-detalle.html`, quitar el método de `evento-detalle.ts` y su import/uso de `aviso` si queda huérfano. Comprobar con una búsqueda `proximamente` en el fichero antes de borrar.

- [ ] **Step 6: Actualizar el spec del detalle**

`evento-detalle.spec.ts`: en el test que hoy comprueba "Lista de la compra (placeholder)", cambiarlo para que compruebe que hay un `<a>` con texto "Lista de la compra" cuyo `href` contiene `/lista-compra` (mismo estilo que la comprobación de "Inventario de la fiesta").

- [ ] **Step 7: Ejecutar los specs afectados**

Run: `cd front && npx vitest run src/app/panel/eventos/lista-compra src/app/panel/eventos/evento-detalle 2>&1 | tail -25`
Espera: verde.

- [ ] **Step 8: Commit**

```bash
git add front/src/app/panel/eventos/lista-compra/ front/src/app/app.routes.ts \
        front/src/app/panel/eventos/evento-detalle/
git commit -m "feat(lista-compra): web — pantalla de lectura y enlace desde el evento"
```

---

## Task 8: Web — pantalla de administración "Cantidades para eventos"

**Files:**
- Create: `front/src/app/admin/lista-compra/lista-compra-admin.ts` + `.html` + `.css` + `.spec.ts`
- Create: `front/src/app/admin/lista-compra/lista-compra-admin-evento.ts` + `.html` + `.css` + `.spec.ts`
- Modify: `front/src/app/app.routes.ts` (2 rutas con `areaGuard('INVENTARIO')`)
- Modify: `front/src/app/admin/indice/indice.ts` + `indice.html` (tarjeta "Cantidades para eventos")

**Interfaces:**
- Consumes: `ListaCompraService` (Task 6), `areaGuard` (`front/src/app/admin/area.guard.ts`).

- [ ] **Step 1: Escribir `lista-compra-admin` (lista de eventos)**

`lista-compra-admin.ts`:

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Volver } from '../../shared/volver/volver';
import { ListaCompraService } from '../../panel/eventos/lista-compra/lista-compra.service';
import { EventoListaCompra } from '../../panel/eventos/lista-compra/lista-compra.types';

/** "Cantidades para eventos": lista de eventos para entrar a ajustar su lista de la compra. Área INVENTARIO. */
@Component({
  selector: 'app-lista-compra-admin',
  imports: [RouterLink, Volver],
  templateUrl: './lista-compra-admin.html',
})
export class ListaCompraAdmin implements OnInit {
  private readonly service = inject(ListaCompraService);
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly eventos = signal<EventoListaCompra[]>([]);

  ngOnInit(): void {
    this.service.adminEventos().subscribe({
      next: (e) => { this.eventos.set(e); this.estado.set('listo'); },
      error: () => this.estado.set('error'),
    });
  }
}
```

`lista-compra-admin.html`: cabecera `<app-volver destino="/panel/administracion" />`, `<h1>Cantidades para eventos</h1>`, `@switch (estado())` con cargando/error, y en "listo" una rejilla de `<a [routerLink]="['/panel/administracion/lista-compra', ev.id]">` con `ev.nombre` y `ev.fecha` (misma estética de tarjeta que `indice.html`).

- [ ] **Step 2: Escribir `lista-compra-admin-evento` (editor)**

`lista-compra-admin-evento.ts`:

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Volver } from '../../shared/volver/volver';
import { ListaCompraService } from '../../panel/eventos/lista-compra/lista-compra.service';
import { CategoriaClave, CrearRegla, ReglaCompraEvento, TipoFormula } from '../../panel/eventos/lista-compra/lista-compra.types';

const CATEGORIAS: { clave: CategoriaClave; etiqueta: string }[] = [
  { clave: 'ALCOHOL', etiqueta: 'Alcohol' },
  { clave: 'CERVEZA', etiqueta: 'Cerveza' },
  { clave: 'REFRESCOS', etiqueta: 'Refrescos' },
  { clave: 'LIMPIEZA', etiqueta: 'Limpieza y utensilios' },
  { clave: 'COMIDA', etiqueta: 'Comida' },
];

const FORMULAS_CREABLES: { clave: TipoFormula; etiqueta: string }[] = [
  { clave: 'POR_PENISTA', etiqueta: 'Por peñista' },
  { clave: 'POR_PENISTA_DIA', etiqueta: 'Por peñista y día' },
  { clave: 'POR_DIA', etiqueta: 'Por día de fiesta' },
  { clave: 'POR_EVENTO', etiqueta: 'Por evento (cantidad fija)' },
  { clave: 'POR_CADA_N_PENISTAS', etiqueta: 'Por cada N peñistas' },
  { clave: 'CERVEZA_ALTERNATIVA', etiqueta: 'Por peñista y día (bebe cerveza)' },
  { clave: 'TINTO_ALTERNATIVA', etiqueta: 'Por peñista y día (bebe tinto)' },
];

/** Editor de la lista de la compra de un evento: ajustar cantidades, activar/desactivar y añadir/quitar reglas. */
@Component({
  selector: 'app-lista-compra-admin-evento',
  imports: [Volver, FormsModule],
  templateUrl: './lista-compra-admin-evento.html',
})
export class ListaCompraAdminEvento implements OnInit {
  private readonly service = inject(ListaCompraService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly categoriasCat = CATEGORIAS;
  protected readonly formulas = FORMULAS_CREABLES;
  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly nombre = signal('');
  protected readonly reglas = signal<ReglaCompraEvento[]>([]);
  protected readonly aviso = signal('');
  protected readonly guardando = signal(false);
  protected readonly formAbierto = signal(false);
  protected nueva: CrearRegla = { categoria: 'COMIDA', nombre: '', tamano: '', tipoFormula: 'POR_EVENTO', factor: 1, porCada: null };

  ngOnInit(): void { this.cargar(); }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.adminEvento(this.eventoId).subscribe({
      next: (d) => { this.nombre.set(d.evento.nombre); this.reglas.set(d.reglas); this.estado.set('listo'); },
      error: () => this.estado.set('error'),
    });
  }

  protected formulaLegible(r: ReglaCompraEvento): string {
    switch (r.tipoFormula) {
      case 'POR_PENISTA': return `${r.factor} por peñista`;
      case 'POR_PENISTA_DIA': return `${r.factor} por peñista y día`;
      case 'POR_DIA': return `${r.factor} por día de fiesta`;
      case 'POR_EVENTO': return `${r.factor} por evento`;
      case 'POR_CADA_N_PENISTAS': return `${r.factor} por cada ${r.porCada} peñistas`;
      case 'CERVEZA_ALTERNATIVA': return `${r.factor} por peñista y día (bebe cerveza)`;
      case 'TINTO_ALTERNATIVA': return `${r.factor} por peñista y día (bebe tinto)`;
      case 'ALCOHOL_SELECCIONADO': return '0,5 botellas por peñista y día, por marca elegida';
      case 'REFRESCO_SELECCIONADO': return '1 botella por peñista y día, por marca elegida';
    }
  }

  protected guardar(r: ReglaCompraEvento): void {
    this.guardando.set(true);
    this.aviso.set('');
    this.service.ajustarRegla(this.eventoId, r.id, { cantidadAjustada: r.cantidadAjustada, activa: r.activa }).subscribe({
      next: () => { this.guardando.set(false); this.cargar(); },
      error: () => { this.guardando.set(false); this.aviso.set('No se pudo guardar.'); },
    });
  }

  protected anadir(): void {
    this.guardando.set(true);
    this.aviso.set('');
    const body: CrearRegla = { ...this.nueva, porCada: this.nueva.tipoFormula === 'POR_CADA_N_PENISTAS' ? this.nueva.porCada : null };
    this.service.crearRegla(this.eventoId, body).subscribe({
      next: () => { this.guardando.set(false); this.formAbierto.set(false); this.nueva = { categoria: 'COMIDA', nombre: '', tamano: '', tipoFormula: 'POR_EVENTO', factor: 1, porCada: null }; this.cargar(); },
      error: () => { this.guardando.set(false); this.aviso.set('No se pudo añadir.'); },
    });
  }

  protected quitar(r: ReglaCompraEvento): void {
    if (!confirm(`¿Quitar "${r.nombre}" de este evento?`)) return;
    this.service.borrarRegla(this.eventoId, r.id).subscribe({
      next: () => this.cargar(),
      error: () => this.aviso.set('No se pudo quitar.'),
    });
  }
}
```

`lista-compra-admin-evento.html`: cabecera con `nombre()`, `@switch (estado())`, y en "listo" una lista de reglas agrupadas visualmente (o plana, ordenadas por categoría) con: nombre + tamaño, `formulaLegible(r)`, "calculado: {{ r.cantidadCalculada }}", `<input type="number" [(ngModel)]="r.cantidadAjustada" placeholder="fórmula">` (deshabilitado si `r.tipoFormula` es dinámica: `ALCOHOL_SELECCIONADO`/`REFRESCO_SELECCIONADO`), `<input type="checkbox" [(ngModel)]="r.activa">`, botón "Guardar" `(click)="guardar(r)"`, y botón "Quitar" `(click)="quitar(r)"` solo si `r.origen === 'MANUAL'`. Botón "Añadir artículo" que hace `formAbierto.set(true)`; formulario con `<select [(ngModel)]="nueva.categoria">`, `nombre`, `tamano`, `<select [(ngModel)]="nueva.tipoFormula">` (opciones de `formulas`), `factor`, y `porCada` visible solo si `nueva.tipoFormula === 'POR_CADA_N_PENISTAS'`; botón "Añadir" `(click)="anadir()"`.

- [ ] **Step 3: Escribir los specs (fallan)**

`lista-compra-admin.spec.ts`: monta con `provideHttpClient` + testing, flushea `GET /admin/lista-compra/eventos` con 2 eventos, comprueba que pinta 2 enlaces a `/panel/administracion/lista-compra/<id>`.

`lista-compra-admin-evento.spec.ts` (`ActivatedRoute` con `paramMap: new Map([['id','7']])`):
- flush `GET /admin/lista-compra/eventos/7` con una regla `Platos` (PLANTILLA) y una `Servilletas` (MANUAL).
- ajustar: poner `cantidadAjustada=50` en la fila de Platos y pulsar "Guardar" → `expectOne PUT /admin/lista-compra/eventos/7/reglas/<id>` con body `{ cantidadAjustada: 50, activa: true }`.
- añadir: abrir formulario, rellenar, pulsar "Añadir" → `expectOne POST /admin/lista-compra/eventos/7/reglas`.
- quitar (con `vi.spyOn(window, 'confirm').mockReturnValue(true)`): en la fila MANUAL pulsar "Quitar" → `expectOne DELETE /admin/lista-compra/eventos/7/reglas/<id>`.

- [ ] **Step 4: Añadir rutas**

`app.routes.ts`, junto a las otras `administracion/*`:

```ts
import { ListaCompraAdmin } from './admin/lista-compra/lista-compra-admin';
import { ListaCompraAdminEvento } from './admin/lista-compra/lista-compra-admin-evento';
// ...
      {
        path: 'administracion/lista-compra',
        component: ListaCompraAdmin,
        canActivate: [perfilCompletoGuard, areaGuard('INVENTARIO')],
      },
      {
        path: 'administracion/lista-compra/:id',
        component: ListaCompraAdminEvento,
        canActivate: [perfilCompletoGuard, areaGuard('INVENTARIO')],
      },
```

Comprobar que `Area` en `front/src/app/admin/admin.types.ts` incluye `'INVENTARIO'` (la rama `feature/inventario` lo añadió; si no, añadirlo).

- [ ] **Step 5: Añadir la tarjeta al índice de administración**

`indice.ts`: añadir a `SECCIONES_ADMIN`:

```ts
  {
    area: 'INVENTARIO',
    descripcion: 'Ajusta las cantidades de la lista de la compra de cada evento y añade o quita artículos.',
    ruta: '/panel/administracion/lista-compra',
  },
```

Verificar que `AREAS` en `admin.types.ts` tiene la etiqueta de `INVENTARIO` (p. ej. `'Cantidades para eventos'` o `'Inventario'`). Si la etiqueta actual de `INVENTARIO` no encaja como título de esta tarjeta, usar un literal en `SeccionAdmin` en vez de `etiquetas[seccion.area]` — pero lo más simple es que `AREAS['INVENTARIO'] = 'Cantidades para eventos'`. Decidir según lo que ya haya; si `INVENTARIO` no está en `AREAS`, añadirlo con `'Cantidades para eventos'`.

- [ ] **Step 6: Ejecutar los specs + typecheck**

Run: `cd front && npx vitest run src/app/admin/lista-compra 2>&1 | tail -25`
Run: `cd front && npx tsc -p tsconfig.app.json --noEmit 2>&1 | tail -20`
Espera: verde.

- [ ] **Step 7: Ejecutar toda la suite web**

Run: `cd front && npx vitest run 2>&1 | tail -15`
Espera: todo verde.

- [ ] **Step 8: Commit**

```bash
git add front/src/app/admin/lista-compra/ front/src/app/app.routes.ts \
        front/src/app/admin/indice/ front/src/app/admin/admin.types.ts
git commit -m "feat(lista-compra): web — pantalla de administración Cantidades para eventos"
```

---

## Task 9: Móvil — DTOs, resultado tipado y repositorio

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/ListaCompraDtos.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoListaCompra.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ListaCompraRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ListaCompraRepositoryImpl.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/Dependencias.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/ListaCompraRepositoryImplTest.kt`

**Interfaces:**
- Consumes: endpoints de Tasks 4-5, `SesionHolder`, `configComun()`, `API_BASE_URL`, `ErrorResponse` DTO.
- Produces: `ListaCompraRepository` (interfaz `suspend`), DTOs, `ResultadoListaCompra` / `CodigoErrorListaCompra`.

- [ ] **Step 1: Escribir los DTOs**

`ListaCompraDtos.kt` (todos `@Serializable`; `Double` para cantidades, `String?` para `fechaFin`):

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LineaCompraDto(
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
    val cantidadCalculada: Double = 0.0,
    val ajustada: Boolean = false,
    val dinamica: Boolean = false,
    val necesitaFicha: Boolean = false,
)

@Serializable
data class CategoriaListaCompraDto(
    val categoria: String,
    val etiqueta: String,
    val lineas: List<LineaCompraDto> = emptyList(),
)

@Serializable
data class ListaCompraResponse(
    val puedoEditar: Boolean = false,
    val llevaFicha: Boolean = false,
    val apuntados: Int = 0,
    val diasFiesta: Int = 0,
    val categorias: List<CategoriaListaCompraDto> = emptyList(),
)

@Serializable
data class EventoListaCompraDto(
    val id: Long,
    val nombre: String,
    val fecha: String,
    val fechaFin: String? = null,
)

@Serializable
data class ReglaCompraEventoDto(
    val id: Long,
    val categoria: String,
    val etiqueta: String,
    val nombre: String,
    val tamano: String,
    val tipoFormula: String,
    val factor: Double,
    val porCada: Int? = null,
    val origen: String,
    val cantidadCalculada: Double,
    val cantidadAjustada: Double? = null,
    val cantidadFinal: Double,
    val activa: Boolean,
)

@Serializable
data class ListaCompraAdminResponse(
    val evento: EventoListaCompraDto,
    val apuntados: Int = 0,
    val diasFiesta: Int = 0,
    val reglas: List<ReglaCompraEventoDto> = emptyList(),
)

@Serializable
data class AjustarReglaBody(val cantidadAjustada: Double?, val activa: Boolean)

@Serializable
data class CrearReglaBody(
    val categoria: String,
    val nombre: String,
    val tamano: String,
    val tipoFormula: String,
    val factor: Double,
    val porCada: Int? = null,
)
```

- [ ] **Step 2: Escribir `ResultadoListaCompra`**

`ResultadoListaCompra.kt` — patrón exacto de `ResultadoInventario.kt`:

```kotlin
package com.baniterio.app.data

sealed class ResultadoListaCompra<out T> {
    data class Exito<T>(val dato: T) : ResultadoListaCompra<T>()
    data class Error(val codigo: CodigoErrorListaCompra, val mensaje: String) : ResultadoListaCompra<Nothing>()
}

enum class CodigoErrorListaCompra {
    EVENTO_NO_ENCONTRADO,
    SIN_PERMISO,
    REGLA_NO_ENCONTRADA,
    REGLA_NO_BORRABLE,
    REGLA_DUPLICADA,
    AJUSTE_NO_APLICA,
    POR_CADA_NO_APLICA,
    FORMULA_NO_CREABLE,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorListaCompra = when (codigo) {
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
            "SIN_PERMISO_INVENTARIO" -> SIN_PERMISO
            "REGLA_COMPRA_NO_ENCONTRADA" -> REGLA_NO_ENCONTRADA
            "REGLA_COMPRA_NO_BORRABLE" -> REGLA_NO_BORRABLE
            "REGLA_COMPRA_DUPLICADA" -> REGLA_DUPLICADA
            "AJUSTE_NO_APLICA" -> AJUSTE_NO_APLICA
            "POR_CADA_NO_APLICA" -> POR_CADA_NO_APLICA
            "FORMULA_NO_CREABLE" -> FORMULA_NO_CREABLE
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
            SIN_PERMISO -> "No tienes permiso para esto."
            REGLA_NO_ENCONTRADA -> "Esa regla ya no existe."
            REGLA_NO_BORRABLE -> "Esa regla es de la plantilla: desactívala en vez de borrarla."
            REGLA_DUPLICADA -> "Ya hay una regla igual en este evento."
            AJUSTE_NO_APLICA -> "Las reglas por marca no admiten cantidad fija."
            POR_CADA_NO_APLICA -> "«Por cada N» necesita el número N."
            FORMULA_NO_CREABLE -> "Esa fórmula no se puede crear a mano."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
```

- [ ] **Step 3: Escribir la interfaz del repo**

`ListaCompraRepository.kt`:

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.CrearReglaBody
import com.baniterio.app.data.dto.EventoListaCompraDto
import com.baniterio.app.data.dto.ListaCompraAdminResponse
import com.baniterio.app.data.dto.ListaCompraResponse
import com.baniterio.app.data.dto.ReglaCompraEventoDto

interface ListaCompraRepository {
    suspend fun lista(eventoId: Long): ResultadoListaCompra<ListaCompraResponse>
    suspend fun adminEventos(): ResultadoListaCompra<List<EventoListaCompraDto>>
    suspend fun adminEvento(eventoId: Long): ResultadoListaCompra<ListaCompraAdminResponse>
    suspend fun ajustarRegla(eventoId: Long, reglaId: Long, cantidadAjustada: Double?, activa: Boolean): ResultadoListaCompra<Unit>
    suspend fun crearRegla(eventoId: Long, body: CrearReglaBody): ResultadoListaCompra<ReglaCompraEventoDto>
    suspend fun borrarRegla(eventoId: Long, reglaId: Long): ResultadoListaCompra<Unit>
}
```

- [ ] **Step 4: Escribir el test (falla)**

`ListaCompraRepositoryImplTest.kt` — patrón exacto de `InventarioRepositoryImplTest.kt` (`MockEngine`, `Vista(metodo, path, cuerpo, auth)`, `req.body.toByteArray().decodeToString()`):

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.CrearReglaBody
import com.baniterio.app.data.dto.ListaCompraResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ListaCompraRepositoryImplTest {

    private data class Vista(val metodo: String, val path: String, val cuerpo: String, val auth: String?)

    private fun repo(status: HttpStatusCode = HttpStatusCode.OK, cuerpo: String = ""):
        Pair<ListaCompraRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(req.method.value, req.url.encodedPath,
                req.body.toByteArray().decodeToString(), req.headers[HttpHeaders.Authorization])
            if (status.value >= 400) respondError(status, cuerpo, headersOf(HttpHeaders.ContentType, "application/json"))
            else respond(ByteReadChannel(cuerpo), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return ListaCompraRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun lista_hace_get_con_bearer() = runTest {
        val (r, v) = repo(cuerpo = """{"puedoEditar":true,"llevaFicha":false,"apuntados":3,"diasFiesta":1,"categorias":[]}""")
        val res = r.lista(7)
        assertIs<ResultadoListaCompra.Exito<ListaCompraResponse>>(res)
        assertEquals("GET", v[0].metodo)
        assertEquals("/api/v1/eventos/7/lista-compra", v[0].path)
        assertEquals("Bearer jwt-x", v[0].auth)
    }

    @Test
    fun admin_eventos_hace_get() = runTest {
        val (r, v) = repo(cuerpo = "[]")
        r.adminEventos()
        assertEquals("/api/v1/admin/lista-compra/eventos", v[0].path)
    }

    @Test
    fun admin_evento_hace_get() = runTest {
        val (r, v) = repo(cuerpo = """{"evento":{"id":7,"nombre":"x","fecha":"2026-09-25"},"apuntados":0,"diasFiesta":1,"reglas":[]}""")
        r.adminEvento(7)
        assertEquals("/api/v1/admin/lista-compra/eventos/7", v[0].path)
    }

    @Test
    fun ajustar_regla_hace_put_con_body() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        r.ajustarRegla(7, 3, 12.0, true)
        assertEquals("PUT", v[0].metodo)
        assertEquals("/api/v1/admin/lista-compra/eventos/7/reglas/3", v[0].path)
        assert(v[0].cuerpo.contains("\"cantidadAjustada\":12")) { v[0].cuerpo }
    }

    @Test
    fun crear_regla_hace_post() = runTest {
        val (r, v) = repo(cuerpo = """{"id":9,"categoria":"COMIDA","etiqueta":"Comida","nombre":"Servilletas","tamano":"paquete","tipoFormula":"POR_EVENTO","factor":2.0,"origen":"MANUAL","cantidadCalculada":2.0,"cantidadFinal":2.0,"activa":true}""")
        r.crearRegla(7, CrearReglaBody("COMIDA", "Servilletas", "paquete", "POR_EVENTO", 2.0))
        assertEquals("POST", v[0].metodo)
        assertEquals("/api/v1/admin/lista-compra/eventos/7/reglas", v[0].path)
    }

    @Test
    fun borrar_regla_hace_delete() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        val res = r.borrarRegla(7, 3)
        assertIs<ResultadoListaCompra.Exito<*>>(res)
        assertEquals("DELETE", v[0].metodo)
        assertEquals("/api/v1/admin/lista-compra/eventos/7/reglas/3", v[0].path)
    }

    @Test
    fun borrar_regla_de_plantilla_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict, cuerpo = """{"codigo":"REGLA_COMPRA_NO_BORRABLE"}""")
        val res = r.borrarRegla(7, 3)
        assertIs<ResultadoListaCompra.Error>(res)
        assertEquals(CodigoErrorListaCompra.REGLA_NO_BORRABLE, res.codigo)
    }
}
```

- [ ] **Step 5: Escribir la implementación**

`ListaCompraRepositoryImpl.kt` — patrón exacto de `InventarioRepositoryImpl.kt` (mismo `auth()`, mismo `peticion { }` con el mapeo de `ResponseException` → `ErrorResponse.codigo` → `CodigoErrorListaCompra.deCodigoBackend`):

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class ListaCompraRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : ListaCompraRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun lista(eventoId: Long): ResultadoListaCompra<ListaCompraResponse> = peticion {
        http.get("$API_BASE_URL/eventos/$eventoId/lista-compra") { auth() }.body()
    }

    override suspend fun adminEventos(): ResultadoListaCompra<List<EventoListaCompraDto>> = peticion {
        http.get("$API_BASE_URL/admin/lista-compra/eventos") { auth() }.body()
    }

    override suspend fun adminEvento(eventoId: Long): ResultadoListaCompra<ListaCompraAdminResponse> = peticion {
        http.get("$API_BASE_URL/admin/lista-compra/eventos/$eventoId") { auth() }.body()
    }

    override suspend fun ajustarRegla(eventoId: Long, reglaId: Long, cantidadAjustada: Double?, activa: Boolean):
        ResultadoListaCompra<Unit> = peticion {
        http.put("$API_BASE_URL/admin/lista-compra/eventos/$eventoId/reglas/$reglaId") {
            auth(); contentType(ContentType.Application.Json)
            setBody(AjustarReglaBody(cantidadAjustada, activa))
        }.let { }
    }

    override suspend fun crearRegla(eventoId: Long, body: CrearReglaBody): ResultadoListaCompra<ReglaCompraEventoDto> = peticion {
        http.post("$API_BASE_URL/admin/lista-compra/eventos/$eventoId/reglas") {
            auth(); contentType(ContentType.Application.Json); setBody(body)
        }.body()
    }

    override suspend fun borrarRegla(eventoId: Long, reglaId: Long): ResultadoListaCompra<Unit> = peticion {
        http.delete("$API_BASE_URL/admin/lista-compra/eventos/$eventoId/reglas/$reglaId") { auth() }.let { }
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoListaCompra<T> =
        try {
            ResultadoListaCompra.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorListaCompra.deCodigoBackend(codigo)
            ResultadoListaCompra.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoListaCompra.Error(CodigoErrorListaCompra.SIN_CONEXION, CodigoErrorListaCompra.SIN_CONEXION.mensaje)
        }
}
```

- [ ] **Step 6: Registrar en `Dependencias`**

`Dependencias.kt`: añadir `val listaCompraRepo: ListaCompraRepository` a la clase y `listaCompraRepo = ListaCompraRepositoryImpl(http, sesion)` en `crearDependencias` (misma forma que `inventarioRepo`).

- [ ] **Step 7: Ejecutar el test**

Run: `cd mobile && ./gradlew :shared:testDebugUnitTest --tests "com.baniterio.app.data.ListaCompraRepositoryImplTest" 2>&1 | tail -25`
(o `:shared:testAndroidHostTest` según el nombre real de la tarea; ver cómo se lanza `InventarioRepositoryImplTest`.)
Espera: verde.

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/ListaCompraDtos.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoListaCompra.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ListaCompraRepository.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ListaCompraRepositoryImpl.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/Dependencias.kt \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/ListaCompraRepositoryImplTest.kt
git commit -m "feat(lista-compra): móvil — repositorio, DTOs y errores tipados"
```

---

## Task 10: Móvil — pantalla de lectura y navegación

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt` (`ListaCompra`, `ListaCompraAdmin`, `ListaCompraAdminEvento`)
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt` (claves, `aClave`/`claveAScreen`, guardia de arranque, estado `listaCompraEventoId`, ramas `when`)
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt` (parámetro `onListaCompra` + botón)

**Interfaces:**
- Consumes: `ListaCompraRepository.lista` (Task 9).
- Produces: `ListaCompraScreen(listaCompraRepo, eventoId, onVolver)`; `Screen.ListaCompra` etc.

- [ ] **Step 1: Añadir los `Screen`**

`Screen.kt`, junto a `Inventario` / `InventarioCategoria` / `InventarioFiesta`:

```kotlin
    data object ListaCompra : Screen()
    data object ListaCompraAdmin : Screen()
    data object ListaCompraAdminEvento : Screen()
```

- [ ] **Step 2: Escribir `ListaCompraScreen`**

`ui/compra/ListaCompraScreen.kt` — estructura de `ui/inventario/InventarioFiestaScreen.kt` (estado sealed `Cargando`/`Error`/`Cargada`, `LaunchedEffect(intento)`, tarjetas `relieveDeCarta` por categoría). Firma:

```kotlin
@Composable
fun ListaCompraScreen(
    listaCompraRepo: ListaCompraRepository,
    eventoId: Long,
    onVolver: () -> Unit,
)
```

Contenido: cabecera "Lista de la compra" + "{apuntados} apuntados · {diasFiesta} día(s)"; por cada `CategoriaListaCompraDto` una tarjeta con filas `nombre — tamano — cantidad` (formatear `Double` sin decimales sobrantes con un helper `fmt(d: Double)` como en `InventarioFiestaScreen`); si `linea.necesitaFicha`, texto pequeño "necesita ficha de bebida"; si `linea.ajustada`, "ajustado". Estado vacío: "Todavía no hay nada que comprar para este evento."

- [ ] **Step 3: Cablear navegación en `App.kt`**

Seguir el patrón de las tres ramas de inventario ya existentes:
- Constantes `CLAVE_LISTA_COMPRA = "lista-compra"`, `CLAVE_LISTA_COMPRA_ADMIN = "lista-compra-admin"`, `CLAVE_LISTA_COMPRA_ADMIN_EVENTO = "lista-compra-admin-evento"`.
- `aClave()` y `claveAScreen()`: +3 casos.
- Guardia de arranque en frío: añadir `|| screen == Screen.ListaCompra || screen == Screen.ListaCompraAdmin || screen == Screen.ListaCompraAdminEvento` donde ya se listan las de inventario.
- Estado: `var listaCompraEventoId by rememberSaveable { mutableStateOf<Long?>(null) }` (reutilizable por la pantalla de lectura y por el editor admin).
- Rama `when` `Screen.ListaCompra` → `ListaCompraScreen(deps.listaCompraRepo, listaCompraEventoId ?: 0L, onVolver = { ir(Screen.EventoDetalle) })`.
- En la rama `Screen.EventoDetalle`, pasar `onListaCompra = { listaCompraEventoId = id; ir(Screen.ListaCompra) }` a `EventoDetalleScreen` (donde `id` es el `eventoId` que ya usa esa rama).

- [ ] **Step 4: Botón en `EventoDetalleScreen`**

Añadir parámetro `onListaCompra: () -> Unit` a la firma (junto a `onInventarioFiesta`). En el bloque `if (ev.asistencia.ficha.llevaFicha) { ... }`, tras el `Row` de "Inventario de la fiesta", añadir:

```kotlin
        Row {
            OutlinedButton(onClick = onListaCompra, modifier = Modifier.weight(1f)) {
                Text("Lista de la compra", fontWeight = FontWeight.Bold)
            }
        }
```

Nota: hoy el botón "Inventario de la fiesta" solo se pinta dentro de `if (llevaFicha)`. La lista de la compra vale para cualquier evento, pero para no reestructurar la pantalla en esta entrega se deja también bajo `llevaFicha` (San Miguel). Si se quiere en todos los eventos, es un cambio pequeño posterior. **Anotar esta limitación en el commit.**

- [ ] **Step 5: Compilar y test**

Run: `cd mobile && ./gradlew :shared:compileKotlinAndroid :shared:testDebugUnitTest 2>&1 | tail -25`
Espera: verde. Ignorar los diagnósticos rojos del IDE (metadata stdlib); vale lo que diga Gradle.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt
git commit -m "feat(lista-compra): móvil — pantalla de lectura y navegación"
```

---

## Task 11: Móvil — editor de administración

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraAdminScreen.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraAdminEventoScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminIndexScreen.kt` (tarjeta "Cantidades para eventos")
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt` (ramas `when` de las dos pantallas admin)

**Interfaces:**
- Consumes: `ListaCompraRepository.adminEventos/adminEvento/ajustarRegla/crearRegla/borrarRegla` (Task 9).

- [ ] **Step 1: Escribir `ListaCompraAdminScreen`**

`ui/compra/ListaCompraAdminScreen.kt` — estructura de `AdminBebidasScreen`/`InventarioScreen`. Firma:

```kotlin
@Composable
fun ListaCompraAdminScreen(
    listaCompraRepo: ListaCompraRepository,
    onAbrirEvento: (Long) -> Unit,
    onVolver: () -> Unit,
)
```

Carga `adminEventos()` en `LaunchedEffect`, pinta una tarjeta `relieveDeCarta` por evento (nombre + fecha) que llama `onAbrirEvento(evento.id)`. Estados Cargando/Error/Cargada.

- [ ] **Step 2: Escribir `ListaCompraAdminEventoScreen`**

`ui/compra/ListaCompraAdminEventoScreen.kt`. Firma:

```kotlin
@Composable
fun ListaCompraAdminEventoScreen(
    listaCompraRepo: ListaCompraRepository,
    eventoId: Long,
    onVolver: () -> Unit,
)
```

Estado: `EstadoAdmin` sealed (`Cargando`/`Error`/`Cargada(nombre, reglas)`), `guardando`, `aviso`, `dialogoAnadir: Boolean`, borradores de la regla nueva. Por cada `ReglaCompraEventoDto`: fila con `nombre` + `tamano`, `formulaLegible(regla)` (misma tabla de textos que la web — Task 8 Step 2), "calculado: {fmt(cantidadCalculada)}", un `OutlinedTextField` numérico para `cantidadAjustada` (deshabilitado si `regla.tipoFormula in setOf("ALCOHOL_SELECCIONADO","REFRESCO_SELECCIONADO")`), un `Switch` para `activa`, botón "Guardar" → `ajustarRegla(eventoId, regla.id, ajustadaEditada, activaEditada)` y recarga; botón "Quitar" (solo si `regla.origen == "MANUAL"`) → `AlertDialog` de confirmación → `borrarRegla`. Botón "Añadir artículo" abre un `AlertDialog` con campos (categoría con selector, nombre, tamaño, tipo de fórmula con selector limitado a las creables, factor, y `porCada` solo si tipo == `POR_CADA_N_PENISTAS`) → `crearRegla(eventoId, CrearReglaBody(...))`. Tras cualquier operación con éxito, recargar `adminEvento(eventoId)`. En `Error` tipado, `aviso = res.mensaje`.

Helper local `formulaLegible(r: ReglaCompraEventoDto): String` con el mismo `when` sobre `r.tipoFormula` que la web.

- [ ] **Step 3: Tarjeta en `AdminIndexScreen`**

Ver cómo `AdminIndexScreen` decide qué secciones enseñar (probablemente una lista de secciones filtrada por área del usuario, o entradas fijas para admin). Añadir una entrada "Cantidades para eventos" visible si el usuario tiene el área `INVENTARIO` (mirar cómo se comprueba el área en esa pantalla; si hay un `usuario.areas`, comprobar `"INVENTARIO" in areas`). Al pulsar → navega a `Screen.ListaCompraAdmin`.

- [ ] **Step 4: Ramas `when` en `App.kt`**

- `Screen.ListaCompraAdmin` → `ListaCompraAdminScreen(deps.listaCompraRepo, onAbrirEvento = { listaCompraEventoId = it; ir(Screen.ListaCompraAdminEvento) }, onVolver = { ir(Screen.AdminIndex) })` (usar el nombre real de la pantalla índice de admin).
- `Screen.ListaCompraAdminEvento` → `ListaCompraAdminEventoScreen(deps.listaCompraRepo, listaCompraEventoId ?: 0L, onVolver = { ir(Screen.ListaCompraAdmin) })`.
- En `AdminIndexScreen`, pasar el callback que va a `Screen.ListaCompraAdmin`.

- [ ] **Step 5: Compilar + test + build Android**

Run: `cd mobile && ./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug 2>&1 | tail -25`
Espera: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraAdminScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraAdminEventoScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminIndexScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt
git commit -m "feat(lista-compra): móvil — editor Cantidades para eventos"
```

---

## Task 12: Verificación final y cierre de rama

**Files:** ninguno (solo ejecución).

- [ ] **Step 1: Suite de backend**

Run: `cd back && ./mvnw -q verify 2>&1 | tee /tmp/mvn.log | tail -15; echo "MVN_EXIT=${PIPESTATUS[0]}"`
Espera: `MVN_EXIT=0`.

- [ ] **Step 2: Suite de web**

Run: `cd front && npx vitest run 2>&1 | tail -15` y `cd front && npx tsc -p tsconfig.app.json --noEmit 2>&1 | tail -10`
Espera: todo verde, sin errores de tipos.

- [ ] **Step 3: Suite de móvil**

Run: `cd mobile && ./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug 2>&1 | tail -15`
Espera: `BUILD SUCCESSFUL`. Si hay entorno iOS, además `./gradlew :shared:compileKotlinIosSimulatorArm64`.

- [ ] **Step 4: Revisar que no se han colado los ficheros prohibidos**

Run: `git status && git log --oneline main..HEAD`
Verificar que `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/cuentas/CuentaDetalleScreen.kt` y `mobile/shared/src/androidMain/kotlin/com/baniterio/app/data/ApiConfig.android.kt` **no** aparecen en ningún commit de la rama. Si aparecen: `git rebase -i main` y quitarlos, o `git restore --source=main --staged --worktree <fichero>` y `git commit --amend` sobre el commit afectado.

- [ ] **Step 5: Actualizar la memoria del proyecto**

Editar `C:\Users\rober\.claude\projects\c--Users-rober-Documents-Desarrollo-Ba-iterio\memory\baniterio_inventario.md` (o crear `baniterio_lista_compra.md` y enlazarlo desde `MEMORY.md`): dejar constancia de la rama `feature/lista-compra`, V38, `regla_compra` / `regla_compra_evento`, el motor de fórmulas, que es el bloque 1 (sin descuento de inventario de fiesta), la pantalla `/panel/eventos/:id/lista-compra` y "Cantidades para eventos" en administración.

- [ ] **Step 6: Cierre de rama**

Anunciar: "I'm using the finishing-a-development-branch skill to complete this work."
**REQUIRED SUB-SKILL:** Use `superpowers:finishing-a-development-branch` con base `main`. Presentar el menú de 3 opciones y esperar la elección del usuario. Antes, decirle que pruebe a mano: web (botón "Lista de la compra" en San Miguel, y "Cantidades para eventos" en administración con un ajuste + un artículo nuevo) y emulador Android (las mismas dos pantallas).

---

## Self-Review

**1. Cobertura del spec:**
- Tabla `regla_compra` + siembra → Task 1. ✅
- Tabla `regla_compra_evento` + materialización → Task 1 (tabla) + Task 4 (`materializar`). ✅
- 9 tipos de fórmula + redondeo → Task 3. ✅
- Reglas dinámicas alcohol/refresco (1 L si n=1, agrupar por marca, orden alfabético) → Task 3. ✅
- Entradas del cálculo (díasFiesta, apuntados = APUNTADO, díasQueVa con asisteDia1/2, condición bebida, evento sin ficha → 0 + necesitaFicha, EN_DUDA no suma) → Task 4 `datosDe` + Task 3. ✅
- `GET /eventos/{id}/lista-compra` (materializa, agrupa, solo activas, categorías no vacías) → Task 4. ✅
- `GET /admin/lista-compra/eventos` → Task 5. ✅
- `GET /admin/lista-compra/eventos/{id}` (todas las reglas, dinámica como 1 fila) → Task 4 `verAdmin` + Task 5 controller. ✅
- `PUT .../reglas/{id}` (ajuste dinámica → 400) → Task 4/5. ✅
- `POST .../reglas` (validaciones: factor, porCada, dinámica no creable, duplicado) → Task 4/5. ✅
- `DELETE .../reglas/{id}` (PLANTILLA → 409) → Task 4/5. ✅
- Códigos de error nuevos en `ApiExceptionHandler` → Task 2. ✅
- Permisos (ver = cualquiera; admin = área INVENTARIO) → Task 4 (`exigirArea`, `puedoEditar`). ✅
- Web: botón evento → pantalla lectura → Task 7; "Cantidades para eventos" en indice + editor → Task 8. ✅
- Móvil: repo + DTOs + resultado tipado → Task 9; pantalla lectura + nav → Task 10; editor admin + AdminIndex → Task 11. ✅
- Fórmula legible → Task 8 Step 2 (web) y Task 11 Step 2 (móvil). ✅
- Pruebas back (motor unitario + IT), web (service + componentes), móvil (repo) → Tasks 3, 5, 6, 7, 8, 9. ✅
- Bloque 2 explícitamente fuera; `cantidadCalculada` en el DTO para no migrar luego → `LineaCompraDto.cantidadCalculada`, Task 4. ✅

**2. Placeholders:** El IT de Task 5 tiene un test (`crear_regla_manual...`) con una expresión de extracción de id sin terminar y comentario "(extraer el id...)". Es una guía deliberada porque los helpers del IT dependen de la base real de test del proyecto (que el ejecutor debe mirar en `InventarioIT`); los pasos de "ejecutar y ver verde" obligan a completarlo. Aceptable pero señalado aquí.

**3. Consistencia de tipos:**
- `ListaCompraService` métodos usados en Task 5 controller (`eventos`, `verAdmin`, `ajustarRegla`, `crearRegla`, `borrarRegla`) coinciden con los definidos en Task 4. ✅
- DTO `ReglaCompraEventoDto` campos (`cantidadCalculada`, `cantidadAjustada`, `cantidadFinal`, `origen`, `etiqueta`) consistentes entre back (Task 4), web types (Task 6) y móvil DTO (Task 9). ✅
- `TipoFormulaCompra.esDinamica()` definido en Task 1, usado en Tasks 3 y 4. ✅
- Endpoints idénticos en back (Tasks 4-5), web service (Task 6) y móvil repo (Task 9): `/api/v1/eventos/{id}/lista-compra`, `/api/v1/admin/lista-compra/eventos[...]`. ✅
- `CalculadoraListaCompra.lineasDe` / `ceil` / records `PersonaCompra`/`DatosEvento`/`LineaCalculada` definidos en Task 3, usados en Task 4. ✅
- `EventoRepository.noOcultos` definido en Task 4 Step 2, usado en `ListaCompraService.eventos` mismo task. ✅
- Móvil `CodigoErrorListaCompra` mapea los strings de Task 2 (`REGLA_COMPRA_NO_BORRABLE`, etc.). ✅

**4. Alcance:** Un solo plan; los tres frentes (back/web/móvil) son la misma feature y comparten contrato. No se descompone.
