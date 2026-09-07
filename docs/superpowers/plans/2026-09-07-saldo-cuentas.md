# Saldo de cuentas, estados de pago y libro de movimientos — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar a cada cuenta un saldo real que solo sube cuando el dinero está en la cuenta de la peña, con estados de pago de cuatro niveles, una bolsa "por ingresar", una estimación y un libro de movimientos al estilo del Excel del tesorero.

**Architecture:** El estado de pago vive en `ficha_bebida.estado_pago` (única verdad); `pago_declarado` queda como registro histórico. Una tabla `movimiento_cuenta` es el libro: su primera fila es el saldo inicial y cada cuota que llega a la cuenta añade una fila. `MovimientoCuentaService` (solo repos, sin ciclos) lo escriben `AsistenciaService`, `PagoDeclaradoService` y `CuentaService`. Web y móvil reflejan los nuevos estados y añaden la pantalla de detalle de cuenta con el botón "He transferido el dinero a la peña".

**Tech Stack:** Spring Boot 3 + Maven + Flyway + JPA/Hibernate + Lombok (backend, Java 17); Angular 20 standalone + signals + Tailwind + Vitest (web); Kotlin Multiplatform + Compose + Ktor + kotlinx.serialization (móvil).

**Spec:** `docs/superpowers/specs/2026-09-07-saldo-cuentas-design.md`

## Global Constraints

- Java **17** (no tocar `pom.xml`). Backend: Maven (`./mvnw`), no Gradle.
- Comandos de test: backend un test `./mvnw -Dtest=Clase test`, suite completa con ITs `./mvnw verify`. Web `npx ng test --watch=false [--include=...]`. Móvil `./gradlew :shared:testAndroidHostTest` y `./gradlew :androidApp:compileDebugKotlin`.
- Migración nueva: `back/src/main/resources/db/migration/V28__saldo_cuentas.sql` (el número más alto hoy es V27).
- La BBDD de los `*IT` es compartida: cada caso crea sus propios miembros/eventos y limpia lo suyo en `@AfterEach` (ver `PagoDeclaradoIT`).
- Peña piloto: slug `baniterio`. Cuenta de las cuotas: `San Miguel` (`lleva_ficha_bebida = true`).
- Saldo inicial acordado: **91,13 €**, concepto "Saldo del año anterior", fecha `2026-01-01`.
- Diagnósticos de Kotlin del IDE están rotos: fiarse solo de Gradle.
- Etiquetas de estado (verbatim, se usan en web y móvil):
  - `PENDIENTE_PAGO` → "Pendiente de pago"
  - `DECLARADO` → "Pagado, pendiente de confirmar"
  - `CONFIRMADO_PENDIENTE_ENVIO` → "Confirmado, pendiente de ingresar en la cuenta"
  - `CONFIRMADO_EN_CUENTA` → "Confirmado y en la cuenta"
- Respuestas y textos de UI en español con acentos correctos.

---

## File Structure

**Backend — nuevo**
- `back/src/main/resources/db/migration/V28__saldo_cuentas.sql` — migración.
- `back/src/main/java/com/baniterio/api/identidad/EstadoPagoCuota.java` — enum 4 estados + `legible()`.
- `back/src/main/java/com/baniterio/api/identidad/OrigenMovimiento.java` — enum `SALDO_INICIAL|CUOTA|AJUSTE`.
- `back/src/main/java/com/baniterio/api/identidad/MovimientoCuenta.java` — entidad.
- `back/src/main/java/com/baniterio/api/identidad/MovimientoCuentaRepository.java` — repo.
- `back/src/main/java/com/baniterio/api/cuenta/MovimientoCuentaService.java` — servicio fino.
- `back/src/main/java/com/baniterio/api/cuenta/dto/MovimientoFila.java` — DTO.
- `back/src/main/java/com/baniterio/api/cuenta/dto/CuentaDetalle.java` — DTO.
- `back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaIT.java` — IT nuevo.

**Backend — modificado**
- `identidad/FichaBebida.java` — `boolean pagado` → `EstadoPagoCuota estadoPago`.
- `identidad/FichaBebidaRepository.java` — consultas para saldo/estimación/por-ingresar.
- `evento/dto/AsistenteFila.java` — `pagado`+`declarado` → `estadoPago`.
- `evento/dto/FichaBebidaDetalle.java` — `MiFicha.pagado` → `estadoPago`.
- `evento/FichaBebidaService.java` — `aMiFicha`.
- `evento/AsistenciaService.java` — `aFila`, `confirmarPago`, `deshacerPago`, quitar `asistenciasConDeclaracionPendiente`.
- `evento/PagoDeclaradoService.java` — escribir `estado_pago` en declarar/anular/rechazar/confirmar; inyectar `MovimientoCuentaService`.
- `cuenta/CuentaService.java` — `detalle(usuarioId, id)` ampliado + `marcarTransferido`.
- `cuenta/CuentaController.java` — `GET /{id}` pasa `principal.id()`; nuevo `POST /{id}/transferencia-a-pena`.
- Tests: `evento/PagoDeclaradoIT.java`, `evento/ConfirmacionPagoIT.java`, `identidad/FichaBebidaPagoIT.java`, `cuenta/CuentaIT.java` — ajustar a `estadoPago`.

**Web — modificado**
- `panel/cuentas/cuentas.types.ts` — `EstadoPagoCuota`, `MovimientoFila`, `CuentaDetalle`.
- `panel/cuentas/cuentas.service.ts` — `detalle` tipado nuevo, `marcarTransferido`.
- `panel/cuentas/cuenta-detalle/cuenta-detalle.ts` + `.html` — saldo/estimación/movimientos/botón.
- `panel/eventos/eventos.types.ts` — `FichaBebidaMia` y `AsistenteFila`: `estadoPago`.
- `panel/eventos/evento-detalle/evento-detalle.ts` + `.html` — botón por `estadoPago`.
- `panel/eventos/modal-asistentes/modal-asistentes.ts` + `.html` — etiqueta por `estadoPago`; "deshacer" en `CONFIRMADO_*`.
- `admin/pagos/pagos.html` — método por fila (dato ya presente en `PagoDeclaradoPendiente`).
- Specs: `cuentas.service.spec.ts`, `cuenta-detalle` (nuevo spec si no existe), `evento-detalle.spec.ts`, `modal-asistentes.spec.ts`.

**Móvil — modificado**
- `data/dto/CuentaDtos.kt` — `MovimientoFilaDto`, `CuentaDetalleDto`.
- `data/dto/EventoDtos.kt` — `FichaBebidaMiaDto.estadoPago`, `AsistenteFilaDto.estadoPago`.
- `data/CuentasRepository.kt` + `CuentasRepositoryImpl.kt` — `detalle` nuevo tipo, `marcarTransferido`.
- `ui/cuentas/CuentaDetalleScreen.kt` — saldo/estimación/movimientos/botón + diálogo.
- `ui/eventos/EventoDetalleScreen.kt` — estados del botón.
- `ui/eventos/ListadoAsistentesDialog.kt` — etiqueta por estado.
- `ui/admin/AdminPagosScreen.kt` — método por fila.
- Tests: `data/CuentasRepositoryImplTest.kt` (crear si no existe), `data/EventosRepositoryImplTest.kt`.

---

## Task 1: Migración V28 + enums + entidad `MovimientoCuenta`

**Files:**
- Create: `back/src/main/resources/db/migration/V28__saldo_cuentas.sql`
- Create: `back/src/main/java/com/baniterio/api/identidad/EstadoPagoCuota.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/OrigenMovimiento.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/MovimientoCuenta.java`
- Create: `back/src/main/java/com/baniterio/api/identidad/MovimientoCuentaRepository.java`
- Test: `back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaIT.java`

**Interfaces:**
- Produces:
  - `EstadoPagoCuota { PENDIENTE_PAGO, DECLARADO, CONFIRMADO_PENDIENTE_ENVIO, CONFIRMADO_EN_CUENTA }` con `String legible()`.
  - `OrigenMovimiento { SALDO_INICIAL, CUOTA, AJUSTE }`.
  - `MovimientoCuenta` entidad JPA (getters Lombok): `Long getId()`, `Cuenta getCuenta()`, `String getConcepto()`, `BigDecimal getImporte()`, `LocalDate getFecha()`, `OrigenMovimiento getOrigen()`, `Long getFichaAsistenciaId()`, `Usuario getCreadoPor()`, `Instant getCreatedAt()`.
  - `MovimientoCuentaRepository extends JpaRepository<MovimientoCuenta, Long>`:
    - `List<MovimientoCuenta> findByCuentaIdOrderByFechaAscIdAsc(Long cuentaId)`
    - `Optional<MovimientoCuenta> findByFichaAsistenciaId(Long fichaAsistenciaId)`
    - `boolean existsByFichaAsistenciaId(Long fichaAsistenciaId)`
    - `@Query("select coalesce(sum(m.importe),0) from MovimientoCuenta m where m.cuenta.id = :cuentaId") BigDecimal sumImporte(Long cuentaId)`

- [ ] **Step 1: Escribir la migración**

Crear `back/src/main/resources/db/migration/V28__saldo_cuentas.sql`:

```sql
-- Libro de movimientos por cuenta (estilo Excel del tesorero): la primera fila
-- es el saldo inicial, y cada cuota que llega a la cuenta de la peña añade una
-- fila. Los gastos, más adelante, serán filas negativas.
CREATE TABLE movimiento_cuenta (
    id                  BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cuenta_id           BIGINT       NOT NULL REFERENCES cuenta (id) ON DELETE CASCADE,
    concepto            VARCHAR(200) NOT NULL,
    importe             NUMERIC(9,2) NOT NULL,
    fecha               DATE         NOT NULL DEFAULT current_date,
    origen              VARCHAR(16)  NOT NULL,
    ficha_asistencia_id BIGINT       REFERENCES ficha_bebida (asistencia_id) ON DELETE SET NULL,
    creado_por          BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_movimiento_origen CHECK (origen IN ('SALDO_INICIAL', 'CUOTA', 'AJUSTE'))
);

CREATE INDEX ix_movimiento_cuenta ON movimiento_cuenta (cuenta_id, fecha, id);
CREATE UNIQUE INDEX ux_movimiento_ficha
    ON movimiento_cuenta (ficha_asistencia_id) WHERE ficha_asistencia_id IS NOT NULL;

-- Saldo inicial de San Miguel: lo que sobró del año anterior.
INSERT INTO movimiento_cuenta (cuenta_id, concepto, importe, fecha, origen)
SELECT c.id, 'Saldo del año anterior', 91.13, DATE '2026-01-01', 'SALDO_INICIAL'
FROM cuenta c
JOIN pena p ON p.id = c.pena_id
WHERE p.slug = 'baniterio' AND c.nombre = 'San Miguel'
  AND NOT EXISTS (
      SELECT 1 FROM movimiento_cuenta m
      WHERE m.cuenta_id = c.id AND m.origen = 'SALDO_INICIAL'
  );

-- Estado de pago de la cuota: cuatro niveles en vez del booleano `pagado`.
ALTER TABLE ficha_bebida ADD COLUMN estado_pago VARCHAR(28) NOT NULL DEFAULT 'PENDIENTE_PAGO';
UPDATE ficha_bebida
   SET estado_pago = CASE WHEN pagado THEN 'CONFIRMADO_EN_CUENTA' ELSE 'PENDIENTE_PAGO' END;
ALTER TABLE ficha_bebida DROP COLUMN pagado;
ALTER TABLE ficha_bebida ADD CONSTRAINT ck_ficha_estado_pago
    CHECK (estado_pago IN ('PENDIENTE_PAGO', 'DECLARADO',
                           'CONFIRMADO_PENDIENTE_ENVIO', 'CONFIRMADO_EN_CUENTA'));
```

- [ ] **Step 2: Crear los enums**

`EstadoPagoCuota.java`:

```java
package com.baniterio.api.identidad;

/** Estado de pago de la cuota de una asistencia (ver ficha_bebida.estado_pago, V28). */
public enum EstadoPagoCuota {
    PENDIENTE_PAGO,
    DECLARADO,
    CONFIRMADO_PENDIENTE_ENVIO,
    CONFIRMADO_EN_CUENTA;

    public String legible() {
        return switch (this) {
            case PENDIENTE_PAGO -> "Pendiente de pago";
            case DECLARADO -> "Pagado, pendiente de confirmar";
            case CONFIRMADO_PENDIENTE_ENVIO -> "Confirmado, pendiente de ingresar en la cuenta";
            case CONFIRMADO_EN_CUENTA -> "Confirmado y en la cuenta";
        };
    }

    /** true si el dinero cuenta como recibido (en cuenta o pendiente de ingresar). */
    public boolean confirmado() {
        return this == CONFIRMADO_PENDIENTE_ENVIO || this == CONFIRMADO_EN_CUENTA;
    }
}
```

`OrigenMovimiento.java`:

```java
package com.baniterio.api.identidad;

/** De dónde sale una fila del libro de movimientos (ver movimiento_cuenta, V28). */
public enum OrigenMovimiento {
    SALDO_INICIAL,
    CUOTA,
    AJUSTE
}
```

- [ ] **Step 3: Crear la entidad y el repositorio**

`MovimientoCuenta.java` — mismo patrón JPA + Lombok que `Cuenta`:

```java
package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

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
 * Una fila del libro de una cuenta (tabla {@code movimiento_cuenta}, V28).
 * {@code importe} positivo = ingreso, negativo = gasto. {@code fichaAsistenciaId}
 * apunta a la {@link FichaBebida} cuya cuota entró (solo en los de origen
 * {@code CUOTA}); es único, así que una cuota genera un movimiento como mucho.
 */
@Entity
@Table(name = "movimiento_cuenta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Column(nullable = false, length = 200)
    private String concepto;

    @Column(nullable = false, precision = 9, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrigenMovimiento origen;

    @Column(name = "ficha_asistencia_id")
    private Long fichaAsistenciaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

`MovimientoCuentaRepository.java`:

```java
package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimientoCuentaRepository extends JpaRepository<MovimientoCuenta, Long> {

    List<MovimientoCuenta> findByCuentaIdOrderByFechaAscIdAsc(Long cuentaId);

    Optional<MovimientoCuenta> findByFichaAsistenciaId(Long fichaAsistenciaId);

    boolean existsByFichaAsistenciaId(Long fichaAsistenciaId);

    @Query("select coalesce(sum(m.importe), 0) from MovimientoCuenta m where m.cuenta.id = :cuentaId")
    BigDecimal sumImporte(@Param("cuentaId") Long cuentaId);
}
```

- [ ] **Step 4: Añadir `estadoPago` a `FichaBebida` (para que compile)**

En `identidad/FichaBebida.java`, sustituir:

```java
    @Column(nullable = false)
    private boolean pagado;
```

por:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 28)
    private EstadoPagoCuota estadoPago;
```

Esto rompe la compilación en los sitios que llaman `f.isPagado()` / `f.setPagado(...)`. Se arreglan en la Task 2. Para **esta** task, comprobar solo que compila el módulo `identidad` no es posible aislado; se deja el arreglo del resto para Task 2 y aquí se verifica únicamente con el IT nuevo tras Task 2. **Cambiar el orden:** hacer el Step 4 en la Task 2. (Ver nota abajo.)

> **Nota de ejecución:** el Step 4 se mueve a la Task 2 para no dejar el árbol sin compilar entre tareas. En la Task 1, `FichaBebida` NO se toca; la columna `pagado` desaparece de la BBDD pero el mapping JPA seguirá apuntando a `pagado` y **fallará al arrancar**. Por eso la Task 1 y la Task 2 se validan juntas: ejecutar el IT de la Task 1 solo después de completar la Task 2. Si se usa subagent-driven-development, tratar Task 1+2 como un bloque.

- [ ] **Step 5: Escribir el IT de la migración (se ejecuta tras la Task 2)**

`back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaIT.java` — empezar solo con el caso de la semilla:

```java
package com.baniterio.api.cuenta;

import com.baniterio.api.identidad.CuentaRepository;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** IT del libro de movimientos y el saldo de una cuenta (V28). */
class MovimientoCuentaIT extends IntegrationTest {

    @Autowired CuentaRepository cuentas;
    @Autowired MovimientoCuentaRepository movimientos;

    @Test
    void la_migracion_siembra_el_saldo_inicial_de_san_miguel() {
        Long sanMiguel = cuentas.findByPenaIdAndNombre(
                cuentas.findAll().get(0).getPena().getId(), "San Miguel").orElseThrow().getId();

        var filas = movimientos.findByCuentaIdOrderByFechaAscIdAsc(sanMiguel);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getOrigen()).isEqualTo(OrigenMovimiento.SALDO_INICIAL);
        assertThat(filas.get(0).getImporte()).isEqualByComparingTo(new BigDecimal("91.13"));
        assertThat(movimientos.sumImporte(sanMiguel)).isEqualByComparingTo(new BigDecimal("91.13"));
    }
}
```

> Si `CuentaRepository` no tiene `findByPenaIdAndNombre`, usarlo igualmente — sí existe (lo usa `PagoDeclaradoIT`). Para obtener la peña, más simple: `@Autowired PenaRepository penas;` y `penas.findBySlug("baniterio").orElseThrow().getId()`.

- [ ] **Step 6: Verificar (bloque Task 1+2)**

Run: `./mvnw -Dtest=MovimientoCuentaIT verify -pl :api` (o `./mvnw -Dtest=MovimientoCuentaIT test` si el proyecto es de un solo módulo).
Expected: PASS, tras completar la Task 2.

- [ ] **Step 7: Commit** (junto con Task 2, ver Task 2 Step final).

---

## Task 2: `ficha_bebida.pagado` → `estadoPago` en todo el backend

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/identidad/FichaBebida.java` (Step 4 de Task 1)
- Modify: `back/src/main/java/com/baniterio/api/evento/dto/AsistenteFila.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/dto/FichaBebidaDetalle.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/FichaBebidaService.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaService.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/PagoDeclaradoService.java`
- Modify: `back/src/test/java/com/baniterio/api/evento/PagoDeclaradoIT.java`
- Modify: `back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java`
- Modify: `back/src/test/java/com/baniterio/api/identidad/FichaBebidaPagoIT.java`

**Interfaces:**
- Consumes: `EstadoPagoCuota` (Task 1).
- Produces:
  - `AsistenteFila(String nombre, String estado, boolean esManual, BebidaFila bebida, BigDecimal cuota, String estadoPago, Long asistenciaId, String metodoPago, String pagadoPor, Instant pagadoAt)` — quita `boolean pagado` y `boolean declarado`, añade `String estadoPago`.
  - `FichaBebidaDetalle.MiFicha(..., String modalidad, BigDecimal cuota, boolean cuotaPendiente, boolean bebidaPendiente, String estadoPago, String metodoPago, String pagadoPor, Instant pagadoAt, MiPagoDeclarado miPagoDeclarado)` — `boolean pagado` → `String estadoPago`.
  - `AsistenciaService.aFila(AsistenciaEvento a, FichaBebida f)` — vuelve a 2 args (se quita el `boolean declarado`).

En esta task el comportamiento se mantiene idéntico: `estado_pago` solo toma los valores `PENDIENTE_PAGO` / `DECLARADO` / `CONFIRMADO_EN_CUENTA` como hasta ahora (el reparto por método es la Task 4).

- [ ] **Step 1: Cambiar la entidad**

Aplicar el Step 4 de la Task 1 en `identidad/FichaBebida.java` (import `EstadoPagoCuota` está en el mismo paquete, no hace falta import). Añadir `import jakarta.persistence.Enumerated;` y `import jakarta.persistence.EnumType;` si no están (ya están).

- [ ] **Step 2: `AsistenteFila` y `aFila`**

En `evento/dto/AsistenteFila.java` dejar el record así:

```java
public record AsistenteFila(
        String nombre,
        String estado,
        boolean esManual,
        BebidaFila bebida,
        BigDecimal cuota,
        String estadoPago,
        Long asistenciaId,
        String metodoPago,
        String pagadoPor,
        Instant pagadoAt) {
}
```

En `evento/AsistenciaService.java`:
- `aFila` pasa a 2 args y usa `f.getEstadoPago()`:

```java
    private static AsistenteFila aFila(AsistenciaEvento a, FichaBebida f) {
        BebidaFila bebida = f == null ? null : new BebidaFila(
                f.getAlcohol() != null ? f.getAlcohol().getNombre() : null,
                f.getRefresco().getNombre(),
                f.getAlternativa().name(),
                f.getModalidad().name());
        EstadoPagoCuota estado = f == null ? null : f.getEstadoPago();
        boolean confirmado = estado != null && estado.confirmado();
        return new AsistenteFila(nombreDe(a), a.getEstado().name(), a.getUsuario() == null,
                bebida, f == null ? null : f.getCuota(),
                estado == null ? null : estado.name(), a.getId(),
                confirmado && f.getMetodoPago() != null ? f.getMetodoPago().name() : null,
                confirmado && f.getPagadoConfirmadoPor() != null
                        ? f.getPagadoConfirmadoPor().getNombre() : null,
                confirmado ? f.getPagadoAt() : null);
    }
```

- En `listadoAsistentes`:
  - Quitar `java.util.Set<Long> declaradas = pagoDeclarado.asistenciasConDeclaracionPendiente(eventoId);` y el `declaradas.contains(a.getId())` del `.map(...)` → `aFila(a, fichaPorAsistencia.get(a.getId()))`.
  - `totalPagado` pasa a sumar lo confirmado:

```java
        BigDecimal totalPagado = filas.stream()
                .map(a -> fichaPorAsistencia.get(a.getId()))
                .filter(f -> f != null && f.getEstadoPago() != null && f.getEstadoPago().confirmado())
                .map(FichaBebida::getCuota).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
```

  (ojo: `AsistenteFila` ya no tiene `pagado()`, por eso se recalcula desde las fichas).

- [ ] **Step 3: `confirmarPago` / `deshacerPago` (comportamiento equivalente, sin método-split todavía)**

En `AsistenciaService.confirmarPago`, sustituir `f.setPagado(true);` por `f.setEstadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);`. En `deshacerPago`, `f.setPagado(false);` → `f.setEstadoPago(EstadoPagoCuota.PENDIENTE_PAGO);`. (El movimiento se añade en la Task 4.)

- [ ] **Step 4: `MiFicha` y `aMiFicha`**

En `evento/dto/FichaBebidaDetalle.java`, en el record `MiFicha` cambiar `boolean pagado` → `String estadoPago` (mismo sitio).

En `evento/FichaBebidaService.aMiFicha`:

```java
    private static FichaBebidaDetalle.MiFicha aMiFicha(FichaBebida f,
            com.baniterio.api.evento.dto.MiPagoDeclarado miPagoDeclarado) {
        Bebida al = f.getAlcohol();
        Bebida re = f.getRefresco();
        boolean bebidaPendiente = (al != null && al.getEstado() != EstadoBebida.ACEPTADA)
                || re.getEstado() != EstadoBebida.ACEPTADA;
        boolean confirmado = f.getEstadoPago() != null && f.getEstadoPago().confirmado();
        return new FichaBebidaDetalle.MiFicha(
                al != null ? al.getId() : null,
                al != null ? al.getNombre() : "No bebo alcohol",
                re.getId(), re.getNombre(),
                f.getAlternativa().name(), f.getCervezaEspecial(),
                f.isEmbarazada(), f.isAsisteDia1(), f.isAsisteDia2(),
                f.getModalidad().name(), f.getCuota(), f.getCuota() == null, bebidaPendiente,
                f.getEstadoPago() == null ? null : f.getEstadoPago().name(),
                confirmado && f.getMetodoPago() != null ? f.getMetodoPago().name() : null,
                confirmado && f.getPagadoConfirmadoPor() != null
                        ? f.getPagadoConfirmadoPor().getNombre() : null,
                confirmado ? f.getPagadoAt() : null,
                miPagoDeclarado);
    }
```

- [ ] **Step 5: `PagoDeclaradoService` — compilar (sin lógica nueva todavía)**

En `PagoDeclaradoService.marcarPagadas`, sustituir `f.setPagado(true);` por `f.setEstadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);`. El resto de la lógica de método-split es la Task 4.

- [ ] **Step 6: Compilar**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS. Si algún `.isPagado()` / `.setPagado(` quedó suelto, el compilador lo señala — arreglarlo leyendo `estadoPago`.

- [ ] **Step 7: Ajustar los ITs existentes que afirman `pagado`**

En `PagoDeclaradoIT.java`:
- `admin_confirma_marca_la_ficha_pagada`: `assertThat(f.isPagado()).isTrue()` → `assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA)` (import `com.baniterio.api.identidad.EstadoPagoCuota`). Y `jsonPath("$.asistencia.ficha.miFicha.pagado").isEqualTo(true)` → `jsonPath("$.asistencia.ficha.miFicha.estadoPago").isEqualTo("CONFIRMADO_EN_CUENTA")`.
- `admin_rechaza_deja_la_cuota_pendiente_y_marca_RECHAZADA`: `assertThat(f.isPagado()).isFalse()` → `assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.PENDIENTE_PAGO)`.
- `declarar_cubriendo_a_la_pareja_confirma_marca_las_dos_fichas`: los dos `assertThat(f.isPagado()).isTrue()` → `assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA)`.

En `ConfirmacionPagoIT.java` y `FichaBebidaPagoIT.java`: buscar `pagado` (`grep -n "pagado\|isPagado\|\"pagado\"" ...`) y sustituir por la comprobación equivalente de `estadoPago` (`"PENDIENTE_PAGO"` / `"CONFIRMADO_EN_CUENTA"`; si un caso confirma un pago por BIZUM/EFECTIVO y aún no está la Task 4, seguirá dando `CONFIRMADO_EN_CUENTA` — se re-ajusta en Task 4).

- [ ] **Step 8: Verificar la suite de pago + el IT de Task 1**

Run: `./mvnw -Dtest='MovimientoCuentaIT,PagoDeclaradoIT,ConfirmacionPagoIT,FichaBebidaPagoIT,ListadoAsistentesIT,FichaBebidaIT' verify`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add back/src/main/resources/db/migration/V28__saldo_cuentas.sql back/src/main/java/com/baniterio/api/identidad back/src/main/java/com/baniterio/api/evento back/src/test/java/com/baniterio/api
git commit -m "feat(cuentas): estado_pago de 4 niveles y tabla movimiento_cuenta (V28)"
```

---

## Task 3: `MovimientoCuentaService`

**Files:**
- Create: `back/src/main/java/com/baniterio/api/cuenta/MovimientoCuentaService.java`
- Test: `back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaIT.java` (añadir casos)

**Interfaces:**
- Consumes: `MovimientoCuentaRepository`, `FichaBebida` (con `getAsistencia().getEvento().getCuenta()`), `EstadoPagoCuota`.
- Produces:
  - `void registrarCuota(FichaBebida ficha, Usuario admin)` — inserta un `MovimientoCuenta` `origen=CUOTA`, `importe = ficha.getCuota()`, `concepto = "Cuota de " + nombre + " — " + evento`, `cuenta = ficha.getAsistencia().getEvento().getCuenta()`, `fichaAsistenciaId = ficha.getAsistenciaId()`, `fecha = LocalDate.now()`, `creadoPor = admin`. No-op si `existsByFichaAsistenciaId`.
  - `void revertirCuota(FichaBebida ficha)` — borra el movimiento de esa ficha si existe.
  - `BigDecimal saldo(Long cuentaId)` — `repo.sumImporte(cuentaId)`.

- [ ] **Step 1: Escribir el test (caso registrar)**

Añadir a `MovimientoCuentaIT` (necesita helpers para crear evento de San Miguel + ficha; copiar el patrón de `PagoDeclaradoIT` — miembro, `sanMiguel()`, `apuntarConFicha()`). Como es mucho andamiaje, **este test se hace vía HTTP en la Task 5** (confirmar un pago y ver el movimiento). Para la Task 3, un test unitario con mocks:

`back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaServiceTest.java`:

```java
package com.baniterio.api.cuenta;

import com.baniterio.api.identidad.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MovimientoCuentaServiceTest {

    private final MovimientoCuentaRepository repo = mock(MovimientoCuentaRepository.class);
    private final MovimientoCuentaService service = new MovimientoCuentaService(repo);

    private FichaBebida ficha(long asisId, String cuota) {
        Cuenta cuenta = Cuenta.builder().id(9L).nombre("San Miguel").build();
        Evento e = Evento.builder().id(3L).nombre("San Miguel 2026").cuenta(cuenta).build();
        AsistenciaEvento a = AsistenciaEvento.builder().id(asisId).evento(e)
                .usuario(Usuario.builder().id(1L).nombre("Rober").build()).build();
        return FichaBebida.builder().asistenciaId(asisId).asistencia(a)
                .cuota(new BigDecimal(cuota)).estadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA).build();
    }

    @Test
    void registrarCuota_inserta_un_movimiento_con_el_importe_de_la_cuota() {
        when(repo.existsByFichaAsistenciaId(7L)).thenReturn(false);

        service.registrarCuota(ficha(7L, "45.00"), null);

        verify(repo).save(argThat(m ->
                m.getImporte().compareTo(new BigDecimal("45.00")) == 0
                        && m.getOrigen() == OrigenMovimiento.CUOTA
                        && m.getFichaAsistenciaId().equals(7L)
                        && m.getCuenta().getId().equals(9L)));
    }

    @Test
    void registrarCuota_no_duplica_si_ya_hay_movimiento_para_esa_ficha() {
        when(repo.existsByFichaAsistenciaId(7L)).thenReturn(true);
        service.registrarCuota(ficha(7L, "45.00"), null);
        verify(repo, never()).save(any());
    }

    @Test
    void revertirCuota_borra_el_movimiento_si_existe() {
        MovimientoCuenta m = MovimientoCuenta.builder().id(1L).build();
        when(repo.findByFichaAsistenciaId(7L)).thenReturn(Optional.of(m));
        service.revertirCuota(ficha(7L, "45.00"));
        verify(repo).delete(m);
    }
}
```

- [ ] **Step 2: Ejecutar el test, verlo fallar**

Run: `./mvnw -Dtest=MovimientoCuentaServiceTest test`
Expected: FAIL (no existe `MovimientoCuentaService`).

- [ ] **Step 3: Implementar el servicio**

```java
package com.baniterio.api.cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baniterio.api.identidad.AsistenciaEvento;
import com.baniterio.api.identidad.FichaBebida;
import com.baniterio.api.identidad.MovimientoCuenta;
import com.baniterio.api.identidad.MovimientoCuentaRepository;
import com.baniterio.api.identidad.OrigenMovimiento;
import com.baniterio.api.identidad.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe el libro de movimientos de una cuenta. Servicio fino: solo depende del
 * repositorio, así que lo pueden inyectar {@code AsistenciaService},
 * {@code PagoDeclaradoService} y {@code CuentaService} sin ciclos.
 */
@Service
public class MovimientoCuentaService {

    private final MovimientoCuentaRepository movimientos;

    public MovimientoCuentaService(MovimientoCuentaRepository movimientos) {
        this.movimientos = movimientos;
    }

    /** Registra en el libro que la cuota de {@code ficha} ha entrado en la cuenta. Idempotente por ficha. */
    @Transactional
    public void registrarCuota(FichaBebida ficha, Usuario admin) {
        Long asistenciaId = ficha.getAsistenciaId();
        if (movimientos.existsByFichaAsistenciaId(asistenciaId)) {
            return;
        }
        AsistenciaEvento a = ficha.getAsistencia();
        String quien = a.getUsuario() != null ? a.getUsuario().getNombre() : a.getNombre();
        movimientos.save(MovimientoCuenta.builder()
                .cuenta(a.getEvento().getCuenta())
                .concepto("Cuota de " + quien + " — " + a.getEvento().getNombre())
                .importe(ficha.getCuota())
                .fecha(LocalDate.now())
                .origen(OrigenMovimiento.CUOTA)
                .fichaAsistenciaId(asistenciaId)
                .creadoPor(admin)
                .build());
    }

    /** Deshace {@link #registrarCuota} (cuando un admin deshace un pago). */
    @Transactional
    public void revertirCuota(FichaBebida ficha) {
        movimientos.findByFichaAsistenciaId(ficha.getAsistenciaId())
                .ifPresent(movimientos::delete);
    }

    @Transactional(readOnly = true)
    public BigDecimal saldo(Long cuentaId) {
        return movimientos.sumImporte(cuentaId);
    }
}
```

- [ ] **Step 4: Ejecutar el test, verlo pasar**

Run: `./mvnw -Dtest=MovimientoCuentaServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add back/src/main/java/com/baniterio/api/cuenta/MovimientoCuentaService.java back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaServiceTest.java
git commit -m "feat(cuentas): MovimientoCuentaService (registrar/revertir cuota, saldo)"
```

---

## Task 4: Máquina de estados — declarar/anular/rechazar/confirmar + atajo + método-split

**Files:**
- Modify: `back/src/main/java/com/baniterio/api/evento/PagoDeclaradoService.java`
- Modify: `back/src/main/java/com/baniterio/api/evento/AsistenciaService.java`
- Modify: `back/src/test/java/com/baniterio/api/evento/PagoDeclaradoIT.java`
- Modify: `back/src/test/java/com/baniterio/api/evento/ConfirmacionPagoIT.java`

**Interfaces:**
- Consumes: `MovimientoCuentaService` (Task 3), `EstadoPagoCuota`, `MetodoPago`.
- Produces: comportamiento del enum de transiciones del spec (tabla "Transiciones").

- [ ] **Step 1: Tests nuevos en `PagoDeclaradoIT`**

Añadir imports `EstadoPagoCuota`, `MovimientoCuentaRepository`, `@Autowired MovimientoCuentaRepository movimientos;`. En `@AfterEach`, antes de `fichas.deleteAllInBatch()`, añadir `movimientos.deleteAll(movimientos.findAll().stream().filter(m -> m.getFichaAsistenciaId() != null).toList());` (deja el `SALDO_INICIAL` sembrado).

```java
    @Test
    void declarar_pone_la_ficha_en_DECLARADO() {
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());
        assertThat(fichas.findByAsistenciaId(asisId)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.DECLARADO));
    }

    @Test
    void anular_vuelve_la_ficha_a_PENDIENTE_PAGO() {
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 16.0, List.of());
        http.delete().uri("/api/v1/eventos/" + e.getId() + "/pagos-declarados/mia")
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk();
        assertThat(fichas.findByAsistenciaId(asisId)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.PENDIENTE_PAGO));
    }

    @Test
    void confirmar_por_transferencia_deja_la_ficha_en_cuenta_y_crea_movimiento() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "TRANSFERENCIA", 26.0, List.of());
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();

        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/confirmar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(fichas.findByAsistenciaId(asisId)).get()
                .satisfies(f -> assertThat(f.getEstadoPago()).isEqualTo(EstadoPagoCuota.CONFIRMADO_EN_CUENTA));
        assertThat(movimientos.findByFichaAsistenciaId(asisId)).isPresent();
    }

    @Test
    void confirmar_por_bizum_deja_la_ficha_pendiente_de_envio_sin_movimiento() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        Long asisId = apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 26.0, List.of());
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();

        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/confirmar")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isNoContent();

        assertThat(fichas.findByAsistenciaId(asisId)).get()
                .satisfies(f -> assertThat(f.getEstadoPago())
                        .isEqualTo(EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO));
        assertThat(movimientos.findByFichaAsistenciaId(asisId)).isEmpty();
    }
```

Ajustar `admin_confirma_marca_la_ficha_pagada` (ahora declara TRANSFERENCIA → sigue siendo `CONFIRMADO_EN_CUENTA`, ok) y `el_atajo_del_modal_asistentes_cierra_la_declaracion_pendiente` (usa EFECTIVO → la ficha queda `CONFIRMADO_PENDIENTE_ENVIO`; añadir esa comprobación).

- [ ] **Step 2: Ejecutar, ver fallar**

Run: `./mvnw -Dtest=PagoDeclaradoIT verify`
Expected: FAIL en los casos nuevos (la ficha queda en `CONFIRMADO_EN_CUENTA` siempre, no hay `DECLARADO` al declarar).

- [ ] **Step 3: Implementar en `PagoDeclaradoService`**

- Inyectar `MovimientoCuentaService movimientoCuenta` (nuevo parámetro del constructor, al final).
- En `declarar`, tras crear la `PagoDeclarado`, poner cada ficha cubierta en `DECLARADO`:

```java
        for (Long asistenciaId : cubre) {
            fichas.findByAsistenciaId(asistenciaId).ifPresent(f -> {
                f.setEstadoPago(EstadoPagoCuota.DECLARADO);
                fichas.save(f);
            });
        }
```

- En `anularMia` y en `rechazar`, tras resolver, poner las fichas cubiertas de vuelta a `PENDIENTE_PAGO`. Refactor: método `private void ponerEstado(Set<Long> asistenciaIds, EstadoPagoCuota estado)`. Para `anularMia` hace falta el `Set<Long> cubre` antes de borrar: `Set<Long> cubre = new LinkedHashSet<>(p.getCubre());` luego `pagos.delete(p);` y `ponerEstado(cubre, PENDIENTE_PAGO)`.
- Sustituir `marcarPagadas` por un método que reparte por método:

```java
    private void aplicarConfirmacion(Set<Long> asistenciaIds, MetodoPago metodo, Usuario admin) {
        Instant ahora = Instant.now();
        EstadoPagoCuota destino = metodo == MetodoPago.TRANSFERENCIA
                ? EstadoPagoCuota.CONFIRMADO_EN_CUENTA
                : EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO;
        for (Long asistenciaId : asistenciaIds) {
            FichaBebida f = fichas.findByAsistenciaId(asistenciaId).orElseThrow(FichaSinCuotaException::new);
            f.setEstadoPago(destino);
            f.setMetodoPago(metodo);
            f.setPagadoConfirmadoPor(admin);
            f.setPagadoAt(ahora);
            fichas.save(f);
            if (destino == EstadoPagoCuota.CONFIRMADO_EN_CUENTA) {
                movimientoCuenta.registrarCuota(f, admin);
            }
        }
    }
```

  y en `confirmar` llamar `aplicarConfirmacion(p.getCubre(), p.getMetodoPago(), admin);`.
- `confirmarPorAtajo`: solo resuelve la `PagoDeclarado` (no toca fichas — eso lo hace el atajo en `AsistenciaService`). Sin cambios de firma.
- Se puede borrar `asistenciasConDeclaracionPendiente` (ya no se llama desde `AsistenciaService`; comprobar con grep que nadie más la usa).

- [ ] **Step 4: Implementar el atajo en `AsistenciaService`**

- Inyectar `MovimientoCuentaService movimientoCuenta`.
- `confirmarPago`:

```java
        Usuario admin = usuarios.findById(usuarioId).orElseThrow();
        EstadoPagoCuota destino = metodo == MetodoPago.TRANSFERENCIA
                ? EstadoPagoCuota.CONFIRMADO_EN_CUENTA
                : EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO;
        f.setEstadoPago(destino);
        f.setMetodoPago(metodo);
        f.setPagadoConfirmadoPor(admin);
        f.setPagadoAt(Instant.now());
        fichas.save(f);
        if (destino == EstadoPagoCuota.CONFIRMADO_EN_CUENTA) {
            movimientoCuenta.registrarCuota(f, admin);
        }
        pagoDeclarado.confirmarPorAtajo(eventoId, asistenciaId, admin);
```

- `deshacerPago`:

```java
        f.setEstadoPago(EstadoPagoCuota.PENDIENTE_PAGO);
        f.setMetodoPago(null);
        f.setPagadoConfirmadoPor(null);
        f.setPagadoAt(null);
        fichas.save(f);
        movimientoCuenta.revertirCuota(f);
```

- [ ] **Step 5: Ajustar `ConfirmacionPagoIT`**

Los casos que confirman por `EFECTIVO`/`BIZUM` ahora esperan `CONFIRMADO_PENDIENTE_ENVIO` (o, si comprueban vía JSON en el listado de asistentes, `estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"`). Los de `TRANSFERENCIA` → `CONFIRMADO_EN_CUENTA`. El "deshacer" → `PENDIENTE_PAGO`.

- [ ] **Step 6: Verificar**

Run: `./mvnw -Dtest='PagoDeclaradoIT,ConfirmacionPagoIT,MovimientoCuentaServiceTest,ListadoAsistentesIT' verify`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add back/src/main/java/com/baniterio/api/evento back/src/test/java/com/baniterio/api/evento
git commit -m "feat(cuentas): transiciones de estado_pago y reparto por método al confirmar"
```

---

## Task 5: `CuentaService.detalle` ampliado + `marcarTransferido` + endpoint

**Files:**
- Create: `back/src/main/java/com/baniterio/api/cuenta/dto/MovimientoFila.java`
- Create: `back/src/main/java/com/baniterio/api/cuenta/dto/CuentaDetalle.java`
- Modify: `back/src/main/java/com/baniterio/api/identidad/FichaBebidaRepository.java`
- Modify: `back/src/main/java/com/baniterio/api/cuenta/CuentaService.java`
- Modify: `back/src/main/java/com/baniterio/api/cuenta/CuentaController.java`
- Modify: `back/src/test/java/com/baniterio/api/cuenta/CuentaIT.java`
- Modify: `back/src/test/java/com/baniterio/api/cuenta/MovimientoCuentaIT.java`

**Interfaces:**
- Consumes: `MovimientoCuentaService`, `MovimientoCuentaRepository`, `FichaBebidaRepository`, `ServicioPermisos.esAdministrador(Long)`, `EstadoPagoCuota`.
- Produces:
  - `record MovimientoFila(String concepto, BigDecimal importe, LocalDate fecha, BigDecimal saldoTras)`
  - `record CuentaDetalle(Long id, String nombre, String descripcion, BigDecimal saldo, BigDecimal estimacion, List<MovimientoFila> movimientos, boolean puedoGestionar, BigDecimal porIngresar)`
  - `CuentaService.detalle(Long usuarioId, Long cuentaId): CuentaDetalle`
  - `CuentaService.marcarTransferido(Long adminId, Long cuentaId): CuentaDetalle`
  - `POST /api/v1/cuentas/{id}/transferencia-a-pena` → `CuentaDetalle`; 403 `SIN_PERMISO` si no admin.

- [ ] **Step 1: Consultas en `FichaBebidaRepository`**

```java
    @Query("""
        select f from FichaBebida f
        where f.estadoPago = :estado
          and f.asistencia.evento.cuenta.id = :cuentaId
        """)
    List<FichaBebida> findByEstadoYCuenta(@Param("estado") EstadoPagoCuota estado,
                                          @Param("cuentaId") Long cuentaId);

    @Query("""
        select coalesce(sum(f.cuota), 0) from FichaBebida f
        where f.asistencia.evento.cuenta.id = :cuentaId
          and f.cuota is not null
          and f.asistencia.estado in (com.baniterio.api.identidad.EstadoAsistencia.APUNTADO,
                                      com.baniterio.api.identidad.EstadoAsistencia.EN_DUDA)
          and f.estadoPago <> com.baniterio.api.identidad.EstadoPagoCuota.CONFIRMADO_EN_CUENTA
        """)
    BigDecimal sumaCuotasPorEntrar(@Param("cuentaId") Long cuentaId);

    @Query("""
        select coalesce(sum(f.cuota), 0) from FichaBebida f
        where f.estadoPago = com.baniterio.api.identidad.EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO
          and f.asistencia.evento.cuenta.id = :cuentaId
        """)
    BigDecimal sumaPorIngresar(@Param("cuentaId") Long cuentaId);
```

Imports necesarios: `java.math.BigDecimal`, `java.util.List`, `EstadoPagoCuota`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`.

- [ ] **Step 2: DTOs**

`cuenta/dto/MovimientoFila.java`:

```java
package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla. */
public record MovimientoFila(String concepto, BigDecimal importe, LocalDate fecha, BigDecimal saldoTras) {
}
```

`cuenta/dto/CuentaDetalle.java`:

```java
package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de una cuenta. {@code saldo} = suma del libro; {@code estimacion} = lo
 * que habrá cuando todos paguen. {@code porIngresar} (lo que el admin ha cobrado
 * y aún no ha pasado a la cuenta) es {@code null} si {@code !puedoGestionar}.
 */
public record CuentaDetalle(
        Long id, String nombre, String descripcion,
        BigDecimal saldo, BigDecimal estimacion,
        List<MovimientoFila> movimientos,
        boolean puedoGestionar, BigDecimal porIngresar) {
}
```

- [ ] **Step 3: Test en `MovimientoCuentaIT` (vía HTTP)**

Añadir a `MovimientoCuentaIT` el andamiaje (copiar de `PagoDeclaradoIT`: `crearMiembro`, `sanMiguel`, `apuntarConFicha`, `declarar`, `@AfterEach`). Casos:

```java
    @Test
    void detalle_recien_migrado_saldo_9113_y_un_movimiento() {
        String token = crearMiembro(RolMembresia.MIEMBRO).token();
        Long cuentaId = cuentaSanMiguelId();

        http.get().uri("/api/v1/cuentas/" + cuentaId)
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(91.13)
                .jsonPath("$.movimientos.length()").isEqualTo(1)
                .jsonPath("$.puedoGestionar").isEqualTo(false)
                .jsonPath("$.porIngresar").doesNotExist();
    }

    @Test
    void estimacion_cuenta_a_los_apuntados_sin_pagar() {
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();          // cuota derivada de 26 → 26.00
        apuntarConFicha(penista, e.getId());

        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + penista.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(91.13)
                .jsonPath("$.estimacion").isEqualTo(117.13);
    }

    @Test
    void confirmar_bizum_sube_por_ingresar_no_el_saldo_y_marcar_transferido_lo_pasa() {
        Sesion admin = crearMiembro(RolMembresia.ADMIN);
        Sesion penista = crearMiembro(RolMembresia.MIEMBRO);
        Evento e = sanMiguel();
        apuntarConFicha(penista, e.getId());
        declarar(penista, e.getId(), "BIZUM", 26.0, List.of());
        Long pagoId = pagos.findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado.PENDIENTE).get(0).getId();
        http.post().uri("/api/v1/admin/pagos-declarados/" + pagoId + "/confirmar")
                .header(AUTHORIZATION, "Bearer " + admin.token()).exchange().expectStatus().isNoContent();

        http.get().uri("/api/v1/cuentas/" + cuentaSanMiguelId())
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(91.13)
                .jsonPath("$.porIngresar").isEqualTo(26.0);

        http.post().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "/transferencia-a-pena")
                .header(AUTHORIZATION, "Bearer " + admin.token())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saldo").isEqualTo(117.13)
                .jsonPath("$.porIngresar").isEqualTo(0.0);
    }

    @Test
    void no_admin_no_puede_marcar_transferido_403() {
        String token = crearMiembro(RolMembresia.MIEMBRO).token();
        http.post().uri("/api/v1/cuentas/" + cuentaSanMiguelId() + "/transferencia-a-pena")
                .header(AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.codigo").isEqualTo("SIN_PERMISO");
    }
```

Helper: `Long cuentaSanMiguelId() { return cuentas.findByPenaIdAndNombre(pena().getId(), "San Miguel").orElseThrow().getId(); }`. En `@AfterEach` borrar los movimientos con `fichaAsistenciaId != null` y las fichas/asistencias/eventos `IT-*`.

- [ ] **Step 4: Ejecutar, ver fallar**

Run: `./mvnw -Dtest=MovimientoCuentaIT verify`
Expected: FAIL (el endpoint devuelve `CuentaResumen`, no hay `saldo`).

- [ ] **Step 5: Implementar `CuentaService`**

- Inyectar `MovimientoCuentaService movimientoCuenta`, `MovimientoCuentaRepository movimientos`, `FichaBebidaRepository fichas`, `ServicioPermisos permisos`, `UsuarioRepository usuarios`.
- Nueva excepción de permiso: reutilizar `com.baniterio.api.admin.SinPermisoException` (ya mapeada a 403 `SIN_PERMISO`).

```java
    @Transactional(readOnly = true)
    public CuentaDetalle detalle(Long usuarioId, Long cuentaId) {
        Cuenta c = cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        boolean admin = permisos.esAdministrador(usuarioId);

        BigDecimal saldo = BigDecimal.ZERO;
        List<MovimientoFila> filas = new ArrayList<>();
        for (MovimientoCuenta m : movimientos.findByCuentaIdOrderByFechaAscIdAsc(cuentaId)) {
            saldo = saldo.add(m.getImporte());
            filas.add(new MovimientoFila(m.getConcepto(), m.getImporte(), m.getFecha(), saldo));
        }
        BigDecimal estimacion = saldo.add(fichas.sumaCuotasPorEntrar(cuentaId));
        BigDecimal porIngresar = admin ? fichas.sumaPorIngresar(cuentaId) : null;

        return new CuentaDetalle(c.getId(), c.getNombre(), c.getDescripcion(),
                saldo, estimacion, filas, admin, porIngresar);
    }

    @Transactional
    public CuentaDetalle marcarTransferido(Long adminId, Long cuentaId) {
        if (!permisos.esAdministrador(adminId)) {
            throw new com.baniterio.api.admin.SinPermisoException();
        }
        cuentas.findById(cuentaId).orElseThrow(CuentaNoEncontradaException::new);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        for (FichaBebida f : fichas.findByEstadoYCuenta(EstadoPagoCuota.CONFIRMADO_PENDIENTE_ENVIO, cuentaId)) {
            f.setEstadoPago(EstadoPagoCuota.CONFIRMADO_EN_CUENTA);
            this.fichas.save(f);
            movimientoCuenta.registrarCuota(f, admin);
        }
        return detalle(adminId, cuentaId);
    }
```

(el `detalle()` viejo de un solo argumento se elimina; ajustar la llamada del controller.)

- [ ] **Step 6: Controller**

```java
    @GetMapping("/{id}")
    public CuentaDetalle detalle(@AuthenticationPrincipal UsuarioPrincipal principal, @PathVariable Long id) {
        return cuentaService.detalle(principal.id(), id);
    }

    @PostMapping("/{id}/transferencia-a-pena")
    public CuentaDetalle marcarTransferido(@AuthenticationPrincipal UsuarioPrincipal principal,
                                           @PathVariable Long id) {
        return cuentaService.marcarTransferido(principal.id(), id);
    }
```

Imports: mirar cómo lo hace `PagoDeclaradoAdminController` / `AsistenciaController` (`@AuthenticationPrincipal`, el tipo del principal y `.id()`). Copiar exactamente ese patrón.

- [ ] **Step 7: Ejecutar**

Run: `./mvnw -Dtest='MovimientoCuentaIT,CuentaIT' verify`
Expected: PASS. `CuentaIT.detalle_devuelve_nombre_y_descripcion` sigue verde (los campos nombre/descripcion siguen). Si ese test creaba una cuenta suelta y ahora `detalle` necesita más, comprobar que no rompe (no debería: saldo 0, sin movimientos, estimación 0).

- [ ] **Step 8: Suite backend completa**

Run: `./mvnw verify`
Expected: BUILD SUCCESS. Revisar `EventoIT`, `FichaBebidaIT`, `AsistenciaIT` (1 `@Disabled` conocido).

- [ ] **Step 9: Commit**

```bash
git add back/src/main/java/com/baniterio/api/cuenta back/src/main/java/com/baniterio/api/identidad/FichaBebidaRepository.java back/src/test/java/com/baniterio/api/cuenta
git commit -m "feat(cuentas): detalle con saldo/estimación/movimientos y POST transferencia-a-pena"
```

---

## Task 6: Web — tipos y servicio

**Files:**
- Modify: `front/src/app/panel/cuentas/cuentas.types.ts`
- Modify: `front/src/app/panel/cuentas/cuentas.service.ts`
- Modify: `front/src/app/panel/eventos/eventos.types.ts`
- Modify: `front/src/app/panel/cuentas/cuentas.service.spec.ts`

**Interfaces:**
- Produces: `EstadoPagoCuota`, `MovimientoFila`, `CuentaDetalle` (en `cuentas.types.ts`); `CuentasService.detalle(id): Observable<CuentaDetalle>`, `CuentasService.marcarTransferido(id): Observable<CuentaDetalle>`.

- [ ] **Step 1: `cuentas.types.ts`**

```ts
export type EstadoPagoCuota =
  | 'PENDIENTE_PAGO'
  | 'DECLARADO'
  | 'CONFIRMADO_PENDIENTE_ENVIO'
  | 'CONFIRMADO_EN_CUENTA';

export const ESTADO_PAGO_TEXTO: Record<EstadoPagoCuota, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  DECLARADO: 'Pagado, pendiente de confirmar',
  CONFIRMADO_PENDIENTE_ENVIO: 'Confirmado, pendiente de ingresar en la cuenta',
  CONFIRMADO_EN_CUENTA: 'Confirmado y en la cuenta',
};

export interface MovimientoFila {
  concepto: string;
  importe: number;
  fecha: string;
  saldoTras: number;
}

export interface CuentaDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  saldo: number;
  estimacion: number;
  movimientos: MovimientoFila[];
  puedoGestionar: boolean;
  porIngresar: number | null;
}
```

- [ ] **Step 2: `cuentas.service.ts`**

```ts
import { CuentaResumen, CuentaDetalle } from './cuentas.types';
// ...
  detalle(id: number): Observable<CuentaDetalle> {
    return this.http.get<CuentaDetalle>(`${this.base}/cuentas/${id}`);
  }

  marcarTransferido(id: number): Observable<CuentaDetalle> {
    return this.http.post<CuentaDetalle>(`${this.base}/cuentas/${id}/transferencia-a-pena`, {});
  }
```

- [ ] **Step 3: `eventos.types.ts`**

- En `FichaBebidaMia` (línea ~74): quitar `pagado: boolean;`, añadir `estadoPago: EstadoPagoCuota;`. Importar `EstadoPagoCuota` de `../cuentas/cuentas.types` (o mover el tipo a un sitio común; lo más simple: `import { EstadoPagoCuota } from '../cuentas/cuentas.types';`).
- En `AsistenteFila` (línea ~243): quitar `pagado: boolean;` y `declarado: boolean;`, añadir `estadoPago: EstadoPagoCuota;`.
- Mantener `metodoPago`, `pagadoPor`, `pagadoAt`.

- [ ] **Step 4: Spec del servicio**

En `cuentas.service.spec.ts`, actualizar el `flush` de `detalle()` y añadir un caso para `marcarTransferido`:

```ts
  it('detalle() hace GET a /cuentas/:id', () => {
    service.detalle(3).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3`);
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 3, nombre: 'San Miguel', descripcion: null,
      saldo: 91.13, estimacion: 91.13, movimientos: [], puedoGestionar: false, porIngresar: null,
    });
  });

  it('marcarTransferido() hace POST a /cuentas/:id/transferencia-a-pena', () => {
    service.marcarTransferido(3).subscribe();
    const req = httpMock.expectOne(`${base}/cuentas/3/transferencia-a-pena`);
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 3, nombre: 'San Miguel', descripcion: null,
      saldo: 117.13, estimacion: 117.13, movimientos: [], puedoGestionar: true, porIngresar: 0,
    });
  });
```

- [ ] **Step 5: Verificar**

Run: `npx ng test --watch=false --include='src/app/panel/cuentas/cuentas.service.spec.ts'`
Expected: PASS. `npx ng build` puede fallar aún por `cuenta-detalle` / `evento-detalle` que usan `pagado` — se arreglan en Tasks 7-8; no ejecutar `ng build` hasta la Task 9.

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/cuentas/cuentas.types.ts front/src/app/panel/cuentas/cuentas.service.ts front/src/app/panel/cuentas/cuentas.service.spec.ts front/src/app/panel/eventos/eventos.types.ts
git commit -m "feat(cuentas): tipos y servicio web de detalle de cuenta y transferencia a la peña"
```

---

## Task 7: Web — pantalla de detalle de cuenta

**Files:**
- Modify: `front/src/app/panel/cuentas/cuenta-detalle/cuenta-detalle.ts`
- Modify: `front/src/app/panel/cuentas/cuenta-detalle/cuenta-detalle.html`
- Create: `front/src/app/panel/cuentas/cuenta-detalle/cuenta-detalle.spec.ts` (si no existe)

**Interfaces:**
- Consumes: `CuentasService.detalle`, `CuentasService.marcarTransferido`, `CuentaDetalle`, `MovimientoFila`.

- [ ] **Step 1: Componente**

`cuenta-detalle.ts`:

```ts
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CuentaDetalle } from '../cuentas.types';
import { CuentasService } from '../cuentas.service';

@Component({
  selector: 'app-cuenta-detalle',
  imports: [Volver],
  templateUrl: './cuenta-detalle.html',
  styleUrl: './cuenta-detalle.css',
})
export class CuentaDetalleComponent implements OnInit {
  private readonly cuentasService = inject(CuentasService);
  private readonly route = inject(ActivatedRoute);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly cuenta = signal<CuentaDetalle | null>(null);
  protected readonly modalTransferir = signal(false);
  protected readonly enviando = signal(false);
  protected readonly aviso = signal<string | null>(null);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.cuentasService.detalle(this.id).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected confirmarTransferencia(): void {
    this.enviando.set(true);
    this.cuentasService.marcarTransferido(this.id).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.modalTransferir.set(false);
        this.enviando.set(false);
        this.aviso.set('Hecho, el dinero consta ingresado en la cuenta de la peña.');
      },
      error: () => {
        this.enviando.set(false);
        this.aviso.set('No se ha podido registrar. Inténtalo de nuevo.');
      },
    });
  }
}
```

- [ ] **Step 2: Template**

`cuenta-detalle.html` — sustituir el bloque `@case ('listo')` por:

```html
    @case ('listo') {
      @if (cuenta(); as c) {
        <article class="carta-relieve rounded-2xl p-6">
          <h1 class="font-display text-2xl font-extrabold">{{ c.nombre }}</h1>
          @if (c.descripcion) {
            <p class="mt-2 text-sm leading-relaxed text-muted">{{ c.descripcion }}</p>
          }
          <p class="mt-4 text-3xl font-extrabold text-gold">
            {{ c.saldo | currency: 'EUR' }}
          </p>
          <p class="text-xs text-muted">
            Estimado cuando todos paguen: {{ c.estimacion | currency: 'EUR' }}
          </p>
        </article>

        @if (aviso(); as a) {
          <p class="mt-3 rounded-xl border border-brand-bright/30 bg-brand/15 px-4 py-3 text-sm text-gold-soft">{{ a }}</p>
        }

        @if (c.puedoGestionar && c.porIngresar && c.porIngresar > 0) {
          <div class="mt-4 rounded-2xl border border-gold/30 bg-gold/10 p-5">
            <p class="text-sm">
              Tienes <strong>{{ c.porIngresar | currency: 'EUR' }}</strong> cobrados por bizum o efectivo
              sin ingresar en la cuenta de la peña.
            </p>
            <button
              type="button"
              (click)="modalTransferir.set(true)"
              class="mt-3 rounded-xl bg-gold px-4 py-2 text-sm font-semibold text-brand-dark transition hover:brightness-110"
            >
              He transferido el dinero a la peña
            </button>
          </div>
        }

        <h2 class="mt-6 font-display text-lg font-bold">Movimientos</h2>
        <ul class="mt-2 divide-y divide-outline/40 rounded-2xl border border-outline">
          @for (m of c.movimientos; track $index) {
            <li class="flex items-center justify-between gap-3 px-4 py-3 text-sm">
              <span class="min-w-0">
                <span class="block truncate">{{ m.concepto }}</span>
                <span class="text-xs text-muted">{{ m.fecha | date: 'd MMM y' }}</span>
              </span>
              <span class="shrink-0 text-right">
                <span class="block" [class.text-red-300]="m.importe < 0">{{ m.importe | currency: 'EUR' }}</span>
                <span class="text-xs text-muted">{{ m.saldoTras | currency: 'EUR' }}</span>
              </span>
            </li>
          } @empty {
            <li class="px-4 py-3 text-sm text-muted">Todavía no hay movimientos.</li>
          }
        </ul>
      }
    }
```

Y el modal al final de la `<section>`:

```html
  @if (modalTransferir()) {
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div class="w-full max-w-sm rounded-2xl bg-surface p-6">
        <h3 class="font-display text-lg font-bold">¿Seguro que has hecho la transferencia?</h3>
        <p class="mt-2 text-sm text-muted">
          Todo lo cobrado por bizum o efectivo pasará a constar como ingresado en la cuenta de la peña.
        </p>
        <div class="mt-5 flex justify-center gap-2">
          <button type="button" [disabled]="enviando()" (click)="confirmarTransferencia()"
            class="rounded-xl bg-gold px-4 py-2 text-sm font-semibold text-brand-dark disabled:opacity-50">Sí</button>
          <button type="button" (click)="modalTransferir.set(false)"
            class="rounded-xl border border-outline px-4 py-2 text-sm">No</button>
        </div>
      </div>
    </div>
  }
```

`DatePipe`/`CurrencyPipe`: añadir `CommonModule` o los pipes a `imports` del componente. Revisar cómo lo hacen otros componentes del panel (probablemente `imports: [Volver, CurrencyPipe, DatePipe]` o `CommonModule`). Registrar `localeEs` si el resto del proyecto lo hace (mirar `main.ts`); si no, `currency: 'EUR'` sale con símbolo € igual.

- [ ] **Step 3: Spec**

`cuenta-detalle.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { CuentaDetalleComponent } from './cuenta-detalle';

function detalle(over: Partial<any> = {}) {
  return {
    id: 3, nombre: 'San Miguel', descripcion: null,
    saldo: 91.13, estimacion: 117.13, movimientos: [], puedoGestionar: false, porIngresar: null,
    ...over,
  };
}

describe('CuentaDetalleComponent', () => {
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '3' } } } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('pinta saldo y estimación', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle());
    fixture.detectChanges();
    const txt = fixture.nativeElement.textContent as string;
    expect(txt).toContain('91.13');
    expect(txt).toContain('117.13');
  });

  it('sin puedoGestionar no muestra el botón de transferencia', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle({ puedoGestionar: false, porIngresar: null }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('He transferido el dinero a la peña');
  });

  it('con porIngresar el botón abre el modal y confirma llama a marcarTransferido', () => {
    const fixture = TestBed.createComponent(CuentaDetalleComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${base}/cuentas/3`).flush(detalle({ puedoGestionar: true, porIngresar: 26 }));
    fixture.detectChanges();

    const btn = [...fixture.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('He transferido el dinero a la peña')) as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();

    const si = [...fixture.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.trim() === 'Sí') as HTMLButtonElement;
    si.click();

    const req = httpMock.expectOne(`${base}/cuentas/3/transferencia-a-pena`);
    expect(req.request.method).toBe('POST');
    req.flush(detalle({ saldo: 117.13, puedoGestionar: true, porIngresar: 0 }));
  });
});
```

- [ ] **Step 4: Verificar**

Run: `npx ng test --watch=false --include='src/app/panel/cuentas/cuenta-detalle/cuenta-detalle.spec.ts'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add front/src/app/panel/cuentas/cuenta-detalle
git commit -m "feat(cuentas): pantalla de detalle de cuenta con saldo, movimientos y transferencia a la peña"
```

---

## Task 8: Web — botón de pago en el detalle del evento + modal de asistentes

**Files:**
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.ts`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.html`
- Modify: `front/src/app/panel/eventos/evento-detalle/evento-detalle.spec.ts`
- Modify: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.ts`
- Modify: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.html`
- Modify: `front/src/app/panel/eventos/modal-asistentes/modal-asistentes.spec.ts`

**Interfaces:**
- Consumes: `FichaBebidaMia.estadoPago`, `AsistenteFila.estadoPago`, `ESTADO_PAGO_TEXTO`.

- [ ] **Step 1: `evento-detalle.ts` — computeds por estado**

Sustituir los computeds de pago:

```ts
  private readonly estadoPago = computed<EstadoPagoCuota | null>(
    () => this.evento()?.asistencia.ficha.miFicha?.estadoPago ?? null,
  );
  protected readonly pagoConfirmado = computed(
    () => this.estadoPago() === 'CONFIRMADO_EN_CUENTA'
       || this.estadoPago() === 'CONFIRMADO_PENDIENTE_ENVIO',
  );
  protected readonly pagoPendienteDeIngreso = computed(
    () => this.estadoPago() === 'CONFIRMADO_PENDIENTE_ENVIO',
  );
  protected readonly pagoDeclaradoPendiente = computed(
    () => this.estadoPago() === 'DECLARADO',
  );
```

`pagoRechazado` se mantiene (lee `miPagoDeclarado.estado === 'RECHAZADA'`). Importar `EstadoPagoCuota` de `../../cuentas/cuentas.types`.

- [ ] **Step 2: `evento-detalle.html` — 3 ramas del botón**

Localizar el bloque del botón de pago (usa `pagoConfirmado()` / `pagoDeclaradoPendiente()`). Dejarlo:

```html
@if (pagoConfirmado()) {
  <button type="button" (click)="modalHePagado.set(true)" class="...">Ya he pagado</button>
} @else if (pagoDeclaradoPendiente()) {
  <button type="button" (click)="modalPagoDeclarado.set(true)" class="...">Ver mi pago declarado</button>
} @else {
  <button type="button" (click)="abrirModalPago()" class="...">Confirmar el pago</button>
}
```

En el modal "Ya he pagado" (componente `ModalHePagado` o inline), añadir, si `pagoPendienteDeIngreso()`, la línea: "Pendiente de ingresar en la cuenta de la peña." Revisar el componente `modal-he-pagado`: probablemente recibe `pagadoPor`, `pagadoAt`, `metodo` como inputs; añadir un input `pendienteDeIngreso: boolean` y pintar esa frase.

- [ ] **Step 3: Spec de `evento-detalle`**

Buscar los tests que montan `miFicha: { pagado: true, ... }` y cambiarlos a `estadoPago: 'CONFIRMADO_EN_CUENTA'`. Añadir:

```ts
  it('estadoPago DECLARADO muestra "Ver mi pago declarado"', () => {
    // montar evento con miFicha.estadoPago = 'DECLARADO'
    // detectChanges; expect textContent toContain 'Ver mi pago declarado'
  });

  it('estadoPago CONFIRMADO_PENDIENTE_ENVIO muestra "Ya he pagado"', () => {
    // miFicha.estadoPago = 'CONFIRMADO_PENDIENTE_ENVIO'
    // expect toContain 'Ya he pagado'
  });
```

(rellenar con el patrón de montaje que ya use el spec — normalmente un helper `eventoConFicha(over)`).

- [ ] **Step 4: `modal-asistentes`**

En el `.ts`, si hay un método/pipe que traduce el estado de la fila, cambiarlo a:

```ts
import { ESTADO_PAGO_TEXTO, EstadoPagoCuota } from '../../cuentas/cuentas.types';
// ...
protected estadoTexto(e: EstadoPagoCuota): string {
  return ESTADO_PAGO_TEXTO[e];
}
```

En el `.html`, la celda de estado: `{{ estadoTexto(a.estadoPago) }}`. El botón de atajo del admin: mostrar "Confirmar el pago" si `a.estadoPago === 'PENDIENTE_PAGO' || a.estadoPago === 'DECLARADO'`; mostrar "deshacer" si `a.estadoPago === 'CONFIRMADO_EN_CUENTA' || a.estadoPago === 'CONFIRMADO_PENDIENTE_ENVIO'`.

- [ ] **Step 5: Spec de `modal-asistentes`**

Cambiar los `pagado: true/false` de los mocks a `estadoPago`. Añadir un caso: fila con `estadoPago: 'CONFIRMADO_PENDIENTE_ENVIO'` → muestra "Confirmado, pendiente de ingresar en la cuenta" y el botón "deshacer".

- [ ] **Step 6: Verificar**

Run: `npx ng test --watch=false --include='src/app/panel/eventos/evento-detalle/**' --include='src/app/panel/eventos/modal-asistentes/**'`
(si `--include` no admite dos, ejecutar dos veces).
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add front/src/app/panel/eventos/evento-detalle front/src/app/panel/eventos/modal-asistentes
git commit -m "feat(cuentas): botón de pago por estado en el detalle del evento y estados en el modal de asistentes"
```

---

## Task 9: Web — `admin/pagos` método por fila + build + suite

**Files:**
- Modify: `front/src/app/admin/pagos/pagos.html`
- Modify: `front/src/app/admin/pagos/pagos.spec.ts` (si aplica)

- [ ] **Step 1: Método en la fila**

`PagoDeclaradoPendiente` ya trae `metodoPago`. En `pagos.html`, en cada fila de la cola, añadir bajo el nombre: `<span class="text-xs text-muted">{{ metodoTexto(p.metodoPago) }}</span>`. Si no existe `metodoTexto`, añadir en `pagos.ts` un `Record<MetodoPago,string>` (`BIZUM: 'Bizum'`, `TRANSFERENCIA: 'Transferencia'`, `EFECTIVO: 'Efectivo'`).

- [ ] **Step 2: Build web completo**

Run: `npx ng build`
Expected: BUILD SUCCESS. Arreglar cualquier referencia a `pagado`/`declarado` que quede (grep `\.pagado\b` y `\.declarado\b` bajo `front/src/app/panel/eventos` y `front/src/app/panel/cuentas`).

- [ ] **Step 3: Suite web completa**

Run: `npx ng test --watch=false`
Expected: PASS (212+ tests).

- [ ] **Step 4: Commit**

```bash
git add front/src/app/admin/pagos
git commit -m "feat(cuentas): la cola de confirmar pagos muestra el método declarado"
```

---

## Task 10: Móvil — DTOs y repositorio

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/CuentaDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/EventoDtos.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/CuentasRepository.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/CuentasRepositoryImpl.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/CuentasRepositoryImplTest.kt`
- Modify: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/EventosRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - `MovimientoFilaDto(concepto: String, importe: Double, fecha: String, saldoTras: Double)`
  - `CuentaDetalleDto(id: Long, nombre: String, descripcion: String? = null, saldo: Double, estimacion: Double, movimientos: List<MovimientoFilaDto> = emptyList(), puedoGestionar: Boolean = false, porIngresar: Double? = null)`
  - `CuentasRepository.detalle(id: Long): ResultadoCuenta<CuentaDetalleDto>`
  - `CuentasRepository.marcarTransferido(id: Long): ResultadoCuenta<CuentaDetalleDto>`
  - `FichaBebidaMiaDto.estadoPago: String`, `AsistenteFilaDto.estadoPago: String` (quitan `pagado`/`declarado`).

- [ ] **Step 1: `CuentaDtos.kt`**

```kotlin
@Serializable
data class MovimientoFilaDto(
    val concepto: String,
    val importe: Double,
    val fecha: String,
    val saldoTras: Double,
)

@Serializable
data class CuentaDetalleDto(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val saldo: Double = 0.0,
    val estimacion: Double = 0.0,
    val movimientos: List<MovimientoFilaDto> = emptyList(),
    val puedoGestionar: Boolean = false,
    val porIngresar: Double? = null,
)
```

(dejar `CuentaResumen` para el listado.)

- [ ] **Step 2: `EventoDtos.kt`**

En `FichaBebidaMiaDto`: quitar `val pagado: Boolean = false`, añadir `val estadoPago: String? = null`. En `AsistenteFilaDto`: quitar `pagado` y `declarado`, añadir `val estadoPago: String? = null`. Mantener `metodoPago`, `pagadoPor`, `pagadoAt`.

- [ ] **Step 3: Repo**

`CuentasRepository.kt`:

```kotlin
suspend fun detalle(id: Long): ResultadoCuenta<CuentaDetalleDto>
suspend fun marcarTransferido(id: Long): ResultadoCuenta<CuentaDetalleDto>
```

`CuentasRepositoryImpl.kt`:

```kotlin
    override suspend fun detalle(id: Long): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.get("$API_BASE_URL/cuentas/$id") { auth() }.body()
    }

    override suspend fun marcarTransferido(id: Long): ResultadoCuenta<CuentaDetalleDto> = peticion {
        http.post("$API_BASE_URL/cuentas/$id/transferencia-a-pena") { auth() }.body()
    }
```

(import `io.ktor.client.request.post`, `com.baniterio.app.data.dto.CuentaDetalleDto`.)

- [ ] **Step 4: Tests**

`CuentasRepositoryImplTest.kt` (nuevo — copiar el `repo()` helper de `EventosRepositoryImplTest`, adaptado a `CuentasRepositoryImpl`):

```kotlin
    @Test
    fun detalle_hace_get_y_deserializa_saldo() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta = """
            {"id":3,"nombre":"San Miguel","saldo":91.13,"estimacion":117.13,
             "movimientos":[{"concepto":"Saldo del año anterior","importe":91.13,
                             "fecha":"2026-01-01","saldoTras":91.13}],
             "puedoGestionar":true,"porIngresar":26.0}
        """.trimIndent())
        val res = r.detalle(3)
        assertIs<ResultadoCuenta.Exito<CuentaDetalleDto>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/3", vistas[0].path)
        assertEquals(91.13, res.dato.saldo)
        assertEquals(1, res.dato.movimientos.size)
    }

    @Test
    fun marcarTransferido_hace_post() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta =
            """{"id":3,"nombre":"San Miguel","saldo":117.13,"estimacion":117.13,"puedoGestionar":true,"porIngresar":0.0}""")
        r.marcarTransferido(3)
        assertEquals("POST", vistas[0].metodo)
        assertEquals("/api/v1/cuentas/3/transferencia-a-pena", vistas[0].path)
    }
```

En `EventosRepositoryImplTest.kt`: en `asistentes_hace_get_con_bearer_y_deserializa`, cambiar `"pagado":false` del JSON por `"estadoPago":"PENDIENTE_PAGO"`; no hay assert sobre ese campo, solo debe deserializar.

- [ ] **Step 5: Verificar**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data mobile/shared/src/commonTest/kotlin/com/baniterio/app/data
git commit -m "feat(cuentas): DTOs y repositorio móvil de detalle de cuenta y transferencia a la peña"
```

---

## Task 11: Móvil — pantallas

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/cuentas/CuentaDetalleScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/ListadoAsistentesDialog.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminPagosScreen.kt`

**Interfaces:**
- Consumes: `CuentaDetalleDto`, `MovimientoFilaDto`, `estadoPago` en los DTOs de evento.

- [ ] **Step 1: `CuentaDetalleScreen.kt`**

Cambiar `EstadoCuentaDetalle.Cargada(val cuenta: CuentaResumen)` → `Cargada(val cuenta: CuentaDetalleDto)`. En el bloque `Cargada`:
- Tras el nombre/descripción, `Text(formatoImporte(c.saldo) + " €", headlineMedium, bold)` y debajo `Text("Estimado cuando todos paguen: " + formatoImporte(c.estimacion) + " €", muted, bodySmall)`.
- Si `c.puedoGestionar && (c.porIngresar ?: 0.0) > 0.0`: una `Column` con relieve: `Text("Tienes " + formatoImporte(c.porIngresar!!) + " € cobrados sin ingresar en la cuenta de la peña.")` + `Button(onClick = { confirmando = true }) { Text("He transferido el dinero a la peña") }`.
- Lista de movimientos: `c.movimientos.forEach { m -> Row { Column { Text(m.concepto); Text(m.fecha, bodySmall, muted) }; Column(horizontalAlignment = End) { Text(formatoImporte(m.importe) + " €"); Text(formatoImporte(m.saldoTras) + " €", bodySmall, muted) } } }`.
- Diálogo de confirmación:

```kotlin
if (confirmando) {
    AlertDialog(
        onDismissRequest = { confirmando = false },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = {
                guardando = true
                scope.launch {
                    when (val r = cuentasRepo.marcarTransferido(cuentaId)) {
                        is ResultadoCuenta.Exito -> { estado = EstadoCuentaDetalle.Cargada(r.dato); confirmando = false }
                        is ResultadoCuenta.Error -> Unit
                    }
                    guardando = false
                }
            }) { Text("Sí") }
        },
        dismissButton = { TextButton(onClick = { confirmando = false }) { Text("No") } },
        title = { Text("¿Seguro que has hecho la transferencia?") },
        text = { Text("Todo lo cobrado por bizum o efectivo pasará a constar como ingresado en la cuenta de la peña.") },
    )
}
```

Necesita `rememberCoroutineScope()`, `var confirmando by remember { mutableStateOf(false) }`, `var guardando`. `formatoImporte` es `internal` en `com.baniterio.app.ui.eventos` — importarlo.

- [ ] **Step 2: `EventoDetalleScreen.kt`**

Donde hoy decide el botón por `f.pagado` / `f.miPagoDeclarado?.estado == "PENDIENTE"`, cambiar a `f.estadoPago`:

```kotlin
val confirmado = f.estadoPago == "CONFIRMADO_EN_CUENTA" || f.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"
when {
    confirmado -> Button(onClick = { verHePagado = true }) { Text("Ya he pagado") }
    f.estadoPago == "DECLARADO" -> Button(onClick = { verPagoDeclarado = true }) { Text("Ver mi pago declarado") }
    else -> Button(onClick = { abrirDeclarar = true }) { Text("Confirmar el pago") }
}
```

En el diálogo "Ya he pagado", si `f.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO"` añadir la línea "Pendiente de ingresar en la cuenta de la peña."

- [ ] **Step 3: `ListadoAsistentesDialog.kt`**

La línea de estado: sustituir el `when { a.pagado -> "pagado"; a.declarado -> "declarado"; else -> "pendiente" }` por:

```kotlin
when (a.estadoPago) {
    "DECLARADO" -> "Pagado, pendiente de confirmar"
    "CONFIRMADO_PENDIENTE_ENVIO" -> "Confirmado, pendiente de ingresar en la cuenta"
    "CONFIRMADO_EN_CUENTA" -> "Confirmado y en la cuenta"
    else -> "Pendiente de pago"
}
```

Y el `if (!a.pagado)` que decide "Confirmar el pago" vs "deshacer" → `if (a.estadoPago != "CONFIRMADO_EN_CUENTA" && a.estadoPago != "CONFIRMADO_PENDIENTE_ENVIO")`. La condición del `metodoLegible` (`a.pagado && a.metodoPago != null`) → `(a.estadoPago == "CONFIRMADO_EN_CUENTA" || a.estadoPago == "CONFIRMADO_PENDIENTE_ENVIO") && a.metodoPago != null`.

- [ ] **Step 4: `AdminPagosScreen.kt`**

Añadir bajo el nombre del declarante: `Text(metodoLegible(p.metodoPago), style = MaterialTheme.typography.bodySmall)`. Si no hay `metodoLegible` en ese fichero, un `when` local (`"BIZUM" -> "Bizum"`, etc.).

- [ ] **Step 5: Compilar shared + android**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Ignorar los diagnósticos del IDE.)

- [ ] **Step 6: Suite móvil**

Run: `./gradlew :shared:testAndroidHostTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui
git commit -m "feat(cuentas): pantallas móviles de saldo, movimientos y estados de pago"
```

---

## Cierre

- [ ] **Verificación final las 3 suites:**
  - `cd back && ./mvnw verify` → BUILD SUCCESS.
  - `cd front && npx ng test --watch=false && npx ng build` → PASS + BUILD.
  - `cd mobile && ./gradlew :shared:testAndroidHostTest :androidApp:compileDebugKotlin` → BUILD SUCCESSFUL.
- [ ] **REQUIRED SUB-SKILL:** Use superpowers:finishing-a-development-branch (verificar tests, presentar opciones de merge, ejecutar y limpiar).
- [ ] Actualizar memoria (`baniterio_cuentas.md`, `baniterio_git_estado.md`, `MEMORY.md`): V28, `movimiento_cuenta`, `estado_pago` de 4 niveles, botón "He transferido el dinero a la peña", rama y HEAD.
- [ ] Reiniciar el servidor de desarrollo (mata el PID del 8080, `cd back && ./mvnw spring-boot:run`) para aplicar V28.

## Self-Review (hecho al escribir el plan)

- **Cobertura del spec:** máquina de estados → Tasks 2/4; saldo y libro → Tasks 1/3/5; por ingresar + botón → Tasks 5/7/11; estimación → Task 5; web → 6-9; móvil → 10-11; tests → en cada task. ✔
- **Sin placeholders:** los pasos de UI de front (Task 8 Step 3) y de detalle móvil dan la forma y el patrón, no código literal completo, porque dependen de helpers de montaje existentes en cada spec/pantalla; se marca explícitamente "rellenar con el patrón del spec". Aceptable: el ejecutor tiene el fichero delante.
- **Consistencia de tipos:** `estadoPago` es `String` en los DTOs de backend y móvil, unión literal en web; `EstadoPagoCuota` enum solo en el dominio backend. `CuentaDetalle` mismos campos en las 3 capas. `porIngresar` nullable en las 3. ✔
- **Orden:** Task 1 y 2 son un bloque (el árbol no compila entre medias); marcado en la Task 1.
