# Confirmación de pago por el administrador (San Miguel) — Diseño

**Fecha:** 2026-09-07
**Rama:** feature/cuentas
**Depende de:** piezas 3a (asistencia), 3b (ficha de bebida + cuota), 4 (listado de
asistentes + modal "Confirmar el pago").
**Pieza:** 5 recortada del subsistema Cuentas. Entra: el estado real de "pagado" y
la confirmación por el administrador. **No** entra: saldo por peñista, movimientos,
pantalla de administración dedicada, avisos push.

## Contexto

Hoy no hay persistencia de pagos. `ficha_bebida.cuota` existe, pero
`AsistenteFila.pagado` es un valor fijo `false` y `ListadoAsistentesResponse.totalPagado`
es `0` siempre. El modal del peñista ("Confirmar el pago", `ModalHePagado`) valida el
importe y el método y emite un evento; no guarda nada y muestra un aviso
("Pago confirmado. Un administrador lo revisará.").

El permiso de administración de eventos ya está resuelto: `AsistenciaService.puedeGestionar(usuarioId, evento)`
es la puerta que usan "mandar notificación", "añadir a mano" y "quitar a mano".

## Comportamiento

### Quién confirma

Un pago solo pasa a "pagado" cuando **un administrador** (quien cumple
`puedeGestionar`) lo confirma desde el modal de asistentes. La declaración que hace
el peñista en `ModalHePagado` **sigue sin persistir** y su flujo no cambia mientras
su pago no esté confirmado.

### Modal "Asistentes" (vista del administrador)

Cada fila con cuota (`cuota != null`):

- **Pago sin confirmar:** botón **"Confirmar el pago"**. Al pulsarlo se abre un
  sub-modal con tres opciones de método (chips, mismo patrón visual que
  `ModalHePagado`): *Bizum*, *Transferencia*, *Efectivo*. El administrador elige uno
  y pulsa "Confirmar". Se llama al endpoint y el modal recarga su propio listado.
- **Pago confirmado:** el botón "Confirmar el pago" **se oculta**. La fila muestra
  el estado como `pagado · {método}` en vez de `pendiente`. Debajo, un enlace
  pequeño **"deshacer"** que revierte a pendiente (para corregir un clic
  equivocado).

`Total cuotas` no cambia. `pagado: {suma}` pasa a reflejar la suma real de las
cuotas de las filas confirmadas.

Filas sin cuota (`cuota == null`): sin botón, como ahora.

Invitados apuntados a mano tienen ficha y cuota; el administrador confirma su pago
igual que el de un peñista (no tienen vista propia).

### Detalle del evento (vista del peñista)

El bloque de botones bajo "Tu cuota", cuando el peñista tiene cuota
(`miFicha.cuota != null`):

- **`miFicha.pagado == false`:** botón **"Confirmar el pago"** — abre `ModalHePagado`,
  exactamente como ahora.
- **`miFicha.pagado == true`:** el botón se sustituye por **"Ya he pagado"**. Al
  pulsarlo se abre un modal informativo, sin acciones:

  > Tu pago está confirmado.
  > Lo confirmó **{nombre de quien confirmó}** el **{fecha} a las {hora}**.
  > Has pagado por **{método legible}**.

  Método legible: Bizum / Transferencia / Efectivo.

## Modelo de datos

Migración **V26** — columnas nuevas en `ficha_bebida` (todas nullable salvo el
booleano):

| Columna                  | Tipo           | Notas                                        |
|--------------------------|----------------|----------------------------------------------|
| `pagado`                 | `boolean`      | `not null default false`                     |
| `metodo_pago`            | `varchar(16)`  | `BIZUM` \| `TRANSFERENCIA` \| `EFECTIVO`; `null` si no pagado |
| `pagado_confirmado_por`  | `bigint`       | FK → `usuario(id)`; `null` si no pagado       |
| `pagado_at`              | `timestamptz`  | `null` si no pagado                           |

Al deshacer se vuelven los cuatro a su estado inicial (`false` / `null`).

No hay tabla `pago` en esta pieza: el estado vive en la ficha. Cuando llegue la
pieza de movimientos/saldo se podrá migrar a una tabla propia sin cambiar el
contrato de estas pantallas.

## Backend

### Enum

`com.baniterio.api.identidad.MetodoPago { TRANSFERENCIA, BIZUM, EFECTIVO }`
(espejo del tipo `MetodoPago` de front). La entidad `FichaBebida` gana
`@Enumerated(EnumType.STRING) MetodoPago metodoPago`, `Long pagadoConfirmadoPor`
(o `@ManyToOne Usuario`), `Instant pagadoAt`, `boolean pagado`.

### Endpoints (`AsistenciaController`, prefijo `/api/v1/eventos`)

- **`PUT /{id}/asistencias/{asistenciaId}/pago`** — cuerpo
  `{ "metodo": "BIZUM" }`.
  - `403 SIN_PERMISO_EVENTO` si `!puedeGestionar`.
  - `404 ASISTENCIA_NO_ENCONTRADA` si la asistencia no es de ese evento.
  - `409 EVENTO_SIN_FICHA` si el evento no es de San Miguel.
  - `409 FICHA_SIN_CUOTA` (código nuevo) si la ficha no tiene cuota, o
    `404` si no hay ficha.
  - Idempotente: confirmar un pago ya confirmado sobrescribe método / autor /
    fecha.
  - Devuelve el `ListadoAsistentesResponse` recalculado (el modal ya consume ese
    contrato al abrirse).
- **`DELETE /{id}/asistencias/{asistenciaId}/pago`** — revierte a pendiente.
  Mismos 403 / 404. Sin cuerpo. Devuelve el listado recalculado.

Un método nuevo en `AsistenciaService` (`confirmarPago` / `deshacerPago`) que carga
el evento, valida permiso, carga la ficha por `asistenciaId` y `eventoId`, y
guarda. Reutiliza `FichaBebidaRepository`.

### DTOs

- **`AsistenteFila`** — añadir: `Long asistenciaId`, `String metodoPago` (o `null`),
  `String pagadoPor` (nombre, o `null`), `Instant pagadoAt` (o `null`). `pagado`
  pasa a ser el valor real de la ficha.
- **`ListadoAsistentesResponse`** — añadir `boolean puedoConfirmarPagos`
  (= `puedeGestionar` del que pregunta). `totalPagado` = suma real de
  `cuota` de las filas con `pagado == true`.
- **`FichaBebidaDetalle.MiFicha`** — añadir `boolean pagado`, `String metodoPago`,
  `String pagadoPorNombre`, `Instant pagadoAt`. Los rellena
  `FichaBebidaService.aMiFicha` desde la ficha.
- Cuerpo de entrada: `ConfirmarPagoRequest(@NotNull MetodoPago metodo)`.

### `ApiExceptionHandler`

Mapear `FichaSinCuotaException` → `409 FICHA_SIN_CUOTA`.

## Front (Angular)

### Tipos (`eventos.types.ts`)

- `AsistenteFila`: `+ asistenciaId: number`, `+ metodoPago: MetodoPago | null`,
  `+ pagadoPor: string | null`, `+ pagadoAt: string | null`.
- `ListadoAsistentes`: `+ puedoConfirmarPagos: boolean`.
- `FichaBebidaMia`: `+ pagado: boolean`, `+ metodoPago: MetodoPago | null`,
  `+ pagadoPor: string | null`, `+ pagadoAt: string | null`.
- `CodigoErrorEvento`: `+ 'FICHA_SIN_CUOTA'`.

### `eventos.service.ts`

- `confirmarPago(eventoId, asistenciaId, metodo): Observable<ListadoAsistentes>`
  → `PUT .../asistencias/{asistenciaId}/pago`.
- `deshacerPago(eventoId, asistenciaId): Observable<ListadoAsistentes>`
  → `DELETE .../asistencias/{asistenciaId}/pago`.

### `ModalAsistentes`

- Estado interno: `filaConfirmando = signal<AsistenteFila | null>(null)`,
  `metodoElegido = signal<MetodoPago>('BIZUM')`, `guardando = signal(false)`.
- Plantilla: por fila, si `datos().puedoConfirmarPagos && a.cuota != null && !a.pagado`
  → botón "Confirmar el pago" (`(click)="filaConfirmando.set(a)"`). Si `a.pagado`
  → texto `pagado · {metodo legible}` + enlace "deshacer".
- Sub-modal (cuando `filaConfirmando() != null`): tres chips de método reutilizando
  el patrón de `ModalHePagado`, botón "Confirmar" → `confirmarPago(...)`, al éxito
  `datos.set(resp); filaConfirmando.set(null); ajustarColumnas()`.
- `deshacer(a)` → `deshacerPago(...)`, mismo refresco.

### `EventoDetalleComponent` + plantilla

- `computed pagoConfirmado = () => !!evento()?.asistencia.ficha.miFicha?.pagado`.
- En la plantilla, donde hoy está el botón "Confirmar el pago"
  (`evento-detalle.html:129`): si `pagoConfirmado()` → botón "Ya he pagado"
  (`(click)="modalPagoInfo.set(true)"`); si no → el botón actual.
- Nuevo bloque `@if (modalPagoInfo())` con el modal informativo (componente nuevo
  `ModalPagoConfirmado` o un bloque inline; se decide en el plan). Muestra
  `miFicha.pagadoPor`, `miFicha.pagadoAt` (con `DatePipe`, `dd/MM/yyyy` + `HH:mm`)
  y el método legible.

## Móvil (KMP shared)

### DTOs (`EventoDtos.kt`)

- `AsistenteFilaDto`: `+ asistenciaId: Long = 0`, `+ metodoPago: String? = null`,
  `+ pagadoPor: String? = null`, `+ pagadoAt: String? = null`.
- `ListadoAsistentesDto`: `+ puedoConfirmarPagos: Boolean = false`.
- `MiFicha` (dto de ficha en el detalle): `+ pagado: Boolean = false`,
  `+ metodoPago: String? = null`, `+ pagadoPor: String? = null`,
  `+ pagadoAt: String? = null`.
- `ConfirmarPagoBody(val metodo: String)`.

### `AsistenciaRepository` / `AsistenciaRepositoryImpl`

- `suspend fun confirmarPago(eventoId: Long, asistenciaId: Long, metodo: String): ResultadoEvento<ListadoAsistentesDto>`.
- `suspend fun deshacerPago(eventoId: Long, asistenciaId: Long): ResultadoEvento<ListadoAsistentesDto>`.

### Pantallas

- `ListadoAsistentesDialog`: por fila, botón "Confirmar el pago" cuando
  `puedoConfirmarPagos && cuota != null && !pagado`; abre un selector de método
  (tres opciones); fila pagada muestra `pagado · {método}` + acción "deshacer".
- `EventoDetalleScreen`: si `miFicha.pagado`, el botón "Confirmar el pago" pasa a
  "Ya he pagado" y abre un diálogo informativo con autor, fecha/hora y método.

## Tests

- **Backend IT** (`AsistenciaIT` o nuevo `ConfirmacionPagoIT`):
  - admin confirma → `pagado=true`, método/autor/fecha guardados, `totalPagado`
    sube.
  - no-admin → `403 SIN_PERMISO_EVENTO`.
  - ficha sin cuota → `409 FICHA_SIN_CUOTA`.
  - evento no San Miguel → `409 EVENTO_SIN_FICHA`.
  - `DELETE` revierte y `totalPagado` vuelve a bajar.
  - el detalle del evento del peñista trae `miFicha.pagado` y los datos de
    confirmación.
- **Front specs:**
  - `modal-asistentes.spec.ts`: botón solo con `puedoConfirmarPagos`; sub-modal
    de método; llamada a `confirmarPago`; fila pasa a "pagado · Bizum"; "deshacer"
    llama a `deshacerPago`.
  - `evento-detalle.spec.ts`: `miFicha.pagado` → botón "Ya he pagado" + modal con
    autor/fecha/método; sin pagar → botón "Confirmar el pago" abre `ModalHePagado`.
- **Móvil:** `AsistenciaRepositoryImplTest` — `confirmarPago` / `deshacerPago`
  (payload y parseo de la respuesta).

## Fuera de alcance

- Persistir la declaración del peñista (estado intermedio "declarado, sin
  confirmar").
- Saldo por peñista, movimientos, historial de pagos.
- Pantalla de administración de pagos dedicada.
- Avisos push al confirmar / deshacer.
- Pago y confirmación para eventos que no son de San Miguel.
