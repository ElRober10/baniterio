# Lista de la compra: "Comprado", devolución y bloqueo — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Marcar líneas de la lista de la compra como "compradas" (pasan al inventario de la fiesta), devolverlas desde ahí sin tocar las sobras del stock, y bloquear la lista para que deje de recalcularse al apuntarse más gente.

**Architecture:** Enfoque A del spec — `linea_compra_evento` es la fuente única de la lista mostrada. Mientras la lista no está bloqueada, cada lectura *sincroniza* esa tabla con el resultado del cálculo (upsert de líneas no compradas, borrado de las que dejan de salir); al bloquear, la sincronización se detiene. Las líneas compradas quedan congeladas y enlazadas a una fila de `articulo_evento`, que gana una columna `cantidad_comprada` separada de la parte de stock.

**Tech Stack:** Spring Boot 3 + Spring Data JPA + Flyway (Postgres) + Lombok; JUnit 5 + `RestTestClient` (IT bajo maven-failsafe, `./mvnw verify`); Angular standalone + signals + `@angular/build:unit-test` (`npx ng test --no-watch --include <dir>`); KMP + Compose Multiplatform + Ktor + kotlinx.serialization (`./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`).

**Spec:** `docs/superpowers/specs/2026-09-10-lista-compra-comprado-bloqueo-design.md`

## Global Constraints

- Java se queda en 17.
- Rama actual: `feature/lista-compra` (este trabajo continúa sobre el bloque 1, todavía sin mergear). No crear rama nueva.
- Peña piloto por `slug = "baniterio"` (`PenaRepository.findBySlug`).
- Área de permiso: `AreaProtegida.INVENTARIO` + `ServicioPermisos.puede(usuarioId, AreaProtegida.INVENTARIO)`; los administradores la tienen implícita.
- `ApiExceptionHandler` (`@RestControllerAdvice`) traduce cada excepción de dominio a `{ "codigo": "<CODE>" }`.
- Los IT comparten una sola BBDD sin rollback: los datos de prueba no deben colisionar y los eventos de prueba van **en el pasado** con `oculto = false` (la lista de la compra solo exige `!oculto`, y así no contaminan la primera página del listado de eventos, que ordena los futuros primero).
- No commitear ni tocar: `mobile/shared/src/androidMain/kotlin/com/baniterio/app/data/ApiConfig.android.kt` ni `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/cuentas/CuentaDetalleScreen.kt` (modificaciones locales ajenas a esta rama).
- Prosa normal en código, comentarios, commits y docs (el modo caveman es solo para el chat).
- Los commits terminan con `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- No pushear ni abrir PR salvo que el usuario lo pida.

## Convención de nombres (usada en todas las tareas)

Backend, paquete `com.baniterio.api.compra` salvo que se diga otra cosa:

- Entidad `LineaCompraEvento` (tabla `linea_compra_evento`).
- Repo `LineaCompraEventoRepository`.
- Campo nuevo en `Evento`: `boolean listaCompraBloqueada` (col `lista_compra_bloqueada`).
- Campo nuevo en `ArticuloEvento` (`com.baniterio.api.inventario`): `BigDecimal cantidadComprada` (col `cantidad_comprada`).
- Excepciones nuevas: `com.baniterio.api.compra.LineaCompraNoEncontradaException`,
  `com.baniterio.api.inventario.NadaQueDevolverException` (ambas `extends RuntimeException`, cuerpo vacío).
- Códigos HTTP: `LINEA_COMPRA_NO_ENCONTRADA` → 404, `NADA_QUE_DEVOLVER` → 400.
- DTO `LineaCompraDto`: `record LineaCompraDto(long id, String nombre, String tamano, BigDecimal cantidad, BigDecimal cantidadCalculada, boolean ajustada, boolean dinamica, boolean necesitaFicha, boolean comprada)`.
- DTO `ListaCompraResponse`: `record (boolean puedoEditar, boolean llevaFicha, boolean bloqueada, int apuntados, int diasFiesta, List<CategoriaListaCompraDto> categorias)`.
- DTO `ListaCompraAdminResponse`: `record (EventoListaCompraDto evento, boolean bloqueada, int apuntados, int diasFiesta, List<ReglaCompraEventoDto> reglas)`.
- DTO `ArticuloDto` (`com.baniterio.api.inventario.dto`): `record ArticuloDto(Long id, String nombre, String tamano, BigDecimal cantidad, BigDecimal cantidadComprada)` — `cantidad` sigue siendo el **total** mostrado.
- DTO nuevo `CambiarBloqueoRequest` (`com.baniterio.api.compra.dto`): `record CambiarBloqueoRequest(boolean bloqueada)`.

Rutas nuevas:

| Método | Ruta | Controlador |
|---|---|---|
| POST | `/api/v1/eventos/{eventoId}/lista-compra/lineas/{lineaId}/comprado` | `ListaCompraController` |
| PUT | `/api/v1/eventos/{eventoId}/lista-compra/bloqueo` | `ListaCompraController` |
| POST | `/api/v1/inventario/evento/{eventoId}/{articuloEventoId}/devolver-a-lista` | `InventarioController` |

Web: `front/src/app/panel/eventos/lista-compra/*` y `front/src/app/panel/inventario/*` + `front/src/app/panel/eventos/inventario-fiesta/*`.

Móvil: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/{data,data/dto,ui/compra,ui/inventario}/*` + `commonTest`.

---

## Task 1: Migración V39 y modelo de datos

**Files:**
- Create: `back/src/main/resources/db/migration/V39__lista_compra_comprado_bloqueo.sql`
- Create: `back/src/main/java/com/baniterio/api/compra/LineaCompraEvento.java`
- Create: `back/src/main/java/com/baniterio/api/compra/LineaCompraEventoRepository.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/Evento.java` (añadir campo)
- Modify: `back/src/main/java/com/baniterio/api/inventario/ArticuloEvento.java` (nullable + campo nuevo)
- Test: `back/src/test/java/com/baniterio/api/compra/LineaCompraEventoRepositoryIT.java`

**Interfaces:**
- Produces:
  - `LineaCompraEvento` con getters/setters Lombok y `@Builder`. Campos: `Long id`, `Evento evento`, `CategoriaInventario categoria`, `String nombre`, `String tamano`, `BigDecimal cantidad`, `int orden`, `boolean dinamica`, `boolean necesitaFicha`, `boolean ajustada`, `boolean comprada`, `Long articuloEventoId`.
  - `LineaCompraEventoRepository`:
    - `List<LineaCompraEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId)`
    - `Optional<LineaCompraEvento> findByIdAndEventoId(Long id, Long eventoId)`
    - `Optional<LineaCompraEvento> findByEventoIdAndArticuloEventoId(Long eventoId, Long articuloEventoId)`
  - `Evento.isListaCompraBloqueada()` / `setListaCompraBloqueada(boolean)`.
  - `ArticuloEvento.getCantidadComprada()` / `setCantidadComprada(BigDecimal)`; `articuloInventario` ahora puede ser `null`.

- [ ] **Step 1: Escribir la migración**

Crea `back/src/main/resources/db/migration/V39__lista_compra_comprado_bloqueo.sql`:

```sql
-- Lista de la compra, bloque 1.5: "comprado" + bloqueo.
--  * evento.lista_compra_bloqueada: al activarlo, la lista deja de recalcularse
--    al apuntarse gente; el admin sigue pudiendo ajustar reglas a mano.
--  * articulo_evento.cantidad_comprada: parte de la fila que se compró desde la
--    lista (sin artículo de origen). articulo_inventario_id pasa a nullable y la
--    clave de fusión pasa a (evento, categoria, nombre, tamano) para que stock y
--    comprado del mismo producto caigan en la misma fila.
--  * linea_compra_evento: la lista mostrada, materializada. Se sincroniza con el
--    cálculo en cada lectura mientras no esté bloqueada; las líneas compradas
--    quedan congeladas.
ALTER TABLE evento ADD COLUMN lista_compra_bloqueada BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE articulo_evento ALTER COLUMN articulo_inventario_id DROP NOT NULL;
ALTER TABLE articulo_evento ADD COLUMN cantidad_comprada NUMERIC(8, 2) NOT NULL DEFAULT 0;
ALTER TABLE articulo_evento ADD CONSTRAINT ck_articulo_evento_comprada CHECK (cantidad_comprada >= 0);
ALTER TABLE articulo_evento DROP CONSTRAINT uq_articulo_evento;
ALTER TABLE articulo_evento ADD CONSTRAINT uq_articulo_evento_producto
    UNIQUE (evento_id, categoria, nombre, tamano);

CREATE TABLE linea_compra_evento (
    id                 BIGINT        GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    evento_id          BIGINT        NOT NULL REFERENCES evento (id) ON DELETE CASCADE,
    categoria          VARCHAR(20)   NOT NULL,
    nombre             VARCHAR(120)  NOT NULL,
    tamano             VARCHAR(20)   NOT NULL,
    cantidad           NUMERIC(8, 2) NOT NULL,
    orden              INT           NOT NULL DEFAULT 0,
    dinamica           BOOLEAN       NOT NULL DEFAULT FALSE,
    necesita_ficha     BOOLEAN       NOT NULL DEFAULT FALSE,
    ajustada           BOOLEAN       NOT NULL DEFAULT FALSE,
    comprada           BOOLEAN       NOT NULL DEFAULT FALSE,
    articulo_evento_id BIGINT        REFERENCES articulo_evento (id) ON DELETE SET NULL,
    CONSTRAINT ck_lce_categoria
        CHECK (categoria IN ('ALCOHOL', 'CERVEZA', 'REFRESCOS', 'LIMPIEZA', 'COMIDA')),
    CONSTRAINT ck_lce_cantidad CHECK (cantidad >= 0),
    CONSTRAINT uq_lce UNIQUE (evento_id, categoria, nombre, tamano)
);
CREATE INDEX ix_lce_evento ON linea_compra_evento (evento_id);
```

- [ ] **Step 2: Añadir el campo a `Evento`**

En `back/src/main/java/com/baniterio/api/identidad/Evento.java`, junto al resto de columnas `boolean` (p. ej. `oculto`), añade:

```java
    @Column(name = "lista_compra_bloqueada", nullable = false)
    private boolean listaCompraBloqueada;
```

(La clase ya usa Lombok `@Getter @Setter`; no hace falta más. Si la clase tiene `@Builder` y un builder por defecto, `boolean` arranca en `false`, que es lo que queremos.)

- [ ] **Step 3: Aflojar `ArticuloEvento` y añadir `cantidadComprada`**

En `back/src/main/java/com/baniterio/api/inventario/ArticuloEvento.java`:

```java
    @ManyToOne(fetch = FetchType.LAZY)                 // antes: optional = false
    @JoinColumn(name = "articulo_inventario_id")       // antes: nullable = false
    private ArticuloInventario articuloInventario;
```

y añade tras `cantidad`:

```java
    @Column(name = "cantidad_comprada", nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidadComprada;
```

Actualiza el Javadoc de la clase: la fila puede tener parte de stock (`cantidad`, con `articuloInventario`) y parte comprada desde la lista (`cantidadComprada`, sin origen); una fila por `(evento, categoria, nombre, tamano)`.

- [ ] **Step 4: Crear la entidad `LineaCompraEvento`**

`back/src/main/java/com/baniterio/api/compra/LineaCompraEvento.java` (copia el estilo de `ReglaCompraEvento.java`):

```java
package com.baniterio.api.compra;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Evento;
import com.baniterio.api.inventario.CategoriaInventario;

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
 * Una línea de la lista de la compra mostrada de un evento (tabla
 * {@code linea_compra_evento}, ver V39). Se sincroniza con el resultado del
 * cálculo en cada lectura mientras la lista no está bloqueada; una vez
 * {@code comprada}, queda congelada y enlazada a una fila de
 * {@code articulo_evento} por {@code articuloEventoId}.
 */
@Entity
@Table(name = "linea_compra_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineaCompraEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

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

    @Column(nullable = false)
    private boolean dinamica;

    @Column(name = "necesita_ficha", nullable = false)
    private boolean necesitaFicha;

    @Column(nullable = false)
    private boolean ajustada;

    @Column(nullable = false)
    private boolean comprada;

    @Column(name = "articulo_evento_id")
    private Long articuloEventoId;
}
```

- [ ] **Step 5: Crear el repositorio**

`back/src/main/java/com/baniterio/api/compra/LineaCompraEventoRepository.java`:

```java
package com.baniterio.api.compra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link LineaCompraEvento} (la lista de la compra mostrada). */
public interface LineaCompraEventoRepository extends JpaRepository<LineaCompraEvento, Long> {

    List<LineaCompraEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId);

    Optional<LineaCompraEvento> findByIdAndEventoId(Long id, Long eventoId);

    Optional<LineaCompraEvento> findByEventoIdAndArticuloEventoId(Long eventoId, Long articuloEventoId);
}
```

- [ ] **Step 6: Escribir el test de repositorio (falla)**

`back/src/test/java/com/baniterio/api/compra/LineaCompraEventoRepositoryIT.java` (copia el estilo de `ReglaCompraRepositoryIT.java` — `extends IntegrationTest`, autowire de repos):

```java
package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baniterio.api.identidad.Cuenta;
import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.Evento;
import com.baniterio.api.identidad.EventoRepository;
import com.baniterio.api.identidad.PenaRepository;
import com.baniterio.api.inventario.CategoriaInventario;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LineaCompraEventoRepositoryIT extends IntegrationTest {

    @Autowired LineaCompraEventoRepository lineas;
    @Autowired EventoRepository eventos;
    @Autowired CuentaRepository cuentas;
    @Autowired PenaRepository penas;

    @Test
    void guarda_y_recupera_una_linea_del_evento() {
        var pena = penas.findBySlug("baniterio").orElseThrow();
        Cuenta c = cuentas.save(Cuenta.builder().pena(pena).nombre("LCE cuenta")
                .llevaFichaBebida(false).build());
        Evento e = eventos.save(Evento.builder().pena(pena).cuenta(c).nombre("LCE evento")
                .fecha(LocalDate.now().minusDays(90)).oculto(false).build());

        assertThat(e.isListaCompraBloqueada()).isFalse();

        lineas.save(LineaCompraEvento.builder()
                .evento(e).categoria(CategoriaInventario.COMIDA).nombre("Picos").tamano("paquete")
                .cantidad(new BigDecimal("4.00")).orden(1).build());

        var guardadas = lineas.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(e.getId());
        assertThat(guardadas).singleElement()
                .satisfies(l -> {
                    assertThat(l.getNombre()).isEqualTo("Picos");
                    assertThat(l.isComprada()).isFalse();
                    assertThat(l.getArticuloEventoId()).isNull();
                });
    }
}
```

- [ ] **Step 7: Compilar el resto de callers de `ArticuloDto`**

El `record ArticuloDto` gana un parámetro en el Task 3, pero al aflojar `ArticuloEvento` ahora, revisa que compila:

Run: `cd back && ./mvnw -q -DskipTests compile`
Expected: compila. Si `ArticuloEvento.builder()...cantidad(...)` en `InventarioService` no pone `cantidadComprada`, el `NOT NULL` de BBDD lo rompería en runtime — se arregla en Task 5, pero para que compile ahora basta con que el builder acepte el campo (Lombok lo genera). No toques `InventarioService` todavía.

- [ ] **Step 8: Ejecutar el test (debe pasar)**

Run: `cd back && ./mvnw -q -Dit.test=LineaCompraEventoRepositoryIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (1 test). Flyway aplica V39 sobre el Testcontainer.

- [ ] **Step 9: Commit**

```bash
git add back/src/main/resources/db/migration/V39__lista_compra_comprado_bloqueo.sql \
        back/src/main/java/com/baniterio/api/compra/LineaCompraEvento.java \
        back/src/main/java/com/baniterio/api/compra/LineaCompraEventoRepository.java \
        back/src/main/java/com/baniterio/api/identidad/Evento.java \
        back/src/main/java/com/baniterio/api/inventario/ArticuloEvento.java \
        back/src/test/java/com/baniterio/api/compra/LineaCompraEventoRepositoryIT.java
git commit -m "feat(compra): V39 — linea_compra_evento, bloqueo y cantidad comprada

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: Sincronización y lectura desde `linea_compra_evento`

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/compra/ListaCompraService.java` (nueva dependencia + `sincronizar` + reescribir `verLista` + `bloqueada` en `verAdmin`)
- Modify: `back/src/main/java/com/baniterio/api/compra/dto/LineaCompraDto.java` (`+ long id`, `+ boolean comprada`)
- Modify: `back/src/main/java/com/baniterio/api/compra/dto/ListaCompraResponse.java` (`+ boolean bloqueada`)
- Modify: `back/src/main/java/com/baniterio/api/compra/dto/ListaCompraAdminResponse.java` (`+ boolean bloqueada`)
- Test: `back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java` (nuevo)

**Interfaces:**
- Consumes: `LineaCompraEventoRepository` (Task 1), `CalculadoraListaCompra.lineasDe` / `.ceil` (bloque 1, sin cambios).
- Produces:
  - `ListaCompraService.sincronizar(Evento e, DatosEvento datos)` — `private`, `@Transactional` (heredado del método público que lo llama).
  - `verLista` devuelve `ListaCompraResponse` con `bloqueada` y líneas con `id`/`comprada`.
  - Registro interno `LineaCalc` (abajo) para pasar el resultado del cálculo con su clave.

- [ ] **Step 1: Actualizar los DTOs**

`LineaCompraDto.java`:

```java
public record LineaCompraDto(long id, String nombre, String tamano, BigDecimal cantidad,
                             BigDecimal cantidadCalculada, boolean ajustada, boolean dinamica,
                             boolean necesitaFicha, boolean comprada) {
}
```

`ListaCompraResponse.java`:

```java
public record ListaCompraResponse(boolean puedoEditar, boolean llevaFicha, boolean bloqueada,
                                  int apuntados, int diasFiesta, List<CategoriaListaCompraDto> categorias) {
}
```

`ListaCompraAdminResponse.java`:

```java
public record ListaCompraAdminResponse(EventoListaCompraDto evento, boolean bloqueada, int apuntados,
                                       int diasFiesta, List<ReglaCompraEventoDto> reglas) {
}
```

- [ ] **Step 2: Escribir el test de sincronización (falla)**

`back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java`. Reutiliza los helpers del estilo de `ListaCompraIT` (crea su propio evento en el pasado, `oculto=false`, con `Cuenta.llevaFichaBebida(false)`; token vía `POST /api/v1/auth/login`). Copia `token(...)`, `pena()`, `crearEventoDeUnDia(...)`, `getMap(...)`, `cantidadLinea(...)` de `ListaCompraIT.java` (o extrae a un helper común si el revisor lo prefiere; de momento duplicar es aceptable y es lo que hace el repo). Añade un helper para apuntar gente:

```java
    // Autowire extra:
    @Autowired com.baniterio.api.identidad.AsistenciaEventoRepository asistencias;
    @Autowired LineaCompraEventoRepository lineas;

    void apuntar(long eventoId, int cuantos) {
        var evento = eventos.findById(eventoId).orElseThrow();
        for (int i = 0; i < cuantos; i++) {
            String tel = "6" + String.format("%08d",
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000));
            var u = usuarios.save(com.baniterio.api.identidad.Usuario.builder()
                    .telefono(tel).email(tel + "@sync.test")
                    .passwordHash(passwordEncoder.encode("secreto1"))
                    .nombre("S" + tel).apellidos("Y").esSuperadmin(false).activo(true).build());
            asistencias.save(com.baniterio.api.identidad.AsistenciaEvento.builder()
                    .evento(evento).usuario(u)
                    .estado(com.baniterio.api.identidad.EstadoAsistencia.APUNTADO).build());
        }
    }
```

> Nota: comprueba la firma real de `AsistenciaEvento.builder()` en
> `back/src/main/java/com/baniterio/api/identidad/AsistenciaEvento.java` antes de escribir esto
> (campos: evento, usuario, estado — y quizá `origen`/`fecha`). Ajusta el builder a lo que exista.

Tests:

```java
    @Test
    void la_lectura_materializa_las_lineas_y_las_actualiza_al_cambiar_los_apuntados() {
        long eventoId = crearEventoDeUnDia("Sync IT materializa");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 10);

        var body1 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        // Platos = POR_PENISTA factor 3 -> 30 con 10 apuntados
        assertThat(cantidadLinea(body1, "LIMPIEZA", "Platos")).isEqualTo(30.0);

        apuntar(eventoId, 10); // ahora 20
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "LIMPIEZA", "Platos")).isEqualTo(60.0);
    }

    @Test
    void quitar_una_regla_borra_su_linea_no_comprada() {
        long eventoId = crearEventoDeUnDia("Sync IT borra");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 5);
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin); // materializa

        // desactiva la regla de Platos
        var reglas = reglasAdmin(eventoId, admin);
        long platosRegla = idDeRegla(reglas, "Platos");
        http.put().uri("/api/v1/admin/lista-compra/eventos/" + eventoId + "/reglas/" + platosRegla)
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("activa", false))
                .exchange().expectStatus().isNoContent();

        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isNull();
    }

    @Test
    void la_respuesta_trae_bloqueada_false_y_las_lineas_traen_id() {
        long eventoId = crearEventoDeUnDia("Sync IT campos");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 3);
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(body.get("bloqueada")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        var cats = (List<Map<String, Object>>) body.get("categorias");
        @SuppressWarnings("unchecked")
        var lineas0 = (List<Map<String, Object>>) cats.get(0).get("lineas");
        assertThat(lineas0.get(0)).containsKeys("id", "comprada");
    }
```

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraSyncIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (compila el DTO nuevo pero `verLista` todavía calcula al vuelo sin persistir → los ids no existen / o el test de "quitar regla" pasa por casualidad; el de `id`/`comprada` falla porque el DTO viejo no está en la respuesta hasta recompilar; en la práctica: fallo de aserción).

- [ ] **Step 3: Añadir la dependencia y el registro auxiliar**

En `ListaCompraService.java`, añade al constructor `LineaCompraEventoRepository lineas` (y el campo `private final`). Añade un `record` privado dentro de la clase:

```java
    /** Una línea del cálculo con su clave de identidad. */
    private record LineaCalc(CategoriaInventario categoria, String nombre, String tamano,
                             BigDecimal cantidad, int orden, boolean dinamica,
                             boolean necesitaFicha, boolean ajustada) {
    }
```

- [ ] **Step 4: Extraer el cálculo a un método que devuelva `List<LineaCalc>`**

Añade a `ListaCompraService`:

```java
    /** Todas las líneas que el cálculo produce ahora mismo para el evento. */
    private List<LineaCalc> calcular(Long eventoId, DatosEvento datos) {
        List<ReglaCompraEvento> activas = reglasEvento
                .findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                .filter(ReglaCompraEvento::isActiva).toList();
        List<LineaCalc> out = new ArrayList<>();
        for (ReglaCompraEvento r : activas) {
            for (LineaCalculada lc : CalculadoraListaCompra.lineasDe(r, datos)) {
                BigDecimal calculada = CalculadoraListaCompra.ceil(lc.bruto());
                boolean ajustada = !r.getTipoFormula().esDinamica() && r.getCantidadAjustada() != null;
                BigDecimal cantidad = ajustada ? r.getCantidadAjustada() : calculada;
                out.add(new LineaCalc(r.getCategoria(), lc.nombre(), lc.tamano(), cantidad,
                        r.getOrden(), lc.dinamica(), lc.necesitaFicha(), ajustada));
            }
        }
        return out;
    }
```

- [ ] **Step 5: Escribir `sincronizar`**

```java
    /** Alinea linea_compra_evento con el cálculo actual. No toca las líneas compradas. */
    private void sincronizar(Evento e, DatosEvento datos) {
        List<LineaCalc> calculadas = calcular(e.getId(), datos);
        Map<String, LineaCompraEvento> existentes = new LinkedHashMap<>();
        for (LineaCompraEvento l : lineas.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(e.getId())) {
            existentes.put(clave(l.getCategoria(), l.getNombre(), l.getTamano()), l);
        }
        Set<String> vistas = new HashSet<>();
        for (LineaCalc c : calculadas) {
            String k = clave(c.categoria(), c.nombre(), c.tamano());
            vistas.add(k);
            LineaCompraEvento fila = existentes.get(k);
            if (fila == null) {
                lineas.save(LineaCompraEvento.builder()
                        .evento(e).categoria(c.categoria()).nombre(c.nombre()).tamano(c.tamano())
                        .cantidad(c.cantidad()).orden(c.orden()).dinamica(c.dinamica())
                        .necesitaFicha(c.necesitaFicha()).ajustada(c.ajustada())
                        .comprada(false).build());
            } else if (!fila.isComprada()) {
                fila.setCantidad(c.cantidad());
                fila.setOrden(c.orden());
                fila.setDinamica(c.dinamica());
                fila.setNecesitaFicha(c.necesitaFicha());
                fila.setAjustada(c.ajustada());
                lineas.save(fila);
            }
        }
        for (Map.Entry<String, LineaCompraEvento> en : existentes.entrySet()) {
            if (!vistas.contains(en.getKey()) && !en.getValue().isComprada()) {
                lineas.delete(en.getValue());
            }
        }
    }

    private static String clave(CategoriaInventario cat, String nombre, String tamano) {
        return cat.name() + "\u0000" + nombre + "\u0000" + tamano;
    }
```

Añade los imports que falten: `java.util.HashSet`, `java.util.Set`.

- [ ] **Step 6: Reescribir `verLista`**

```java
    @Transactional
    public ListaCompraResponse verLista(Long usuarioId, Long eventoId) {
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        DatosEvento datos = datosDe(e);
        if (!e.isListaCompraBloqueada()) {
            sincronizar(e, datos);
        }
        boolean puedoEditar = permisos.puede(usuarioId, AreaProtegida.INVENTARIO);

        Map<CategoriaInventario, List<LineaCompraDto>> porCategoria = new LinkedHashMap<>();
        for (CategoriaInventario cat : CategoriaInventario.values()) {
            porCategoria.put(cat, new ArrayList<>());
        }
        for (LineaCompraEvento l : lineas.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId)) {
            porCategoria.get(l.getCategoria()).add(new LineaCompraDto(
                    l.getId(), l.getNombre(), l.getTamano(), l.getCantidad(), BigDecimal.ZERO,
                    l.isAjustada(), l.isDinamica(), l.isNecesitaFicha(), l.isComprada()));
        }

        List<CategoriaListaCompraDto> categorias = new ArrayList<>();
        porCategoria.forEach((cat, ls) -> {
            if (!ls.isEmpty()) {
                categorias.add(new CategoriaListaCompraDto(cat.name(), cat.etiqueta(), ls));
            }
        });
        return new ListaCompraResponse(puedoEditar, datos.llevaFicha(), e.isListaCompraBloqueada(),
                datos.apuntados(), datos.diasFiesta(), categorias);
    }
```

Borra el bloque viejo de `verLista` que iteraba reglas y llamaba a `CalculadoraListaCompra` directamente (ahora vive en `calcular`). El import de `LineaCalculada` / `PersonaCompra` puede quedar si `datosDe` lo usa; deja solo lo necesario.

- [ ] **Step 7: `bloqueada` en `verAdmin`**

En `verAdmin`, cambia el `return`:

```java
        return new ListaCompraAdminResponse(
                new EventoListaCompraDto(e.getId(), e.getNombre(), e.getFecha(), e.getFechaFin()),
                e.isListaCompraBloqueada(), datos.apuntados(), datos.diasFiesta(), reglas);
```

- [ ] **Step 8: Arreglar `ListaCompraIT` existente**

`ListaCompraIT.cantidadLinea` ya lee `l.get("cantidad")` — sigue valiendo. Pero los tests que comprueban la forma de la respuesta pueden asumir el orden viejo de campos; revisa `lectura_materializa_las_reglas_y_agrupa_por_categoria` y `crear_regla_manual_aparece_en_la_lectura_y_se_puede_borrar` — deben seguir pasando porque `cantidadLinea` navega por nombre. Ejecuta:

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraIT,ListaCompraSyncIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (todos).

- [ ] **Step 9: Suite de compra completa**

Run: `cd back && ./mvnw -q -Dtest='com.baniterio.api.compra.*' -Dit.test='com.baniterio.api.compra.*IT' verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `CalculadoraListaCompraTest` no cambia.

- [ ] **Step 10: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/ListaCompraService.java \
        back/src/main/java/com/baniterio/api/compra/dto/LineaCompraDto.java \
        back/src/main/java/com/baniterio/api/compra/dto/ListaCompraResponse.java \
        back/src/main/java/com/baniterio/api/compra/dto/ListaCompraAdminResponse.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraIT.java
git commit -m "feat(compra): la lista se materializa y sincroniza en linea_compra_evento

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: Marcar comprada → inventario de la fiesta

**Files:**
- Create: `back/src/main/java/com/baniterio/api/compra/LineaCompraNoEncontradaException.java`
- Modify: `back/src/main/java/com/baniterio/api/compra/ListaCompraService.java` (`marcarComprada`)
- Modify: `back/src/main/java/com/baniterio/api/compra/ListaCompraController.java` (endpoint POST)
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java` (`LINEA_COMPRA_NO_ENCONTRADA`)
- Modify: `back/src/main/java/com/baniterio/api/inventario/ArticuloEventoRepository.java` (finder por producto)
- Modify: `back/src/main/java/com/baniterio/api/inventario/dto/ArticuloDto.java` (`+ cantidadComprada`)
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioService.java` (`verEvento` mapea el total + `cantidadComprada`; `crear`/`moverAlEvento` inicializan `cantidadComprada = ZERO`)
- Test: `back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java` (añadir tests)

**Interfaces:**
- Consumes: `LineaCompraEventoRepository.findByIdAndEventoId` (Task 1), `ArticuloEventoRepository` (ampliado aquí).
- Produces:
  - `ListaCompraService.marcarComprada(Long usuarioId, Long eventoId, Long lineaId)` → `void`, `@Transactional`, área INVENTARIO.
  - `ArticuloEventoRepository.findByEventoIdAndCategoriaAndNombreAndTamano(Long eventoId, CategoriaInventario categoria, String nombre, String tamano)`.
  - `ArticuloDto` con 5 componentes; helper estático nuevo `deEvento(ArticuloEvento f)`.

- [ ] **Step 1: Excepción + handler**

`LineaCompraNoEncontradaException.java`:

```java
package com.baniterio.api.compra;

/** La línea de la lista de la compra no existe o no es de ese evento. */
public class LineaCompraNoEncontradaException extends RuntimeException {
}
```

En `ApiExceptionHandler.java`, tras `formulaNoCreable()`:

```java
    @ExceptionHandler(com.baniterio.api.compra.LineaCompraNoEncontradaException.class)
    ResponseEntity<Map<String, Object>> lineaCompraNoEncontrada() {
        return error(HttpStatus.NOT_FOUND, "LINEA_COMPRA_NO_ENCONTRADA");
    }
```

- [ ] **Step 2: Ampliar `ArticuloEventoRepository`**

```java
    Optional<ArticuloEvento> findByEventoIdAndCategoriaAndNombreAndTamano(
            Long eventoId, com.baniterio.api.inventario.CategoriaInventario categoria,
            String nombre, String tamano);
```

- [ ] **Step 3: `ArticuloDto` con `cantidadComprada`**

```java
package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import com.baniterio.api.inventario.ArticuloEvento;
import com.baniterio.api.inventario.ArticuloInventario;

/** Un artículo en el JSON. En el inventario de la fiesta {@code cantidad} es el total
 *  (stock + comprado) y {@code cantidadComprada} la parte venida de la lista de la compra. */
public record ArticuloDto(Long id, String nombre, String tamano, BigDecimal cantidad,
                          BigDecimal cantidadComprada) {

    public static ArticuloDto de(ArticuloInventario a) {
        return new ArticuloDto(a.getId(), a.getNombre(), a.getTamano(), a.getCantidad(), BigDecimal.ZERO);
    }

    public static ArticuloDto deEvento(ArticuloEvento f) {
        return new ArticuloDto(f.getId(), f.getNombre(), f.getTamano(),
                f.getCantidad().add(f.getCantidadComprada()), f.getCantidadComprada());
    }
}
```

- [ ] **Step 4: `InventarioService` — usar `deEvento` y no romper `cantidadComprada` NOT NULL**

En `verEvento`, cambia el `.map(f -> new ArticuloDto(...))` por `.map(ArticuloDto::deEvento)`.

En `crear` (alta de `ArticuloInventario`) no hace falta nada — eso es `articulo_inventario`, no tiene la columna.

En `moverAlEvento`, cuando construye la fila nueva `ArticuloEvento.builder()...`, añade `.cantidadComprada(BigDecimal.ZERO)`.

Run: `cd back && ./mvnw -q -DskipTests compile`
Expected: compila (todos los `new ArticuloDto(...)` con 4 args deben migrarse a los helpers o al 5º arg; busca con `grep -rn "new ArticuloDto(" back/src/main`).

- [ ] **Step 5: Escribir los tests (fallan)**

En `ListaCompraSyncIT.java` añade un helper para leer líneas con id y otro para el inventario de la fiesta, y los tests:

```java
    @SuppressWarnings("unchecked")
    long idLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) continue;
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) return ((Number) l.get("id")).longValue();
            }
        }
        throw new AssertionError("línea no encontrada: " + categoria + "/" + nombre);
    }

    @SuppressWarnings("unchecked")
    Double cantidadFiesta(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> a : (List<Map<String, Object>>) cat.get("articulos")) {
                if (nombre.equals(a.get("nombre"))) return ((Number) a.get("cantidad")).doubleValue();
            }
        }
        return null;
    }

    @Test
    void marcar_comprada_mueve_la_cantidad_al_inventario_de_la_fiesta() {
        long eventoId = crearEventoDeUnDia("Sync IT comprado");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4); // Platos = 12
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        long platosLinea = idLinea(body, "LIMPIEZA", "Platos");

        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        var fiesta = getMap("/api/v1/inventario/evento/" + eventoId, admin);
        assertThat(cantidadFiesta(fiesta, "Platos")).isEqualTo(12.0);

        // la línea aparece como comprada en la lectura
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(compradaLinea(body2, "LIMPIEZA", "Platos")).isTrue();
    }

    @SuppressWarnings("unchecked")
    Boolean compradaLinea(Map<String, Object> body, String categoria, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            if (!categoria.equals(cat.get("categoria"))) continue;
            for (Map<String, Object> l : (List<Map<String, Object>>) cat.get("lineas")) {
                if (nombre.equals(l.get("nombre"))) return (Boolean) l.get("comprada");
            }
        }
        return null;
    }

    @Test
    void una_linea_comprada_no_cambia_aunque_cambien_los_apuntados() {
        long eventoId = crearEventoDeUnDia("Sync IT congela");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4); // Platos = 12
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNoContent();

        apuntar(eventoId, 10); // ahora 14 -> Platos calcularía 42
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void marcar_comprada_una_linea_de_otro_evento_es_404() {
        long eventoId = crearEventoDeUnDia("Sync IT 404");
        String admin = token(RolMembresia.ADMIN, false);
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/99999999/comprado")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("LINEA_COMPRA_NO_ENCONTRADA");
    }
```

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraSyncIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (no existe el endpoint `/comprado`).

- [ ] **Step 6: `marcarComprada` en el servicio**

```java
    @Transactional
    public void marcarComprada(Long usuarioId, Long eventoId, Long lineaId) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        LineaCompraEvento linea = lineas.findByIdAndEventoId(lineaId, eventoId)
                .orElseThrow(LineaCompraNoEncontradaException::new);
        if (linea.isComprada()) {
            return;
        }
        ArticuloEvento fila = articulosEvento
                .findByEventoIdAndCategoriaAndNombreAndTamano(
                        eventoId, linea.getCategoria(), linea.getNombre(), linea.getTamano())
                .orElse(null);
        if (fila == null) {
            int orden = articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(eventoId).stream()
                    .filter(f -> f.getCategoria() == linea.getCategoria())
                    .mapToInt(ArticuloEvento::getOrden).max().orElse(0) + 1;
            fila = ArticuloEvento.builder()
                    .evento(e).articuloInventario(null).categoria(linea.getCategoria())
                    .nombre(linea.getNombre()).tamano(linea.getTamano())
                    .cantidad(BigDecimal.ZERO).cantidadComprada(linea.getCantidad())
                    .orden(orden).build();
        } else {
            fila.setCantidadComprada(fila.getCantidadComprada().add(linea.getCantidad()));
        }
        fila = articulosEvento.save(fila);
        linea.setComprada(true);
        linea.setArticuloEventoId(fila.getId());
        lineas.save(linea);
    }
```

Nueva dependencia en `ListaCompraService`: `ArticuloEventoRepository articulosEvento` (inyéctalo en el constructor).

- [ ] **Step 7: Endpoint en `ListaCompraController`**

```java
    @PostMapping("/{eventoId}/lista-compra/lineas/{lineaId}/comprado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void comprado(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long lineaId) {
        service.marcarComprada(principal.id(), eventoId, lineaId);
    }
```

Imports: `org.springframework.http.HttpStatus`, `PostMapping`, `ResponseStatus`.

- [ ] **Step 8: Ejecutar los tests (pasan)**

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraSyncIT,ListaCompraIT,InventarioIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `InventarioIT` puede fallar si algún test compara el JSON del artículo con 4 campos exactos — arréglalo aceptando el 5º (`cantidadComprada`).

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/LineaCompraNoEncontradaException.java \
        back/src/main/java/com/baniterio/api/compra/ListaCompraService.java \
        back/src/main/java/com/baniterio/api/compra/ListaCompraController.java \
        back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java \
        back/src/main/java/com/baniterio/api/inventario/ArticuloEventoRepository.java \
        back/src/main/java/com/baniterio/api/inventario/dto/ArticuloDto.java \
        back/src/main/java/com/baniterio/api/inventario/InventarioService.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java \
        back/src/test/java/com/baniterio/api/inventario/InventarioIT.java
git commit -m "feat(compra): marcar una linea como comprada la envia al inventario de la fiesta

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: Bloquear / desbloquear la lista

**Files:**
- Create: `back/src/main/java/com/baniterio/api/compra/dto/CambiarBloqueoRequest.java`
- Modify: `back/src/main/java/com/baniterio/api/compra/ListaCompraService.java` (`cambiarBloqueo`)
- Modify: `back/src/main/java/com/baniterio/api/compra/ListaCompraController.java` (endpoint PUT)
- Test: `back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java` (añadir tests)

**Interfaces:**
- Produces: `ListaCompraService.cambiarBloqueo(Long usuarioId, Long eventoId, boolean bloqueada)` → `void`, `@Transactional`, área INVENTARIO.

- [ ] **Step 1: Request DTO**

```java
package com.baniterio.api.compra.dto;

/** Cuerpo de {@code PUT /api/v1/eventos/{id}/lista-compra/bloqueo}. */
public record CambiarBloqueoRequest(boolean bloqueada) {
}
```

- [ ] **Step 2: Test (falla)**

```java
    @Test
    void bloquear_detiene_la_sincronizacion() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4); // Platos = 12
        getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin); // materializa

        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();

        apuntar(eventoId, 8); // 12 personas -> Platos calcularía 36
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(body.get("bloqueada")).isEqualTo(true);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);

        // desbloquear reanuda
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", false))
                .exchange().expectStatus().isNoContent();
        var body2 = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body2, "LIMPIEZA", "Platos")).isEqualTo(36.0);
    }

    @Test
    void bloquear_sincroniza_una_ultima_vez() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo sync");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4);
        // NO se lee la lista antes: al bloquear debe materializar el estado actual
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + admin)
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isNoContent();
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @Test
    void bloquear_sin_area_es_403() {
        long eventoId = crearEventoDeUnDia("Sync IT bloqueo 403");
        http.put().uri("/api/v1/eventos/" + eventoId + "/lista-compra/bloqueo")
                .header(AUTHORIZATION, "Bearer " + token(RolMembresia.MIEMBRO, false))
                .body(Map.of("bloqueada", true))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO_INVENTARIO");
    }
```

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraSyncIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (no existe el endpoint).

- [ ] **Step 3: `cambiarBloqueo` en el servicio**

```java
    @Transactional
    public void cambiarBloqueo(Long usuarioId, Long eventoId, boolean bloqueada) {
        exigirArea(usuarioId);
        Evento e = eventoAbierto(eventoId);
        materializar(e);
        if (bloqueada && !e.isListaCompraBloqueada()) {
            sincronizar(e, datosDe(e));
        }
        e.setListaCompraBloqueada(bloqueada);
        eventos.save(e);
    }
```

- [ ] **Step 4: Endpoint**

```java
    @PutMapping("/{eventoId}/lista-compra/bloqueo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bloqueo(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @RequestBody CambiarBloqueoRequest req) {
        service.cambiarBloqueo(principal.id(), eventoId, req.bloqueada());
    }
```

Imports: `PutMapping`, `RequestBody`, `com.baniterio.api.compra.dto.CambiarBloqueoRequest`.

- [ ] **Step 5: Ejecutar (pasa)**

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraSyncIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/compra/dto/CambiarBloqueoRequest.java \
        back/src/main/java/com/baniterio/api/compra/ListaCompraService.java \
        back/src/main/java/com/baniterio/api/compra/ListaCompraController.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java
git commit -m "feat(compra): bloquear la lista de la compra congela el auto-calculo

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: Devolver a la lista + ajustar devolución/envío de stock

**Files:**
- Create: `back/src/main/java/com/baniterio/api/inventario/NadaQueDevolverException.java`
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioService.java` (`devolverALista`, ajustar `devolver` y `moverAlEvento`, nueva dependencia)
- Modify: `back/src/main/java/com/baniterio/api/inventario/InventarioController.java` (endpoint POST `devolver-a-lista`)
- Modify: `back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java` (`NADA_QUE_DEVOLVER`)
- Modify: `back/src/main/java/com/baniterio/api/inventario/ArticuloEventoRepository.java` (ya tiene el finder del Task 3)
- Test: `back/src/test/java/com/baniterio/api/inventario/InventarioIT.java` + `back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java`

**Interfaces:**
- Consumes: `LineaCompraEventoRepository.findByEventoIdAndArticuloEventoId` (Task 1).
- Produces: `InventarioService.devolverALista(Long usuarioId, Long eventoId, Long articuloEventoId)` → `void`, `@Transactional`, área INVENTARIO.

- [ ] **Step 1: Excepción + handler**

```java
package com.baniterio.api.inventario;

/** La fila del inventario de la fiesta no tiene parte comprada que devolver a la lista. */
public class NadaQueDevolverException extends RuntimeException {
}
```

`ApiExceptionHandler`, tras `nadaQueEnviar()`:

```java
    @ExceptionHandler(com.baniterio.api.inventario.NadaQueDevolverException.class)
    ResponseEntity<Map<String, Object>> nadaQueDevolver() {
        return error(HttpStatus.BAD_REQUEST, "NADA_QUE_DEVOLVER");
    }
```

- [ ] **Step 2: Tests (fallan)**

En `ListaCompraSyncIT.java` (tiene ya el contexto de líneas):

```java
    @Test
    void devolver_a_la_lista_revierte_solo_lo_comprado_y_la_linea_vuelve_a_pendiente() {
        long eventoId = crearEventoDeUnDia("Sync IT devolver");
        String admin = token(RolMembresia.ADMIN, false);
        apuntar(eventoId, 4); // Platos = 12
        long platosLinea = idLinea(getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin),
                "LIMPIEZA", "Platos");
        http.post().uri("/api/v1/eventos/" + eventoId + "/lista-compra/lineas/" + platosLinea + "/comprado")
                .header(AUTHORIZATION, "Bearer " + admin).exchange().expectStatus().isNoContent();

        // localiza el articuloEventoId de "Platos" en el inventario de la fiesta
        long artId = idArticuloFiesta(getMap("/api/v1/inventario/evento/" + eventoId, admin), "Platos");

        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/" + artId + "/devolver-a-lista")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNoContent();

        // ya no está en la fiesta
        assertThat(cantidadFiesta(getMap("/api/v1/inventario/evento/" + eventoId, admin), "Platos")).isNull();
        // vuelve a pendiente en la lista
        var body = getMap("/api/v1/eventos/" + eventoId + "/lista-compra", admin);
        assertThat(compradaLinea(body, "LIMPIEZA", "Platos")).isFalse();
        assertThat(cantidadLinea(body, "LIMPIEZA", "Platos")).isEqualTo(12.0);
    }

    @SuppressWarnings("unchecked")
    long idArticuloFiesta(Map<String, Object> body, String nombre) {
        for (Map<String, Object> cat : (List<Map<String, Object>>) body.get("categorias")) {
            for (Map<String, Object> a : (List<Map<String, Object>>) cat.get("articulos")) {
                if (nombre.equals(a.get("nombre"))) return ((Number) a.get("id")).longValue();
            }
        }
        throw new AssertionError("artículo de fiesta no encontrado: " + nombre);
    }

    @Test
    void devolver_a_la_lista_sin_parte_comprada_es_400() {
        // usa un articulo_evento creado por envío de stock (sin comprar): ver InventarioIT para el patrón de alta+envío
        // Alternativa mínima: 404 si el articuloEventoId no existe
        long eventoId = crearEventoDeUnDia("Sync IT devolver 400");
        String admin = token(RolMembresia.ADMIN, false);
        http.post().uri("/api/v1/inventario/evento/" + eventoId + "/99999999/devolver-a-lista")
                .header(AUTHORIZATION, "Bearer " + admin)
                .exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("ARTICULO_EVENTO_NO_ENCONTRADO");
    }
```

En `InventarioIT.java` (tiene el patrón de alta de `ArticuloInventario` + `enviar`):

```java
    @Test
    void devolver_stock_de_una_fila_con_parte_comprada_deja_la_fila() {
        // 1. crear evento + apuntar gente para que la lista tenga "Cerveza / lata"
        //    (o usa un producto de LIMPIEZA POR_PENISTA que no dependa de ficha)
        // 2. marcar esa línea como comprada -> articulo_evento con cantidad_comprada > 0, cantidad = 0
        // 3. dar de alta "Platos / unidad" en el inventario general y enviarlo al evento
        //    -> misma fila (evento, LIMPIEZA, Platos, unidad), ahora cantidad > 0 y cantidad_comprada > 0
        // 4. devolver (stock): la fila NO se borra; cantidad vuelve a 0, cantidad_comprada intacta
        // Afirmar: GET inventario/evento/{id} sigue mostrando "Platos" con cantidad == cantidad_comprada
    }
```

> El comentario de arriba describe el test; escríbelo con el patrón real de `InventarioIT`
> (helpers `token`, alta de artículo, `enviar`). Si el montaje resulta demasiado enredado para
> un IT, cúbrelo en `ListaCompraSyncIT` donde ya está el helper `apuntar`.

Run: `cd back && ./mvnw -q -Dit.test=ListaCompraSyncIT verify -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (no existe `devolver-a-lista`).

- [ ] **Step 3: `devolverALista` + ajustes en `InventarioService`**

Nueva dependencia: `LineaCompraEventoRepository lineas` en el constructor.

```java
    @Transactional
    public void devolverALista(Long usuarioId, Long eventoId, Long articuloEventoId) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        ArticuloEvento fila = articulosEvento.findById(articuloEventoId)
                .filter(f -> f.getEvento().getId().equals(eventoId))
                .orElseThrow(ArticuloEventoNoEncontradoException::new);
        if (fila.getCantidadComprada().signum() == 0) {
            throw new NadaQueDevolverException();
        }
        lineas.findByEventoIdAndArticuloEventoId(eventoId, fila.getId()).ifPresent(l -> {
            l.setComprada(false);
            l.setArticuloEventoId(null);
            lineas.save(l);
        });
        fila.setCantidadComprada(BigDecimal.ZERO);
        if (fila.getCantidad().signum() == 0) {
            articulosEvento.delete(fila);
        } else {
            articulosEvento.save(fila);
        }
    }
```

Ajusta `devolver` (el de stock existente):

```java
    @Transactional
    public void devolver(Long usuarioId, Long eventoId, Long articuloEventoId) {
        if (!permisos.puede(usuarioId, AreaProtegida.INVENTARIO)) {
            throw new SinPermisoInventarioException();
        }
        ArticuloEvento fila = articulosEvento.findById(articuloEventoId)
                .filter(f -> f.getEvento().getId().equals(eventoId))
                .orElseThrow(ArticuloEventoNoEncontradoException::new);
        ArticuloInventario origen = fila.getArticuloInventario();
        if (origen != null && fila.getCantidad().signum() > 0) {
            origen.setCantidad(origen.getCantidad().add(fila.getCantidad()));
            articulos.save(origen);
        }
        fila.setCantidad(BigDecimal.ZERO);
        if (fila.getCantidadComprada().signum() == 0) {
            articulosEvento.delete(fila);
        } else {
            articulosEvento.save(fila);
        }
    }
```

Ajusta `moverAlEvento` para fusionar por producto y no por `articulo_inventario_id`:

```java
    private void moverAlEvento(Evento evento, ArticuloInventario art) {
        ArticuloEvento fila = articulosEvento
                .findByEventoIdAndCategoriaAndNombreAndTamano(
                        evento.getId(), art.getCategoria(), art.getNombre(), art.getTamano())
                .orElse(null);
        if (fila == null) {
            int orden = articulosEvento.findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(evento.getId())
                    .stream().filter(f -> f.getCategoria() == art.getCategoria())
                    .mapToInt(ArticuloEvento::getOrden).max().orElse(0) + 1;
            fila = ArticuloEvento.builder()
                    .evento(evento).articuloInventario(art).categoria(art.getCategoria())
                    .nombre(art.getNombre()).tamano(art.getTamano())
                    .cantidad(art.getCantidad()).cantidadComprada(BigDecimal.ZERO)
                    .orden(orden).build();
        } else {
            if (fila.getArticuloInventario() == null) {
                fila.setArticuloInventario(art);
            }
            fila.setCantidad(fila.getCantidad().add(art.getCantidad()));
        }
        articulosEvento.save(fila);
        art.setCantidad(BigDecimal.ZERO);
        articulos.save(art);
    }
```

Borra el finder `findByEventoIdAndArticuloInventarioId` de `ArticuloEventoRepository` si ya no lo usa nadie (busca con `grep`), o déjalo si se usa en tests.

- [ ] **Step 4: Endpoint**

```java
    /** Devuelve la parte comprada de una fila del inventario de la fiesta a la lista de la compra. */
    @PostMapping("/evento/{eventoId}/{articuloEventoId}/devolver-a-lista")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolverALista(@AuthenticationPrincipal UsuarioPrincipal principal,
            @PathVariable Long eventoId, @PathVariable Long articuloEventoId) {
        service.devolverALista(principal.id(), eventoId, articuloEventoId);
    }
```

- [ ] **Step 5: Ejecutar toda la suite backend**

Run: `cd back && ./mvnw verify`
Expected: `MVN_EXIT=0`. Presta atención a `InventarioIT` y `ArticuloEventoRepositoryIT` — el cambio de `UNIQUE` y de finder puede tocar sus expectativas.

- [ ] **Step 6: Commit**

```bash
git add back/src/main/java/com/baniterio/api/inventario/NadaQueDevolverException.java \
        back/src/main/java/com/baniterio/api/inventario/InventarioService.java \
        back/src/main/java/com/baniterio/api/inventario/InventarioController.java \
        back/src/main/java/com/baniterio/api/inventario/ArticuloEventoRepository.java \
        back/src/main/java/com/baniterio/api/web/ApiExceptionHandler.java \
        back/src/test/java/com/baniterio/api/inventario/InventarioIT.java \
        back/src/test/java/com/baniterio/api/compra/ListaCompraSyncIT.java
git commit -m "feat(inventario): devolver a la lista revierte solo la parte comprada

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Web — comprado, bloqueo, devolver a la lista y alineación de columnas

**Files:**
- Modify: `front/src/app/panel/eventos/lista-compra/lista-compra.types.ts`
- Modify: `front/src/app/panel/eventos/lista-compra/lista-compra.service.ts`
- Modify: `front/src/app/panel/eventos/lista-compra/lista-compra.ts`
- Modify: `front/src/app/panel/eventos/lista-compra/lista-compra.html` (ya lleva el `<colgroup>` sin commitear; añadir columna de acción + bloqueo)
- Modify: `front/src/app/panel/eventos/lista-compra/lista-compra.service.spec.ts`
- Modify: `front/src/app/panel/eventos/lista-compra/lista-compra.spec.ts`
- Modify: `front/src/app/panel/inventario/inventario.types.ts` (`ArticuloFiesta` + `cantidadComprada`)
- Modify: `front/src/app/panel/inventario/inventario.service.ts` (`devolverALista`)
- Modify: `front/src/app/panel/inventario/inventario.service.spec.ts`
- Modify: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.ts`
- Modify: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.html`
- Modify: `front/src/app/panel/eventos/inventario-fiesta/inventario-fiesta.spec.ts`

**Interfaces:**
- Consumes: endpoints del Task 3/4/5.
- Produces (tipos):
  - `LineaCompra`: `+ id: number`, `+ comprada: boolean`.
  - `ListaCompraResponse`: `+ bloqueada: boolean`.
  - `ListaCompraAdminResponse`: `+ bloqueada: boolean`.
  - `ArticuloFiesta`: `+ cantidadComprada: number`.
  - Servicio `ListaCompraService`: `comprado(eventoId, lineaId): Observable<void>`, `bloqueo(eventoId, bloqueada): Observable<void>`.
  - Servicio `InventarioService`: `devolverALista(eventoId, articuloEventoId): Observable<void>`.

- [ ] **Step 1: Tipos**

`lista-compra.types.ts`:

```typescript
export interface LineaCompra {
  id: number;
  nombre: string;
  tamano: string;
  cantidad: number;
  cantidadCalculada: number;
  ajustada: boolean;
  dinamica: boolean;
  necesitaFicha: boolean;
  comprada: boolean;
}
```

En `ListaCompraResponse` y `ListaCompraAdminResponse` añade `bloqueada: boolean;`.

`inventario.types.ts`, en `ArticuloFiesta`:

```typescript
export interface ArticuloFiesta {
  id: number;
  nombre: string;
  tamano: string;
  cantidad: number;
  cantidadComprada: number;
}
```

- [ ] **Step 2: Servicios + specs (fallan)**

`lista-compra.service.ts`, añade:

```typescript
  comprado(eventoId: number, lineaId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/eventos/${eventoId}/lista-compra/lineas/${lineaId}/comprado`,
      {},
    );
  }

  bloqueo(eventoId: number, bloqueada: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/eventos/${eventoId}/lista-compra/bloqueo`, { bloqueada });
  }
```

`inventario.service.ts`, añade:

```typescript
  /** Devuelve la parte comprada de una fila del inventario de la fiesta a la lista de la compra. */
  devolverALista(eventoId: number, articuloEventoId: number): Observable<void> {
    return this.http.post<void>(
      `${this.base}/inventario/evento/${eventoId}/${articuloEventoId}/devolver-a-lista`,
      {},
    );
  }
```

En `lista-compra.service.spec.ts` añade dos tests con el patrón existente (`HttpTestingController`, `expectOne`, `req.request.method`):

```typescript
  it('comprado hace POST a la ruta de la línea', () => {
    servicio.comprado(7, 3).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7/lista-compra/lineas/3/comprado`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('bloqueo hace PUT con { bloqueada }', () => {
    servicio.bloqueo(7, true).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7/lista-compra/bloqueo`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ bloqueada: true });
    req.flush(null);
  });
```

En `inventario.service.spec.ts`:

```typescript
  it('devolverALista hace POST a la ruta devolver-a-lista', () => {
    servicio.devolverALista(7, 4).subscribe();
    const req = httpMock.expectOne(`${base}/inventario/evento/7/4/devolver-a-lista`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
```

(Usa el nombre real de la variable del servicio/base que ya haya en cada spec.)

Run: `cd front && npx ng test --no-watch --include src/app/panel/eventos/lista-compra --include src/app/panel/inventario`
Expected: FAIL (métodos aún no existen → en realidad Step 2 ya los añade; el fallo real es si el orden de pasos se sigue: escribe el spec primero, corre, luego implementa. Si ya implementaste, corre y verifica PASS de estos).

- [ ] **Step 3: Componente lista-compra**

`lista-compra.ts` — añade señales y acciones:

```typescript
  protected readonly bloqueada = signal(false);
  protected readonly puedoEditar = signal(false);
  protected readonly ocupado = signal(false);

  // en cargar(), dentro del next:
  //   this.puedoEditar.set(d.puedoEditar);
  //   this.bloqueada.set(d.bloqueada);

  protected comprar(l: LineaCompra): void {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.service.comprado(this.eventoId, l.id).subscribe({
      next: () => { this.ocupado.set(false); this.cargar(); },
      error: () => { this.ocupado.set(false); this.estado.set('error'); },
    });
  }

  protected alternarBloqueo(): void {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.service.bloqueo(this.eventoId, !this.bloqueada()).subscribe({
      next: () => { this.ocupado.set(false); this.cargar(); },
      error: () => { this.ocupado.set(false); this.estado.set('error'); },
    });
  }
```

Importa `LineaCompra` de `./lista-compra.types`.

- [ ] **Step 4: Plantilla lista-compra**

En `lista-compra.html`:

1. El `<colgroup>` (ya presente sin commitear) pasa a 4 columnas cuando `puedoEditar()`:

```html
              <colgroup>
                <col />
                <col class="w-28 sm:w-40" />
                <col class="w-20" />
                @if (puedoEditar()) { <col class="w-28" /> }
              </colgroup>
```

y una `<th>` extra vacía tras "Cantidad" con el mismo `@if (puedoEditar())`.

2. En la cabecera, junto a `{{ apuntados() }} apuntados · {{ diasFiesta() }} día(s) de fiesta`, añade `@if (bloqueada()) { <span> · lista bloqueada</span> }` y, si `puedoEditar()`, un botón:

```html
      @if (puedoEditar()) {
        <button
          type="button"
          [disabled]="ocupado()"
          (click)="alternarBloqueo()"
          class="mt-2 rounded-lg border border-outline px-3 py-1 text-sm disabled:opacity-50"
        >
          {{ bloqueada() ? 'Desbloquear lista' : 'Bloquear lista' }}
        </button>
      }
```

3. En cada `<tr>`, la fila comprada se atenúa y se añade la celda de acción:

```html
                  <tr class="border-t border-outline/30" [class.opacity-50]="l.comprada">
                    ...
                    <td class="py-1 text-right font-semibold">{{ l.cantidad }}</td>
                    @if (puedoEditar()) {
                      <td class="py-1 text-right">
                        @if (l.comprada) {
                          <span class="text-xs text-gold-soft">✓ Comprada</span>
                        } @else if (l.cantidad > 0) {
                          <button
                            type="button"
                            [disabled]="ocupado()"
                            (click)="comprar(l)"
                            class="whitespace-nowrap rounded-lg border border-outline px-2 py-1 text-xs font-medium hover:border-gold hover:text-gold disabled:opacity-50"
                          >
                            Comprado
                          </button>
                        }
                      </td>
                    }
                  </tr>
```

- [ ] **Step 5: Pantalla inventario-fiesta**

`inventario-fiesta.ts` — añade:

```typescript
  protected devolverALista(a: ArticuloFiesta): void {
    if (!confirm(`¿Devolver "${a.nombre}" a la lista de la compra?`)) return;
    this.devolviendo.set(true);
    this.aviso.set('');
    this.inventarioService.devolverALista(this.eventoId, a.id).subscribe({
      next: () => { this.devolviendo.set(false); this.cargar(); },
      error: () => { this.devolviendo.set(false); this.aviso.set('No se pudo devolver.'); },
    });
  }

  protected stockDe(a: ArticuloFiesta): number {
    return a.cantidad - a.cantidadComprada;
  }
```

`inventario-fiesta.html` — en la celda de acción, sustituye el único botón por:

```html
                    <td class="px-3 py-2 text-right">
                      @if (puedoEditar()) {
                        <div class="flex flex-col items-end gap-1">
                          @if (stockDe(a) > 0) {
                            <button
                              type="button"
                              [disabled]="devolviendo()"
                              (click)="devolver(a)"
                              class="whitespace-nowrap rounded-lg border border-outline px-2 py-1 text-xs font-medium hover:border-gold hover:text-gold disabled:opacity-50"
                            >
                              Devolver al inventario
                            </button>
                          }
                          @if (a.cantidadComprada > 0) {
                            <button
                              type="button"
                              [disabled]="devolviendo()"
                              (click)="devolverALista(a)"
                              class="whitespace-nowrap rounded-lg border border-outline px-2 py-1 text-xs font-medium hover:border-gold hover:text-gold disabled:opacity-50"
                            >
                              Devolver a la lista
                            </button>
                          }
                        </div>
                      }
                    </td>
```

- [ ] **Step 6: Specs de componente**

`lista-compra.spec.ts` — añade dos tests (usa el `montar()` existente con `HttpTestingController` o mocks del servicio, según el patrón del fichero):

```typescript
  it('muestra el botón "Comprado" solo con permiso y cantidad > 0', async () => {
    // respuesta con puedoEditar: true, bloqueada: false, una línea id 1 cantidad 3 comprada:false
    // -> el botón "Comprado" está en el DOM
  });

  it('una línea comprada se pinta atenuada y sin botón', async () => {
    // línea comprada:true -> fila con clase opacity-50, texto "✓ Comprada", sin botón
  });
```

`inventario-fiesta.spec.ts` — añade:

```typescript
  it('muestra "Devolver a la lista" cuando cantidadComprada > 0', async () => {
    // artículo cantidad 12, cantidadComprada 12 -> botón "Devolver a la lista" presente, "Devolver al inventario" ausente
  });
```

Rellena estos tests con el patrón real del fichero (mock de `ListaCompraService`/`InventarioService` con `of(...)`, o `HttpTestingController`). El fichero `lista-compra.spec.ts` ya monta con `{ provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id','7']]) } } }` + `provideRouter([])`.

- [ ] **Step 7: Ejecutar los tests web tocados**

Run: `cd front && npx ng test --no-watch --include src/app/panel/eventos/lista-compra --include src/app/panel/inventario --include src/app/panel/eventos/inventario-fiesta`
Expected: PASS.

- [ ] **Step 8: Build**

Run: `cd front && npx ng build`
Expected: OK.

- [ ] **Step 9: Commit**

```bash
git add front/src/app/panel/eventos/lista-compra front/src/app/panel/inventario front/src/app/panel/eventos/inventario-fiesta
git commit -m "feat(web): comprado + bloqueo en la lista de la compra y devolver a la lista

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: Móvil — comprado, bloqueo y devolver a la lista

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/ListaCompraDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/InventarioDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ListaCompraRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ListaCompraRepositoryImpl.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/InventarioRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/InventarioRepositoryImpl.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoListaCompra.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoInventario.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/inventario/InventarioFiestaScreen.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/ListaCompraRepositoryImplTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/InventarioRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - `LineaCompraDto`: `+ val id: Long = 0`, `+ val comprada: Boolean = false`.
  - `ListaCompraResponse`: `+ val bloqueada: Boolean = false`.
  - `ListaCompraAdminResponse`: `+ val bloqueada: Boolean = false`.
  - `ArticuloFiestaDto`: `+ val cantidadComprada: Double = 0.0`.
  - `ListaCompraRepository.marcarComprada(eventoId: Long, lineaId: Long): ResultadoListaCompra<Unit>`
  - `ListaCompraRepository.cambiarBloqueo(eventoId: Long, bloqueada: Boolean): ResultadoListaCompra<Unit>`
  - `InventarioRepository.devolverALista(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit>`

- [ ] **Step 1: DTOs**

`ListaCompraDtos.kt` — `LineaCompraDto`:

```kotlin
@Serializable
data class LineaCompraDto(
    val id: Long = 0,
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
    val cantidadCalculada: Double = 0.0,
    val ajustada: Boolean = false,
    val dinamica: Boolean = false,
    val necesitaFicha: Boolean = false,
    val comprada: Boolean = false,
)
```

`ListaCompraResponse` y `ListaCompraAdminResponse`: añade `val bloqueada: Boolean = false`.

Nuevo body:

```kotlin
/** Cuerpo de `PUT /api/v1/eventos/{id}/lista-compra/bloqueo`. */
@Serializable
data class CambiarBloqueoBody(val bloqueada: Boolean)
```

`InventarioDtos.kt` — `ArticuloFiestaDto`:

```kotlin
@Serializable
data class ArticuloFiestaDto(
    val id: Long,
    val nombre: String,
    val tamano: String,
    val cantidad: Double,
    val cantidadComprada: Double = 0.0,
)
```

- [ ] **Step 2: Resultado enums**

`ResultadoListaCompra.kt` — añade `LINEA_NO_ENCONTRADA` al enum, al `when` de `deCodigoBackend` (`"LINEA_COMPRA_NO_ENCONTRADA" -> LINEA_NO_ENCONTRADA`) y al `when` de `mensaje` (`LINEA_NO_ENCONTRADA -> "Esa línea ya no está en la lista."`).

`ResultadoInventario.kt` — añade `NADA_QUE_DEVOLVER`, `"NADA_QUE_DEVOLVER" -> NADA_QUE_DEVOLVER`, `NADA_QUE_DEVOLVER -> "No hay nada comprado que devolver."`.

- [ ] **Step 3: Repos (interfaz + impl)**

`ListaCompraRepository.kt` — añade a la interfaz:

```kotlin
    suspend fun marcarComprada(eventoId: Long, lineaId: Long): ResultadoListaCompra<Unit>
    suspend fun cambiarBloqueo(eventoId: Long, bloqueada: Boolean): ResultadoListaCompra<Unit>
```

`ListaCompraRepositoryImpl.kt` — añade (con `import com.baniterio.app.data.dto.CambiarBloqueoBody`):

```kotlin
    override suspend fun marcarComprada(eventoId: Long, lineaId: Long): ResultadoListaCompra<Unit> = peticion {
        http.post("$API_BASE_URL/eventos/$eventoId/lista-compra/lineas/$lineaId/comprado") { auth() }.let { }
    }

    override suspend fun cambiarBloqueo(eventoId: Long, bloqueada: Boolean): ResultadoListaCompra<Unit> = peticion {
        http.put("$API_BASE_URL/eventos/$eventoId/lista-compra/bloqueo") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CambiarBloqueoBody(bloqueada))
        }.let { }
    }
```

`InventarioRepository.kt` / `InventarioRepositoryImpl.kt` — añade:

```kotlin
    suspend fun devolverALista(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit>
```

```kotlin
    override suspend fun devolverALista(eventoId: Long, articuloEventoId: Long): ResultadoInventario<Unit> = peticion {
        http.post("$API_BASE_URL/inventario/evento/$eventoId/$articuloEventoId/devolver-a-lista") { auth() }.let { }
    }
```

- [ ] **Step 4: Tests de repo (fallan)**

`ListaCompraRepositoryImplTest.kt`:

```kotlin
    @Test
    fun marcar_comprada_hace_post() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        r.marcarComprada(7, 3)
        assertEquals("POST", v[0].metodo)
        assertEquals("/api/v1/eventos/7/lista-compra/lineas/3/comprado", v[0].path)
        assertEquals("Bearer jwt-x", v[0].auth)
    }

    @Test
    fun cambiar_bloqueo_hace_put_con_body() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        r.cambiarBloqueo(7, true)
        assertEquals("PUT", v[0].metodo)
        assertEquals("/api/v1/eventos/7/lista-compra/bloqueo", v[0].path)
        assert(v[0].cuerpo.contains("\"bloqueada\":true")) { v[0].cuerpo }
    }
```

`InventarioRepositoryImplTest.kt` (copia su patrón de `Vista`/`MockEngine`):

```kotlin
    @Test
    fun devolver_a_lista_hace_post() = runTest {
        val (r, v) = repo(status = HttpStatusCode.NoContent)
        r.devolverALista(7, 4)
        assertEquals("POST", v[0].metodo)
        assertEquals("/api/v1/inventario/evento/7/4/devolver-a-lista", v[0].path)
    }
```

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest`
Expected: FAIL (métodos no existen aún → si ya se implementaron en Step 3, PASS; sigue el orden: en KMP a veces es más práctico implementar y test juntos, pero deja el test escrito).

- [ ] **Step 5: `ListaCompraScreen` — bloqueo + comprado**

- `EstadoLista.Cargada` gana `val puedoEditar: Boolean` y `val bloqueada: Boolean`.
- El `LaunchedEffect` los rellena: `EstadoLista.Cargada(r.dato.puedoEditar, r.dato.bloqueada, r.dato.apuntados, r.dato.diasFiesta, r.dato.categorias)`.
- `var intento` ya fuerza recarga; añade `val scope = rememberCoroutineScope()` y `var ocupado by remember { mutableStateOf(false) }`.
- Bajo el recuento de apuntados, si `e.puedoEditar`:

```kotlin
                OutlinedButton(
                    enabled = !ocupado,
                    onClick = {
                        ocupado = true
                        scope.launch {
                            listaCompraRepo.cambiarBloqueo(eventoId, !e.bloqueada)
                            ocupado = false
                            intento++
                        }
                    },
                ) { Text(if (e.bloqueada) "Desbloquear lista" else "Bloquear lista") }
```

y si `e.bloqueada`, añade " · lista bloqueada" al texto del recuento.

- En la `Row` de cada línea, tras el `Text(fmt(l.cantidad), ...)`, si `e.puedoEditar`:

```kotlin
                                if (l.comprada) {
                                    Text("✓ Comprada", style = MaterialTheme.typography.bodySmall,
                                        color = BaniterioColors.gold)
                                } else if (l.cantidad > 0.0) {
                                    OutlinedButton(
                                        enabled = !ocupado,
                                        onClick = {
                                            ocupado = true
                                            scope.launch {
                                                listaCompraRepo.marcarComprada(eventoId, l.id)
                                                ocupado = false
                                                intento++
                                            }
                                        },
                                    ) { Text("Comprado") }
                                }
```

Imports nuevos: `androidx.compose.material3.OutlinedButton`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`.

- [ ] **Step 6: `InventarioFiestaScreen` — devolver a la lista**

- En la `Row` de acciones (`if (e.puedoEditar)`), sustituye el único `OutlinedButton` por dos, según la parte de stock y la comprada:

```kotlin
                            if (e.puedoEditar) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (a.cantidad - a.cantidadComprada > 0.0) {
                                        OutlinedButton(onClick = { confirmar = a }) {
                                            Text("Devolver a inventario")
                                        }
                                    }
                                    if (a.cantidadComprada > 0.0) {
                                        OutlinedButton(onClick = { confirmarLista = a }) {
                                            Text("Devolver a la lista")
                                        }
                                    }
                                }
                            }
```

- Añade `var confirmarLista by remember { mutableStateOf<ArticuloFiestaDto?>(null) }` y un segundo `AlertDialog` (copia el de `confirmar`) que llame a `inventarioRepo.devolverALista(eventoId, art.id)` y ponga el título/textos hacia "la lista de la compra".

- [ ] **Step 7: Build móvil**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL. (Recuerda: los diagnósticos del IDE para Kotlin no son fiables; manda `./gradlew`.)

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/compra/ListaCompraScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/inventario/InventarioFiestaScreen.kt \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data
git commit -m "feat(movil): comprado + bloqueo en la lista de la compra y devolver a la lista

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: Verificación de las tres suites y cierre

**Files:**
- Modify: `C:\Users\rober\.claude\projects\c--Users-rober-Documents-Desarrollo-Ba-iterio\memory\baniterio_lista_compra.md`
- Modify: `C:\Users\rober\.claude\projects\c--Users-rober-Documents-Desarrollo-Ba-iterio\memory\MEMORY.md` (solo si el hook cambia el resumen de una línea)

- [ ] **Step 1: Backend completo**

Run: `cd back && ./mvnw verify`
Expected: `MVN_EXIT=0`.

- [ ] **Step 2: Web completo**

Run: `cd front && npx ng build && npx ng test --no-watch`
Expected: build OK; los tests pasan (pueden quedar los ~5 "Unhandled Rejection" de router preexistentes, no regresiones).

- [ ] **Step 3: Móvil completo**

Run: `cd mobile && ./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Actualizar la memoria**

En `baniterio_lista_compra.md` añade un párrafo: bloque 1.5 hecho — `linea_compra_evento` (V39) materializa la lista mostrada y se sincroniza mientras no esté bloqueada; botón "Comprado" por línea (envía a `articulo_evento.cantidad_comprada`), botón "Bloquear lista" (área INVENTARIO, congela el auto-cálculo), "Devolver a la lista" desde el inventario de la fiesta (revierte solo `cantidad_comprada`). Rutas `POST .../lista-compra/lineas/{id}/comprado`, `PUT .../lista-compra/bloqueo`, `POST /inventario/evento/{ev}/{art}/devolver-a-lista`. Sigue pendiente el bloque 2 (descontar inventario en el cálculo) y las "excepciones" (edición admin de líneas con la lista bloqueada).

- [ ] **Step 5: Commit de la memoria (si aplica; la memoria vive fuera del repo, no va en un commit de git — solo escribir el fichero)**

- [ ] **Step 6: Cerrar la rama**

Announce: "I'm using the finishing-a-development-branch skill to complete this work."
**REQUIRED SUB-SKILL:** superpowers:finishing-a-development-branch — verifica las tres suites (ya hecho), detecta el entorno, presenta el menú de 3 opciones (merge local a `main` / push + PR / dejar la rama) y ejecuta la elección del usuario. Base branch: `main`. No proceder sin que el usuario elija.

---

## Self-review

**Cobertura del spec:**
- Modelo V39 (evento flag, articulo_evento nullable + cantidad_comprada + UNIQUE, linea_compra_evento) → Task 1. ✅
- Sincronización + lectura desde la tabla, `bloqueada` en respuestas, `id`/`comprada` en líneas → Task 2. ✅
- `marcarComprada` + upsert en articulo_evento + `ArticuloDto.cantidadComprada` + excepción `LINEA_COMPRA_NO_ENCONTRADA` → Task 3. ✅
- `cambiarBloqueo` (sincroniza al bloquear, admin sigue editando reglas) → Task 4. ✅
- `devolverALista` + ajuste de `devolver` (stock) y `moverAlEvento` (fusión por producto) + `NADA_QUE_DEVOLVER` → Task 5. ✅
- Web: tipos, servicio, pantalla lectura (bloqueo + comprado + colgroup), inventario-fiesta (devolver a la lista), specs → Task 6. ✅
- Móvil: DTOs, repos, Resultado enums, pantallas, tests → Task 7. ✅
- Pruebas backend/web/móvil enumeradas en el spec → repartidas en Tasks 2-7; verificación global en Task 8. ✅
- Fuera de alcance (arrastre anual, edición admin con lista bloqueada, bloque 2) → no hay tareas, correcto. ✅

**Consistencia de tipos:**
- `LineaCompraDto` (9 componentes, `id` primero) usado igual en Task 2 y Task 3. ✅
- `ArticuloDto` (5 componentes) + helpers `de`/`deEvento` definidos en Task 3, usados en Task 3 Step 4. ✅
- `LineaCompraEventoRepository` métodos definidos en Task 1, consumidos en Tasks 2/3/5 con la misma firma. ✅
- Rutas idénticas entre backend (Tasks 3/4/5), web (Task 6) y móvil (Task 7). ✅
- `cantidad` en `ArticuloEvento` = parte de stock; `cantidad` en `ArticuloDto` = total (stock + comprada). Diferencia intencionada y documentada en la convención de nombres y en el Javadoc del DTO. ✅

**Placeholders:** El Task 5 Step 2 y el Task 6 Step 6 dejan el cuerpo de algunos tests descrito en prosa en vez de código completo, porque dependen del patrón exacto de montaje de cada fichero de test (mock vs `HttpTestingController`, helpers locales de `InventarioIT`). El ejecutor debe escribirlos siguiendo el fichero hermano; las asserts concretas (rutas, `cantidad`, `comprada`) sí están dadas. Aceptable pero es el punto más flojo del plan — si el ejecutor es un subagente, conviene revisarlo con cuidado en esas dos tareas.
