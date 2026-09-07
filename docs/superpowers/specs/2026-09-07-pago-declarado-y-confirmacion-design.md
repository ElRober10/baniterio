# Declaración de pago + confirmación por el administrador — Diseño

**Fecha:** 2026-09-07
**Rama:** feature/pago-declarado (desde `main` = `8a3e69c`)
**Depende de:** pieza 4 (listado de asistentes) y confirmación de pago por el admin
(V26: estado `pagado`/`metodo_pago`/`pagado_confirmado_por`/`pagado_at` en `ficha_bebida`).
**Pieza:** 5 (parcial) del subsistema Cuentas. **No** entra saldo ni movimientos.

## Contexto

Hoy: el peñista pulsa "Confirmar el pago" en el detalle del evento → `ModalHePagado`
recoge importe/método/a quién cubre pero **no persiste**, solo muestra un aviso. El
admin confirma el pago de cada asistente desde el modal "Listado de asistentes"
(botón por fila → sub-modal de método → `PUT .../asistencias/{id}/pago`).

Lo que falta y pide el usuario: que la declaración del peñista **persista**, avise a
los administradores, y quede a la espera de que un administrador la **confirme o
rechace** desde una **sección nueva del panel de administración**.

## Comportamiento

### Los tres botones

**1. Botón del detalle del evento** (`/panel/eventos/:id`) — para todos, admin
incluido. Solo aparece si el usuario tiene cuota (`miFicha.cuota != null`):

| Situación | Botón | Al pulsar |
|---|---|---|
| Sin declaración (o la anterior fue rechazada) | **Confirmar el pago** | abre `ModalHePagado` → crea una `pago_declarado` en estado `PENDIENTE` |
| Declaración `PENDIENTE` | **Ver mi pago declarado** | modal: importe, método, a quién cubre (nombres + cuotas), fecha, y texto "pendiente de que un administrador lo confirme". Botón **Anular declaración** (borra la `pago_declarado`, vuelve al estado inicial) |
| `miFicha.pagado` (confirmado) | **Ya he pagado** | modal info: quién lo confirmó, cuándo, método *(ya existe)* |

Si la última declaración fue `RECHAZADA` y la ficha sigue sin pagar: además del
botón "Confirmar el pago", un aviso "Tu pago anterior no se pudo confirmar. Vuelve a
declararlo."

**2. Botón por fila del modal "Listado de asistentes"** (vista admin, ya existe) —
atajo: el admin marca la cuota de ESE asistente como pagada al instante, sin cola,
para cuando recibe dinero de alguien que no declaró. **Sin cambios de UI.** Cambio
de lógica: si ese asistente tenía una `pago_declarado` `PENDIENTE` que le cubre, esa
declaración pasa a `CONFIRMADA` (mismo admin, misma hora) para que no quede
colgada en la cola. El "deshacer" existente sigue igual (revierte solo la ficha; no
resucita la declaración).

**3. Sección nueva "Confirmar pagos"** en `/panel/administracion` — **solo
admin/superadmin** (como "Bebidas", no es un área concedible). Lista todas las
`pago_declarado` en estado `PENDIENTE` de todos los eventos, más antigua primero:
declarante, evento, importe, método, a quién cubre (nombre + cuota de cada uno),
fecha. Por cada una:

- **Confirmar** → para cada asistencia cubierta pone `ficha_bebida.pagado=true`,
  `metodo_pago` = el declarado, `pagado_confirmado_por` = el admin, `pagado_at` =
  ahora. La `pago_declarado` pasa a `CONFIRMADA`.
- **Rechazar** → la `pago_declarado` pasa a `RECHAZADA`; **push al declarante**
  ("Tu pago de «{evento}» no se pudo confirmar."). Las cuotas siguen pendientes.

### Notificaciones push

- Al crear una `pago_declarado` → `Audiencia.Administradores`, título "Pagos",
  cuerpo "{nombre} dice que ha pagado su cuota de «{evento}»".
- Al rechazar → `Audiencia.UsuarioUnico(declaranteId)`.

## Modelo de datos

Migración **V27**:

```sql
CREATE TABLE pago_declarado (
    id              BIGSERIAL PRIMARY KEY,
    evento_id       BIGINT NOT NULL REFERENCES evento(id),
    declarado_por   BIGINT NOT NULL REFERENCES usuario(id),
    importe         NUMERIC(7,2) NOT NULL,
    metodo_pago     VARCHAR(16) NOT NULL,       -- BIZUM | TRANSFERENCIA | EFECTIVO
    estado          VARCHAR(16) NOT NULL,       -- PENDIENTE | CONFIRMADA | RECHAZADA
    resuelto_por    BIGINT REFERENCES usuario(id),
    resuelto_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE pago_declarado_cubre (
    pago_declarado_id BIGINT NOT NULL REFERENCES pago_declarado(id) ON DELETE CASCADE,
    asistencia_id     BIGINT NOT NULL REFERENCES asistencia_evento(id),
    PRIMARY KEY (pago_declarado_id, asistencia_id)
);
-- Como mucho una declaración PENDIENTE por (evento, declarante):
CREATE UNIQUE INDEX ux_pago_declarado_pendiente
    ON pago_declarado (evento_id, declarado_por) WHERE estado = 'PENDIENTE';
```

`pago_declarado_cubre` incluye SIEMPRE la asistencia del propio declarante, más las
de pareja/hijos/invitados marcadas en el modal.

Entidades: `PagoDeclarado` (`@OneToMany` a `PagoDeclaradoCubre` o `@ElementCollection`
de `asistencia_id`; se decide en el plan). Enum `EstadoPagoDeclarado`.
`MetodoPago` ya existe.

## Backend

### Enum de error nuevo
- `PAGO_DECLARADO_YA_PENDIENTE` (409) — ya tienes una declaración sin resolver en ese evento.
- `PAGO_DECLARADO_NO_ENCONTRADO` (404).
- `PAGO_DECLARADO_YA_RESUELTO` (409) — confirmar/rechazar uno que ya no está PENDIENTE.

### Endpoints

Peñista (prefijo `/api/v1/eventos`):
- **`POST /{id}/pagos-declarados`** — cuerpo
  `{ importe, metodo, cubreUsuarioIds:[], cubreAsistenciaIds:[] }` (el mismo objeto
  que ya emite `ModalHePagado`). Resuelve cada `cubreUsuarioId` a su asistencia en
  ese evento; añade la asistencia del propio usuario. Valida que todas tengan cuota.
  Crea `PENDIENTE`, push a admins. 409 `PAGO_DECLARADO_YA_PENDIENTE` si ya hay una.
  409 `EVENTO_SIN_FICHA` si no es San Miguel. Devuelve `EventoDetalle` recargado.
- **`DELETE /{id}/pagos-declarados/mia`** — borra mi declaración `PENDIENTE` de ese
  evento (404 si no hay). Devuelve `EventoDetalle` recargado.

Admin (prefijo `/api/v1/admin`, solo admin/superadmin → `SinPermisoException`/403):
- **`GET /pagos-declarados`** → `List<PagoDeclaradoPendiente>` (todas las
  `PENDIENTE`, más antigua primero).
- **`POST /pagos-declarados/{id}/confirmar`** → 204.
- **`POST /pagos-declarados/{id}/rechazar`** → 204.

### DTOs

- `PagoDeclaradoPendiente(Long id, Long eventoId, String eventoNombre, String declaradoPor,
  BigDecimal importe, String metodoPago, Instant createdAt, List<Cubierto> cubre)`
  con `Cubierto(String nombre, BigDecimal cuota)`.
- `FichaBebidaDetalle.MiFicha` (bloque `asistencia.ficha.miFicha`) gana
  `MiPagoDeclarado miPagoDeclarado` (o `null`):
  `MiPagoDeclarado(BigDecimal importe, String metodoPago, String estado, Instant createdAt, List<Cubierto> cubre)`.
  Se rellena con la última `pago_declarado` del usuario en ese evento si está
  `PENDIENTE` o `RECHAZADA` (para el aviso); si está `CONFIRMADA` no hace falta
  (ya manda `pagado`).
- `AsistenteFila` gana `boolean declarado` — `true` si esa asistencia está cubierta
  por una `pago_declarado` `PENDIENTE` y la ficha aún no está `pagado`.

### Servicios

- `PagoDeclaradoService` nuevo: `declarar`, `anularMia`, `pendientes` (admin),
  `confirmar` (admin), `rechazar` (admin). Reutiliza `AsistenciaService.puedoPagarPor`
  para validar a quién puede cubrir (o al menos que cada asistencia cubierta sea
  suya / de su pareja / hijo / invitado). Publica los `AvisoPushEvent`.
- `confirmar` toca `FichaBebidaRepository` (marca `pagado` de cada asistencia).
- `AsistenciaService.aFila` / `listadoAsistentes` — rellenan `declarado` cruzando con
  las `pago_declarado` PENDIENTE del evento.
- `AsistenciaService.confirmarPago` (el atajo del modal Asistentes) — al marcar
  `pagado`, si hay una `pago_declarado` PENDIENTE que cubre esa asistencia, pásala a
  `CONFIRMADA`.
- `FichaBebidaService.aMiFicha` / `detalleDe` — rellena `miPagoDeclarado`.

### `ApiExceptionHandler`
Mapear las 3 excepciones nuevas.

## Front (Angular)

### Tipos (`eventos.types.ts`)
- `MiPagoDeclarado { importe:number; metodo:MetodoPago; estado:'PENDIENTE'|'RECHAZADA'; creadoAt:string; cubre:{nombre:string;cuota:number}[] }`.
- `FichaBebidaMia` gana `miPagoDeclarado: MiPagoDeclarado | null`.
- `AsistenteFila` gana `declarado: boolean`.
- `CodigoErrorEvento` gana `'PAGO_DECLARADO_YA_PENDIENTE'`.
- Nuevo `PagoDeclaradoPendiente { id; eventoId; eventoNombre; declaradoPor; importe; metodoPago; creadoAt; cubre:{nombre;cuota}[] }`.

### Servicio
- `eventos.service.ts`: `declararPago(eventoId, body): Observable<EventoDetalle>`,
  `anularPagoDeclarado(eventoId): Observable<EventoDetalle>`.
- `admin.service.ts` (o `EventosService`): `pagosDeclaradosPendientes()`,
  `confirmarPagoDeclarado(id)`, `rechazarPagoDeclarado(id)`.

### `EventoDetalleComponent` + `ModalHePagado`
- `ModalHePagado` (`enviar()`) ya emite `PagoDeclarado`. `EventoDetalleComponent.onPagoEnviado`
  pasa a llamar `declararPago(...)` con ese objeto y recargar; el aviso ahora dice
  "Pago enviado. Un administrador lo confirmará."
- Nuevo `computed pagoDeclaradoPendiente` = `miFicha?.miPagoDeclarado?.estado === 'PENDIENTE'`.
- Botón: `pagado` → "Ya he pagado"; si no, `pagoDeclaradoPendiente()` → "Ver mi pago
  declarado" (nuevo modal inline con detalle + "Anular declaración" → `anularPagoDeclarado`);
  si no, "Confirmar el pago" (abre `ModalHePagado`). Aviso de rechazo si
  `miPagoDeclarado?.estado === 'RECHAZADA'`.

### `ModalAsistentes`
- La fila muestra `· declarado` cuando `a.declarado` (entre "pendiente" y "pagado").
  Sin cambios en los botones.

### Sección nueva `admin/pagos/`
- Componente `AdminPagos` (copia de `AdminBebidas`: `esAdmin` computed, rebota a
  `/panel` si no; `ngOnInit` → `asegurarYo` → cargar). Lista `PagoDeclaradoPendiente`
  con Confirmar / Rechazar por fila (optimista: quita de la lista al resolver).
- Ruta `administracion/pagos` en `app.routes.ts` (solo `perfilCompletoGuard`).
- Tarjeta en `indice.html` dentro del `@if (esAdmin())`, junto a "Bebidas".

## Móvil (KMP shared)

- DTOs (`EventoDtos.kt`): `MiFichaDto` gana `miPagoDeclarado`; `AsistenteFilaDto`
  gana `declarado`; nuevos `DeclararPagoBody`, `MiPagoDeclaradoDto`,
  `PagoDeclaradoPendienteDto`, `CubiertoDto`.
- `EventosRepository(Impl)`: `declararPago`, `anularPagoDeclarado`.
- `AdminRepository(Impl)`: `pagosDeclaradosPendientes`, `confirmarPagoDeclarado`,
  `rechazarPagoDeclarado`.
- `HePagadoDialog` (`onConfirmar`) → llama `declararPago` (hoy solo emite y muestra
  aviso).
- `EventoDetalleScreen`: botón "Confirmar el pago" / "Ver mi pago declarado" /
  "Ya he pagado" según estado; diálogo nuevo "mi pago declarado" con "Anular".
- `AdminBebidasScreen` como plantilla para `AdminPagosScreen` + `Screen.AdminPagos`
  en `App.kt` + entrada en `AdminIndexScreen`.

## Tests

- **Backend IT** (`PagoDeclaradoIT`): declarar (crea + push a admins, verificable
  por el registro de push en modo log o por el evento publicado); 409 si ya hay una;
  anular; admin lista pendientes; confirmar (marca las fichas `pagado`, declaración
  `CONFIRMADA`); rechazar (declaración `RECHAZADA`, cuotas sin tocar); no-admin →
  403; el atajo del modal Asistentes cierra la declaración PENDIENTE que cubría a
  esa asistencia; `EventoDetalle` trae `miFicha.miPagoDeclarado`.
- **Front specs:** `eventos.service.spec` (2 endpoints nuevos), `evento-detalle.spec`
  (3 estados del botón + anular), `pagos.spec` (lista + confirmar + rechazar),
  `modal-asistentes.spec` (fila "declarado").
- **Móvil:** `EventosRepositoryImplTest` / `AdminRepositoryImplTest` — los métodos
  nuevos (payload + parseo).

## Fuera de alcance

- Saldo por peñista, movimientos, arrastre entre años (pieza 5 real).
- Editar una declaración (se anula y se rehace).
- Historial de declaraciones resueltas (solo se listan las `PENDIENTE`).
