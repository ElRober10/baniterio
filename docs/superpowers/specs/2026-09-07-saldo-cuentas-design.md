# Saldo de cuentas: hoja tipo Excel, estados de pago, movimientos, ropa y recibos

**Fecha:** 2026-09-07 (rev. 2)
**Estado:** aprobado, en implementación en `feature/saldo-cuentas`
**Depende de:** V27 (`pago_declarado`), V26 (`ficha_bebida` pago), sección Cuentas, media (`AlmacenImagenes`, `${app.media.dir}`).

## Contexto y qué cambia respecto a la rev. 1

La rev. 1 (commits `a204980`, `d0823dd`, `7f5424d`, `9543fab`) añadió `movimiento_cuenta`,
`ficha_bebida.estado_pago` de 4 niveles y una vista de cuenta con saldo + estimación + lista de
movimientos. El usuario la rechazó: (1) el saldo no reflejaba los pagos confirmados por
bizum/efectivo (se quedaban en `CONFIRMADO_PENDIENTE_ENVIO`, fuera del saldo hasta pulsar "He
transferido"); (2) la vista no se parece a su Excel — no se ve de un vistazo quién ha pagado ni en
qué se ha gastado el dinero.

**Esta revisión, sobre lo ya hecho:**

1. **El saldo cuenta todo pago confirmado**, sea el método que sea. Confirmar = línea en el libro.
   `CONFIRMADO_PENDIENTE_ENVIO` sigue existiendo pero es solo un aviso ("tienes X € en efectivo/bizum
   sin llevar al banco"); "He transferido el dinero a la peña" apaga el aviso y **no cambia el saldo**.
2. **La vista de la cuenta se rehace** como la hoja del Excel: tres bloques — cabecera con el saldo,
   tabla de **peñistas** (quién ha pagado cuota / camiseta / sudadera), tabla de **movimientos** con
   entra/sale/saldo corriente, y resumen de gastos por categoría.
3. **Ropa por peñista**: `precio_camiseta` / `precio_sudadera` en el evento; casilla
   `camiseta_pagada` / `sudadera_pagada` por peñista que marca el admin; marcarla mete un ingreso en
   el libro.
4. **Gastos e ingresos manuales** con categoría y **recibo** adjunto (PDF/JPG/PNG).

## Modelo de datos — migración V29 (sobre V28)

```sql
-- Amplía los orígenes del libro y añade categoría, recibo y quién lo adelantó.
ALTER TABLE movimiento_cuenta DROP CONSTRAINT ck_movimiento_origen;
ALTER TABLE movimiento_cuenta ADD CONSTRAINT ck_movimiento_origen
    CHECK (origen IN ('SALDO_INICIAL', 'CUOTA', 'CAMISETA', 'SUDADERA',
                      'GASTO', 'INGRESO', 'AJUSTE'));
ALTER TABLE movimiento_cuenta ADD COLUMN categoria      VARCHAR(24);
ALTER TABLE movimiento_cuenta ADD COLUMN recibo_archivo VARCHAR(80);
ALTER TABLE movimiento_cuenta ADD COLUMN adelantado_por BIGINT REFERENCES usuario (id) ON DELETE SET NULL;

-- Precio de la ropa por evento (lo fija el admin, como las cuotas).
ALTER TABLE evento ADD COLUMN precio_camiseta NUMERIC(7,2);
ALTER TABLE evento ADD COLUMN precio_sudadera NUMERIC(7,2);

-- Ropa pagada por peñista.
ALTER TABLE ficha_bebida ADD COLUMN camiseta_pagada BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ficha_bebida ADD COLUMN sudadera_pagada BOOLEAN NOT NULL DEFAULT false;
```

- `movimiento_cuenta.importe`: sigue siendo `+` entra / `−` sale. `CUOTA`, `CAMISETA`, `SUDADERA`,
  `INGRESO` positivos; `GASTO` negativo; `SALDO_INICIAL`/`AJUSTE` cualquiera.
- `ux_movimiento_ficha` (único por `ficha_asistencia_id`) **se elimina** — ahora una ficha puede
  tener 3 movimientos (cuota, camiseta, sudadera). Se sustituye por un único parcial por
  `(ficha_asistencia_id, origen)`:
  ```sql
  DROP INDEX ux_movimiento_ficha;
  CREATE UNIQUE INDEX ux_movimiento_ficha_origen
      ON movimiento_cuenta (ficha_asistencia_id, origen) WHERE ficha_asistencia_id IS NOT NULL;
  ```
- Categorías (enum en código, no en BBDD): `REFRESCOS, CERVEZA_Y_TINTO, ALCOHOL, COMIDA, HIELOS,
  MENAJE, ROPA, OTROS`. Solo aplican a `GASTO`/`INGRESO`.

## Máquina de estados de la cuota (sin cambios de rev.1 salvo el saldo)

`ficha_bebida.estado_pago` sigue con `PENDIENTE_PAGO / DECLARADO / CONFIRMADO_PENDIENTE_ENVIO /
CONFIRMADO_EN_CUENTA`. Transiciones iguales, **con un cambio**: al confirmar (declaración o atajo
del admin), se crea el movimiento `CUOTA` **siempre**, no solo si el método es transferencia. El
estado destino sigue dependiendo del método (`TRANSFERENCIA` → `CONFIRMADO_EN_CUENTA`;
`BIZUM`/`EFECTIVO` → `CONFIRMADO_PENDIENTE_ENVIO`) pero solo para el aviso "sin ingresar".
`marcarTransferido` pasa las fichas `PENDIENTE_ENVIO` → `EN_CUENTA` y **no crea movimientos**
(ya existen). `deshacerPago` / rechazo / anulación borran el movimiento `CUOTA` de esa ficha.

## Fórmulas

- **Saldo** = `Σ movimiento_cuenta.importe` de la cuenta.
- **Cobrado sin ingresar** (aviso, solo admin) = `Σ ficha.cuota` de fichas `CONFIRMADO_PENDIENTE_ENVIO`
  de la cuenta. Botón "He transferido" las pasa a `EN_CUENTA`.
- **Estimación** = saldo + `Σ ficha.cuota` de asistentes `APUNTADO`/`EN_DUDA` con cuota cuyo
  `estado_pago` es `PENDIENTE_PAGO` o `DECLARADO` (las confirmadas ya están en el saldo) +
  `Σ precio_camiseta/sudadera` de la ropa marcada como pendiente… **no**: la estimación solo cuenta
  cuotas (la ropa es opcional y su recaudación va aparte). Estimación = saldo + cuotas aún sin confirmar.

## Backend

### Entidades / enums
- `OrigenMovimiento` + `CAMISETA, SUDADERA, GASTO, INGRESO`.
- `CategoriaMovimiento` (enum nuevo, `identidad`): los 8 valores + `legible()`.
- `MovimientoCuenta` + `CategoriaMovimiento categoria`, `String reciboArchivo`, `Usuario adelantadoPor`.
- `Evento` + `BigDecimal precioCamiseta`, `BigDecimal precioSudadera`.
- `FichaBebida` + `boolean camisetaPagada`, `boolean sudaderaPagada`.

### `MovimientoCuentaService`
- `registrarCuota(ficha, admin)` — igual, idempotente por `(ficha, CUOTA)`.
- `revertirCuota(ficha)` — igual.
- `registrarRopa(ficha, OrigenMovimiento tipo, BigDecimal precio, Usuario admin)` /
  `revertirRopa(ficha, tipo)` — análogos, origen `CAMISETA`/`SUDADERA`, importe `+precio`,
  concepto "Camiseta de X" / "Sudadera de X".
- `crearManual(Cuenta, tipo GASTO|INGRESO, concepto, importe, fecha, CategoriaMovimiento, Usuario adelantadoPor, Usuario admin, String reciboArchivo)` → devuelve el `MovimientoCuenta`.
- `borrarManual(Long movId, Usuario admin)` — solo `GASTO`/`INGRESO`; borra el recibo del disco si lo hay.
- `saldo(cuentaId)`.

### Recibos
- `AlmacenRecibos` (media, análogo a `AlmacenImagenes`): `${app.media.dir}/recibos/`, nombre
  `UUID.<ext>` con `ext ∈ {pdf,jpg,png}`. `guardar(byte[], contentType) → archivo`, `leer(archivo)
  → Optional<byte[]>` + su content-type, `borrar(archivo)`, `existe`.
- Endpoint autenticado (cualquier miembro) `GET /api/v1/cuentas/movimientos/{movId}/recibo` →
  el fichero con su `Content-Type` (404 si el movimiento no existe o no tiene recibo).
- Subida: parte del `POST /cuentas/{id}/movimientos` como `multipart/form-data` (campo `recibo`
  opcional) **o** `POST /api/v1/cuentas/movimientos/{movId}/recibo` (multipart) para añadirlo/cambiarlo después.

### Endpoints
- `GET /api/v1/cuentas/{id}` → `CuentaDetalle` (ver DTO abajo). Pasa `principal.id()`.
- `POST /api/v1/cuentas/{id}/transferencia-a-pena` (admin) → `CuentaDetalle`. **Ya no crea movimientos.**
- `POST /api/v1/cuentas/{id}/movimientos` (admin, `multipart/form-data`): campos `tipo`
  (`GASTO`|`INGRESO`), `concepto`, `importe` (>0), `fecha` (ISO), `categoria`, `adelantadoPorId`
  (opcional), `recibo` (fichero opcional). Para `GASTO` el importe se guarda negado. → `CuentaDetalle`.
- `DELETE /api/v1/cuentas/movimientos/{movId}` (admin) → `CuentaDetalle` (necesita saber la cuenta:
  se resuelve del movimiento). 409 `MOVIMIENTO_NO_MANUAL` si no es `GASTO`/`INGRESO`.
- `PUT /api/v1/eventos/{id}/asistencias/{asisId}/ropa` (admin) body `{camiseta: bool, sudadera: bool}`
  → pone cada flag; al pasar de false→true registra el ingreso (exige `evento.precio_*` no nulo →
  409 `SIN_PRECIO_ROPA`), true→false lo revierte. Devuelve `ListadoAsistentesResponse` recalculado.
- `GuardarEventoRequest` + `precioCamiseta`, `precioSudadera` (solo admin los fija; `EventoService.crear/editar`).
  `EventoDetalle` + los dos.

### DTOs (`cuenta.dto`)
```java
record MovimientoFila(Long id, String concepto, String categoria, BigDecimal importe,
                      LocalDate fecha, BigDecimal saldoTras, boolean tieneRecibo, boolean manual,
                      String adelantadoPor) {}

record PenistaCuota(Long asistenciaId, String nombre, BigDecimal cuota, String estadoPago,
                    String metodoPago, boolean camisetaPagada, boolean sudaderaPagada) {}

record ResumenGasto(String categoria, BigDecimal total) {}

record CuentaDetalle(Long id, String nombre, String descripcion,
                     BigDecimal saldo, BigDecimal estimacion, BigDecimal cobradoSinIngresar,
                     boolean puedoGestionar,
                     BigDecimal precioCamiseta, BigDecimal precioSudadera,
                     List<PenistaCuota> penistas,
                     BigDecimal totalCuotas, BigDecimal totalCobrado,
                     List<MovimientoFila> movimientos,
                     List<ResumenGasto> resumenGastos) {}
```
`cobradoSinIngresar`/`puedoGestionar`-dependientes van a `null` si no es admin. `penistas` = todos
los asistentes `APUNTADO`/`EN_DUDA` con cuota de cualquier evento de la cuenta, orden: primero los
que no han pagado, luego por nombre.

### `AsistenteFila` / `MiFicha`
Sin cambios respecto a rev.1 (ya llevan `estadoPago`). Se puede añadir `camisetaPagada`/`sudaderaPagada`
a `AsistenteFila` si el modal de asistentes los muestra — **no** en esta tanda, la ropa se ve en la
pantalla de la cuenta.

## Web — `cuenta-detalle` rehecho

Tres secciones (boceto aprobado):

1. **Cabecera**: nombre, `Saldo actual` grande, `Si pagan todos` (estimación), y si admin y
   `cobradoSinIngresar > 0`: línea + botón `He transferido al banco` (modal ¿seguro? Sí/No).
2. **Peñistas**: tabla `nombre · cuota · estado · camiseta · sudadera`. `estado` = etiqueta de
   `EstadoPagoCuota` (+ `· {método}` si confirmado). `camiseta`/`sudadera`: ✓/✗; si admin y hay
   precio, son casillas que llaman a `PUT .../ropa`. Pie: `Total en cuotas: X € · cobrado: Y €`,
   `N de M han pagado`.
3. **Movimientos**: tabla `fecha · concepto · categoría · entra · sale · saldo`. Fila con recibo →
   botón `📄` que hace `GET .../recibo` como *blob* (con el interceptor del token) y
   `window.open(URL.createObjectURL(blob))`. Si admin: fila `GASTO`/`INGRESO` con botón borrar, y
   arriba botón `+ Gasto / Ingreso` → formulario (tipo, concepto, importe, fecha, categoría,
   "lo adelantó" opcional = select de peñistas, subir recibo `<input type=file accept=".pdf,image/*">`).
4. **Resumen de gastos**: lista `categoría — total` (solo las que tienen gasto).

Editor de evento: dos inputs `Precio camiseta (€)` / `Precio sudadera (€)` (solo admin), como
`cuotaCubatas`.

`cuentas.types.ts`: `MovimientoFila`, `PenistaCuota`, `ResumenGasto`, `CuentaDetalle` (arriba);
`CategoriaMovimiento` unión de strings + `CATEGORIA_TEXTO`.
`cuentas.service.ts`: `detalle`, `marcarTransferido`, `crearMovimiento(id, FormData)`,
`borrarMovimiento(movId)`, `marcarRopa(eventoId, asisId, {camiseta,sudadera})`,
`urlRecibo(movId)` / `verRecibo(movId): Observable<Blob>`.

## Móvil — `cuenta-detalle`

Los tres bloques en **modo lectura** (sin casillas de ropa ni alta de gastos — eso es web esta
tanda). El botón `📄` de recibo abre la URL en el visor del sistema (intent `ACTION_VIEW`;
`expect fun abrirUrl(url: String)` con `actual` Android usando `Intent`; iOS deja un TODO). La URL
del recibo lleva `?token=` como *query param* (el visor externo no manda cabeceras) → el endpoint
`GET .../recibo` acepta el JWT por `Authorization` **o** por `?token=`.
`CuentaDetalleDto` con los campos nuevos; `AsistenteFilaDto` sin cambios.

## Tests

### Backend (ITs, ampliar `MovimientoCuentaIT` + `PagoDeclaradoIT` + `ConfirmacionPagoIT`)
- Confirmar por **bizum** ahora sube el saldo y crea movimiento; `cobradoSinIngresar` sube.
- `transferencia-a-pena`: baja `cobradoSinIngresar` a 0, **el saldo no cambia**.
- `deshacerPago` borra el movimiento y baja el saldo.
- `POST /movimientos` GASTO con recibo → aparece en `movimientos`, `tieneRecibo=true`, saldo baja;
  `GET .../recibo` devuelve el fichero con su content-type; `DELETE` lo quita y sube el saldo.
- `POST /movimientos` INGRESO categoría ROPA → saldo sube, aparece en `resumenGastos`? (no —
  resumen solo gastos). Aparece como entrada.
- `PUT .../ropa` con `camiseta:true` sin `precio_camiseta` → 409 `SIN_PRECIO_ROPA`; con precio →
  ingreso `CAMISETA`, `penistas[].camisetaPagada=true`, saldo sube; `camiseta:false` lo revierte.
- `CuentaDetalle.penistas` lista a los asistentes con cuota, no pagados primero.
- No-admin: `POST/DELETE /movimientos` y `PUT /ropa` → 403; `GET .../recibo` sí (cualquier miembro).

### Web (Vitest)
- `cuenta-detalle`: pinta las 3 tablas; casilla de ropa (admin) llama a `marcarRopa`; `+ Gasto`
  envía `FormData` con los campos; `📄` pide el blob y abre; sin admin no hay casillas ni alta ni borrar.
- `cuentas.service`: cada método pega al endpoint correcto (multipart en `crearMovimiento`).

### Móvil (MockEngine)
- `CuentasRepositoryImplTest`: `detalle` deserializa `penistas`/`movimientos`/`resumenGastos`;
  `marcarTransferido` POST. (Alta de gastos y ropa no van en móvil → sin test.)

## Riesgos / decisiones
- **Saldo = suma del libro**, sin caché. Peña pequeña, pocas filas. Si crece, materializar.
- **Recibos con `?token=`**: el visor externo del móvil no manda `Authorization`. El endpoint acepta
  ambos. El token va en claro en la URL que se abre — aceptable para este caso (lo abre el propio
  dueño del móvil).
- **Alta de gastos solo web**: si el usuario la quiere en móvil, es otra tanda.
- **`ux_movimiento_ficha` → `(ficha, origen)`**: permite cuota + camiseta + sudadera por ficha.
- La rev.1 dejó fichas `CONFIRMADO_EN_CUENTA` de la migración V28 sin movimiento `CUOTA` (mapeo del
  viejo `pagado=true`). V29 no las reconstruye (entorno dev, poca cosa); si molesta, el admin
  deshace y rehace el pago, o un `UPDATE` puntual.
