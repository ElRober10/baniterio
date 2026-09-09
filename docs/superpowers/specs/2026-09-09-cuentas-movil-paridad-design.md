# Cuentas en el móvil — paridad con la web (rev.3)

## Contexto

La sección Cuentas de la web avanzó en varias sesiones (commits `b53edac`..`eee8038`,
9 commits, backend V30–V34) y el módulo móvil se quedó en la revisión anterior
(`bd56ea2`, "rev.2 móvil — hoja de cuenta en modo lectura"). El backend ya expone
todo el contrato nuevo; falta llevarlo al móvil.

Alcance acordado con el usuario (2026-09-09):

- **Paridad total**: el móvil tendrá también las acciones de admin (cerrar año,
  alta de gasto/ingreso con recibo, marcar ropa, borrar movimiento), no solo
  lectura. Esto revierte la decisión previa de "móvil = solo lectura de la hoja".
- **Réplica de la tabla**: la hoja se muestra como la tabla de 8 columnas de la
  web, dentro de un scroll horizontal, no como secciones verticales.

La sección Eventos y sus modales (`ListadoAsistentesDialog`, `HePagadoDialog`,
responder, ocultos, ficha de bebida) **no han cambiado en la web** desde que el
móvil se sincronizó, y los DTOs de evento del backend tampoco. No hay nada que
portar ahí.

Este trabajo es solo Kotlin Multiplatform (`mobile/`). El backend y la web ya
están en `main` (`489e74e`).

## Contrato nuevo (backend V34, ya en `main`)

### `GET /api/v1/cuentas/{id}?anio={n}`

`anio` opcional. Sin él = año contable en curso; con él = ese año pasado (solo
lectura). Devuelve `CuentaDetalle`:

```
record CuentaDetalle(
    Long id, String nombre, String descripcion,
    int anio, List<Integer> anios, boolean esAnioActual,
    BigDecimal saldo, BigDecimal saldoInicial, BigDecimal estimacion, BigDecimal cobradoSinIngresar,
    boolean puedoGestionar,
    BigDecimal precioCamiseta, BigDecimal precioSudadera,
    List<PenistaCuota> penistas,
    BigDecimal totalCuotas, BigDecimal totalCobrado,
    List<MovimientoFila> movimientos,
    List<ResumenGasto> resumenGastos)

record PenistaCuota(
    Long asistenciaId, String nombre, int anio, BigDecimal cuota,
    String estadoPago, String metodoPago,
    int camisetaCantidad, String camisetaTalla, boolean camisetaConfirmada,
    int sudaderaCantidad, String sudaderaTalla, boolean sudaderaConfirmada,
    BigDecimal ingreso, BigDecimal saldoTras)

record MovimientoFila(
    Long id, String concepto, String categoria, BigDecimal importe, LocalDate fecha,
    BigDecimal saldoTras, String reciboArchivo, boolean manual, String origen, String adelantadoPor)
```

`origen` ∈ `SALDO_INICIAL | CUOTA | CAMISETA | SUDADERA | GASTO | INGRESO | …`.
`ingreso`/`saldoTras` de `PenistaCuota` van a `null` mientras la cuota no esté
cobrada. `cobradoSinIngresar` es `null` si `!puedoGestionar`.

### Resto de endpoints

| Método | Ruta | Cuerpo | Devuelve |
|---|---|---|---|
| `POST` | `/cuentas/{id}/transferencia-a-pena` | — | `CuentaDetalle` |
| `POST` | `/cuentas/{id}/cerrar-anio` | — | `CuentaDetalle` |
| `POST` | `/cuentas/{id}/movimientos` | multipart: `tipo`, `concepto`, `importe`, `fecha?`, `categoria?`, `adelantadoPorId?`, `recibo?` | `CuentaDetalle` |
| `DELETE` | `/cuentas/movimientos/{movId}` | — | `CuentaDetalle` |
| `PUT` | `/cuentas/{id}/asistencias/{asistenciaId}/ropa` | JSON: campos `camiseta/sudaderaCantidad·Talla·Confirmada`, cada uno `null` = "no tocar" | `CuentaDetalle` |

`tipo` ∈ `GASTO | INGRESO`. `categoria` ∈ `REFRESCOS | CERVEZA_Y_TINTO | ALCOHOL |
COMIDA | HIELOS | MENAJE | ROPA | OTROS`.

### Códigos de error (cuerpo `{ "codigo": … }`)

`CUENTA_NO_ENCONTRADA` (404), `SIN_PERMISO` (403), `SIN_PRECIO_ROPA` (409 — hay que
poner antes el precio de la ropa en el evento), `VALIDACION` (422).

## Diseño

### 1. Contrato en Kotlin

- **`data/dto/CuentaDtos.kt`**
  - `CuentaDetalleDto`: `+anio: Int`, `+anios: List<Int> = emptyList()`,
    `+esAnioActual: Boolean = false`, `+saldoInicial: Double = 0.0`.
  - `MovimientoFilaDto`: `+origen: String = ""`.
  - `PenistaCuotaDto`: quitar `camisetaPagada`/`sudaderaPagada`; añadir
    `anio: Int = 0`, `camisetaCantidad: Int = 0`, `camisetaTalla: String? = null`,
    `camisetaConfirmada: Boolean = false`, ídem sudadera, `ingreso: Double? = null`,
    `saldoTras: Double? = null`.
  - Nuevo `CrearMovimientoInput` (params del multipart) y `MarcarRopaInput`
    (los 6 campos opcionales) para no arrastrar listas de parámetros.
- **`data/ResultadoCuenta.kt`**: `CodigoErrorCuenta` `+SIN_PRECIO_ROPA` con
  mensaje "Pon antes el precio de la camiseta / sudadera en el evento." y su
  entrada en `deCodigoBackend`.
- **`data/CuentasRepository.kt` / `CuentasRepositoryImpl.kt`**
  - `detalle(id, anio: Int? = null)` → `?anio=` cuando no es `null`.
  - `cerrarAnio(id): ResultadoCuenta<CuentaDetalleDto>`
  - `crearMovimiento(id, input: CrearMovimientoInput): ResultadoCuenta<CuentaDetalleDto>`
    — `MultiPartFormDataContent`/`formData { … }` como en `PerfilRepositoryImpl`;
    el `recibo` (si lo hay) se adjunta con su `ContentType` y `filename`.
  - `borrarMovimiento(movId): ResultadoCuenta<CuentaDetalleDto>`
  - `marcarRopa(cuentaId, asistenciaId, cambio: MarcarRopaInput): ResultadoCuenta<CuentaDetalleDto>`
    — PUT JSON; solo se serializan los campos no nulos (mismo criterio que la web).
- **Tests** `data/CuentasRepositoryImplTest.kt`: un caso por endpoint con
  `MockEngine` — verifica ruta, método, query `anio`, cuerpo, y la traducción de
  `SIN_PRECIO_ROPA`.

### 2. Selector de recibo (`SelectorArchivo`)

- `data/SelectorArchivo.kt`: `expect fun rememberSelectorArchivo(): SelectorArchivo`
  con la misma forma que `SelectorFoto` (`disponible`, `elegir(onArchivo)` que
  devuelve `ArchivoElegido(bytes, nombre, tipoMime)` o `null`).
- **Android** (`androidMain`): `ActivityResultContracts.GetContent("*/*")` filtrando
  a `application/pdf` e `image/*`; leer bytes por `ContentResolver`.
- **iOS** (`iosMain`): `disponible = false`, `elegir` no-op. Aparcado junto al
  resto del trabajo de iOS que necesita Mac (`docs/superpowers/notas/push-ios-pasos.md`,
  memoria "iOS pendiente").
- Reutilizable: el editor de perfil podría migrar a esto más adelante; fuera de
  alcance ahora.

### 3. `CuentaDetalleScreen` — la hoja (lectura)

Réplica de `front/src/app/panel/cuentas/cuenta-detalle/cuenta-detalle.html`.

- **Cabecera** (`relieveDeCarta`): nombre; fila de botones de año (los 5 más
  nuevos de `anios`, el actual resaltado, + "Años anteriores" si hay más);
  `saldo €` grande; línea "Saldo del año {anio}" y, si `esAnioActual`,
  "· si pagan todos: {estimacion} €"; aviso de `cobradoSinIngresar` con botón
  "He transferido al banco" (ya existe el modal).
- **Tabla** en `horizontalScroll`, 8 columnas
  (`Peñista/concepto · Estado · Camiseta · Sudadera · Gasto · Ingreso · Recibo · Saldo`):
  - Bloque 1: fila "Saldo del año anterior" → `saldoInicial`.
  - Bloque 2: cabecera "Peñistas · N de M han pagado"; una fila por `penista`
    (nombre + "· cuota X € · año"; estado compacto + método; ropa: en lectura,
    `cantidad · talla ✓`; `ingreso` en la col. Ingreso; `saldoTras` en Saldo);
    fila total "Total en cuotas … · cobrado …".
  - Bloque 3: cabecera "Ingresos y gastos"; filas de `movimientos` filtradas a
    `manual || origen ∈ {CAMISETA, SUDADERA}` (fecha + concepto + categoría +
    "adelantó X"; importe en Gasto si `< 0` / Ingreso si `> 0`; recibo = enlace
    que abre `/media/recibos/{archivo}` con `uriHandler`; `saldoTras`).
  - Pie: "Saldo actual" → `saldo`.
- **Resumen de gastos** debajo, lista `categoría → total` (ya existe, se mantiene).
- Cambiar de año: recarga con `detalle(id, anio)`; años pasados no muestran
  controles de admin.

### 4. `CuentaDetalleScreen` — acciones de admin

Solo si `puedoGestionar && esAnioActual`.

- **Botón "Cerrar el año {anio}"** en la cabecera → modal de confirmación
  ("El saldo de X € pasará como saldo de partida a {anio+1}. No se puede
  deshacer.") → `cerrarAnio(id)`; al volver, resetear el año seleccionado a
  `null` y colapsar "años anteriores".
- **"+ Gasto / Ingreso"**: sección plegable con `tipo` (Gasto/Ingreso),
  `importe`, `concepto`, `fecha` (opcional), `categoría` (opcional), y
  "Adjuntar recibo" con `SelectorArchivo` (si `disponible`). Guardar →
  `crearMovimiento`. Al éxito: colapsar, limpiar, mensaje "Movimiento añadido".
- **Ropa por peñista**: en las columnas Camiseta/Sudadera, en vez del texto,
  un campo cantidad + campo talla + (si cantidad > 0) checkbox "€ confirmado".
  Cada cambio → `marcarRopa`. Traducir `SIN_PRECIO_ROPA`.
- **Borrar movimiento**: en las filas `manual`, enlace "borrar" → `borrarMovimiento`.
- Mensaje/aviso reutilizable arriba de la hoja (un `Text` con estado), como el
  `aviso` de la web.

### Fuera de alcance

- Confirmar/deshacer pago de cuota desde la hoja (eso vive en el modal de
  asistentes del evento, ya portado).
- Migrar el editor de perfil a `SelectorArchivo`.
- iOS del `SelectorArchivo` (necesita Mac).

## Plan de ejecución

Una pieza por sesión de trabajo, con parada y resumen tras cada una
(preferencia del usuario). Cada pieza: TDD donde aplique, `./gradlew
:shared:testAndroidHostTest` + `:shared:compileCommonMainKotlinMetadata` verdes,
commit.

1. **Contrato**: DTOs + `ResultadoCuenta`/códigos + repo + tests de repo.
2. **`SelectorArchivo`** (expect/actual Android, iOS parcheado) + multipart de
   `crearMovimiento` en el repo.
3. **Pantalla — hoja en lectura**: tabla de 8 columnas + 3 bloques + cabecera con
   selector de años + modal "He transferido".
4. **Pantalla — acciones de admin**: cerrar año (+ modal), "+ Gasto / Ingreso",
   inputs de ropa, borrar movimiento.

Merge a `main` por fast-forward al terminar las 4 (rama corta
`feature/cuentas-movil`), humo manual en el emulador Android.
