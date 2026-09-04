# Ficha de bebida + cálculo de cuota (pieza 3b) — Diseño

**Fecha:** 2026-09-04
**Rama:** `feature/cuentas` (continúa)
**Depende de:** pieza 3a (asistencia a eventos), ya en `feature/cuentas`.

## Objetivo

Para los eventos de **San Miguel**, cuando alguien responde *Me apunto* o *En
duda*, pedirle qué va a beber y con esos datos calcular y mostrarle su **cuota**.
El admin que añade gente a mano rellena también esa ficha. Las listas de bebida
viven en BBDD; si alguien mete una bebida nueva ("Otra…") se avisa a los
administradores para que la acepten, y al aceptarla empieza a salir en los
desplegables.

## Alcance

**Dentro:**
- Catálogo de bebidas en BBDD (alcohol / refresco) con aprobación.
- Ficha de bebida por asistencia en eventos de San Miguel (respuesta *Me apunto* y
  *En duda*).
- Cálculo de modalidad y cuota; se muestra al peñista.
- Añadir a mano (admin) con ficha completa.
- Recalcular todas las cuotas de un evento cuando cambia su cuota máxima.
- Pantalla de admin para aceptar/rechazar bebidas propuestas.
- Web + móvil + backend.

**Fuera (piezas siguientes):**
- Sección peñista dentro de la Cuenta con pagado / pendiente de pago (pieza 4).
- Movimientos y saldo (pieza 5).
- Pareja / hijos / invitados (pieza 3c).
- Ficha/cuota para los otros eventos (Chuletas, Migas) — su modelo de dinero se
  decide al desarrollarlos.

## Reglas de negocio

### Cuándo hay ficha

Un evento lleva ficha de bebida ⟺ `evento.cuenta.lleva_ficha_bebida = true`.
Se siembra `true` solo para la cuenta "San Miguel". San Miguel es **siempre de
2 días** (`evento.fecha` y `evento.fechaFin`); si un evento marcado no tiene
`fechaFin` o el rango no es de 2 días, se degrada a "1 día" = `fecha` y no se
ofrece elegir día (se asume asistencia completa).

### Formulario

Aparece tras responder *Me apunto* o *En duda* (mismo formulario; en *En duda* el
texto de ayuda dice "¿qué beberías si al final vas?"). Campos:

- **Alcohol** — desplegable. Primero "No bebo alcohol" (por defecto), luego las
  bebidas `ACEPTADA` de tipo `ALCOHOL` por nombre, luego "Otra…". "Otra…" abre un
  campo de texto.
- **Refresco** — desplegable, obligatorio. Bebidas `ACEPTADA` de tipo `REFRESCO`
  por nombre + "Otra…" (con texto).
- **Alternativa** — desplegable: `Cerveza` / `Tinto de verano` / `Nada` (por
  defecto) / `Cerveza especial`. Si `Cerveza especial` → campo de texto libre
  (**no** pasa por aprobación, es una nota).
- **Embarazada** — check. Si se marca: se deshabilitan alcohol y alternativa
  (quedan en "No bebo alcohol" / "Nada"); solo se elige refresco.
- **¿Qué días vas?** — solo si el evento tiene 2 días: *Los dos días* (por
  defecto) / *Solo el {fecha día 1}* / *Solo el {fecha día 2}*.

### Modalidad (calculada, se guarda)

Primera que encaje:

1. `embarazada` → **EMBARAZADA**
2. va exactamente 1 de los 2 días → **UN_DIA**
3. `alcohol = "No bebo alcohol"` **y** `alternativa ∈ {CERVEZA, CERVEZA_ESPECIAL,
   TINTO_VERANO}` → **SOLO_CERVEZA**
4. resto → **COMPLETA**

### Cuota (calculada, se guarda)

`M = evento.cuotaMaxima`. Si `M` es `null` → `cuota = null` (se muestra "pendiente
de que se fije la cuota máxima").

| modalidad | cuota |
|---|---|
| EMBARAZADA | `5` |
| UN_DIA | `M / 2 + 1` |
| SOLO_CERVEZA | `max(M - 10, 0)` |
| COMPLETA | `M` |

La modalidad no cambia al cambiar `M`; solo se recalcula el importe.

### Bebida nueva ("Otra…")

Al guardar la ficha con `alcoholOtra` / `refrescoOtra` no vacío:

1. Se normaliza el nombre (trim, colapsar espacios).
2. Si ya existe una `bebida` de ese tipo con ese nombre (ignore case):
   - `ACEPTADA` → se usa esa, sin aviso.
   - `PENDIENTE` → se reutiliza esa fila (no se duplica), sin aviso nuevo.
   - `RECHAZADA` → se reabre a `PENDIENTE` y se avisa otra vez.
3. Si no existe → se crea `bebida(tipo, nombre, estado=PENDIENTE, propuesta_por)`
   y se publica `AvisoPushEvent(Audiencia.Administradores, "Bebidas",
   "{nombre usuario} ha propuesto «{bebida}» ({alcohol|refresco})")`.
4. La ficha guarda la referencia a esa `bebida` (aunque esté `PENDIENTE`).

Una bebida `PENDIENTE` **no** aparece en los desplegables de nadie; el que la
propuso ve su elección en su propia ficha.

### Aprobación de bebidas

Cualquier administrador o superadministrador (mismo criterio que
`ServicioPermisos.esAdministrador`, sin área nueva del panel).

- **Aceptar** → `estado = ACEPTADA`. Ya sale en los desplegables. No recalcula
  cuotas (elegir "Otra" en alcohol ya contaba como "bebe alcohol").
- **Rechazar** → `estado = RECHAZADA` + push a quien la propuso
  ("Tu propuesta de bebida «{X}» no se ha aceptado."). Las fichas que la
  referencian se quedan como están; la próxima vez que esa persona edite su ficha
  tendrá que elegir otra (el desplegable ya no la ofrece; se le muestra como
  "«X» (rechazada) — elige otra").

### Recalcular al cambiar la cuota máxima

En `EventoService.editar`, si `evento.cuotaMaxima` cambia de valor y el evento
lleva ficha, se recalcula `cuota` (no `modalidad`) de todas las
`ficha_bebida` de las asistencias de ese evento.

### Poder cambiar la ficha

Como la respuesta de 3a: se puede editar la ficha hasta `evento.fecha` (inclusive
no, `fecha.isBefore(hoy)` → 409 `EVENTO_YA_PASADO`, mismo criterio que responder).

## Modelo de datos

### V22 — catálogo y flag

```sql
-- Catálogo de bebidas para la ficha de San Miguel. Se propone desde la ficha
-- ("Otra…") y un admin la acepta/rechaza. Solo las ACEPTADA salen en los
-- desplegables.
CREATE TABLE bebida (
    id               BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tipo             VARCHAR(16)  NOT NULL,
    nombre           VARCHAR(80)  NOT NULL,
    estado           VARCHAR(16)  NOT NULL DEFAULT 'PENDIENTE',
    propuesta_por_id BIGINT       REFERENCES usuario (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_bebida_tipo   CHECK (tipo IN ('ALCOHOL', 'REFRESCO')),
    CONSTRAINT ck_bebida_estado CHECK (estado IN ('ACEPTADA', 'PENDIENTE', 'RECHAZADA'))
);
CREATE UNIQUE INDEX uq_bebida_tipo_nombre ON bebida (tipo, lower(nombre));

INSERT INTO bebida (tipo, nombre, estado) VALUES
  ('ALCOHOL','Barceló','ACEPTADA'), ('ALCOHOL','White Label','ACEPTADA'),
  ('ALCOHOL','Brugal','ACEPTADA'), ('ALCOHOL','JB','ACEPTADA'),
  ('ALCOHOL','Legendario','ACEPTADA'), ('ALCOHOL','Puerto de Indias','ACEPTADA'),
  ('ALCOHOL','Four Roses','ACEPTADA'), ('ALCOHOL','Negruita','ACEPTADA'),
  ('ALCOHOL','Red Label','ACEPTADA'), ('ALCOHOL','Beefeater','ACEPTADA'),
  ('ALCOHOL','Malibú','ACEPTADA'), ('ALCOHOL','Tanqueray','ACEPTADA'),
  ('ALCOHOL','Negrita','ACEPTADA'), ('ALCOHOL','Ballantines','ACEPTADA'),
  ('ALCOHOL','Absolut','ACEPTADA'), ('ALCOHOL','Seagram''s','ACEPTADA'),
  ('ALCOHOL','DYC','ACEPTADA'), ('ALCOHOL','Larios','ACEPTADA'),
  ('REFRESCO','Coca-Cola','ACEPTADA'), ('REFRESCO','Coca-Cola Zero','ACEPTADA'),
  ('REFRESCO','Coca-Cola Light','ACEPTADA'), ('REFRESCO','Fanta Naranja','ACEPTADA'),
  ('REFRESCO','Fanta Limón','ACEPTADA'), ('REFRESCO','Schweppes Limón','ACEPTADA'),
  ('REFRESCO','Sprite','ACEPTADA'), ('REFRESCO','Sprite Zero','ACEPTADA'),
  ('REFRESCO','Nestea','ACEPTADA'), ('REFRESCO','Tónica','ACEPTADA'),
  ('REFRESCO','Trina Naranja','ACEPTADA'), ('REFRESCO','Aquarius','ACEPTADA');

ALTER TABLE cuenta ADD COLUMN lleva_ficha_bebida BOOLEAN NOT NULL DEFAULT false;
UPDATE cuenta SET lleva_ficha_bebida = true
 WHERE nombre = 'San Miguel'
   AND pena_id = (SELECT id FROM pena WHERE slug = 'baniterio');
```

### V23 — ficha de bebida

```sql
CREATE TABLE ficha_bebida (
    asistencia_id     BIGINT       PRIMARY KEY REFERENCES asistencia_evento (id) ON DELETE CASCADE,
    alcohol_bebida_id BIGINT       REFERENCES bebida (id),
    refresco_bebida_id BIGINT      NOT NULL REFERENCES bebida (id),
    alternativa       VARCHAR(20)  NOT NULL DEFAULT 'NADA',
    cerveza_especial  VARCHAR(80),
    embarazada        BOOLEAN      NOT NULL DEFAULT false,
    asiste_dia_1      BOOLEAN      NOT NULL DEFAULT true,
    asiste_dia_2      BOOLEAN      NOT NULL DEFAULT true,
    modalidad         VARCHAR(16)  NOT NULL,
    cuota             NUMERIC(7,2),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ficha_alternativa CHECK (alternativa IN ('CERVEZA','TINTO_VERANO','NADA','CERVEZA_ESPECIAL')),
    CONSTRAINT ck_ficha_modalidad   CHECK (modalidad IN ('COMPLETA','SOLO_CERVEZA','UN_DIA','EMBARAZADA')),
    CONSTRAINT ck_ficha_algun_dia   CHECK (asiste_dia_1 OR asiste_dia_2)
);
```

### Entidades (JPA + Lombok, patrón del repo)

- `identidad/Bebida.java` — `id`, `TipoBebida tipo`, `String nombre`,
  `EstadoBebida estado`, `Usuario propuestaPor` (LAZY, nullable),
  `Instant createdAt/updatedAt`.
- `identidad/TipoBebida` = `{ ALCOHOL, REFRESCO }`.
- `identidad/EstadoBebida` = `{ ACEPTADA, PENDIENTE, RECHAZADA }`.
- `identidad/FichaBebida.java` — `@Id @OneToOne AsistenciaEvento asistencia`
  (`@MapsId`), `Bebida alcohol` (LAZY, nullable), `Bebida refresco` (LAZY, not
  null), `Alternativa alternativa`, `String cervezaEspecial`, `boolean embarazada`,
  `boolean asisteDia1/asisteDia2`, `Modalidad modalidad`, `BigDecimal cuota`,
  timestamps.
- `identidad/Alternativa` = `{ CERVEZA, TINTO_VERANO, NADA, CERVEZA_ESPECIAL }`.
- `identidad/Modalidad` = `{ COMPLETA, SOLO_CERVEZA, UN_DIA, EMBARAZADA }`.

## API

Prefijo `/api/v1`.

### Catálogo

- `GET /bebidas/catalogo` → `{ "alcohol": [{id,nombre}], "refresco": [{id,nombre}] }`
  (solo `ACEPTADA`, por nombre). Cualquier miembro.

### Ficha

- `PUT /eventos/{id}/ficha-bebida` — cuerpo:
  ```json
  {
    "estado": "APUNTADO",            // APUNTADO | EN_DUDA
    "alcoholBebidaId": 3,            // null si "No bebo alcohol" o si viene alcoholOtra
    "alcoholOtra": null,             // texto si eligió "Otra"
    "refrescoBebidaId": 20,
    "refrescoOtra": null,
    "alternativa": "CERVEZA",
    "cervezaEspecial": null,
    "embarazada": false,
    "asisteDia1": true,
    "asisteDia2": true
  }
  ```
  Hace upsert de la asistencia (estado) **y** de la ficha. Resuelve `*Otra`
  (crea/reutiliza `bebida` PENDIENTE + push admins). Calcula modalidad y cuota.
  Respuesta 200 `FichaBebidaResponse`:
  ```json
  { "modalidad": "SOLO_CERVEZA", "cuota": 16.00, "cuotaPendiente": false }
  ```
  Errores: 404 `EVENTO_NO_ENCONTRADO`; 409 `EVENTO_YA_PASADO`; 409
  `EVENTO_SIN_FICHA` si el evento no lleva ficha; 400 `VALIDACION`
  (estado ∉ {APUNTADO, EN_DUDA}; refresco ausente; `cervezaEspecial` sin
  `alternativa=CERVEZA_ESPECIAL`; texto "Otra" y id a la vez).

- `GET /eventos/{id}` (`EventoDetalle`) gana, dentro del bloque `asistencia` ya
  existente, un sub-bloque `ficha` (o `null` si no aplica / sin rellenar):
  ```json
  "ficha": {
    "llevaFicha": true,
    "diasEvento": ["2026-09-25","2026-09-26"],
    "miFicha": {
      "alcoholBebidaId": null, "alcohol": "No bebo alcohol",
      "refrescoBebidaId": 20, "refresco": "Fanta Limón",
      "alternativa": "CERVEZA", "cervezaEspecial": null,
      "embarazada": false, "asisteDia1": true, "asisteDia2": false,
      "modalidad": "UN_DIA", "cuota": 14.00, "cuotaPendiente": false,
      "bebidaPendiente": false      // true si su alcohol/refresco está PENDIENTE o RECHAZADA
    }
  }
  ```
  `miFicha` es `null` si el usuario aún no ha rellenado la ficha.
  `llevaFicha=false` en eventos que no son de San Miguel.

### Añadir a mano (extiende 3a)

- `POST /eventos/{id}/asistencias` — cuerpo `{ nombre, estado, ficha? }` donde
  `ficha` es el mismo objeto que `PUT /ficha-bebida` sin `estado`. Obligatoria si
  el evento lleva ficha y `estado ∈ {APUNTADO, EN_DUDA}`. Respuesta
  `AsistenciaResumen` gana `cuota` (nullable) y `modalidad` (nullable).

### Admin de bebidas

- `GET /bebidas?estado=PENDIENTE` → `[{ id, tipo, nombre, propuestaPor:{id,nombre},
  createdAt }]`. Solo admin/superadmin (403 `SIN_PERMISO`).
- `POST /bebidas/{id}/aceptar` → 204. 404 `BEBIDA_NO_ENCONTRADA`. Idempotente si
  ya `ACEPTADA`.
- `POST /bebidas/{id}/rechazar` → 204 + push al proponente. 404
  `BEBIDA_NO_ENCONTRADA`.

## Servicios (backend)

- `evento/CalculadoraCuota.java` — pura, sin Spring. `Resultado calcular(BigDecimal
  cuotaMaxima, boolean embarazada, boolean dia1, boolean dia2, Bebida alcohol,
  Alternativa alt)` → `record Resultado(Modalidad modalidad, BigDecimal cuota)`
  (`cuota` null si `cuotaMaxima` null). Todo el peso de los tests unitarios aquí.
- `evento/FichaBebidaService.java` — orquesta: cargar evento (valida
  `llevaFicha` y no pasado), upsert asistencia (reusa `AsistenciaService` o su
  repo), resolver `*Otra` (`BebidaService`), calcular con `CalculadoraCuota`,
  guardar `FichaBebida`. Métodos: `guardar(usuarioId, eventoId, FichaBebidaRequest)
  : FichaBebidaResponse`; `guardarAMano(adminId, eventoId, asistenciaId,
  FichaBebidaRequest)`; `recalcularCuotas(eventoId)`; `detalleDe(usuarioId, Evento)
  : FichaBebidaDetalle` (para `EventoDetalle`).
- `evento/BebidaService.java` — `catalogo() : CatalogoBebidas`;
  `resolverOtra(TipoBebida, String nombre, Long propuestaPorId) : Bebida` (crea /
  reutiliza / reabre + push); `pendientes()`; `aceptar(id)`; `rechazar(id)` (+push).
- `EventoService.editar` llama a `fichaBebidaService.recalcularCuotas(id)` si la
  cuota máxima cambió y el evento lleva ficha.

Dependencia circular: `FichaBebidaService` usa `AsistenciaEventoRepository`
directamente (no `AsistenciaService`) para el upsert de estado, igual que
`AsistenciaService` no depende de `EventoService`. `EventoService` →
`FichaBebidaService` (ya depende de `AsistenciaService`, mismo patrón).

## Web

- `panel/eventos/eventos.types.ts` — tipos `Alternativa`, `Modalidad`,
  `FichaBebidaMia`, `FichaBebidaBloque`, `CatalogoBebidas`, `AsistenciaResumen`
  (+ `cuota`, `modalidad`); `EventoDetalle.asistencia.ficha`.
  `CodigoErrorEvento` += `EVENTO_SIN_FICHA`, `BEBIDA_NO_ENCONTRADA`.
- `panel/eventos/eventos.service.ts` — `catalogoBebidas()`,
  `guardarFichaBebida(id, body)`, `bebidasPendientes()`, `aceptarBebida(id)`,
  `rechazarBebida(id)`.
- `panel/eventos/ficha-bebida/ficha-bebida.ts` + `.html` — componente reutilizable
  del formulario. Entradas: `catalogo`, `dias` (0/1/2 fechas), `fichaActual`,
  `enDuda` (cambia textos). Salida: `(guardar)` con el body. Reglas de
  habilitar/deshabilitar (embarazada) en el propio componente.
- `panel/eventos/evento-detalle/` — tras responder *Me apunto* / *En duda* en un
  evento con `asistencia.ficha.llevaFicha`, mostrar `<app-ficha-bebida>`
  (precargada con `miFicha` si existe). Al guardar → llamar al servicio, mostrar
  "Tu cuota: {cuota} € · {modalidad legible}" o "Cuota pendiente de fijar". Si
  `bebidaPendiente` → aviso "Tu bebida está pendiente de que un admin la acepte".
- `panel/responder/` — igual: en un evento de San Miguel, tras pulsar *Me apunto*
  / *En duda* mostrar la ficha antes de avanzar al siguiente pendiente.
- `panel/eventos/evento-detalle/` bloque "Añadir a mano" — si el evento lleva
  ficha, tras nombre+estado mostrar `<app-ficha-bebida>` y mandar todo junto.
- `panel/admin/bebidas/bebidas.ts` + `.html` — lista de `PENDIENTE` con botones
  Aceptar / Rechazar. Ruta `panel/administracion/bebidas`
  (`canActivate: [perfilCompletoGuard, respuestaPendienteGuard]`, y dentro el
  componente esconde todo si no es admin — igual que `AdminSolicitudes` usa
  `esAdmin`). Enlace en `panel/admin/indice`.
- Tests: servicio (un caso por método), `ficha-bebida.spec` (embarazada
  deshabilita; un día marca modalidad; "Otra" manda texto), `evento-detalle.spec`
  (aparece la ficha para San Miguel, no para otros; muestra la cuota),
  `bebidas.spec` (lista + aceptar).

## Móvil (KMP + Compose)

- `data/dto/` — `CatalogoBebidasDto`, `BebidaRefDto(id,nombre)`,
  `FichaBebidaBody`, `FichaBebidaResponseDto`, `FichaBebidaMiaDto`,
  `FichaBebidaBloqueDto`; `EventoDetalle.asistencia.ficha`;
  `AsistenciaResumenDto` (+ `cuota`, `modalidad`); `BebidaPendienteDto`.
- `data/BebidaRepository.kt` (+`Impl`) — `catalogo()`, `pendientes()`,
  `aceptar(id)`, `rechazar(id)`; `ResultadoBebida` + `CodigoErrorBebida`.
- `data/AsistenciaRepository` gana `guardarFicha(eventoId, FichaBebidaBody) :
  ResultadoAsistencia<FichaBebidaResponseDto>` y el `anadir` con ficha opcional.
- `Dependencias` + `bebidaRepo`.
- `ui/eventos/FichaBebidaForm.kt` — composable del formulario (desplegables
  `ExposedDropdownMenuBox`, check embarazada, radios de día). Reutilizado en
  `EventoDetalleScreen`, `ResponderEventoScreen` y el bloque "Añadir a mano".
- `ui/eventos/EventoDetalleScreen` / `ResponderEventoScreen` — mostrar la ficha
  tras *Me apunto* / *En duda* en San Miguel; mostrar la cuota resultante.
- `nav/Screen` + `App.kt` — `Screen.AdminBebidas` + `AdminBebidasScreen` (lista
  pendientes, aceptar/rechazar), enlazada desde `AdminIndexScreen`.
- Tests: `BebidaRepositoryImplTest`, `AsistenciaRepositoryImplTest` (+ guardarFicha
  con MockEngine).

## Errores nuevos (`ApiExceptionHandler`)

| excepción | HTTP | código |
|---|---|---|
| `EventoSinFichaException` | 409 | `EVENTO_SIN_FICHA` |
| `BebidaNoEncontradaException` | 404 | `BEBIDA_NO_ENCONTRADA` |

(`EVENTO_YA_PASADO`, `EVENTO_NO_ENCONTRADO`, `SIN_PERMISO`, `VALIDACION` ya existen.)

## Casos límite decididos

- **No bebe alcohol + alternativa NADA + no embarazada + 2 días** → `COMPLETA`,
  cuota `M` (paga completa aunque solo tome refresco).
- **`M - 10 < 0`** → cuota `0` (no negativa).
- **Evento marcado sin `fechaFin` o rango ≠ 2 días** → sin elegir día, se asume
  asistencia completa (no `UN_DIA`).
- **Evento sin `cuotaMaxima`** → la ficha se guarda, `cuota = null`,
  `cuotaPendiente = true`; al fijar la cuota máxima se recalculan.
- **Bebida propuesta ya existente `ACEPTADA`** → se usa directamente, sin aviso.
- **En duda** → misma ficha y misma cuota calculada que apuntado (tentativa).
- **`cerveza_especial`** → nota de texto, nunca entra al catálogo ni a aprobación.

## Test plan

- **Unitario (backend):** `CalculadoraCuotaTest` — matriz de las 4 modalidades,
  precedencia (embarazada+un día → 5; solo cerveza+un día → M/2+1), `M null`,
  `M-10<0`, "no bebe + nada" → COMPLETA.
- **IT (backend):** `FichaBebidaIT` — guardar ficha calcula cuota; evento sin
  ficha → 409; evento pasado → 409; "Otra" crea bebida PENDIENTE + no sale en
  catálogo; aceptar bebida → sale en catálogo; cambiar cuota máxima recalcula;
  añadir a mano con ficha; `EventoDetalle` trae `ficha`.
- **Web:** Vitest como arriba.
- **Móvil:** `:shared:testAndroidHostTest` con MockEngine.

## Plan de implementación

Ver `docs/superpowers/plans/2026-09-04-ficha-bebida-cuota-3b.md`.
