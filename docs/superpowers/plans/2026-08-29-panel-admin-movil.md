# Panel de administración — Móvil (KMP / Compose) — Plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usa superpowers:subagent-driven-development.
> Los pasos usan checkbox (`- [ ]`).

**Goal:** Añadir a la app móvil la sección de administración: tarjeta
"Administración" al final del panel (visible solo si el usuario tiene áreas), una
pantalla índice, y las pantallas Solicitudes y Permisos que consumen
`/api/v1/admin/*`. De paso, mover el token JWT a un `SesionHolder` compartido y
adjuntarlo (Bearer) a las peticiones protegidas.

**Architecture:** Nuevo `SesionHolder` (token + usuario en memoria, ámbito de
proceso) que comparten `AuthRepositoryImpl` y el nuevo `AdminRepositoryImpl`.
`UsuarioResponse` gana `rol` + `areas` (llegan en la respuesta de `/login`, que
la app hace en cada arranque → siempre frescos). Navegación por `screenKey` como
el resto de la app.

**Tech Stack:** Kotlin Multiplatform 2.4.10, Compose Multiplatform 1.11.1, Ktor
3.5.2 (OkHttp/Darwin), kotlinx-serialization 1.11.0. Verificación:
`./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileAndroidMain
:shared:compileKotlinIosSimulatorArm64` y `./gradlew :androidApp:assembleDebug`
(desde `mobile/`).

**Spec:** `docs/superpowers/specs/2026-08-29-panel-administracion-design.md`
**Depende de:** el plan de backend (contrato `/api/v1/admin/*`, `/login` con
`rol`/`areas`, `password` opcional en `/auth/solicitudes`). El contrato está
descrito en cada tarea.

## Global Constraints

- **Kotlin Multiplatform:** el código de UI y de datos vive en `commonMain`.
  `expect/actual` solo si algo es específico de plataforma (aquí nada nuevo lo es).
- **Sin infra de tests:** el proyecto móvil no tiene tests unitarios y el usuario
  prueba en dispositivo Android real. **La verificación de cada tarea es:
  compila (los 3 targets) + `:androidApp:assembleDebug` pasa.** Montar test infra
  queda fuera de alcance (ruling: coste si se equivoca = un bug que el usuario
  detecta en la prueba manual; el beneficio no compensa el trabajo de montar
  `commonTest` con dobles de Ktor para esta tanda).
- **Modelo de sesión del móvil intacto:** token JWT SOLO en memoria (ahora en
  `SesionHolder`), credenciales cifradas en `AlmacenCredenciales`, desbloqueo
  biométrico en cada arranque. `logout()` sigue limpiando todo.
- **Estilo Compose:** copiar el patrón de las pantallas existentes
  (`SolicitarAccesoScreen`, `PanelScreen`): `Column`/`LazyColumn` con
  `verticalScroll`/`padding(24.dp)`, `BaniterioWordmark()`, sealed interface de
  estado, `rememberSaveable` para campos de formulario, `scope.launch { }` para
  IO, colores de `BaniterioColors` (`brand`, `gold`, `muted`, `error`,
  `brandBright`, `panel`). `BackHandler` para volver. Cabecera de comentario
  explicativa en cada fichero nuevo.
- **Idioma:** identificadores, textos de UI y comentarios en español.
- **"Administración" va SIEMPRE la última** tarjeta del panel.
- **iOS:** el código Kotlin compila para iOS (`compileKotlinIosSimulatorArm64`)
  pero NO se ejecuta (sin Mac). No usar APIs que solo existan en `androidMain`.

---

## File Structure

**Nuevos:**
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/SesionHolder.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/AdminDtos.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoAdmin.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AdminRepository.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AdminRepositoryImpl.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminIndexScreen.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminSolicitudesScreen.kt`
- `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/AdminPermisosScreen.kt`

**Modificados:**
- `data/dto/AuthDtos.kt` — `UsuarioResponse` + `rol`/`areas`; `SolicitudIngresoRequest` + `password`
- `data/AuthRepositoryImpl.kt` — usa `SesionHolder` en vez de campos privados
- `data/AuthRepository.kt` — (sin cambios de interfaz, salvo que se quiera exponer nada)
- `data/Dependencias.kt` — construye `SesionHolder`, añade `adminRepo`
- `nav/Screen.kt` — `AdminIndex`, `AdminSolicitudes`, `AdminPermisos`
- `nav/SolicitudPrecarga.kt` — `password`
- `App.kt` — claves + `when` de las pantallas nuevas + Saver de `datosSolicitud` (5 campos)
- `model/Seccion.kt` — `seccionesPanel` pasa a función `seccionesPanel(tieneAdmin)`
- `ui/panel/PanelScreen.kt` — recibe `tieneAdmin` y navega a `AdminIndex`
- `ui/auth/registro/RegistroScreen.kt` — pasa `password` en `SolicitudPrecarga`
- `ui/auth/solicitaracceso/SolicitarAccesoScreen.kt` — envía `password` de la precarga
- `mobile/README.md` — nota de la sección de administración

---

## Task 1: `SesionHolder`, DTOs (`rol`/`areas`, admin, `password`), `ResultadoAdmin`

**Files:**
- Create: `data/SesionHolder.kt`, `data/dto/AdminDtos.kt`, `data/ResultadoAdmin.kt`
- Modify: `data/dto/AuthDtos.kt`, `data/AuthRepositoryImpl.kt`, `data/Dependencias.kt`

**Interfaces:**
- Consumes (contrato backend): `/login` → `LoginResponse(token, usuario)` con
  `usuario` = `UsuarioResponse(id, nombre, apellidos, mote?, esSuperadmin, rol?, areas)`,
  `rol ∈ {"ADMIN","MIEMBRO",null}`, `areas: List<String>`.
- Produces:
  - `class SesionHolder { var token: String?; var usuario: UsuarioResponse? }`
  - `UsuarioResponse` con `val rol: String? = null`, `val areas: List<String> = emptyList()`
  - `SolicitudIngresoRequest` con `val password: String? = null` (último campo)
  - `sealed class ResultadoAdmin<out T> { Exito<T>; Error(codigo, mensaje) }` +
    `enum CodigoErrorAdmin { SIN_PERMISO, SOLICITUD_YA_RESUELTA, ULTIMO_ADMIN,
    AUTO_MODIFICACION, SOLO_EL_SUPERADMIN, CONFLICTO, VALIDACION, SIN_CONEXION,
    DESCONOCIDO }` con `deCodigoBackend(String?)` y `val mensaje`
  - DTOs admin `@Serializable` (ver Step 3)
  - `Dependencias` con `val adminRepo: AdminRepository` (se añade en Task 2; en
    esta tarea `Dependencias`/`crearDependencias` ya crean el `SesionHolder`)

- [ ] **Step 1: `SesionHolder`**

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.UsuarioResponse

/**
 * Estado de sesión en memoria (ámbito de proceso). Lo comparten
 * [AuthRepositoryImpl] (lo rellena al hacer login) y [AdminRepositoryImpl] (lee
 * el token para la cabecera Bearer). NO se persiste: el modelo de sesión del
 * móvil re-loguea en cada arranque tras el desbloqueo biométrico.
 */
class SesionHolder {
    var token: String? = null
    var usuario: UsuarioResponse? = null

    fun limpiar() {
        token = null
        usuario = null
    }
}
```

- [ ] **Step 2: `AuthDtos.kt` — `rol`/`areas` y `password`**

`UsuarioResponse`: añadir `val rol: String? = null` y `val areas: List<String> =
emptyList()` (tras `esSuperadmin`; con default para tolerar respuestas viejas).
`SolicitudIngresoRequest`: añadir como último parámetro `val password: String? =
null`.

- [ ] **Step 3: `AdminDtos.kt`**

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SolicitudResumen(
    val id: Long, val nombre: String, val apellidos: String, val telefono: String,
    val email: String, val motivo: String, val relacion: String, val conocidos: String,
    val traeContrasena: Boolean, val estado: String, val createdAt: String,
)

@Serializable
data class AprobarResponse(val resultado: String) // "CUENTA_CREADA" | "TELEFONO_AUTORIZADO"

@Serializable
data class RechazoRequest(val motivo: String? = null)

@Serializable
data class MiembroResumen(
    val id: Long, val nombre: String, val apellidos: String, val mote: String? = null,
    val telefono: String, val rol: String, val activo: Boolean, val esSuperadmin: Boolean,
    val areas: List<String> = emptyList(),
)

@Serializable data class RolRequest(val rol: String)
@Serializable data class ActivoRequest(val activo: Boolean)
@Serializable data class AreasRequest(val areas: List<String>)
```

- [ ] **Step 4: `ResultadoAdmin.kt`**

Mismo patrón que `ResultadoAuth.kt`. `CodigoErrorAdmin.deCodigoBackend`:
`"SIN_PERMISO"` → `SIN_PERMISO`; `"SOLICITUD_YA_RESUELTA"` → `SOLICITUD_YA_RESUELTA`;
`"ULTIMO_ADMIN"` → `ULTIMO_ADMIN`; `"NO_TE_PUEDES_DEGRADAR"`/`"NO_TE_PUEDES_DESACTIVAR"`
→ `AUTO_MODIFICACION`; `"SOLO_EL_SUPERADMIN"` → `SOLO_EL_SUPERADMIN`;
`"YA_REGISTRADO"` → `CONFLICTO`; `"VALIDACION"` → `VALIDACION`; else → `DESCONOCIDO`.
`mensaje`: textos en español (p. ej. `ULTIMO_ADMIN` → "No puedes dejar la peña sin
ningún administrador."; `AUTO_MODIFICACION` → "No puedes cambiarte a ti mismo el
rol ni desactivarte."; `SOLO_EL_SUPERADMIN` → "A esa persona solo puede tocarla
ella misma."; `SIN_PERMISO` → "No tienes permiso para esto."; `SIN_CONEXION` →
"No se pudo conectar con el servidor.").

- [ ] **Step 5: `AuthRepositoryImpl` usa `SesionHolder`**

Cambiar el constructor a `AuthRepositoryImpl(private val http: HttpClient,
private val sesion: SesionHolder)`. Sustituir los campos privados `token` y
`_usuario` por `sesion.token` / `sesion.usuario`. `usuarioActual` →
`get() = sesion.usuario`. En `login`, en la rama `Exito`: `sesion.token =
res.dato.token; sesion.usuario = res.dato.usuario`. `logout()` → `sesion.limpiar()`.

- [ ] **Step 6: `Dependencias` / `crearDependencias`**

```kotlin
class Dependencias(
    val repo: AuthRepository,
    val almacen: AlmacenCredenciales,
    val adminRepo: AdminRepository,   // implementado en Task 2
)

fun crearDependencias(almacen: AlmacenCredenciales): Dependencias {
    val http = crearHttpClient()
    val sesion = SesionHolder()
    return Dependencias(
        repo = AuthRepositoryImpl(http, sesion),
        almacen = almacen,
        adminRepo = AdminRepositoryImpl(http, sesion),
    )
}
```

**Nota:** hasta que Task 2 cree `AdminRepository`/`AdminRepositoryImpl`, este
Step no compila. Ruling de secuenciación: **fusiona el contenido de Task 2 Step 1-2
en esta tarea si el revisor prefiere una tarea que compile sola**; si no, deja
`Dependencias` sin `adminRepo` en Task 1 y añádelo en Task 2. Elegido:
**Task 1 NO toca `Dependencias.adminRepo`; lo añade Task 2.** Reescribe este Step 6
para que solo cree el `SesionHolder` y lo pase a `AuthRepositoryImpl`, dejando
`crearDependencias` devolviendo el `Dependencias` actual (2 args) + el `http`
guardado en una `val` local para reutilizarlo en Task 2.

- [ ] **Step 7: Verificar y commit**

Run (desde `mobile/`): `./gradlew :shared:compileCommonMainKotlinMetadata
:shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64`. Expected: BUILD SUCCESSFUL.

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/
git commit -m "feat(movil): SesionHolder + DTOs admin + rol/areas + password en solicitud"
```

---

## Task 2: `AdminRepository` + `AdminRepositoryImpl`

**Files:**
- Create: `data/AdminRepository.kt`, `data/AdminRepositoryImpl.kt`
- Modify: `data/Dependencias.kt` (añadir `adminRepo`)

**Interfaces:**
- Consumes: `SesionHolder` (Task 1), DTOs admin (Task 1), `HttpClient`, `API_BASE_URL`.
- Contrato backend:
  - `GET /api/v1/admin/solicitudes?estado=PENDIENTE` → `200 List<SolicitudResumen>`
  - `POST /api/v1/admin/solicitudes/{id}/aprobar` → `200 AprobarResponse`
  - `POST /api/v1/admin/solicitudes/{id}/rechazar` body `RechazoRequest` → `204`
  - `GET /api/v1/admin/miembros` → `200 List<MiembroResumen>`
  - `PUT /api/v1/admin/miembros/{id}/rol` body `RolRequest` → `204`
  - `PUT /api/v1/admin/miembros/{id}/activo` body `ActivoRequest` → `204`
  - `PUT /api/v1/admin/miembros/{id}/areas` body `AreasRequest` → `204`
  - Todas exigen `Authorization: Bearer <token>`; error `{ "codigo": "..." }`.
- Produces:
  - `interface AdminRepository` con:
    `suspend fun solicitudes(estado: String = "PENDIENTE"): ResultadoAdmin<List<SolicitudResumen>>`,
    `suspend fun aprobar(id: Long): ResultadoAdmin<AprobarResponse>`,
    `suspend fun rechazar(id: Long, motivo: String?): ResultadoAdmin<Unit>`,
    `suspend fun miembros(): ResultadoAdmin<List<MiembroResumen>>`,
    `suspend fun cambiarRol(id: Long, rol: String): ResultadoAdmin<Unit>`,
    `suspend fun cambiarActivo(id: Long, activo: Boolean): ResultadoAdmin<Unit>`,
    `suspend fun cambiarAreas(id: Long, areas: List<String>): ResultadoAdmin<Unit>`

- [ ] **Step 1: `AdminRepository.kt`** — la interfaz de arriba, con cabecera Javadoc.

- [ ] **Step 2: `AdminRepositoryImpl.kt`**

Copiar el patrón `peticion { }` de `AuthRepositoryImpl` (traducción de
`ResponseException` → lee `codigo` del cuerpo con `ErrorResponse`; `CancellationException`
se relanza; otras → `SIN_CONEXION`), pero devolviendo `ResultadoAdmin` y usando
`CodigoErrorAdmin.deCodigoBackend`. Cada petición añade la cabecera:

```kotlin
private fun HttpRequestBuilder.auth() {
    header(HttpHeaders.Authorization, "Bearer ${sesion.token}")
}
```

Ejemplos:

```kotlin
override suspend fun solicitudes(estado: String) = peticion {
    http.get("$API_BASE_URL/admin/solicitudes") {
        auth(); parameter("estado", estado)
    }.body<List<SolicitudResumen>>()
}

override suspend fun aprobar(id: Long) = peticion {
    http.post("$API_BASE_URL/admin/solicitudes/$id/aprobar") { auth() }.body<AprobarResponse>()
}

override suspend fun rechazar(id: Long, motivo: String?) = peticion {
    http.post("$API_BASE_URL/admin/solicitudes/$id/rechazar") {
        auth(); contentType(ContentType.Application.Json); setBody(RechazoRequest(motivo))
    }.bodyAsText().let { }
}

override suspend fun cambiarRol(id: Long, rol: String) = peticion {
    http.put("$API_BASE_URL/admin/miembros/$id/rol") {
        auth(); contentType(ContentType.Application.Json); setBody(RolRequest(rol))
    }.bodyAsText().let { }
}
// ... miembros(), cambiarActivo(), cambiarAreas() análogos
```

- [ ] **Step 3: `Dependencias`** — añadir `val adminRepo: AdminRepository` y en
`crearDependencias` construir `AdminRepositoryImpl(http, sesion)` reutilizando el
`http` y el `sesion` creados en Task 1.

- [ ] **Step 4: Verificar y commit**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileAndroidMain
:shared:compileKotlinIosSimulatorArm64`. Expected: BUILD SUCCESSFUL.

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/
git commit -m "feat(movil): AdminRepository (endpoints /api/v1/admin con Bearer)"
```

---

## Task 3: Navegación — `Screen`, `App.kt`, panel, `AdminIndexScreen`

**Files:**
- Modify: `nav/Screen.kt`, `App.kt`, `model/Seccion.kt`, `ui/panel/PanelScreen.kt`
- Create: `ui/admin/AdminIndexScreen.kt`
- Modify: `mobile/README.md`

**Interfaces:**
- Consumes: `deps.repo.usuarioActual?.areas` (Task 1), `deps.adminRepo` (Task 2).
- Produces:
  - `Screen.AdminIndex`, `Screen.AdminSolicitudes`, `Screen.AdminPermisos` (data objects)
  - `fun seccionesPanel(tieneAdmin: Boolean): List<Seccion>`
  - `PanelScreen(onAbrirSeccion, onCerrarSesion, tieneAdmin: Boolean)`
  - `AdminIndexScreen(areas: List<String>, onAbrir: (Screen) -> Unit, onVolver: () -> Unit)`

- [ ] **Step 1: `Screen.kt`** — añadir los 3 `data object`.

- [ ] **Step 2: `App.kt`** — claves y mapeos

Añadir constantes `CLAVE_ADMIN_INDEX = "AdminIndex"`, `CLAVE_ADMIN_SOLICITUDES`,
`CLAVE_ADMIN_PERMISOS`. Añadir los casos a `Screen.aClave()` y `claveAScreen()`.
Añadir estas pantallas al guard de arranque en frío (misma condición que
`Panel`/`Historia`: si `usuarioActual == null` y `screen` es una de admin →
volver a Desbloqueo/Login).

Actualizar el `Saver` de `datosSolicitud` para 5 campos (añadir `password`):
`save = { listOf(it.nombre, it.apellidos, it.telefono, it.email, it.password) }`,
`restore = { SolicitudPrecarga(it[0], it[1], it[2], it[3], it[4]) }`.

En el `when (screen)`:
```kotlin
is Screen.Panel -> PanelScreen(
    onAbrirSeccion = { destino -> ir(destino) },
    onCerrarSesion = { deps.repo.logout(); deps.almacen.borrar(); ir(Screen.Login) },
    tieneAdmin = deps.repo.usuarioActual?.areas?.isNotEmpty() == true,
)
is Screen.AdminIndex -> {
    BackHandler { ir(Screen.Panel) }
    AdminIndexScreen(
        areas = deps.repo.usuarioActual?.areas ?: emptyList(),
        onAbrir = { ir(it) },
        onVolver = { ir(Screen.Panel) },
    )
}
is Screen.AdminSolicitudes -> {
    BackHandler { ir(Screen.AdminIndex) }
    AdminSolicitudesScreen(adminRepo = deps.adminRepo, onVolver = { ir(Screen.AdminIndex) })
}
is Screen.AdminPermisos -> {
    BackHandler { ir(Screen.AdminIndex) }
    AdminPermisosScreen(adminRepo = deps.adminRepo, onVolver = { ir(Screen.AdminIndex) })
}
```
(`AdminSolicitudesScreen`/`AdminPermisosScreen` se crean en Task 4/5; en esta
tarea, para que compile, crea stubs mínimos de esas dos funciones `@Composable`
en `ui/admin/` — solo `Column { BaniterioWordmark(); Text("Solicitudes"); Text("Volver", Modifier.clickable{onVolver()}) }`.)

- [ ] **Step 3: `Seccion.kt`** — `seccionesPanel` a función

```kotlin
fun seccionesPanel(tieneAdmin: Boolean): List<Seccion> = buildList {
    add(Seccion("Historia", "Cómo nació el Bañiterio y qué significa su escudo.", destino = Screen.Historia))
    add(Seccion("Miembros", "Socios de la peña y sus datos de contacto."))
    add(Seccion("Eventos", "Calendario y organización de las quedadas y fiestas de la peña."))
    add(Seccion("Cuentas", "Ingresos, gastos y balance de la peña."))
    add(Seccion("Inventario", "Material y enseres que tiene la peña."))
    add(Seccion("Ropa", "Pedidos y tallas del vestuario de la peña."))
    if (tieneAdmin) {
        add(Seccion("Administración", "Solicitudes de acceso y permisos de la peña.", destino = Screen.AdminIndex))
    }
}
```

- [ ] **Step 4: `PanelScreen.kt`** — nuevo parámetro `tieneAdmin: Boolean`;
`items(seccionesPanel(tieneAdmin))`. Nada más cambia (la tarjeta ya navega con
`seccion.destino?.let(onAbrirSeccion)`).

- [ ] **Step 5: `AdminIndexScreen.kt`**

`Column` con `verticalScroll` + `padding(24.dp)`. `BaniterioWordmark()`, un
"← Volver" (`clickable { onVolver() }`), título "Administración". Luego, por cada
área que el usuario tenga, una tarjeta (estilo `TarjetaSeccion` de `PanelScreen`):
- `if ("ADMIN_SOLICITUDES" in areas)` → tarjeta "Solicitudes" / "Revisa y resuelve
  las peticiones de acceso a la peña." → `onAbrir(Screen.AdminSolicitudes)`
- `if ("ADMIN_PERMISOS" in areas)` → tarjeta "Permisos" / "Rol y accesos de cada
  miembro." → `onAbrir(Screen.AdminPermisos)`

- [ ] **Step 6: `README.md`** — añadir un párrafo corto en la sección de
funcionalidad: "Administración: los admins (y miembros con permiso concedido) ven
una sección al final del panel para revisar solicitudes de acceso y gestionar
rol/estado/permisos de los miembros. El backend valida el permiso en cada
endpoint (`/api/v1/admin/*`)."

- [ ] **Step 7: Verificar y commit**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileAndroidMain
:shared:compileKotlinIosSimulatorArm64 && ./gradlew :androidApp:assembleDebug`.
Expected: BUILD SUCCESSFUL.

```bash
git add mobile/
git commit -m "feat(movil): navegación de administración + tarjeta en el panel + pantalla índice"
```

---

## Task 4: `AdminSolicitudesScreen`

**Files:**
- Modify: `ui/admin/AdminSolicitudesScreen.kt` (rellenar el stub)

**Interfaces:**
- Consumes: `AdminRepository.solicitudes`, `aprobar`, `rechazar`; `SolicitudResumen`.

- [ ] **Step 1: Estado y carga**

`sealed interface EstadoLista { Cargando; Cargada(val items: List<SolicitudResumen>); Error(val mensaje: String) }`.
`var estado by remember { mutableStateOf<EstadoLista>(EstadoLista.Cargando) }`.
`var aviso by remember { mutableStateOf<String?>(null) }`.
`var rechazandoId by remember { mutableStateOf<Long?>(null) }` +
`var motivoRechazo by remember { mutableStateOf("") }` (diálogo).
`LaunchedEffect(Unit) { cargar() }`; `cargar()` = `suspend` que llama a
`adminRepo.solicitudes()` y setea `estado`.

- [ ] **Step 2: Acciones**

`aprobar(s)`: `scope.launch { when (val r = adminRepo.aprobar(s.id)) {
is Exito -> { aviso = if (r.dato.resultado == "CUENTA_CREADA") "Cuenta creada y
correo enviado." else "Teléfono autorizado y correo enviado."; cargar() }
is Error -> { aviso = r.mensaje; if (r.codigo == SOLICITUD_YA_RESUELTA) cargar() } } }`.

`confirmarRechazo(s)`: `adminRepo.rechazar(s.id, motivoRechazo.ifBlank { null })`
→ cerrar diálogo, `cargar()`; error → `aviso`.

- [ ] **Step 3: UI**

`LazyColumn(padding 24.dp, spacedBy 16.dp)`:
- item: `Row { BaniterioWordmark(); Text("Volver", clickable { onVolver() }) }`
- item: `Text("Solicitudes de acceso", headlineMedium)`
- item: `aviso?.let { Text(it, color = brandBright) }`
- según `estado`:
  - `Cargando` → item `Text("Cargando…", color = muted)`
  - `Error` → item con el mensaje + `Text("Reintentar", clickable { scope.launch { cargar() } })`
  - `Cargada` → si vacío, item `Text("No hay solicitudes pendientes.")`; si no,
    `items(estado.items) { s -> TarjetaSolicitud(s, onAprobar = { aprobar(s) },
    onRechazar = { rechazandoId = s.id; motivoRechazo = "" }) }`

`TarjetaSolicitud` (`Column` con `background(BaniterioColors.panel)`,
`clip(RoundedCornerShape(16.dp))`, `padding(20.dp)`):
- `Text("${s.nombre} ${s.apellidos}", titleLarge, bold)`
- `Text(s.telefono + "  ·  " + s.email, color = muted)`
- `Text(if (s.traeContrasena) "Trae contraseña" else "Sin contraseña", color = muted, labelLarge)`
- bloques "Motivo" / "Relación" / "Conocidos": `Text(etiqueta, bold, small)` + `Text(valor, color = muted)`
- `Row { Button("Aprobar", onAprobar, brand/gold); Spacer; TextButton("Rechazar", onRechazar) }`

Diálogo de rechazo (`if (rechazandoId != null)`): `AlertDialog` con
`OutlinedTextField(motivoRechazo, minLines = 3, label = "Motivo (opcional)")`,
confirm "Rechazar" → `confirmarRechazo`, dismiss "Cancelar" → `rechazandoId = null`.

- [ ] **Step 4: Verificar y commit**

Run: `./gradlew :shared:compileCommonMainKotlinMetadata :shared:compileAndroidMain
:shared:compileKotlinIosSimulatorArm64 && ./gradlew :androidApp:assembleDebug`.

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/
git commit -m "feat(movil): pantalla de solicitudes de administración"
```

---

## Task 5: `AdminPermisosScreen`

**Files:**
- Modify: `ui/admin/AdminPermisosScreen.kt` (rellenar el stub)

**Interfaces:**
- Consumes: `AdminRepository.miembros`, `cambiarRol`, `cambiarActivo`, `cambiarAreas`; `MiembroResumen`.

- [ ] **Step 1: Estado y carga**

`sealed interface EstadoLista { Cargando; Cargada(List<MiembroResumen>); Error(String) }`.
`aviso` como en Task 4. `LaunchedEffect(Unit) { cargar() }`.
Const local `AREAS = listOf("ADMIN_SOLICITUDES" to "Solicitudes", "ADMIN_PERMISOS" to "Permisos")`.

- [ ] **Step 2: Acciones**

`ponerRol(m, rol)`, `activar(m, activo)`, `alternarArea(m, area, incluir)`:
cada una `scope.launch { val r = adminRepo.xxx(...); when (r) { is Exito -> cargar();
is Error -> { aviso = r.mensaje; cargar() } } }`. `alternarArea` calcula
`val nuevas = if (incluir) m.areas + area else m.areas - area` y llama a
`cambiarAreas(m.id, nuevas)`.

- [ ] **Step 3: UI**

`LazyColumn` con cabecera (Wordmark + Volver), título "Permisos de la peña",
`aviso`, y `items(miembros)` → `TarjetaMiembro`:
- `Text("${m.nombre} ${m.apellidos}", titleLarge, bold)` + `m.mote?.let { Text("($it)", muted) }`
- `Text(m.telefono, muted)`
- `if (m.esSuperadmin) Text("Fundadora · acceso total", color = gold, labelLarge)`
- rol: `Row { FilterChip(selected = m.rol == "ADMIN", onClick = { ponerRol(m, "ADMIN") }, label = "Admin");
  FilterChip(selected = m.rol == "MIEMBRO", onClick = { ponerRol(m, "MIEMBRO") }, label = "Miembro") }`
  (si `m.esSuperadmin`, deshabilitar salvo que el usuario actual sea esa misma persona — simplificación: deja habilitado y que el backend responda `SOLO_EL_SUPERADMIN`, que se muestra como `aviso`).
- activo: `Row { Text("Activo"); Switch(checked = m.activo, onCheckedChange = { activar(m, it) }) }`
- áreas: `if (m.rol == "ADMIN" || m.esSuperadmin) Text("Acceso a todas las áreas por rol", muted)`
  `else AREAS.forEach { (clave, etiqueta) -> Row { Checkbox(checked = clave in m.areas,
  onCheckedChange = { alternarArea(m, clave, it) }); Text(etiqueta) } }`

Usar `androidx.compose.material3.FilterChip`, `Switch`, `Checkbox`.

- [ ] **Step 4: Verificar y commit**

Run: compilación 3 targets + `:androidApp:assembleDebug`. Expected: BUILD SUCCESSFUL.

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/
git commit -m "feat(movil): pantalla de permisos de administración"
```

---

## Task 6: Arrastrar la contraseña del registro a "solicitar acceso"

**Files:**
- Modify: `nav/SolicitudPrecarga.kt`, `ui/auth/registro/RegistroScreen.kt`,
  `ui/auth/solicitaracceso/SolicitarAccesoScreen.kt`
- (el Saver de `App.kt` ya se actualizó en Task 3 Step 2)

**Interfaces:**
- Consumes: `SolicitudIngresoRequest.password` (Task 1).
- Produces: `SolicitudPrecarga` con `val password: String`.

- [ ] **Step 1: `SolicitudPrecarga`** — añadir `val password: String` (5º campo).

- [ ] **Step 2: `RegistroScreen`** — en el `onClick` del botón "Solicitar acceso a
la peña" (estado `NoAutorizado`):
`onSolicitarAcceso(SolicitudPrecarga(nombre, apellidos, telefono, email, password))`.
(`password` ya es un `var` en scope.)

- [ ] **Step 3: `SolicitarAccesoScreen`** — en el `SolicitudIngresoRequest` que se
construye en el `onClick` de "Enviar solicitud", añadir
`password = precarga?.password?.takeIf { it.isNotBlank() }`. No se añade ningún
campo visible al formulario (la contraseña no se muestra ni se pide aquí).

- [ ] **Step 4: Verificar y commit**

Run: compilación 3 targets + `:androidApp:assembleDebug`.

```bash
git add mobile/
git commit -m "feat(movil): arrastra la contraseña del registro a la solicitud de acceso"
```

---

## Self-review

- **Cobertura del spec §6:** `Screen`/nav + `AdminRepository` (T1-T3); tarjeta
  "Administración" al final + `AdminIndexScreen` (T3); Solicitudes (T4); Permisos
  (T5); interceptor Bearer vía `SesionHolder` + `auth()` por petición (T1-T2);
  contraseña arrastrada (T6); `rol`/`areas` en `UsuarioResponse` (T1).
- **Consistencia de tipos:** `UsuarioResponse` (con `rol`/`areas`) fijada en T1.
  DTOs admin definidos una vez en `AdminDtos.kt`. `ResultadoAdmin`/`CodigoErrorAdmin`
  espejo de los de auth. `seccionesPanel(Boolean)` — todos los llamantes (solo
  `PanelScreen`) actualizados en T4... **corrección: en T3 Step 4**.
- **Secuenciación:** T1 crea `SesionHolder` y refactoriza `AuthRepositoryImpl`
  pero NO toca `Dependencias.adminRepo` (ruling en T1 Step 6); T2 añade
  `AdminRepository` y lo cablea en `Dependencias`; T3 crea stubs de las 2
  pantallas para que `App.kt` compile; T4/T5 las rellenan. Cada tarea compila +
  `assembleDebug`.
- **Modelo de sesión intacto:** token solo en memoria (`SesionHolder`, ámbito de
  proceso vía `BaniterioApp`/`MainViewController`), `logout()` → `sesion.limpiar()`
  + `almacen.borrar()`. La app sigue re-logueando en cada arranque.
- **Riesgo:** mover el token de campo privado a `SesionHolder` toca la ruta
  caliente de auth; el humo manual debe confirmar login + desbloqueo + logout.
  `FilterChip`/`Switch` son experimentales en algunas versiones de Material3 —
  si piden `@OptIn(ExperimentalMaterial3Api::class)`, añadirlo.
- **iOS:** solo se compila (`compileKotlinIosSimulatorArm64`), no se ejecuta.
