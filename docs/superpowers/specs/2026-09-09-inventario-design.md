# Inventario — diseño

Fecha: 2026-09-09
Estado: aprobado (pendiente de revisión del usuario antes del plan)

## Objetivo

Una sección "Inventario" en el panel web que muestra lo que la peña tiene
almacenado, agrupado en cinco categorías. Cualquier peñista logueado puede
consultarlo. Quien tenga el nuevo permiso de área `inventario` puede modificar
los artículos pulsando un botón "Editar".

Esta primera entrega es **solo web** y **solo consulta + edición de artículos
existentes**. Quedan explícitamente fuera: la app móvil, el alta y baja de
artículos, el historial de movimientos y las alertas de stock bajo.

## Alcance

### Dentro

- Modelo y migración de base de datos con la siembra de los datos actuales.
- Endpoints REST: listar el inventario y actualizar un artículo.
- Nuevo permiso de área `INVENTARIO`.
- Pantalla web: vista de solo lectura + modo edición.
- Entrada "Inventario" en el menú del panel.
- Tests de integración del backend y spec del componente Angular.

### Fuera (trabajo posterior)

- App móvil (Kotlin/Compose).
- Crear y borrar artículos desde la interfaz.
- Historial de movimientos (quién cambió qué y cuándo).
- Avisos de "queda poco".

## Modelo de datos

Paquete backend nuevo: `com.baniterio.api.inventario`.

### Tabla `articulo_inventario`

| Columna     | Tipo           | Notas                                                        |
|-------------|----------------|-------------------------------------------------------------|
| `id`        | BIGINT PK      | autoincremental                                             |
| `categoria` | VARCHAR(20)    | `ALCOHOL`, `CERVEZA`, `LIMPIEZA`, `REFRESCOS`, `COMIDA`     |
| `nombre`    | VARCHAR(120)   | nombre del producto                                         |
| `tamano`    | VARCHAR(20)    | uno de la lista fija de su categoría (validado en servicio) |
| `cantidad`  | NUMERIC(8,2)   | admite decimales (p. ej. 1.5 botellas); ≥ 0                 |
| `orden`     | INT            | orden de aparición dentro de la categoría                   |

- Una fila por combinación nombre + tamaño. Un mismo producto en dos tamaños
  (Four Roses 70 cl y Four Roses 1 L) son dos filas.
- Sin restricción de unicidad en base de datos por ahora (el editor no crea
  filas todavía); se puede añadir cuando exista el alta.

Migración: `V35__inventario.sql` — crea la tabla y siembra los datos de abajo.

### Categoría

Enum Java `CategoriaInventario { ALCOHOL, CERVEZA, LIMPIEZA, REFRESCOS, COMIDA }`.

### Listas de tamaño por categoría

Fijas en código (constante en el servicio o en el propio enum de categoría):

| Categoría  | Tamaños permitidos                     |
|------------|----------------------------------------|
| ALCOHOL    | `70 cl`, `1 L`, `1,5 L`                |
| CERVEZA    | `lata`, `botellín`, `tercio`           |
| REFRESCOS  | `botella`, `lata`, `garrafa`, `brick`  |
| LIMPIEZA   | `unidad`, `rollo`, `paquete`, `litro`  |
| COMIDA     | `unidad`, `paquete`, `kg`, `lata`      |

El servicio rechaza (400) un `tamano` que no esté en la lista de la categoría
del artículo.

## Permiso de área

Añadir `INVENTARIO` al enum `AreaProtegida`. Con eso:

- Aparece solo en la pantalla de permisos del panel de administración.
- Se incluye en `GET /api/v1/auth/yo` (el front sabe si mostrar "Editar").
- `ServicioPermisos.puede(usuarioId, AreaProtegida.INVENTARIO)` ya funciona sin
  cambios (admins y superadmins lo tienen implícito).

Ninguna migración para el permiso: `permiso_area` guarda el área como texto.

## API REST

Prefijo coherente con el resto (`/api/v1/...`; el controlador confirma el
prefijo real al implementar).

### `GET /api/v1/inventario`

Cualquier usuario autenticado.

```json
{
  "puedoEditar": true,
  "categorias": [
    {
      "categoria": "ALCOHOL",
      "etiqueta": "Alcohol",
      "tamanos": ["70 cl", "1 L", "1,5 L"],
      "articulos": [
        { "id": 1, "nombre": "Tanqueray", "tamano": "70 cl", "cantidad": 1.5 }
      ]
    }
  ]
}
```

- Siempre devuelve las cinco categorías, incluso vacías (COMIDA saldrá vacía).
- Artículos ordenados por `orden`, luego `nombre`.
- `puedoEditar` = `ServicioPermisos.puede(usuario, INVENTARIO)`.

### `PUT /api/v1/inventario/{id}`

Requiere permiso `INVENTARIO` (403 si no).

```json
{ "nombre": "Tanqueray", "tamano": "1 L", "cantidad": 2 }
```

- 404 si el artículo no existe.
- 400 si `tamano` no pertenece a la categoría del artículo, si `nombre` está
  vacío o si `cantidad` es negativa.
- La categoría **no** se puede cambiar en esta entrega.
- Respuesta: el artículo actualizado.

## Web

Carpeta `front/src/app/panel/inventario/`, misma estructura que otras secciones
(`inventario.ts`, `inventario.html`, `inventario.service.ts`,
`inventario.types.ts`, specs).

### Navegación

Nueva entrada "Inventario" en el menú del panel. Visible para todo peñista
logueado (no se esconde tras el permiso: el permiso solo controla editar).

### Vista de solo lectura (por defecto)

- Cabecera con el título.
- Cinco bloques, uno por categoría, en el orden del enum.
- Cada bloque: nombre de la categoría y una tabla de columnas
  **nombre · tamaño · cantidad**.
- Categoría sin artículos: línea "Nada apuntado todavía.".
- Botón "Editar" arriba a la derecha, solo si `puedoEditar`.

### Modo edición

- Al pulsar "Editar", las filas pasan a campos:
  - nombre: campo de texto.
  - tamaño: desplegable con los tamaños de esa categoría.
  - cantidad: campo numérico (paso 0,1; mínimo 0).
- Botones "Guardar" y "Cancelar".
- "Guardar" hace un `PUT` por cada fila modificada (comparando con el valor
  original); si alguno falla, se muestra el error y se recarga.
- "Cancelar" descarta y vuelve a solo lectura.
- Sin botones de añadir/borrar fila todavía.

Estética coherente con Cuentas y Eventos (cartas con relieve, colores de marca).

## Manejo de errores

| Situación                                   | Respuesta                                  |
|---------------------------------------------|--------------------------------------------|
| GET sin sesión                              | 401 (filtro JWT existente)                 |
| PUT sin permiso `INVENTARIO`                | 403                                        |
| PUT a un `id` inexistente                   | 404                                        |
| `tamano` fuera de la lista de la categoría  | 400 con mensaje claro                      |
| `nombre` vacío / `cantidad` negativa        | 400                                        |
| Fallo de red en "Guardar" (web)             | aviso en pantalla + recarga del inventario |

## Tests

### Backend (IT, base de datos real como el resto)

- `GET /api/v1/inventario` devuelve las 5 categorías y la siembra.
- `puedoEditar` es `true` para admin y para usuario con permiso; `false` para
  peñista normal.
- `PUT` como usuario con permiso cambia la cantidad y persiste.
- `PUT` como peñista sin permiso → 403.
- `PUT` con `tamano` inválido para la categoría → 400.
- `PUT` a `id` inexistente → 404.

### Web

- Spec del componente: pinta las categorías, muestra "Editar" solo con permiso,
  el modo edición manda un `PUT` por fila cambiada.
- Spec del servicio: mapea la respuesta de `GET` y llama al `PUT` correcto.

## Datos de siembra (V35)

Cantidades tal cual las pasó el usuario el 2026-09-09. `COMIDA` se crea sin
artículos.

### ALCOHOL

| Nombre        | Tamaño | Cantidad |
|---------------|--------|----------|
| Tanqueray     | 70 cl  | 1.5      |
| Seagram's     | 70 cl  | 0.8      |
| Beefeater     | 1 L    | 0.8      |
| Negrita       | 1 L    | 1        |
| Legendario    | 70 cl  | 2.5      |
| Puerto de Indias | 1 L | 0.8      |
| Barceló       | 70 cl  | 0.5      |
| Brugal        | 1 L    | 1        |
| Four Roses    | 70 cl  | 0.8      |
| Four Roses    | 1 L    | 1        |
| Ballantine's  | 70 cl  | 1        |

### CERVEZA

| Nombre              | Tamaño   | Cantidad |
|---------------------|----------|----------|
| Sin gluten          | lata     | 8        |
| Mahou Clásica       | lata     | 192      |
| Mahou 0,0 Tostada   | lata     | 14       |
| Mixta               | lata     | 3        |
| Coronita            | lata     | 3        |

### LIMPIEZA

| Nombre             | Tamaño | Cantidad |
|--------------------|--------|----------|
| Bayetas            | unidad | 5        |
| Fregasuelos        | litro  | 0.5      |
| Papel de cocina    | rollo  | 1.5      |
| Platos             | unidad | 35       |
| Vasos de chupito   | unidad | 25       |
| Papel higiénico    | rollo  | 12       |
| Vasos de mini      | unidad | 100      |
| Vasos de sidra     | unidad | 35       |
| Mantel 1,2 x 5     | unidad | 1        |

### REFRESCOS

| Nombre                 | Tamaño  | Cantidad |
|------------------------|---------|----------|
| Tónica                 | botella | 3        |
| Coca-Cola Zero         | botella | 15       |
| Coca-Cola Zero Zero    | botella | 5        |
| Red Bull               | lata    | 2        |
| Gin-tonic Tanqueray    | lata    | 2        |
| Aquarius naranja       | botella | 4        |
| Aquarius limón         | botella | 1        |
| Agua                   | garrafa | 3        |
| Coca-Cola Light        | botella | 5        |
| Coca-Cola normal       | botella | 4        |
| Nestea                 | botella | 4        |
| Fanta limón            | botella | 1        |
| Tinto de verano        | botella | 4        |

### COMIDA

Sin artículos.
