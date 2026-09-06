# Listado de asistentes + "He pagado" (San Miguel) — Diseño

**Fecha:** 2026-09-06
**Rama:** `feature/cuentas` (continúa; HEAD `a68b48d`)
**Depende de:** piezas 3a (asistencia), 3b (ficha de bebida + cuota), 3c tanda 2
(VínculoFamiliar), todas ya en `feature/cuentas`.

## Objetivo

En el detalle de un evento de **San Miguel**, junto al botón "Cambiar lo que voy
a beber", añadir dos botones:

1. **Listado de asistentes** — abre un modal con la lista de quién va, qué bebe,
   cuánto le toca pagar y si ha pagado.
2. **He pagado** — abre un modal donde el peñista declara un pago: importe, a
   quién cubre (él, su pareja, sus hijos ≥18 con cuenta, sus invitados) y método
   (transferencia a la cuenta de la peña · bizum al administrador · efectivo).

Es la **pieza 4** del subsistema Cuentas ("sección peñista: cuota + pendiente /
pagado"), montada como botones en el detalle del evento en vez de dentro de la
Cuenta.

## Alcance

**Dentro (esta tanda):**
- Backend **solo de lectura**: `GET /api/v1/eventos/{id}/asistentes`.
- Web: los dos botones y los dos modales, completos.
- Móvil: lo mismo (KMP + Compose).
- Tests en las tres capas.

**Fuera (tandas siguientes, "cuando se defina cómo van los pagos"):**
- Persistir el pago declarado. Esta tanda el modal "He pagado" **no guarda
  nada**: valida, cierra y muestra "Pago enviado, un administrador lo
  confirmará". El estado de pago en el listado es `pendiente` para todos.
- Tabla `pago`, entidad, endpoint de alta, confirmación por el admin, pantalla
  de admin, avisos push, movimientos y saldo (pieza 5).
- Listado / pago para Chuletas, Migas y otros eventos — "cada evento tendrá su
  diseño propio".

## Reglas

### Cuándo salen los botones

Ambos botones salen ⟺ `evento.asistencia.ficha.llevaFicha` (hoy: solo San
Miguel). Van juntos, en el mismo sitio donde hoy vive "Cambiar lo que voy a
beber", dentro de la `<article class="carta-relieve">` del detalle.

- **Listado de asistentes**: siempre visible (cualquier miembro de la peña, esté
  apuntado o no, venga al pueblo o no).
- **He pagado**: solo si el usuario tiene cuota en ese evento, es decir
  `evento.asistencia.ficha.miFicha != null` y `miFicha.cuota != null`. Si su
  cuota está pendiente de fijar (`cuotaPendiente`), el botón no sale (no hay
  importe que pagar todavía).

### Listado de asistentes

Filas: todas las `asistencia_evento` del evento con estado `APUNTADO` o
`EN_DUDA` (los `NO_VOY` no salen). Orden: primero `APUNTADO`, luego `EN_DUDA`;
dentro de cada grupo, por nombre (insensible a mayúsculas/acentos, como el resto
del repo).

Por fila:
- **nombre** — el del usuario, o el `nombre` de la fila si es manual.
- **estado** — `APUNTADO` / `EN_DUDA` (para pintar "en duda" en otro color).
- **esManual** — `usuario_id IS NULL`.
- **bebida** — de `ficha_bebida` si existe: `alcohol` (nombre o `null`),
  `refresco` (nombre), `alternativa` (enum), `modalidad` (enum). `null` si la
  asistencia no tiene ficha (p. ej. añadido a mano sin rellenarla).
- **cuota** — `ficha_bebida.cuota` (puede ser `null` si el evento aún no tiene
  las cuotas puestas o no hay ficha).
- **pagado** — `false` siempre en esta tanda (placeholder para cuando haya
  pagos).

Totales del pie:
- **totalCuotas** — suma de las `cuota` no nulas.
- **totalPagado** — `0` siempre en esta tanda.

### "A quién puedo cubrir" (bloque `puedoPagarPor`)

El mismo `GET /asistentes` devuelve, para el usuario que pregunta, a quién puede
incluir en un pago además de a sí mismo. Una entrada por persona que **cumpla
las tres**: (a) tiene relación con el usuario, (b) está apuntada o en duda a
**este** evento, (c) tiene cuota no nula en este evento.

Relaciones (reusa `VinculoFamiliarService` + asistencias manuales):
- **PAREJA** — la pareja con `VinculoPareja.estado = ACEPTADO`.
- **HIJO** — hijos con `Hijo.usuario != null` **y** `Hijo.mayorDeEdad = true`,
  suyos o co-declarados por la pareja (igual que
  `VinculoFamiliarService.personasQuePuedoResponder`, pero filtrando por
  `mayorDeEdad`).
- **INVITADO** — filas `asistencia_evento` de este evento con `usuario_id IS
  NULL` y `registrado_por_id = usuarioId` (los que el propio usuario añadió a
  mano).

Cada entrada: `{ nombre, cuota, relacion, usuarioId?, asistenciaId? }`
(`usuarioId` para pareja/hijo, `asistenciaId` para invitado).

El propio usuario **no** va en `puedoPagarPor` (en el modal es una línea fija
"Yo — {miFicha.cuota} €").

### Modal "He pagado" (solo UI, no persiste)

Se abre desde el detalle. Contenido:

- **Importe** — `number`, €, obligatorio, > 0. Sugerencia inicial: la suma de
  las cuotas de las personas marcadas (se recalcula al marcar/desmarcar, pero el
  usuario puede sobrescribirlo).
- **A quién cubre** — lista de checkboxes:
  - Línea fija arriba: "Yo — {miFicha.cuota} €", marcada y no desmarcable.
  - Una línea por entrada de `puedoPagarPor`: "{nombre} ({relacion legible}) —
    {cuota} €", desmarcada por defecto.
  - Si `puedoPagarPor` está vacío, solo se ve la línea "Yo".
- **Método** — radios, obligatorio, sin valor por defecto:
  - `TRANSFERENCIA` → "Transferencia a la cuenta de la peña"
  - `BIZUM` → "Bizum al administrador"
  - `EFECTIVO` → "Efectivo"
- **Enviar** — habilitado si importe > 0 y hay método. Al pulsar: cierra el
  modal y muestra en el detalle un aviso "Pago enviado. Un administrador lo
  confirmará." **No hay llamada al backend.**
- **Cancelar** — cierra sin más.

El objeto que el modal tendría listo para mandar (se define aquí para que la
tanda de pagos lo herede, aunque ahora no se envíe):

```json
{
  "importe": 90.00,
  "metodo": "BIZUM",
  "cubre": {
    "yo": true,
    "usuarioIds": [42],
    "asistenciaIds": [17]
  }
}
```

## Contrato de API

Prefijo `/api/v1`. Cuelga de `AsistenciaController` (donde ya viven
`/eventos/{id}/asistencias*`).

```
GET /api/v1/eventos/{id}/asistentes
  auth: cualquier miembro activo de la peña
  200: ListadoAsistentesResponse
  404: EVENTO_NO_ENCONTRADO
  409: EVENTO_SIN_FICHA        // el evento no es de San Miguel (cuenta.llevaFichaBebida = false)
```

```jsonc
// ListadoAsistentesResponse
{
  "asistentes": [
    {
      "nombre": "Juan Pérez",
      "estado": "APUNTADO",              // APUNTADO | EN_DUDA
      "esManual": false,
      "bebida": {                        // o null
        "alcohol": "Barceló",            // o null (no bebe alcohol)
        "refresco": "Coca-Cola",
        "alternativa": "CERVEZA",        // CERVEZA | TINTO_VERANO | NADA | CERVEZA_ESPECIAL
        "modalidad": "COMPLETA"          // COMPLETA | SOLO_CERVEZA | UN_DIA | EMBARAZADA
      },
      "cuota": 45.00,                    // o null
      "pagado": false                    // siempre false esta tanda
    }
  ],
  "totalCuotas": 1234.00,
  "totalPagado": 0.00,
  "puedoPagarPor": [
    { "nombre": "Ana", "cuota": 45.00, "relacion": "PAREJA", "usuarioId": 42, "asistenciaId": null },
    { "nombre": "Primo de Juan", "cuota": 45.00, "relacion": "INVITADO", "usuarioId": null, "asistenciaId": 17 }
  ],
  "miCuota": 45.00                       // = miFicha.cuota, o null si no tengo ficha/cuota
}
```

## Componentes

### Backend — paquete `com.baniterio.api.evento`

- **DTOs nuevos** (`dto/`): `ListadoAsistentesResponse`, `AsistenteFila`,
  `BebidaFila`, `PersonaPagable` (con enum `RelacionPago = { PAREJA, HIJO,
  INVITADO }`).
- `AsistenciaService.listadoAsistentes(Long usuarioId, Long eventoId) :
  ListadoAsistentesResponse` — `@Transactional(readOnly = true)`:
  - carga el evento (404), valida `evento.getCuenta().isLlevaFichaBebida()` (409
    `EventoSinFichaException`, ya existe).
  - `asistencias.findByEventoIdAndEstadoIn(eventoId, [APUNTADO, EN_DUDA])` con
    la ficha cargada (fetch join o `@EntityGraph` para no hacer N+1).
  - construye filas, ordena, suma totales.
  - `puedoPagarPor`: `vinculoFamiliar` para pareja/hijos ≥18 con cuenta +
    `asistencias.findByEventoIdAndUsuarioIsNullAndRegistradoPorId(...)` para
    invitados; cruza con las asistencias del evento y su cuota; descarta cuota
    nula.
- `VinculoFamiliarService` — método nuevo o parámetro para filtrar hijos por
  `mayorDeEdad` sin romper `personasQuePuedoResponder` (que no filtra edad).
  Opción: `personasParaPago(actuanteId)` que reusa la lógica y añade el filtro.
- `AsistenciaEventoRepository` — `findByEventoIdAndEstadoIn`,
  `findByEventoIdAndUsuarioIsNullAndRegistradoPorId` (o equivalente ya
  existente).
- `AsistenciaController` — `GET /eventos/{id}/asistentes` → delega en el
  servicio con el `usuarioId` del token.
- **Sin migración, sin entidad nueva, sin cambios de escritura.**

### Web — `front/src/app/panel/eventos`

- `eventos.types.ts` — `ListadoAsistentes`, `AsistenteFila`, `BebidaFila`,
  `PersonaPagable`, `RelacionPago`, `MetodoPago = 'TRANSFERENCIA' | 'BIZUM' |
  'EFECTIVO'`.
- `eventos.service.ts` — `asistentesEvento(id: number)`.
- `evento-detalle/evento-detalle.ts` + `.html` — dos botones nuevos junto a
  "Cambiar lo que voy a beber"; señales `modalAsistentes`, `modalPago`.
- `eventos/modal-asistentes/` — componente del modal del listado (overlay fijo
  como `ModalRespuestaEvento`: `fixed inset-0 z-50`, cierre con botón y con
  Escape / clic fuera). Entrada: `eventoId`. Carga el listado al abrirse.
- `eventos/modal-he-pagado/` — componente del modal de pago. Entradas:
  `miCuota`, `puedoPagarPor`. Estado interno: importe, set de marcados, método.
  Salida `(enviar)` con el objeto de arriba — el padre solo muestra el aviso y
  cierra (no llama a servicio).
- Reutiliza los helpers de texto de modalidad/alternativa que ya hay en
  `asistencia-evento.ts` / `ficha-bebida` (extraer a un `eventos.textos.ts` si
  hace falta compartirlos).

### Móvil — `mobile/shared/.../`

- `data/dto/EventoDtos.kt` — `ListadoAsistentesDto`, `AsistenteFilaDto`,
  `BebidaFilaDto`, `PersonaPagableDto`; enums `RelacionPago`, `MetodoPago`.
- `data/EventosRepository.kt` (+ `Impl`) — `asistentes(eventoId): ResultadoEvento<ListadoAsistentesDto>`.
- `ui/eventos/EventoDetalleScreen.kt` — dos botones + dos composables de diálogo
  (`ListadoAsistentesDialog`, `HePagadoDialog`) en el mismo fichero o en
  `ui/eventos/`. El de pago emite el objeto y la pantalla solo muestra un
  `snackbar`/aviso; no toca el repo.

## Errores

Ninguno nuevo. `EVENTO_NO_ENCONTRADO`, `EVENTO_SIN_FICHA`, `SIN_PERMISO`,
`VALIDACION` ya existen en `ApiExceptionHandler`.

## Casos límite

- **Evento de San Miguel sin cuotas puestas** → el listado sale, todas las
  `cuota` a `null`, `totalCuotas = 0`, el botón "He pagado" no aparece para
  nadie (nadie tiene `miCuota`).
- **Asistente apuntado sin ficha** (añadido a mano y no rellenada) → fila con
  `bebida: null`, `cuota: null`.
- **`puedoPagarPor` vacío** (sin pareja/hijos/invitados apuntados) → el modal de
  pago solo muestra la línea "Yo".
- **Pareja o hijo apuntado pero con cuota `null`** → no sale en `puedoPagarPor`
  (no hay importe que cubrir).
- **Hijo con cuenta pero menor de edad** → nunca sale en `puedoPagarPor` aunque
  esté apuntado.
- **El usuario no está apuntado** pero abre el listado → lo ve entero; el botón
  "He pagado" no le sale.
- **Evento que no es de San Miguel** → los dos botones no se pintan; si aun así
  se llama al endpoint, 409 `EVENTO_SIN_FICHA`.

## Test plan

- **IT (backend)** `ListadoAsistentesIT`:
  - trae `APUNTADO` + `EN_DUDA`, no `NO_VOY`; orden apuntados→enduda→nombre.
  - fila con ficha trae bebida + cuota; fila sin ficha trae `null`/`null`.
  - `totalCuotas` = suma de cuotas no nulas.
  - evento que no es San Miguel → 409 `EVENTO_SIN_FICHA`.
  - `puedoPagarPor`: incluye pareja apuntada con cuota; incluye hijo ≥18 con
    cuenta apuntado; excluye hijo menor; excluye pareja sin cuota; incluye
    invitado añadido por mí, excluye invitado añadido por otro; nunca incluye a
    uno mismo.
- **Web (Vitest)**:
  - `evento-detalle.spec` — los dos botones salen en San Miguel y no en otro
    evento; "He pagado" solo con `miFicha.cuota`.
  - `modal-asistentes.spec` — pinta filas, marca "en duda", pinta totales.
  - `modal-he-pagado.spec` — "Yo" fijo marcado; marcar una persona suma al
    importe sugerido; "Enviar" deshabilitado sin método; al enviar emite el
    objeto correcto.
- **Móvil (`:shared:testAndroidHostTest`)**:
  - `EventosRepositoryImplTest` — `asistentes` pega a
    `/api/v1/eventos/{id}/asistentes` con Bearer y deserializa el DTO
    (incluido `puedoPagarPor` y enums).

## Plan de implementación

Se genera con la skill `writing-plans` tras aprobar este spec.
