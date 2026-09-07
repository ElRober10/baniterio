# Saldo de cuentas, estados de pago y libro de movimientos

**Fecha:** 2026-09-07
**Estado:** aprobado, pendiente de plan
**Depende de:** V27 (`pago_declarado`), V26 (`ficha_bebida` pago), sección Cuentas (listar/detalle), sección admin "Confirmar pagos".

## Contexto

La sección Cuentas hoy solo lista las cuentas de la peña y muestra nombre y
descripción; el detalle dice "los movimientos todavía están en construcción". El
pago de la cuota de San Miguel se modeló en dos capas: `ficha_bebida.pagado`
(booleano, lo confirma un admin) y `pago_declarado` (el peñista declara que ha
pagado y un admin confirma o rechaza).

Falta lo que da sentido a todo: un **saldo real** por cuenta, que solo sube
cuando el dinero está de verdad en la cuenta bancaria de la peña, y la distinción
entre "el admin ha recibido el bizum" y "el admin ya lo ha pasado a la cuenta de
la peña". El tesorero lleva esto en un Excel cuya columna central es un **saldo
corriente** que arranca con lo que sobró el año pasado (91,13 €) y va cambiando
fila a fila.

## Objetivo

1. Estado de pago de la cuota con cuatro niveles, no un booleano.
2. Saldo por cuenta = saldo inicial + cuotas que ya están en la cuenta de la peña.
3. Bolsa "por ingresar": lo que el admin ha cobrado por bizum/efectivo y todavía
   no ha transferido a la peña, con un botón para marcarlo como transferido de
   golpe.
4. Estimación: lo que habrá cuando todos paguen (cuenta desde que la gente dice
   que va y qué bebe).
5. Libro de movimientos por cuenta, con saldo corriente, al estilo del Excel.

**Fuera de esta tanda:** merchandising (camiseta/sudadera), gastos manuales,
ajustes de saldo por la UI, y todo el inventario/lista de la compra del Excel.
Los gastos entrarán como movimientos negativos sin rediseñar nada.

## Máquina de estados de la cuota

El estado vive en `ficha_bebida.estado_pago`. Es la única fuente de verdad del
estado actual; `pago_declarado` sigue guardando el registro de cada declaración
(importe, método, a quién cubre, quién y cuándo la resolvió).

| Estado | Significado | ¿Suma al saldo? | ¿En estimación? |
|---|---|---|---|
| `PENDIENTE_PAGO` | Dijo que va, cuota fijada, no ha pagado | No | Sí |
| `DECLARADO` | El peñista pulsó "he pagado", espera al admin | No | Sí |
| `CONFIRMADO_PENDIENTE_ENVIO` | El admin recibió el bizum/efectivo, aún no lo pasó a la peña | No (va a "por ingresar") | Sí |
| `CONFIRMADO_EN_CUENTA` | El dinero está en la cuenta bancaria de la peña | **Sí** | Sí |

Etiquetas para la UI:

- `PENDIENTE_PAGO` → "Pendiente de pago"
- `DECLARADO` → "Pagado, pendiente de confirmar"
- `CONFIRMADO_PENDIENTE_ENVIO` → "Confirmado, pendiente de ingresar en la cuenta"
- `CONFIRMADO_EN_CUENTA` → "Confirmado y en la cuenta"

### Transiciones

| Disparador | Origen | Destino | Efecto extra |
|---|---|---|---|
| Se fija la cuota de la ficha | (sin estado) | `PENDIENTE_PAGO` | — |
| Peñista declara el pago | `PENDIENTE_PAGO` | `DECLARADO` (todas las fichas cubiertas) | crea `pago_declarado` PENDIENTE, push a admins |
| Peñista anula su declaración | `DECLARADO` | `PENDIENTE_PAGO` | borra `pago_declarado` |
| Admin rechaza la declaración | `DECLARADO` | `PENDIENTE_PAGO` | `pago_declarado` RECHAZADA, push al peñista |
| Admin confirma la declaración, método = `TRANSFERENCIA` | `DECLARADO` | `CONFIRMADO_EN_CUENTA` | `pago_declarado` CONFIRMADA; un movimiento `CUOTA` por ficha |
| Admin confirma la declaración, método = `BIZUM`/`EFECTIVO` | `DECLARADO` | `CONFIRMADO_PENDIENTE_ENVIO` | `pago_declarado` CONFIRMADA |
| Atajo del admin en el modal Asistentes, método = `TRANSFERENCIA` | cualquiera salvo `CONFIRMADO_EN_CUENTA` | `CONFIRMADO_EN_CUENTA` | cierra `pago_declarado` PENDIENTE que la cubriera; movimiento `CUOTA` |
| Atajo del admin en el modal Asistentes, método = `BIZUM`/`EFECTIVO` | cualquiera salvo `CONFIRMADO_EN_CUENTA` | `CONFIRMADO_PENDIENTE_ENVIO` | cierra `pago_declarado` PENDIENTE que la cubriera |
| Admin: "He transferido el dinero a la peña" (por cuenta) | `CONFIRMADO_PENDIENTE_ENVIO` (todas las de esa cuenta) | `CONFIRMADO_EN_CUENTA` | un movimiento `CUOTA` por cada ficha |
| Admin deshace el pago | `CONFIRMADO_*` | `PENDIENTE_PAGO` | si había movimiento de esa ficha, se borra |

El método lo elige el peñista al declarar y el admin al usar el atajo; en ambos
casos el reparto entre los dos estados "confirmado" es automático.

## Fórmulas

Todas por cuenta. Una cuenta agrupa varios eventos (uno por año); el dinero es
acumulado.

- **Saldo** = `SUM(movimiento_cuenta.importe)` de esa cuenta.
- **Por ingresar** (solo lo ve el admin) = `SUM(ficha.cuota)` de las fichas en
  `CONFIRMADO_PENDIENTE_ENVIO` cuyos eventos pertenecen a esa cuenta.
- **Estimación** = saldo + `SUM(ficha.cuota)` de las fichas con cuota de
  asistentes `APUNTADO` o `EN_DUDA` en eventos de esa cuenta que **todavía no
  están** en `CONFIRMADO_EN_CUENTA` (esas ya están dentro del saldo). Es decir:
  lo que hay en la cuenta más lo que falta por entrar cuando todos paguen.

La estimación no resta gastos porque todavía no hay gastos; cuando los haya, ya
van restados en el saldo (movimientos negativos) y se descontarán además los
gastos previstos.

## Modelo de datos — migración V28

### `movimiento_cuenta` (nueva)

```sql
CREATE TABLE movimiento_cuenta (
    id                  BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    cuenta_id           BIGINT       NOT NULL REFERENCES cuenta (id) ON DELETE CASCADE,
    concepto            VARCHAR(200) NOT NULL,
    importe             NUMERIC(9,2) NOT NULL,          -- + ingreso, - gasto
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
```

Semilla del saldo inicial de San Miguel (idempotente por el índice único no
sirve porque `ficha_asistencia_id` es NULL; se usa `NOT EXISTS`):

```sql
INSERT INTO movimiento_cuenta (cuenta_id, concepto, importe, fecha, origen)
SELECT c.id, 'Saldo del año anterior', 91.13, DATE '2026-01-01', 'SALDO_INICIAL'
FROM cuenta c
JOIN pena p ON p.id = c.pena_id
WHERE p.slug = 'baniterio' AND c.nombre = 'San Miguel'
  AND NOT EXISTS (
      SELECT 1 FROM movimiento_cuenta m
      WHERE m.cuenta_id = c.id AND m.origen = 'SALDO_INICIAL'
  );
```

### `ficha_bebida.estado_pago` (sustituye a `pagado`)

```sql
ALTER TABLE ficha_bebida ADD COLUMN estado_pago VARCHAR(28) NOT NULL DEFAULT 'PENDIENTE_PAGO';
UPDATE ficha_bebida
   SET estado_pago = CASE WHEN pagado THEN 'CONFIRMADO_EN_CUENTA' ELSE 'PENDIENTE_PAGO' END;
ALTER TABLE ficha_bebida DROP COLUMN pagado;
ALTER TABLE ficha_bebida ADD CONSTRAINT ck_ficha_estado_pago
    CHECK (estado_pago IN ('PENDIENTE_PAGO', 'DECLARADO',
                           'CONFIRMADO_PENDIENTE_ENVIO', 'CONFIRMADO_EN_CUENTA'));
```

Se conservan `metodo_pago`, `pagado_confirmado_por`, `pagado_at` con su
significado actual ("confirmado por / cuándo / con qué método"). Las fichas que
en el momento de la migración tengan una `pago_declarado` PENDIENTE que las
cubra se pueden dejar en `PENDIENTE_PAGO`; la migración no intenta reconstruir
`DECLARADO` (entorno de desarrollo, se puede rehacer la declaración).

### `pago_declarado`

Sin cambios de esquema.

## Backend

### Nuevo

- **`EstadoPagoCuota`** (enum en `identidad`): los cuatro valores, con
  `legible()` que devuelve la etiqueta de UI.
- **`MovimientoCuenta`** (entidad) + **`MovimientoCuentaRepository`**
  (`findByCuentaIdOrderByFechaAscIdAsc`, `existsByFichaAsistenciaId`,
  `findByFichaAsistenciaId`).
- **`OrigenMovimiento`** (enum): `SALDO_INICIAL`, `CUOTA`, `AJUSTE`.
- **`MovimientoCuentaService`**: servicio fino, sin dependencias de los servicios
  de evento (evita ciclos). API:
  - `registrarCuota(FichaBebida ficha, Usuario admin)` — inserta un movimiento
    `CUOTA` con `importe = ficha.cuota`, `concepto = "Cuota de " + nombre + " — " + evento.nombre`,
    `cuenta = ficha.asistencia.evento.cuenta`, `ficha_asistencia_id = ficha.id`.
    No hace nada si ya existe uno para esa ficha.
  - `revertirCuota(FichaBebida ficha)` — borra el movimiento `CUOTA` de esa ficha
    si existe.
  - `saldo(Long cuentaId): BigDecimal`.
- **DTOs** en `cuenta.dto`:
  - `MovimientoFila(String concepto, BigDecimal importe, LocalDate fecha, BigDecimal saldoTras)`
  - `CuentaDetalle(Long id, String nombre, String descripcion, BigDecimal saldo,
    BigDecimal estimacion, List<MovimientoFila> movimientos, boolean puedoGestionar,
    BigDecimal porIngresar)` — `porIngresar` es `null` si `!puedoGestionar`.
- **Endpoint** `POST /api/v1/cuentas/{id}/transferencia-a-pena` → `CuentaDetalle`.
  403 `SIN_PERMISO` si no es admin.

### Cambios

- **`FichaBebida`**: `boolean pagado` → `@Enumerated(STRING) EstadoPagoCuota estadoPago`.
- **`FichaBebidaService.aMiFicha`**: el campo `pagado` de `MiFicha` pasa a
  `estadoPago` (String). Se mantienen `metodoPago`, `pagadoPor`, `pagadoAt`,
  `miPagoDeclarado`. El front decide el botón por `estadoPago`.
- **`AsistenciaService`**:
  - `aFila`: `AsistenteFila` deja de tener `pagado` y `declarado`; gana
    `estadoPago` (String). `totalPagado` en `ListadoAsistentesResponse` pasa a
    ser la suma de las cuotas en `CONFIRMADO_EN_CUENTA` o
    `CONFIRMADO_PENDIENTE_ENVIO` (todo lo confirmado).
  - `confirmarPago(usuarioId, eventoId, asistenciaId, metodo)`: según `metodo`,
    deja la ficha en `CONFIRMADO_EN_CUENTA` (+ `movimientoCuenta.registrarCuota`)
    o en `CONFIRMADO_PENDIENTE_ENVIO`. Luego `pagoDeclarado.confirmarPorAtajo`.
  - `deshacerPago`: ficha a `PENDIENTE_PAGO` + `movimientoCuenta.revertirCuota`.
  - Se elimina la llamada a `pagoDeclarado.asistenciasConDeclaracionPendiente`
    (el estado se lee de la ficha).
- **`PagoDeclaradoService`**:
  - Inyecta `MovimientoCuentaService`.
  - `declarar`: además de crear la `pago_declarado`, pone cada ficha cubierta en
    `DECLARADO`.
  - `anularMia`: fichas cubiertas de vuelta a `PENDIENTE_PAGO`.
  - `rechazar`: fichas cubiertas de vuelta a `PENDIENTE_PAGO`.
  - `confirmar`: por cada ficha cubierta, según `p.metodoPago`:
    `CONFIRMADO_EN_CUENTA` (+ `registrarCuota`) o `CONFIRMADO_PENDIENTE_ENVIO`.
  - `confirmarPorAtajo`: sin cambios de firma; solo cierra la `pago_declarado`.
  - `miPagoDeclarado`: igual (sigue devolviendo PENDIENTE/RECHAZADA para el aviso).
  - `asistenciasConDeclaracionPendiente`: se puede borrar si nadie más la usa.
- **`CuentaService`**:
  - Inyecta `MovimientoCuentaService`, `FichaBebidaRepository`,
    `AsistenciaEventoRepository` (o consultas equivalentes), `ServicioPermisos`.
  - `detalle(usuarioId, cuentaId)` (gana el `usuarioId`): construye `CuentaDetalle`
    con saldo, estimación, lista de movimientos (calculando `saldoTras` acumulado),
    y si `permisos.esAdministrador(usuarioId)` también `porIngresar`.
  - `marcarTransferido(adminId, cuentaId)`: 403 si no admin; busca las fichas
    `CONFIRMADO_PENDIENTE_ENVIO` de los eventos de esa cuenta, las pasa a
    `CONFIRMADO_EN_CUENTA` y llama `registrarCuota` por cada una; devuelve el
    detalle recalculado.
- **`CuentaController`**: `GET /{id}` pasa el `principal.id()` al servicio; nuevo
  `POST /{id}/transferencia-a-pena`.
- **Consultas nuevas** en `FichaBebidaRepository`:
  - `findByEstadoPagoAndAsistencia_Evento_Cuenta_Id(EstadoPagoCuota, Long)`
  - fichas con cuota de asistentes APUNTADO/EN_DUDA por cuenta (para la estimación)
    — se puede hacer con un `@Query` que una `ficha_bebida` → `asistencia_evento`
    → `evento` filtrando por `cuenta_id` y `estado IN (APUNTADO, EN_DUDA)` y
    `cuota IS NOT NULL`.

### Dependencias entre servicios

`MovimientoCuentaService` no depende de ningún servicio (solo repos). Lo inyectan
`AsistenciaService`, `PagoDeclaradoService` y `CuentaService`. `PagoDeclaradoService`
sigue sin depender de `AsistenciaService`/`FichaBebidaService`. Sin ciclos.

## Web

### `cuentas.types.ts`

```ts
export type EstadoPagoCuota =
  | 'PENDIENTE_PAGO' | 'DECLARADO'
  | 'CONFIRMADO_PENDIENTE_ENVIO' | 'CONFIRMADO_EN_CUENTA';

export interface MovimientoFila {
  concepto: string;
  importe: number;
  fecha: string;      // ISO date
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

`AsistenteFila`: quita `pagado`/`declarado`, añade `estadoPago: EstadoPagoCuota`.
`FichaBebidaMia`: `pagado` → `estadoPago: EstadoPagoCuota`.

### `cuentas.service.ts`

- `detalle(id)` ahora devuelve `Observable<CuentaDetalle>`.
- `marcarTransferido(id): Observable<CuentaDetalle>` — `POST .../transferencia-a-pena`.

### `cuenta-detalle`

Fuera el cartel de "en construcción". Layout:

- Cabecera: nombre + descripción (como ahora).
- **Saldo** grande (formato `1.234,56 €`).
- **Estimación** debajo, en tono menor: "Estimado cuando todos paguen: X €".
- Si `puedoGestionar` y `porIngresar > 0`: tarjeta destacada
  "Tienes X € cobrados sin ingresar en la cuenta de la peña" +
  botón **"He transferido el dinero a la peña"**.
  - Al pulsar: modal "¿Seguro que has hecho la transferencia?" con
    **Sí** / **No**. "Sí" → `marcarTransferido(id)`, refresca el detalle y
    muestra aviso "Hecho, X € ingresados".
- **Movimientos**: tabla/lista `fecha · concepto · importe (+/-) · saldo`. El
  más reciente arriba o abajo — abajo, como el Excel (orden ascendente, saldo
  corriente creciente). Vacía salvo el saldo inicial → solo esa fila.

### `evento-detalle`

El botón de pago lo decide `ficha.estadoPago`:

| Estado | Botón | Al pulsar |
|---|---|---|
| `PENDIENTE_PAGO` | "Confirmar el pago" | modal declarar pago (importe + método + a quién cubre) |
| `DECLARADO` | "Ver mi pago declarado" | modal con el detalle + "Anular declaración" |
| `CONFIRMADO_PENDIENTE_ENVIO` | "Ya he pagado" | modal info: "confirmado por X el …, has pagado por {método}. Pendiente de ingresar en la cuenta de la peña." |
| `CONFIRMADO_EN_CUENTA` | "Ya he pagado" | modal info: "confirmado por X el …, has pagado por {método}." |

El aviso de rechazo (`miPagoDeclarado.estado === 'RECHAZADA'`) se mantiene igual.

### `modal-asistentes`

La celda de estado de cada fila usa `estadoPago` con las cuatro etiquetas. El
botón de atajo del admin ("Confirmar el pago" / sub-modal de método / "deshacer")
se mantiene; "deshacer" aparece si el estado es cualquiera de los `CONFIRMADO_*`.

### `admin/pagos`

La cola sigue siendo las `pago_declarado` PENDIENTE. Se añade el método a cada
fila para que el admin sepa si al confirmar irá a la cuenta o quedará por
ingresar.

## Móvil

Espejo de lo anterior:

- `data/dto`: `FichaBebidaMiaDto.estadoPago` (String), `AsistenteFilaDto.estadoPago`
  (quita `pagado`/`declarado`), nuevos `MovimientoFilaDto`, `CuentaDetalleDto`.
- `CuentasRepository(Impl)`: `detalle` devuelve el DTO ampliado;
  `marcarTransferido(cuentaId): ResultadoCuenta<CuentaDetalleDto>`.
- `CuentaDetalleScreen`: saldo, estimación, lista de movimientos y, para admin,
  el bloque "por ingresar" + botón + diálogo de confirmación.
- `EventoDetalleScreen`: los cuatro estados del botón (los dos `CONFIRMADO_*`
  comparten "Ya he pagado", el texto del diálogo cambia).
- `ListadoAsistentesDialog`: la línea de estado con las cuatro etiquetas.
- `AdminPagosScreen`: método por fila.

Diagnósticos de Kotlin del IDE rotos: fiarse solo de Gradle.

## Tests

### Backend (ampliar / crear ITs)

- `MovimientoCuentaIT` (o ampliar `CuentaIT`):
  - Detalle de San Miguel recién migrado → saldo `91.13`, un movimiento
    `SALDO_INICIAL`, estimación `91.13` si nadie tiene cuota.
  - Peñista con cuota `45` apuntado sin pagar → estimación `136.13`, saldo `91.13`.
  - Admin confirma declaración método `TRANSFERENCIA` → ficha
    `CONFIRMADO_EN_CUENTA`, saldo sube, aparece movimiento `CUOTA`.
  - Admin confirma declaración método `BIZUM` → ficha
    `CONFIRMADO_PENDIENTE_ENVIO`, saldo igual, `porIngresar` sube.
  - `POST /transferencia-a-pena` → todas las `CONFIRMADO_PENDIENTE_ENVIO` de la
    cuenta pasan a `CONFIRMADO_EN_CUENTA`, saldo sube, `porIngresar` a 0.
  - No-admin: `GET /{id}` no trae `porIngresar`; `POST /transferencia-a-pena` → 403.
  - Admin deshace un pago en cuenta → ficha `PENDIENTE_PAGO`, movimiento borrado,
    saldo baja.
- Ampliar `PagoDeclaradoIT`: al confirmar, la ficha queda en el estado correcto
  según el método; al rechazar/anular vuelve a `PENDIENTE_PAGO`.
- Ampliar `ConfirmacionPagoIT`: el atajo reparte por método.
- Ajustar los ITs que hoy afirman `pagado` (booleano) para leer `estadoPago`.

### Web (Vitest)

- `cuenta-detalle.spec`: pinta saldo y estimación; con `puedoGestionar` y
  `porIngresar` muestra el botón; el modal "¿seguro?" llama a `marcarTransferido`
  y refresca; sin `puedoGestionar` no hay botón ni "por ingresar".
- `cuentas.service.spec`: `detalle` mapea el nuevo cuerpo; `marcarTransferido`
  hace el `POST` correcto.
- `evento-detalle.spec`: los cuatro estados del botón y el texto del modal info.
- `modal-asistentes.spec`: etiqueta por estado; "deshacer" solo en `CONFIRMADO_*`.

### Móvil (MockEngine)

- `CuentasRepositoryImplTest`: `detalle` deserializa saldo/estimación/movimientos;
  `marcarTransferido` hace `POST .../transferencia-a-pena`.
- Ajustar `EventosRepositoryImplTest` a `estadoPago`.

## Riesgos y decisiones

- **Quitar `ficha_bebida.pagado`** toca varios ITs y DTOs. Se asume entorno de
  desarrollo; la migración convierte el dato y los tests se ajustan en la misma
  tarea.
- **Doble fuente de estado** (ficha vs `pago_declarado`): se corta haciendo la
  ficha autoritativa y `pago_declarado` un registro histórico. Todos los caminos
  que cambian el estado pasan por `PagoDeclaradoService` o `AsistenciaService`,
  que escriben la ficha.
- **`marcarTransferido` global por cuenta**: coincide con lo pedido ("todos los
  que están confirmado pendiente… pasarán a…"). No hay selección fila a fila.
- **Nota del Excel "restar 630,71"**: se ignora; parece un apunte del tesorero
  anterior. El saldo inicial acordado es 91,13 €.
- **Merch y gastos**: fuera. Los gastos encajarán como movimientos `AJUSTE`/
  negativos y una resta en la estimación, sin tocar este modelo.
