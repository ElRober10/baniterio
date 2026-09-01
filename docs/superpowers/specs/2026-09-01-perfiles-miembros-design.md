# Perfiles de miembro y tarjetas — diseño

**Fecha:** 2026-09-01
**Estado:** aprobado (pendiente de escribir el plan)

## Objetivo

Cada miembro, al entrar por primera vez, completa su perfil: foto o avatar,
datos, un texto libre, si tiene pareja en la peña y sus hijos. Con los perfiles
hechos, la sección **Miembros** (hoy "próximamente") pasa a mostrar una lista de
**tarjetas** ordenada de forma útil para cada persona. Los teléfonos de la
pareja y de los hijos que se aporten se dan de alta automáticamente en la lista
de autorizados, para que esas personas puedan descargarse la app.

## Alcance

- **Incluye:** editor de perfil (web + móvil), obligatorio en el primer login;
  foto subida (procesada en servidor) o avatar de un catálogo fijo; vínculo de
  pareja con aceptación; hijos; alta automática en `telefono_autorizado`;
  sección Miembros con tarjetas ordenadas; notificaciones push de los eventos
  de pareja; selector de contactos en Android para el teléfono.
- **Fuera (foreshadowing, no se implementa):**
  - Toda la sección **Eventos** y la lógica de apuntarse ("un adulto apunta a
    toda la familia", "el menor de 18 no se apunta solo", "A gestiona los
    eventos de su pareja SIN_CUENTA"). El check **mayor de 18** se guarda pero
    por ahora es **solo informativo**.
  - `actual` de iOS del selector de contactos
    (`CNContactPickerViewController`) — necesita Mac. Ver memoria
    `baniterio_push_ios_pendiente`. En iOS el botón "Elegir de contactos" no
    aparece; el campo de texto manual funciona igual.
  - Panel de administración del catálogo de avatares (se edita
    `avatares.json` a mano en el repo).
  - Soporte de HEIC (iPhone) y WebP como formatos de **subida** de foto (se
    aceptan JPEG y PNG; se pueden añadir después).

## Global Constraints

- **Backend:** Java 17, Spring Boot 4.1.x, Maven. Migraciones Flyway
  `V*__descripcion.sql` — las siguientes libres son **V10, V11, V12**.
  Hibernate `ddl-auto: validate` (las entidades `@Entity` deben cuadrar
  exactamente con lo que crea Flyway). `open-in-view: false` (cargar en el
  servicio, dentro de `@Transactional`, lo que el controlador vaya a
  serializar). Config propia tipada en `AppProperties`
  (`@ConfigurationProperties("app")`), valores por defecto de desarrollo en
  `application.yml` como `${VAR:default}`.
- **Entidades:** Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor
  @Builder`), en el paquete `com.baniterio.api.identidad`. PK
  `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Timestamps con
  `@CreationTimestamp` / `@UpdateTimestamp`.
- **Peña única:** el proyecto asume una sola peña por ahora (slug
  `"baniterio"`). El código nuevo resuelve la peña igual que
  `ResolutorAudiencia` (no se hardcodea el id).
- **Seguridad:** JWT Bearer, stateless. Rutas nuevas autenticadas salvo
  `/api/v1/media/**`, que se añade a `permitAll` en `SecurityConfig`. Nunca se
  exponen `passwordHash`, y el teléfono/email siguen sin aparecer en las
  respuestas públicas de miembros.
- **Frontend:** Angular (componentes standalone, `ChangeDetectionStrategy.OnPush`,
  signals), Tailwind, colores de marca `#372FA5` / `#F8D349`. Tests con Jasmine
  (`*.spec.ts`).
- **Móvil:** Kotlin Multiplatform + Compose Multiplatform. Lógica en
  `commonMain`; `expect/actual` para lo específico de plataforma. Tests reales
  en `:shared:testAndroidHostTest`. La `MainActivity` es `FragmentActivity` y
  su validador de `requestCode` **rechaza los códigos de 32 bits** de
  `registerForActivityResult`: usar el `startActivityForResult` clásico con un
  `requestCode` de 16 bits.
- **Idioma:** todo el texto de cara al usuario y los identificadores de dominio
  en español.

## Modelo de datos

### `perfil` (V10) — 1:1 con `usuario`

| Columna | Tipo | Notas |
|---|---|---|
| `id` | PK identity | |
| `usuario_id` | FK `usuario(id)` NOT NULL UNIQUE, `ON DELETE CASCADE` | |
| `sobre_mi` | `varchar(500)` NULL | texto libre; se muestra en la tarjeta solo si no está vacío |
| `imagen_tipo` | `varchar(10)` NOT NULL | enum `ImagenPerfil` = `FOTO` \| `AVATAR` |
| `imagen_ref` | `varchar(80)` NOT NULL | si `FOTO`: `{uuid}.jpg`; si `AVATAR`: id del catálogo (`03_chica`) |
| `completado` | `boolean` NOT NULL DEFAULT false | |
| `created_at` / `updated_at` | `timestamptz` NOT NULL | |

`nombre`, `apellidos`, `mote` **siguen en `usuario`**; el editor de perfil los
actualiza ahí.

Hasta que un usuario complete el perfil no tiene fila en `perfil` (o la tiene
con `completado=false`). Todos los usuarios actuales (fundador incluido)
empiezan sin perfil completado → pasan por el editor en su siguiente login.

**Valor inicial de la imagen:** el editor obliga a elegir; no hay defecto en
BBDD. Mientras `completado=false` la tarjeta de ese usuario no se lista.

### `vinculo_pareja` (V11)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | PK identity | |
| `solicitante_id` | FK `usuario(id)` NOT NULL | quien declara la pareja |
| `pareja_usuario_id` | FK `usuario(id)` NULL | se rellena cuando la pareja tiene cuenta |
| `pareja_nombre` | `varchar(140)` NOT NULL | lo que teclea el solicitante |
| `pareja_telefono` | `varchar(20)` NOT NULL | normalizado a 9 dígitos |
| `estado` | `varchar(20)` NOT NULL | enum `EstadoVinculo` = `SIN_CUENTA` \| `PENDIENTE` \| `ACEPTADO` \| `RECHAZADO` |
| `created_at` / `updated_at` | `timestamptz` NOT NULL | |

- Índice único parcial: **un usuario solo puede tener un vínculo "vivo"**
  (`estado IN ('SIN_CUENTA','PENDIENTE','ACEPTADO')`), ni como `solicitante_id`
  ni como `pareja_usuario_id`. Se implementa con dos índices únicos parciales
  (`WHERE estado <> 'RECHAZADO'`), uno por columna.
- `RECHAZADO` es terminal e histórico; el solicitante puede crear uno nuevo
  después.

### `hijo` (V12)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | PK identity | |
| `creador_id` | FK `usuario(id)` NOT NULL | quién lo añadió (a él vuelve si se rompe el vínculo) |
| `vinculo_pareja_id` | FK `vinculo_pareja(id)` NULL | si hay vínculo `ACEPTADO`, cuelga de aquí (lo ven los dos adultos) |
| `nombre` | `varchar(80)` NOT NULL | solo el nombre |
| `mayor_de_edad` | `boolean` NOT NULL DEFAULT false | check +18, informativo por ahora |
| `telefono` | `varchar(20)` NULL | opcional, normalizado |
| `visible` | `boolean` NOT NULL DEFAULT false | "mostrar en la peña" |
| `usuario_id` | FK `usuario(id)` NULL UNIQUE | se enlaza si el hijo se registra con `telefono` |
| `created_at` / `updated_at` | `timestamptz` NOT NULL | |

- Al crearse un vínculo `ACEPTADO` entre A y B, los `hijo` de A y de B pasan a
  colgar del mismo `vinculo_pareja_id` (se mantienen los dos conjuntos, sin
  fusionar duplicados: el usuario los depura a mano si hace falta).
- Al romperse el vínculo, cada `hijo` vuelve a `vinculo_pareja_id = NULL`
  (queda con su `creador_id`).

### `telefono_autorizado` — sin cambios de esquema

Al guardar el perfil, por cada `pareja_telefono` de un vínculo `SIN_CUENTA` y
por cada `hijo.telefono` no nulo: si no existe fila con ese teléfono en la
peña, se inserta una (`autorizado_por` = el usuario que edita, `usado=false`).
Al **quitar** una pareja `SIN_CUENTA` o un hijo con teléfono: si la fila de
`telefono_autorizado` correspondiente sigue con `usado=false` y no la referencia
ningún otro vínculo/hijo, se borra.

## Flujo: completar perfil (primer login)

1. Tras `POST /auth/login`, el cliente pide `GET /perfil` (siempre 200; si aún
   no hay fila, devuelve la forma vacía con `completado=false` y los datos de
   `usuario`). Si `completado=false`, navega al **editor** y bloquea el resto
   de secciones.
   - Web: un guard `perfilCompletoGuard` en las rutas hijas de `/panel`
     (excepto la propia ruta del editor) redirige a `/panel/perfil`.
   - Móvil: `App.kt` enruta a la pantalla de editor si el perfil no está
     completo, en lugar de a `Panel`.
2. El editor carga: nombre, apellidos, mote (de `usuario`), y campos vacíos de
   perfil. Muestra el selector de imagen, "sobre mí", el bloque de pareja y la
   lista de hijos.
3. **Guardar** → `PUT /perfil` con todo el estado. El backend valida:
   - imagen elegida (tipo + ref válida)
   - pareja: respondida (sí/no); si sí, `pareja_nombre` no vacío +
     `pareja_telefono` válido (`^[67]\d{8}$`)
   - hijos: cada uno con `nombre` no vacío; `telefono` si viene, válido
4. En una transacción: actualiza `usuario` (nombre/apellidos/mote), hace
   *upsert* de `perfil` con `completado=true`, reconcilia `vinculo_pareja` e
   `hijo` (altas/bajas/ediciones respecto a lo que había), y da de alta/baja
   los teléfonos en `telefono_autorizado`.
5. Si el vínculo apunta a un teléfono que **sí** es de un miembro → `estado
   = PENDIENTE` y se publica un evento push a esa persona. Si no → `SIN_CUENTA`.

El editor es reutilizable después desde el botón **"Editar"** de la propia
tarjeta (mismo `PUT /perfil`). La subida de foto es una llamada aparte
(`POST /perfil/foto`) que devuelve la `imagen_ref` a usar en el `PUT`.

## Flujo: vínculo de pareja

Estados y transiciones de `vinculo_pareja.estado`:

```
(A declara pareja)
   telefono no es de un miembro  ─────────────►  SIN_CUENTA
   telefono es de un miembro B   ─────────────►  PENDIENTE   ──► push a B

SIN_CUENTA  ──(B se registra con ese telefono, primer login)──►  PENDIENTE ──► push a B
PENDIENTE   ──(B acepta:  POST /perfil/pareja/aceptar)──►  ACEPTADO   ──► push a A
PENDIENTE   ──(B rechaza: POST /perfil/pareja/rechazar)─►  RECHAZADO  ──► push a A; B fuera de la tarjeta/gestión de A
cualquiera vivo ──(DELETE /perfil/pareja, cualquiera de los dos)──►  se borra la fila (o RECHAZADO); push al otro
```

- **`ACEPTADO`** es simétrico: sale en las dos tarjetas, los dos gestionan los
  mismos hijos, y las tarjetas se ordenan como pareja.
- Al **aceptar**, se comprueba de nuevo que B no tenga otro vínculo vivo (si lo
  adquirió entre medias → 409).
- Al declarar, si el teléfono corresponde a un miembro que ya tiene vínculo
  `ACEPTADO` → 409 («ese teléfono ya tiene pareja en la peña»).
- La detección "SIN_CUENTA → PENDIENTE" al registrarse se hace en el primer
  `GET /perfil` del nuevo usuario (busca `vinculo_pareja` con
  `pareja_telefono = usuario.telefono` y `estado='SIN_CUENTA'`).

**Avisos en la app** (además del push): la sección Miembros muestra arriba un
banner "X dice que sois pareja — Confirmar / Rechazar" mientras haya un vínculo
`PENDIENTE` dirigido a ti.

## Flujo: hijos

- Se editan en la sección "Hijos" del editor (0..N). Campos: nombre, check
  mayor de 18, teléfono (opcional), check "mostrar en la peña".
- Junto al teléfono, texto fijo: «Lo pedimos para que cuando haya un evento, un
  solo miembro de la familia pueda apuntar a todos.» (mismo texto en el bloque
  de pareja).
- Al guardar con teléfono → alta en `telefono_autorizado`.
- **Si el hijo se registra** con ese teléfono: el registro/primer login enlaza
  `hijo.usuario_id`. A partir de ahí:
  - tiene **tarjeta propia** de miembro (posición 3 en el orden)
  - **no** se pinta ya como nombre suelto en la tarjeta de los padres
  - la gestiona él con su propio editor de perfil
  - si `mayor_de_edad=false`, al guardar su perfil se publica un push a los
    **padres** (creador del `hijo` y su pareja `ACEPTADA` si la hay): "Tu hijo
    X ha actualizado su tarjeta" (aviso, sin bloqueo ni aprobación)

## Fotos y avatares

### Avatares (catálogo fijo)

- Ficheros en `back/src/main/resources/avatares/` (`NN_chico.png` /
  `NN_chica.png`, ~500×500, PNG con alfa). Hoy: 11 chicas + 14 chicos.
- `avatares.json` en esa carpeta: lista `[{ "id": "03_chica", "genero":
  "CHICA" }, …]`. **Al añadir/quitar avatares hay que regenerarlo.**
- `GET /api/v1/perfil/avatares` → devuelve el contenido del manifiesto
  (autenticado).
- `GET /api/v1/media/avatares/{id}.png` → sirve el fichero del classpath
  (`Cache-Control: public, max-age=604800`). Público.
- Selector: rejilla con todos, filtro **Todos** (defecto) / Chicos / Chicas.

### Foto subida

- `POST /api/v1/perfil/foto`, `multipart/form-data`, campo `archivo`.
  - Acepta `image/jpeg`, `image/png`. Rechaza el resto con 415.
  - Límite de subida 10 MB (`spring.servlet.multipart.max-file-size`).
  - Procesado con `javax.imageio.ImageIO` (incluido en el JDK):
    1. leer a `BufferedImage`
    2. recorte cuadrado centrado (lado = `min(w,h)`)
    3. escalado a 512×512 (`Image.SCALE_SMOOTH`)
    4. re-codificado a JPEG calidad ~0.85 (`ImageWriteParam`)
    5. sin copiar metadatos → EXIF eliminado
  - Escribe `${MEDIA_DIR}/fotos/{uuid}.jpg`. Devuelve
    `{ "imagenRef": "{uuid}.jpg" }`.
  - El cliente hace luego `PUT /perfil` con `imagenTipo=FOTO`,
    `imagenRef={uuid}.jpg`.
  - Al cambiar de foto/avatar, el servicio borra el fichero anterior si era
    una `FOTO`.
- `GET /api/v1/media/fotos/{uuid}.jpg` → sirve desde `${MEDIA_DIR}`. Público
  (nombres UUID no adivinables). Valida que el nombre casa
  `^[0-9a-f-]{36}\.jpg$` para evitar *path traversal*.

### `MEDIA_DIR`

- `AppProperties`: `record Media(String dir)` bajo `app.media.dir`.
- `application.yml`: `app.media.dir: ${MEDIA_DIR:./media-baniterio}`.
- Al arrancar, un `@PostConstruct` (o el propio servicio) crea
  `${MEDIA_DIR}/fotos/` si no existe.
- `.gitignore` del repo: `media-baniterio/`.
- **Checklist de despliegue:** `MEDIA_DIR` a un volumen persistente montado en
  el servidor.

### Render en cliente

- **Base de URL de media**: se deriva de la base de API ya existente
  (`environment` en web, `API_BASE_URL` en móvil) quitando el sufijo
  `/api/v1` → `.../api/v1/media/...`. (Realmente las rutas ya están bajo
  `/api/v1/media`, así que se concatena directamente.)
- Web: `<img>` con recorte circular (`rounded-full object-cover`).
- Móvil: **Coil 3** (`io.coil-kt.coil3:coil-compose` +
  `coil3:coil-network-ktor`), dependencia nueva en `libs.versions.toml` y
  `shared/build.gradle.kts` (`commonMain`). `AsyncImage` con `CircleShape`.

## Sección Miembros

- **Web:** nueva ruta `/panel/miembros` (hija de `panel`, `authGuard` +
  `perfilCompletoGuard`), componente `Miembros` + `TarjetaMiembro` +
  `SelectorAvatar` + `EditorPerfil`. "Miembros" se saca de `SECCIONES`
  ("próximamente") y se añade como entrada real del nav en `panel.html` /
  `panel.ts`.
- **Móvil:** nueva `MiembrosScreen` + `TarjetaMiembro` + `EditorPerfilScreen`
  + `SelectorAvatar`, enganchadas en `App.kt` / navegación del panel.
- `GET /api/v1/miembros` → lista de `TarjetaMiembroResponse` **ya ordenada**
  para el usuario autenticado:
  1. la suya
  2. la de su pareja (`vinculo ACEPTADO` con cuenta)
  3. las de sus hijos registrados (`hijo.usuario_id` no nulo), alfabético entre
     ellos
  4. el resto de miembros con perfil completado, alfabético por
     `nombre + ' ' + apellidos`
- `TarjetaMiembroResponse`: `id`, `nombre`, `apellidos`, `mote`, `sobreMi`
  (null si vacío), `imagenUrl` (URL absoluta o relativa de `/media/...`),
  `parejaNombre` (null si no hay), `hijos` (lista de nombres; solo los
  `visible=true`, y solo los no registrados). **No** incluye teléfono ni email.
- Botón "Editar" solo en la tarjeta propia (el cliente compara `id` con el del
  usuario en sesión).

## Endpoints (resumen)

| Método | Ruta | Cuerpo / notas |
|---|---|---|
| `GET` | `/api/v1/perfil` | mi perfil: datos + `completado` + vínculo + hijos + (aviso de vínculo pendiente dirigido a mí) |
| `PUT` | `/api/v1/perfil` | `nombre, apellidos, mote, sobreMi, imagenTipo, imagenRef, tienePareja, parejaNombre, parejaTelefono, hijos[]` |
| `POST` | `/api/v1/perfil/foto` | multipart `archivo` → `{ imagenRef }` |
| `GET` | `/api/v1/perfil/avatares` | `[{ id, genero }]` |
| `POST` | `/api/v1/perfil/pareja/aceptar` | — (el destinatario del vínculo `PENDIENTE`) |
| `POST` | `/api/v1/perfil/pareja/rechazar` | — |
| `DELETE` | `/api/v1/perfil/pareja` | rompe el vínculo vivo (cualquiera de los dos) |
| `GET` | `/api/v1/miembros` | tarjetas ordenadas |
| `GET` | `/api/v1/media/avatares/{id}.png` | público |
| `GET` | `/api/v1/media/fotos/{uuid}.jpg` | público |

Errores vía `ApiExceptionHandler` (mismo estilo que el resto): 400 validación,
409 conflictos de vínculo, 415 tipo de imagen, 413 tamaño.

## Móvil: selector de contactos (Android)

- `commonMain`: `expect class SelectorContacto { fun disponible(): Boolean;
  suspend fun elegirTelefono(): String? }` (o equivalente con callback).
- `androidMain`: `Intent.ACTION_PICK` sobre
  `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` → **sin permiso
  `READ_CONTACTS`**, sin cambios en el manifiesto. `startActivityForResult`
  clásico con `RC_PICK_CONTACTO = 1002` (16 bits) en `MainActivity`;
  `onActivityResult` consulta `NUMBER` del `content://` y lo entrega a Compose
  (callback en `Dependencias` o `SharedFlow`).
- `iosMain`: `disponible()` = `false`; `elegirTelefono()` = `null`. El botón
  no se pinta.
- El número elegido pasa por la normalización común antes de meterse en el
  campo.

## Normalización de teléfono (común)

`commonMain` (móvil) y su equivalente en el front / backend:

```
fun normalizarTelefonoEs(entrada: String): String? {
    val limpio = entrada.filter { !it.isWhitespace() && it != '-' && it != '(' && it != ')' }
    val sinPrefijo = limpio
        .removePrefix("+34")
        .removePrefix("0034")
    return if (sinPrefijo.matches(Regex("^[67]\\d{8}$"))) sinPrefijo else null
}
```

`null` → el campo conserva lo tecleado y se muestra error "revisa el número".
El backend valida igual (fuente de verdad) y devuelve 400 por campo.

## Pruebas

**Backend**
- Unit (`*Test`, sin Spring): `NormalizadorTelefono`; `OrdenadorTarjetas`
  (yo → pareja → hijos → alfabético, con casos de empate y sin pareja);
  máquina de estados `EstadoVinculo` (transiciones válidas/ inválidas);
  procesado de imagen (entrada rectangular → 512×512 JPEG, rechazo de GIF).
- IT (`*IT`, Testcontainers Postgres):
  - `PUT /perfil` completo → `perfil.completado=true`, `usuario` actualizado,
    `vinculo_pareja` e `hijo` creados, teléfonos en `telefono_autorizado`.
  - `POST /perfil/foto` → fichero en el `MEDIA_DIR` de test (tmp), `imagenRef`
    válida, `GET /media/fotos/...` lo devuelve; cambiar de foto borra la
    anterior.
  - Vínculo: declarar con teléfono de miembro → `PENDIENTE` + push
    (`@MockitoBean ServicioPush`); aceptar → `ACEPTADO` en ambos sentidos;
    rechazar → `RECHAZADO` + push a A; declarar teléfono ya emparejado → 409.
  - `SIN_CUENTA` → registrar ese teléfono → primer `GET /perfil` lo pone
    `PENDIENTE`.
  - Hijos compartidos: A y B `ACEPTADO`, A añade hijo → aparece para B;
    romper vínculo → el hijo vuelve al creador.
  - `GET /miembros` ordenado; excluye perfiles no completados; no expone
    teléfono/email; hijos no visibles no salen.
  - Path traversal en `/media/fotos/{n}` → 400.
- Ajustar los *call sites* de `new AppProperties(...)` en los tests (nuevo
  componente `Media`).

**Front** (`*.spec.ts`)
- `perfilCompletoGuard` redirige si `completado=false`.
- `EditorPerfil`: validación (imagen obligatoria, pareja respondida, teléfono
  formato); añadir/quitar hijos.
- `SelectorAvatar`: filtro Todos/Chicos/Chicas; selección.
- `TarjetaMiembro`: oculta "sobre mí" vacío; botón editar solo en la propia.
- `Miembros`: pinta el banner de vínculo pendiente.

**Móvil** (`:shared:testAndroidHostTest`)
- `normalizarTelefonoEs` (casos con/sin prefijo, inválidos).
- `PerfilRepository` / `MiembrosRepository` con `MockEngine` (rutas, cuerpos,
  cabecera `Authorization`).
- Máquina de estados del vínculo en el lado cliente si se replica.
- El selector de contactos Android se prueba a mano.

## Impacto en despliegue

Añadir a `baniterio_checklist_despliegue`:
- `MEDIA_DIR` → volumen persistente (si no, se pierden las fotos subidas al
  redeploy).
- `spring.servlet.multipart.max-file-size` / `max-request-size` a 10 MB si el
  proxy/servidor los limita antes.

## Riesgos y decisiones

- **Cualquier miembro puede añadir teléfonos a la lista de autorizados** (vía
  pareja SIN_CUENTA / hijos). Antes eso era acción de admin. Se acepta: peña
  pequeña y de confianza, y el `autorizado_por` deja traza. Un admin puede
  revisar/limpiar `telefono_autorizado`.
- **`/media/**` público:** las fotos de miembros quedan accesibles por URL sin
  token. Mitigación: nombres UUID. Alternativa (descartada por complejidad):
  servir con token vía fetch+blob en los dos clientes.
- **Coil 3 en móvil:** primera dependencia de carga de imágenes; peso asumible.
- **Reconciliación en `PUT /perfil`:** enviar todo el estado (datos + pareja +
  hijos) en una llamada simplifica el cliente pero obliga a un *diff*
  cuidadoso en el servicio (qué hijo es nuevo, cuál se editó, cuál se borró).
  Los hijos llevan `id` opcional en el request para casarlos.
