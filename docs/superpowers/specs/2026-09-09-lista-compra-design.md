# Lista de la compra por evento — Diseño

**Fecha:** 2026-09-09
**Rama:** `feature/lista-compra` (desde `main`, ya con V35–V37 del inventario)
**Estado:** aprobado en brainstorming, pendiente de plan

## Objetivo

Cada evento tiene una "lista de la compra" que se calcula sola a partir de la
gente apuntada y de los días de fiesta, aplicando una fórmula por artículo. Sale
al pulsar el botón **"Lista de la compra"** del detalle del evento (hoy es un
placeholder). Un administrador con el área `INVENTARIO` puede ajustar las
cantidades de cada evento según las sobras del año anterior y añadir o quitar
artículos, desde una pantalla nueva del panel de administración:
**"Cantidades para eventos"**.

Esta entrega es el **bloque 1**: cálculo bruto con redondeo hacia arriba. El
**bloque 2** (restar lo que ya hay en el inventario de la fiesta antes de
redondear, con el caso especial del fregasuelos) se implementará después; el
modelo y los DTO se dejan preparados para no necesitar migración entonces.

## Alcance

Dentro:

- Tabla `regla_compra` (plantilla global de la peña) sembrada con la lista de
  fórmulas actual.
- Tabla `regla_compra_evento` (instancias por evento, copiadas de la plantilla
  la primera vez que se abre la lista de un evento).
- Motor de cálculo con fórmulas tipadas.
- API de lectura (`GET /eventos/{id}/lista-compra`) y API de administración
  (`/admin/lista-compra/**`).
- Web: pantalla de lectura `/panel/eventos/:id/lista-compra`; pantalla de
  administración `/panel/administracion/lista-compra` (lista de eventos) y
  `/panel/administracion/lista-compra/:id` (editor).
- Móvil: paridad completa — repositorio + DTO, pantalla de lectura desde el
  detalle del evento, y editor de administración desde el índice de admin.

Fuera (bloque 2 o más adelante):

- Restar el inventario de la fiesta (`articulo_evento`) del cálculo.
- Umbral del fregasuelos ("si hay 0,2 L o menos se pide una botella").
- Arrastrar automáticamente las reglas de un evento al año siguiente cuando se
  cierra el año. Hoy solo hay tres eventos; se aborda cuando se creen ediciones
  nuevas. La plantilla global es la base sobre la que se apoyará ese arrastre.
- Editar la plantilla global desde la interfaz (por ahora solo se cambia con una
  migración). El editor de administración trabaja siempre sobre las reglas de un
  evento concreto.

## Modelo de datos

### `regla_compra` (plantilla global)

| Columna | Tipo | Nota |
|---|---|---|
| `id` | `BIGINT` GENERATED PK | |
| `pena_id` | `BIGINT NOT NULL` → `pena(id)` | |
| `categoria` | `VARCHAR(20) NOT NULL` | `ALCOHOL` / `CERVEZA` / `REFRESCOS` / `LIMPIEZA` / `COMIDA` (CHECK) |
| `nombre` | `VARCHAR(120) NOT NULL` | `''` en las reglas dinámicas |
| `tamano` | `VARCHAR(20) NOT NULL` | etiqueta libre ("70 cl", "lata", "bolsa", "bote", "rollo"…). No se valida contra `CategoriaInventario.tamanos()`: es solo texto para mostrar |
| `tipo_formula` | `VARCHAR(30) NOT NULL` | enum, ver abajo (CHECK) |
| `factor` | `NUMERIC(8,3) NOT NULL` | `CHECK (factor >= 0)` |
| `por_cada` | `INT` | divisor de `POR_CADA_N_PENISTAS`; `NULL` en el resto. `CHECK (por_cada IS NULL OR por_cada > 0)` |
| `orden` | `INT NOT NULL DEFAULT 0` | orden dentro de la categoría |

Siembra idempotente por `(pena_id, categoria, nombre, tipo_formula)` con
`INSERT ... WHERE NOT EXISTS`.

### `regla_compra_evento` (instancia por evento)

Mismas columnas que `regla_compra`, más:

| Columna | Tipo | Nota |
|---|---|---|
| `evento_id` | `BIGINT NOT NULL` → `evento(id) ON DELETE CASCADE` | |
| `origen` | `VARCHAR(10) NOT NULL` | `PLANTILLA` (copiada de `regla_compra`) o `MANUAL` (añadida por un admin en este evento) |
| `cantidad_ajustada` | `NUMERIC(8,2)` | override manual del admin; `NULL` = usar la fórmula. `CHECK (cantidad_ajustada IS NULL OR cantidad_ajustada >= 0)` |
| `activa` | `BOOLEAN NOT NULL DEFAULT TRUE` | `FALSE` = el admin ha quitado esta regla de este evento |

- Índice `ix_regla_compra_evento_evento (evento_id)`.
- `UNIQUE (evento_id, categoria, nombre, tipo_formula)` — misma clave que la
  siembra de la plantilla, evita duplicar al materializar.

### Materialización

La primera vez que se pide la lista de un evento (lectura o administración), si
`regla_compra_evento` no tiene ninguna fila para ese evento, se copian **todas**
las filas de `regla_compra` de la peña con `origen = PLANTILLA`,
`cantidad_ajustada = NULL`, `activa = TRUE`. A partir de ahí el evento es
independiente de la plantilla.

## Tipos de fórmula

`tipo_formula` (enum Java `TipoFormulaCompra`):

| Valor | Cálculo del valor bruto (antes de redondear) |
|---|---|
| `POR_PENISTA` | `factor × apuntados` |
| `POR_PENISTA_DIA` | `factor × Σ díasQueVa(persona)` sobre los apuntados |
| `POR_DIA` | `factor × díasFiesta` |
| `POR_EVENTO` | `factor` |
| `POR_CADA_N_PENISTAS` | `factor × ceil(apuntados / por_cada)` |
| `CERVEZA_ALTERNATIVA` | `factor × Σ díasQueVa(persona)` sobre apuntados con ficha y `alternativa == CERVEZA` |
| `TINTO_ALTERNATIVA` | `factor × Σ díasQueVa(persona)` sobre apuntados con ficha y `alternativa == TINTO_VERANO` |
| `ALCOHOL_SELECCIONADO` | dinámica — ver abajo |
| `REFRESCO_SELECCIONADO` | dinámica — ver abajo |

**Redondeo (bloque 1):** la cantidad final de cada línea es
`ceil(bruto)`, con `cantidad_ajustada` teniendo prioridad cuando no es `NULL`
(en ese caso se muestra tal cual, sin redondear). En el bloque 2 el redondeo
pasará a ser `ceil(bruto − hayEnInventarioFiesta)`.

## Entradas del cálculo

Se calculan una vez por evento:

- **`díasFiesta`** = `fechaFin != null ? DAYS.between(fecha, fechaFin) + 1 : 1`.
  San Miguel (fecha, fecha+1) = 2.
- **`apuntados`** = número de `AsistenciaEvento` del evento en estado `APUNTADO`
  (no `EN_DUDA`, no `NO_VOY`). Incluye los añadidos a mano (invitados sin cuenta).
- **`díasQueVa(persona)`**:
  - Evento con ficha de bebida y de dos días: `asisteDia1 ? 1 : 0` + `asisteDia2 ? 1 : 0` de su `FichaBebida`.
  - Evento sin ficha, o de un solo día: `díasFiesta` para todo apuntado.
- **Condición de bebida** (solo eventos con ficha): se mira la `FichaBebida` de
  cada apuntado que la tenga.
  - `CERVEZA_ALTERNATIVA`: cuenta si `alternativa == CERVEZA`.
  - `TINTO_ALTERNATIVA`: cuenta si `alternativa == TINTO_VERANO`.
  - `CERVEZA_ESPECIAL` no cuenta para ninguna regla (esa cerveza la trae el peñista).
- **Evento sin ficha de bebida**: las cuatro reglas de bebida
  (`CERVEZA_ALTERNATIVA`, `TINTO_ALTERNATIVA`, `ALCOHOL_SELECCIONADO`,
  `REFRESCO_SELECCIONADO`) rinden `0`. Su línea se muestra con cantidad `0` y la
  marca `necesitaFicha = true` (para que la interfaz pinte una nota "necesita
  ficha de bebida"); no se ocultan.
- **Peñista `EN_DUDA`**: no suma. La lista es siempre en vivo; si alguien cambia
  su respuesta o su ficha, la lista cambia al recargar. No se congela.
- **Añadido a mano sin ficha** en un evento de San Miguel: cuenta para
  `apuntados` y para `díasFiesta`, pero no para las reglas de bebida, salvo que
  el admin le haya rellenado la ficha al darlo de alta.

## Reglas dinámicas (alcohol y refresco por marca)

Una sola fila de regla que al calcular se expande en varias líneas de la lista,
una por marca elegida.

### `ALCOHOL_SELECCIONADO` (factor `0.5`, `tamano` de referencia `"70 cl"`)

1. Agrupa los apuntados con `ficha != null` y `ficha.alcohol != null` por
   `alcohol.nombre`.
2. Por grupo: `bruto = 0.5 × Σ díasQueVa(persona del grupo)`.
3. `n = ceil(bruto)`.
4. Línea resultante:
   - `n == 1` → `"Legendario" · "1 L" · 1`
   - `n >= 2` → `"Legendario" · "70 cl" · n`
5. Ordenadas alfabéticamente por marca dentro de la categoría `ALCOHOL`.

### `REFRESCO_SELECCIONADO` (factor `1`, `tamano` de referencia `"botella"`)

1. Agrupa los apuntados con `ficha != null` por `refresco.nombre` (el refresco es
   obligatorio en la ficha; entran todos los que tienen ficha).
2. `bruto = 1 × Σ díasQueVa(persona del grupo)`; `n = ceil(bruto)`.
3. Línea: `"Coca-Cola Zero" · "botella" · n`. Sin regla de 1 L / 70 cl (eso es
   solo alcohol).
4. Ordenadas alfabéticamente por marca dentro de la categoría `REFRESCOS`.

### Ajuste del admin sobre reglas dinámicas

El override manual (`cantidad_ajustada`) y el "quitar" (`activa = false`) actúan
sobre la fila-regla entera, no sobre una marca suelta. Si un admin quiere fijar
una marca concreta, añade una regla `MANUAL` de tipo `POR_EVENTO`
("Legendario · 70 cl · 4") y desactiva la dinámica. Es el caso raro; no se añade
interfaz por marca en esta entrega.

## API

Paquete backend nuevo `com.baniterio.api.compra`.

### Lectura — cualquier peñista logueado

`GET /api/v1/eventos/{eventoId}/lista-compra`

Materializa las reglas del evento si hace falta. Respuesta:

```
ListaCompraResponse {
  puedoEditar: boolean          // = tiene área INVENTARIO
  llevaFicha: boolean           // el evento es de San Miguel
  apuntados: int
  diasFiesta: int
  categorias: [
    CategoriaListaCompraDto {
      categoria: string         // "LIMPIEZA"
      etiqueta: string          // "Limpieza y utensilios"
      lineas: [
        LineaCompraDto {
          nombre: string
          tamano: string
          cantidad: number      // entero salvo ajuste manual decimal
          cantidadCalculada: number   // bruto redondeado, sin ajuste (para el bloque 2)
          ajustada: boolean     // cantidad viene de cantidad_ajustada
          dinamica: boolean     // vino de una regla ALCOHOL/REFRESCO_SELECCIONADO
          necesitaFicha: boolean
        }
      ]
    }
  ]
}
```

Solo se incluyen líneas de reglas `activa = true`. Las categorías sin líneas no
se incluyen. Orden: por el `orden` de la regla dentro de cada categoría; las
líneas dinámicas, alfabéticas.

Errores: `EVENTO_NO_ENCONTRADO` (404) si el evento no existe, no es de la peña o
está oculto.

### Administración — área `INVENTARIO` (admins implícito)

- `GET /api/v1/admin/lista-compra/eventos` → lista de eventos no ocultos de la
  peña, por fecha descendente:
  `[{ id, nombre, fecha, fechaFin }]`.

- `GET /api/v1/admin/lista-compra/eventos/{eventoId}` → materializa si hace falta
  y devuelve **todas** las reglas del evento (activas e inactivas):

  ```
  ListaCompraAdminResponse {
    evento: { id, nombre, fecha, fechaFin }
    apuntados: int
    diasFiesta: int
    reglas: [
      ReglaCompraEventoDto {
        id: long
        categoria: string
        etiqueta: string
        nombre: string
        tamano: string
        tipoFormula: string
        factor: number
        porCada: int | null
        origen: "PLANTILLA" | "MANUAL"
        cantidadCalculada: number   // lo que da la fórmula ahora mismo
        cantidadAjustada: number | null
        cantidadFinal: number       // ajustada ?? ceil(calculada)
        activa: boolean
      }
    ]
  }
  ```

  Las reglas dinámicas se devuelven como **una** fila (`nombre = ""`,
  `tipoFormula = ALCOHOL_SELECCIONADO`); `cantidadCalculada` es la suma de todas
  sus líneas expandidas. El editor las muestra como "Alcohol (por marca elegida)"
  y solo permite activarlas/desactivarlas (no ajustar cantidad).

- `PUT /api/v1/admin/lista-compra/eventos/{eventoId}/reglas/{reglaId}`
  Body `{ cantidadAjustada: number | null, activa: boolean }`.
  - `cantidadAjustada` en una regla dinámica → `400 AJUSTE_NO_APLICA`.
  - `reglaId` que no es de ese evento → `404 REGLA_COMPRA_NO_ENCONTRADA`.
  - `cantidadAjustada < 0` → `400` (validación de bean).

- `POST /api/v1/admin/lista-compra/eventos/{eventoId}/reglas`
  Body `{ categoria, nombre, tamano, tipoFormula, factor, porCada? }`.
  Crea una regla `origen = MANUAL`, `activa = true`. Validación:
  - `factor >= 0`.
  - `porCada > 0` y obligatorio si `tipoFormula == POR_CADA_N_PENISTAS`;
    prohibido (debe ser `null`) en cualquier otro tipo → `400 POR_CADA_NO_APLICA`.
  - `tipoFormula` no puede ser `ALCOHOL_SELECCIONADO` ni `REFRESCO_SELECCIONADO`
    (esas solo existen en la plantilla) → `400 FORMULA_NO_CREABLE`.
  - `nombre` no vacío.
  - Choca con la clave `(evento_id, categoria, nombre, tipoFormula)` →
    `409 REGLA_COMPRA_DUPLICADA`.

- `DELETE /api/v1/admin/lista-compra/eventos/{eventoId}/reglas/{reglaId}`
  - `origen = MANUAL` → borra la fila, `204`.
  - `origen = PLANTILLA` → `409 REGLA_COMPRA_NO_BORRABLE` (hay que desactivarla
    con el `PUT`).
  - `reglaId` que no es de ese evento → `404 REGLA_COMPRA_NO_ENCONTRADA`.

### `ApiExceptionHandler`

Códigos nuevos: `REGLA_COMPRA_NO_ENCONTRADA` (404), `REGLA_COMPRA_NO_BORRABLE`
(409), `REGLA_COMPRA_DUPLICADA` (409), `AJUSTE_NO_APLICA` (400),
`POR_CADA_NO_APLICA` (400), `FORMULA_NO_CREABLE` (400). Se reutilizan
`EVENTO_NO_ENCONTRADO` y `SIN_PERMISO_INVENTARIO`.

## Web

- **Detalle del evento** (`evento-detalle.html` / `.ts`): el botón "Lista de la
  compra" pasa de `proximamente('Lista de la compra')` a
  `[routerLink]="['/panel/eventos', e.id, 'lista-compra']"`. Se quita el método
  `proximamente` si no lo usa nadie más (lo usa: revisar).
- **Ruta nueva** en `app.routes.ts`:
  `{ path: 'eventos/:id/lista-compra', component: ListaCompra, canActivate: [perfilCompletoGuard] }`
  **antes** de `eventos/:id`.
- **`ListaCompra`** (`front/src/app/panel/eventos/lista-compra/`): solo lectura.
  Cabecera con `apuntados` y `diasFiesta`; una tarjeta `carta-relieve` por
  categoría con una tabla (Artículo / Tamaño / Cantidad). Nota "necesita ficha de
  bebida" en las líneas con `necesitaFicha`. Estado cargando / error (Reintentar)
  / listo, como `inventario-fiesta`.
- **`indice.ts` / `indice.html`**: tarjeta "Cantidades para eventos" visible si
  `auth.tieneArea('INVENTARIO')`, enlazando a
  `/panel/administracion/lista-compra`.
- **Rutas admin**:
  `{ path: 'administracion/lista-compra', component: ListaCompraAdmin, canActivate: [areaGuard('INVENTARIO')] }`
  y `.../lista-compra/:id` → `ListaCompraAdminEvento`.
- **`ListaCompraAdmin`**: lista de tarjetas de evento (nombre + fecha) → enlaza al
  editor.
- **`ListaCompraAdminEvento`**: cabecera del evento; por categoría, una fila por
  regla con: nombre, tamaño, fórmula legible ("3 por peñista", "2 por cada 30
  peñistas", "por marca elegida"), cantidad calculada, campo de cantidad
  ajustada (número, vacío = usar fórmula), interruptor activa/inactiva. Botón
  "Guardar" por fila (`PUT`). Botón "Añadir artículo" abre un formulario
  (categoría, nombre, tamaño, tipo de fórmula, factor, por cada) → `POST`. Botón
  de quitar: en reglas `MANUAL` hace `DELETE`; en `PLANTILLA` es el propio
  interruptor a inactiva.
- **`ListaCompraService`**: `lista(eventoId)`, `adminEventos()`,
  `adminEvento(eventoId)`, `ajustarRegla(eventoId, reglaId, body)`,
  `crearRegla(eventoId, body)`, `borrarRegla(eventoId, reglaId)`.

## Móvil

- **DTO** (`data/dto/ListaCompraDtos.kt`): `ListaCompraResponse`,
  `CategoriaListaCompraDto`, `LineaCompraDto`, `ListaCompraAdminResponse`,
  `ReglaCompraEventoDto`, `EventoListaCompraDto`, `AjustarReglaBody`,
  `CrearReglaBody`. Todos `@Serializable`.
- **`ResultadoListaCompra`** sealed + `CodigoErrorListaCompra` enum con
  `deCodigoBackend()` y `mensaje`, patrón de `ResultadoInventario`. Códigos:
  `EVENTO_NO_ENCONTRADO`, `SIN_PERMISO`, `REGLA_NO_ENCONTRADA`,
  `REGLA_NO_BORRABLE`, `REGLA_DUPLICADA`, `AJUSTE_NO_APLICA`, `POR_CADA_NO_APLICA`,
  `FORMULA_NO_CREABLE`, `DESCONOCIDO`.
- **`ListaCompraRepository`** / `...Impl`: `lista`, `adminEventos`,
  `adminEvento`, `ajustarRegla`, `crearRegla`, `borrarRegla` — `suspend` que
  devuelven `ResultadoListaCompra<...>`.
- **`Dependencias`**: `val listaCompraRepo` + alta en `crearDependencias`.
- **`Screen.kt`**: `ListaCompra`, `ListaCompraAdmin`, `ListaCompraAdminEvento`.
- **`App.kt`**: constantes de clave, `aClave()` / `claveAScreen()`, guardia de
  arranque en frío, estado del `eventoId` seleccionado, ramas del `when`.
- **`ListaCompraScreen(listaCompraRepo, eventoId, onVolver)`**: lectura, tarjetas
  `relieveDeCarta` por categoría. Se llega desde `EventoDetalleScreen` con un
  botón nuevo `OutlinedButton("Lista de la compra")` junto a "Inventario de la
  fiesta" (nuevo parámetro `onListaCompra: () -> Unit`).
- **`ListaCompraAdminScreen`**: lista de eventos, desde `AdminIndexScreen` (nueva
  tarjeta visible con área `INVENTARIO`).
- **`ListaCompraAdminEventoScreen`**: editor — fila por regla con cantidad
  ajustada, interruptor activa, y diálogo "Añadir artículo".

## Pruebas

### Backend

- **`CalculadoraListaCompraTest`** (unitario, sin Spring): una prueba por
  `tipo_formula` con números fijos; las dinámicas de alcohol y refresco
  (agrupación por marca, regla de 1 L cuando `n == 1`); el redondeo hacia arriba;
  evento sin ficha → reglas de bebida a 0 con `necesitaFicha`.
- **`ListaCompraIT`** (failsafe, comparte BBDD): materializar en la primera
  llamada; `GET` de lectura agrupa por categoría; `PUT` con `cantidadAjustada`
  cambia `cantidadFinal`; `PUT` con `activa = false` quita la línea de la
  lectura; `POST` regla `MANUAL` aparece en lectura; `DELETE` de `PLANTILLA` →
  409; `DELETE` de `MANUAL` → 204; `GET` admin sin área → 403; `PUT`
  `cantidadAjustada` en dinámica → 400.
  Sembrar un evento propio para el test o usar uno de `V15` con asistencias
  sembradas; usar reglas concretas para no chocar con pruebas hermanas.

### Web

- **`lista-compra.spec.ts`**: pinta categorías y líneas; muestra la nota cuando
  `necesitaFicha`.
- **`lista-compra-admin-evento.spec.ts`**: ajustar cantidad hace `PUT`; el
  interruptor hace `PUT` con `activa`; "Añadir artículo" hace `POST`; quitar una
  `MANUAL` hace `DELETE`.
- **`lista-compra.service.spec.ts`**: un test por método (verbo + ruta + body).

### Móvil

- **`ListaCompraRepositoryImplTest`**: `lista` GET; `adminEvento` GET;
  `ajustarRegla` PUT con body; `crearRegla` POST; `borrarRegla` DELETE; error
  tipado (`REGLA_NO_BORRABLE`). `MockEngine` + `Vista(metodo, path, cuerpo, auth)`
  como en `InventarioRepositoryImplTest`.
- Screens compilando (`:shared:testAndroidHostTest`, compilación iOS,
  `:androidApp:assembleDebug`).

## Siembra (V38)

`regla_compra`, una fila por artículo, `INSERT ... WHERE NOT EXISTS` por
`(pena_id, categoria, nombre, tipo_formula)` y `WHERE p.slug = 'baniterio'`.

| categoría | nombre | tamaño | tipo_formula | factor | por_cada | orden |
|---|---|---|---|---|---|---|
| ALCOHOL | *(vacío)* | 70 cl | `ALCOHOL_SELECCIONADO` | 0.5 | | 1 |
| CERVEZA | Cerveza | lata | `CERVEZA_ALTERNATIVA` | 5 | | 1 |
| REFRESCOS | *(vacío)* | botella | `REFRESCO_SELECCIONADO` | 1 | | 1 |
| REFRESCOS | Tinto de verano | botella | `TINTO_ALTERNATIVA` | 1 | | 2 |
| LIMPIEZA | Bayetas | unidad | `POR_CADA_N_PENISTAS` | 2 | 30 | 1 |
| LIMPIEZA | Fregasuelos | litro | `POR_CADA_N_PENISTAS` | 0.2 | 50 | 2 |
| LIMPIEZA | Papel de cocina | rollo | `POR_DIA` | 1 | | 3 |
| LIMPIEZA | Platos | unidad | `POR_PENISTA` | 3 | | 4 |
| LIMPIEZA | Vasos de chupito | unidad | `POR_PENISTA` | 3 | | 5 |
| LIMPIEZA | Papel higiénico | rollo | `POR_CADA_N_PENISTAS` | 6 | 30 | 6 |
| LIMPIEZA | Vasos de mini | unidad | `POR_PENISTA_DIA` | 3 | | 7 |
| LIMPIEZA | Vasos de sidra | unidad | `POR_PENISTA_DIA` | 3 | | 8 |
| LIMPIEZA | Mantel | unidad | `POR_DIA` | 1 | | 9 |
| LIMPIEZA | Vasos de invitar | unidad | `POR_EVENTO` | 50 | | 10 |
| LIMPIEZA | Film transparente | rollo | `POR_EVENTO` | 1 | | 11 |
| REFRESCOS | Hielos | bolsa | `POR_PENISTA` | 1 | | 3 |
| COMIDA | Paletilla ibérica | unidad | `POR_EVENTO` | 1 | | 1 |
| COMIDA | Salchichón ibérico | unidad | `POR_EVENTO` | 1 | | 2 |
| COMIDA | Chorizo ibérico | unidad | `POR_EVENTO` | 1 | | 3 |
| COMIDA | Nocilla grande | bote | `POR_CADA_N_PENISTAS` | 1 | 12 | 4 |
| COMIDA | Nocilla pequeña sin gluten | bote | `POR_EVENTO` | 1 | | 5 |
| COMIDA | Pan de molde sin gluten | paquete | `POR_EVENTO` | 1 | | 6 |
| COMIDA | Picos | paquete | `POR_CADA_N_PENISTAS` | 2 | 10 | 7 |
| COMIDA | Pan de molde | paquete | `POR_CADA_N_PENISTAS` | 2 | 12 | 8 |

Nota: "Hielos" va en `REFRESCOS` con tamaño libre "bolsa" (el tamaño de las
reglas no se valida contra `CategoriaInventario.tamanos()`).

## Fórmula legible (para el editor y la lectura)

| tipo_formula | texto |
|---|---|
| `POR_PENISTA` | "{factor} por peñista" |
| `POR_PENISTA_DIA` | "{factor} por peñista y día" |
| `POR_DIA` | "{factor} por día de fiesta" |
| `POR_EVENTO` | "{factor} por evento" |
| `POR_CADA_N_PENISTAS` | "{factor} por cada {por_cada} peñistas" |
| `CERVEZA_ALTERNATIVA` | "{factor} por peñista y día (quien bebe cerveza)" |
| `TINTO_ALTERNATIVA` | "{factor} por peñista y día (quien bebe tinto de verano)" |
| `ALCOHOL_SELECCIONADO` | "0,5 botellas por peñista y día, por marca elegida" |
| `REFRESCO_SELECCIONADO` | "1 botella por peñista y día, por marca elegida" |

## Constantes globales

- Java 17.
- Peña piloto por `slug = "baniterio"`.
- Errores de dominio → `{ "codigo": "<CODE>" }` vía `ApiExceptionHandler`.
- Móvil: errores tipados con enum + `deCodigoBackend()`, patrón de
  `BebidaRepository` / `InventarioRepository`.
- IT comparten BBDD sin rollback: cada test usa filas propias o las crea y borra.
