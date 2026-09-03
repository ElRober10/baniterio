# Eventos — Plan C: Móvil (KMP / Compose Multiplatform)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sección Eventos en la app: listado paginado (cada evento → ver / gestionar), botón de crear evento y diálogo de "solicitar crear evento", editor de crear/editar, y el bloque de "Solicitudes de evento" para administradores dentro de la pantalla de solicitudes.

**Architecture:** Lógica en `commonMain`. `EventosRepository` (interfaz + `Impl` con Ktor, Bearer de `SesionHolder`, `ResultadoEvento` sin lanzar por HTTP esperado) igual que `PerfilRepository`/`AdminRepository`. Pantallas Compose con estado local (`remember` + `mutableStateOf`) y `rememberCoroutineScope`, mismo patrón que `MiembrosScreen`/`AdminSolicitudesScreen`. Navegación por el `screenKey` (String) de `App.kt`; el id del evento seleccionado va en un `var` de estado aparte, como `datosSolicitud`/`editorObligatorio`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor client, kotlinx.serialization, `kotlin.test` + `MockEngine` (tests en `:shared:testAndroidHostTest`).

**Spec:** `docs/superpowers/specs/2026-09-03-eventos-design.md`
**Depende de:** Plan A (backend). Se puede desarrollar en paralelo a Plan B.

## Global Constraints

- **Lógica en `commonMain`**; `expect/actual` solo para lo específico de plataforma (aquí no hace falta ninguno nuevo). Tests reales en `:shared:testAndroidHostTest`.
- **Repos**: interfaz en `data/`, `Impl` con `HttpClient` + `SesionHolder`, helper `peticion { }` que traduce `ResponseException` → `ResultadoEvento.Error(codigo)` y el resto → `SIN_CONEXION`; **nunca** `runCatching` (relanzar `CancellationException`). `API_BASE_URL` como base. Copiar el patrón exacto de `PerfilRepositoryImpl`.
- **DTOs** `@Serializable data class` en `data/dto/`, campos opcionales con `= null` / valor por defecto. **Fechas como `String`** (ISO `yyyy-MM-dd`), como el resto de DTOs del proyecto (no se serializa `LocalDate`).
- **Pantallas** Compose en `ui/eventos/`, con `BaniterioColors` / `BaniterioWordmark` / `MaterialTheme`. Reutilizar `Modifier.relieveDeCarta(forma)` de `ui/comun/RelieveDeCarta.kt` para las tarjetas. Nada de ViewModel (el proyecto no los usa).
- **Navegación**: añadir entradas a `nav/Screen.kt`, a las dos funciones de mapeo clave↔Screen y a la guardia de arranque en frío de `App.kt`. El id seleccionado, en un `var eventoSeleccionado by rememberSaveable { mutableStateOf<Long?>(null) }` en `App()`.
- **Idioma**: todo el texto de usuario en español.
- Tras cada tarea: `./gradlew :shared:testAndroidHostTest` verde y, en tareas de UI, `./gradlew :androidApp:compileDebugKotlin` verde. Commits pequeños por paso.

---

## File Structure

**Nuevos** (`mobile/shared/src/commonMain/kotlin/com/baniterio/app/`)
- `data/dto/EventoDtos.kt` — `EventoResumen`, `EventoDetalle`, `ListaEventosResponse`, `GuardarEventoRequest`, `SolicitudEventoResumen`, `CrearSolicitudEventoResponse`
- `data/ResultadoEvento.kt` — `sealed class ResultadoEvento` + `enum CodigoErrorEvento`
- `data/EventosRepository.kt` — interfaz
- `data/EventosRepositoryImpl.kt` — implementación Ktor
- `ui/eventos/EventosScreen.kt` — listado
- `ui/eventos/EventoDetalleScreen.kt` — vista
- `ui/eventos/EditorEventoScreen.kt` — crear / editar

**Nuevos** (`mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/`)
- `EventosRepositoryImplTest.kt`

**Modificados**
- `data/Dependencias.kt` — `EventosRepository` en el grafo
- `nav/Screen.kt` — `Eventos`, `EventoDetalle`, `EditorEvento`
- `App.kt` — mapeo de claves, guardia de arranque en frío, estado `eventoSeleccionado` + `editorEventoId`, ramas del `when (screen)`
- `model/Seccion.kt` — `Eventos` con `destino = Screen.Eventos`
- `data/AdminRepository.kt` + `data/AdminRepositoryImpl.kt` + `data/dto/AdminDtos.kt` — 3 métodos y 1 DTO para solicitudes de evento
- `ui/admin/AdminSolicitudesScreen.kt` — bloque de solicitudes de evento (solo si el usuario es admin/superadmin)
- `data/AdminRepositoryImplTest.kt` (si existe) — 3 casos nuevos

---

## Task 1: Repositorio de eventos + DTOs

**Files:**
- Create: `data/dto/EventoDtos.kt`, `data/ResultadoEvento.kt`, `data/EventosRepository.kt`, `data/EventosRepositoryImpl.kt`
- Modify: `data/Dependencias.kt`
- Test: `commonTest/.../data/EventosRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - DTOs (ver Step 1).
  - `sealed class ResultadoEvento<out T> { data class Exito<T>(val dato: T); data class Error(val codigo: CodigoErrorEvento, val mensaje: String) }`.
  - `EventosRepository` con: `listar(pagina: Int)`, `detalle(id: Long)`, `crear(req)`, `editar(id, req)`, `borrar(id): ResultadoEvento<BorradoEvento>`, `solicitarCrear(mensaje: String?)`.
  - `enum class BorradoEvento { BORRADO, SOLICITUD_CREADA }`.
  - `Dependencias.eventosRepo: EventosRepository`.

- [ ] **Step 1: Escribir `EventoDtos.kt`**

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class EventoResumen(
    val id: Long,
    val nombre: String,
    val fecha: String,
    val fechaFin: String? = null,
    val lugar: String? = null,
    val pasado: Boolean,
)

@Serializable
data class CreadoPor(val id: Long, val nombre: String)

@Serializable
data class EventoDetalle(
    val id: Long,
    val nombre: String,
    val descripcion: String? = null,
    val lugar: String? = null,
    val fecha: String,
    val fechaFin: String? = null,
    val pasado: Boolean,
    val creadoPor: CreadoPor? = null,
    val puedoEditar: Boolean,
    val puedoBorrar: Boolean,
    val borradoPendiente: Boolean,
)

@Serializable
data class ListaEventosResponse(
    val eventos: List<EventoResumen> = emptyList(),
    val pagina: Int,
    val totalPaginas: Int,
    val puedeCrear: Boolean,
    val puedeSolicitar: Boolean,
)

@Serializable
data class GuardarEventoRequest(
    val nombre: String,
    val descripcion: String? = null,
    val lugar: String? = null,
    val fecha: String,
    val fechaFin: String? = null,
)

/** Fila del bloque de administración "Solicitudes de evento". */
@Serializable
data class SolicitudEventoResumen(
    val id: Long,
    val tipo: String,           // CREAR | BORRAR
    val estado: String,
    val solicitante: SolicitanteEvento,
    val evento: EventoRefResumen? = null,
    val mensaje: String? = null,
    val createdAt: String,
)

@Serializable
data class SolicitanteEvento(val id: Long, val nombre: String, val apellidos: String)

@Serializable
data class EventoRefResumen(val id: Long, val nombre: String, val fecha: String)

/** `POST /eventos/solicitudes` y respuesta 202 de `DELETE`. */
@Serializable
data class CrearSolicitudEventoResponse(val id: Long? = null, val estado: String)
```

- [ ] **Step 2: Escribir `ResultadoEvento.kt`** (copia adaptada de `ResultadoPerfil.kt`)

```kotlin
package com.baniterio.app.data

sealed class ResultadoEvento<out T> {
    data class Exito<T>(val dato: T) : ResultadoEvento<T>()
    data class Error(val codigo: CodigoErrorEvento, val mensaje: String) : ResultadoEvento<Nothing>()
}

enum class BorradoEvento { BORRADO, SOLICITUD_CREADA }

enum class CodigoErrorEvento {
    EVENTO_NO_ENCONTRADO,
    SIN_PERMISO_EVENTO,
    SIN_CREDITO_EVENTO,
    SOLICITUD_EVENTO_YA_PENDIENTE,
    CREDITO_SIN_CONSUMIR,
    SOLICITUD_EVENTO_NO_APLICA,
    SOLICITUD_EVENTO_YA_RESUELTA,
    SIN_PERMISO,
    VALIDACION,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorEvento = when (codigo) {
            "EVENTO_NO_ENCONTRADO" -> EVENTO_NO_ENCONTRADO
            "SIN_PERMISO_EVENTO" -> SIN_PERMISO_EVENTO
            "SIN_CREDITO_EVENTO" -> SIN_CREDITO_EVENTO
            "SOLICITUD_EVENTO_YA_PENDIENTE" -> SOLICITUD_EVENTO_YA_PENDIENTE
            "CREDITO_SIN_CONSUMIR" -> CREDITO_SIN_CONSUMIR
            "SOLICITUD_EVENTO_NO_APLICA" -> SOLICITUD_EVENTO_NO_APLICA
            "SOLICITUD_EVENTO_YA_RESUELTA" -> SOLICITUD_EVENTO_YA_RESUELTA
            "SIN_PERMISO" -> SIN_PERMISO
            "VALIDACION" -> VALIDACION
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            EVENTO_NO_ENCONTRADO -> "Ese evento ya no existe."
            SIN_PERMISO_EVENTO, SIN_PERMISO -> "No tienes permiso para esto."
            SIN_CREDITO_EVENTO -> "No tienes ningún evento autorizado sin crear."
            SOLICITUD_EVENTO_YA_PENDIENTE -> "Ya hay una solicitud pendiente."
            CREDITO_SIN_CONSUMIR -> "Ya tienes un evento autorizado sin crear."
            SOLICITUD_EVENTO_NO_APLICA -> "Como administrador puedes crear eventos directamente."
            SOLICITUD_EVENTO_YA_RESUELTA -> "Esa solicitud ya la resolvió alguien."
            VALIDACION -> "Revisa los datos del formulario."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
```

- [ ] **Step 3: Escribir `EventosRepository.kt`**

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.GuardarEventoRequest
import com.baniterio.app.data.dto.ListaEventosResponse

/**
 * Endpoints de la sección Eventos (`/api/v1/eventos*`). Misma mecánica que
 * [PerfilRepository]: Bearer de [SesionHolder], nunca lanza por errores HTTP
 * esperados, relanza `CancellationException`.
 */
interface EventosRepository {
    suspend fun listar(pagina: Int): ResultadoEvento<ListaEventosResponse>
    suspend fun detalle(id: Long): ResultadoEvento<EventoDetalle>
    suspend fun crear(req: GuardarEventoRequest): ResultadoEvento<EventoDetalle>
    suspend fun editar(id: Long, req: GuardarEventoRequest): ResultadoEvento<EventoDetalle>
    suspend fun borrar(id: Long): ResultadoEvento<BorradoEvento>
    suspend fun solicitarCrear(mensaje: String?): ResultadoEvento<Unit>
}
```

- [ ] **Step 4: Escribir `EventosRepositoryImpl.kt`**

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.EventoDetalle
import com.baniterio.app.data.dto.GuardarEventoRequest
import com.baniterio.app.data.dto.ListaEventosResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class EventosRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : EventosRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun listar(pagina: Int): ResultadoEvento<ListaEventosResponse> = peticion {
        http.get("$API_BASE_URL/eventos") {
            auth()
            parameter("pagina", pagina)
        }.body()
    }

    override suspend fun detalle(id: Long): ResultadoEvento<EventoDetalle> = peticion {
        http.get("$API_BASE_URL/eventos/$id") { auth() }.body()
    }

    override suspend fun crear(req: GuardarEventoRequest): ResultadoEvento<EventoDetalle> = peticion {
        http.post("$API_BASE_URL/eventos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun editar(id: Long, req: GuardarEventoRequest): ResultadoEvento<EventoDetalle> = peticion {
        http.put("$API_BASE_URL/eventos/$id") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun borrar(id: Long): ResultadoEvento<BorradoEvento> = peticion {
        val res = http.delete("$API_BASE_URL/eventos/$id") { auth() }
        if (res.status == HttpStatusCode.Accepted) BorradoEvento.SOLICITUD_CREADA else BorradoEvento.BORRADO
    }

    override suspend fun solicitarCrear(mensaje: String?): ResultadoEvento<Unit> = peticion {
        http.post("$API_BASE_URL/eventos/solicitudes") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("mensaje" to (mensaje ?: "")))
        }
        Unit
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoEvento<T> =
        try {
            ResultadoEvento.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorEvento.deCodigoBackend(codigo)
            ResultadoEvento.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoEvento.Error(CodigoErrorEvento.SIN_CONEXION, CodigoErrorEvento.SIN_CONEXION.mensaje)
        }
}
```

- [ ] **Step 5: Añadir al grafo** — en `Dependencias.kt`:

```kotlin
class Dependencias(
    val repo: AuthRepository,
    val almacen: AlmacenCredenciales,
    val adminRepo: AdminRepository,
    val dispositivoRepo: DispositivoRepository,
    val perfilRepo: PerfilRepository,
    val eventosRepo: EventosRepository,   // nuevo
)

// en crearDependencias(...), añadir:
    eventosRepo = EventosRepositoryImpl(http, sesion),
```

- [ ] **Step 6: Escribir el test que falla** (`EventosRepositoryImplTest.kt`, patrón de `PerfilRepositoryImplTest.kt`)

```kotlin
package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import com.baniterio.app.data.dto.GuardarEventoRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class EventosRepositoryImplTest {

    private data class Vista(val metodo: String, val path: String, val query: String, val auth: String?, val cuerpo: String)

    private fun repo(
        status: HttpStatusCode = HttpStatusCode.OK,
        cuerpoRespuesta: String = "",
    ): Pair<EventosRepositoryImpl, MutableList<Vista>> {
        val vistas = mutableListOf<Vista>()
        val engine = MockEngine { req ->
            vistas += Vista(req.method.value, req.url.encodedPath, req.url.encodedQuery,
                req.headers[HttpHeaders.Authorization], req.body.toByteArray().decodeToString())
            if (status.value >= 400) {
                respondError(status, cuerpoRespuesta, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(ByteReadChannel(cuerpoRespuesta), status,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val http = HttpClient(engine) { configComun() }
        val sesion = SesionHolder().apply { token = "jwt-x" }
        return EventosRepositoryImpl(http, sesion) to vistas
    }

    @Test
    fun listar_hace_get_con_pagina_y_bearer() = runTest {
        val (r, vistas) = repo(cuerpoRespuesta =
            """{"eventos":[],"pagina":1,"totalPaginas":2,"puedeCrear":false,"puedeSolicitar":true}""")
        val res = r.listar(1)
        assertIs<ResultadoEvento.Exito<*>>(res)
        assertEquals("GET", vistas[0].metodo)
        assertEquals("/api/v1/eventos", vistas[0].path)
        assertTrue(vistas[0].query.contains("pagina=1"))
        assertEquals("Bearer jwt-x", vistas[0].auth)
    }

    @Test
    fun crear_sin_credito_devuelve_error_tipado() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Conflict, cuerpoRespuesta = """{"codigo":"SIN_CREDITO_EVENTO"}""")
        val res = r.crear(GuardarEventoRequest(nombre = "X", fecha = "2999-01-01"))
        assertIs<ResultadoEvento.Error>(res)
        assertEquals(CodigoErrorEvento.SIN_CREDITO_EVENTO, res.codigo)
    }

    @Test
    fun borrar_202_es_SOLICITUD_CREADA() = runTest {
        val (r, _) = repo(status = HttpStatusCode.Accepted, cuerpoRespuesta = """{"estado":"PENDIENTE"}""")
        val res = r.borrar(5)
        assertIs<ResultadoEvento.Exito<BorradoEvento>>(res)
        assertEquals(BorradoEvento.SOLICITUD_CREADA, res.dato)
    }
}
```

> **Nota:** confirmar cómo se llama el helper de configuración del `HttpClient` en los tests (`configComun()` en `PerfilRepositoryImplTest`). Usar el mismo.

- [ ] **Step 7: Ejecutar** — primero FAIL, luego (con los ficheros) PASS.

Run: `./gradlew :shared:testAndroidHostTest`

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/EventosRepositoryImplTest.kt
git commit -m "feat(movil): EventosRepository, DTOs y ResultadoEvento"
```

---

## Task 2: Navegación de la sección Eventos

**Files:**
- Modify: `nav/Screen.kt`, `App.kt`, `model/Seccion.kt`

**Interfaces:**
- Produces: `Screen.Eventos`, `Screen.EventoDetalle`, `Screen.EditorEvento` (data objects); estado `eventoSeleccionado: Long?` y `editorEventoId: Long?` en `App()`. Callbacks que las pantallas de Task 3–4 reciben: `onAbrirEvento: (Long) -> Unit`, `onCrear: () -> Unit`, `onEditar: (Long) -> Unit`, `onVolver: () -> Unit`, `onGuardado: () -> Unit`.

- [ ] **Step 1: `nav/Screen.kt`** — añadir:

```kotlin
    data object Eventos : Screen()
    data object EventoDetalle : Screen()
    data object EditorEvento : Screen()
```

- [ ] **Step 2: `App.kt`** — mapeo de claves

Añadir constantes y ramas en `Screen.aClave()` y `claveAScreen(...)`:

```kotlin
private const val CLAVE_EVENTOS = "Eventos"
private const val CLAVE_EVENTO_DETALLE = "EventoDetalle"
private const val CLAVE_EDITOR_EVENTO = "EditorEvento"
// aClave(): Screen.Eventos -> CLAVE_EVENTOS, etc.
// claveAScreen(): CLAVE_EVENTOS -> Screen.Eventos, etc.
```

Añadir esas 3 pantallas a la lista de la **guardia de arranque en frío** (el `if` grande del `LaunchedEffect(Unit)` que comprueba `deps.repo.usuarioActual == null`).

- [ ] **Step 3: `App.kt`** — estado del id seleccionado

Junto a `editorObligatorio`:

```kotlin
var eventoSeleccionado by rememberSaveable { mutableStateOf<Long?>(null) }   // detalle
var editorEventoId by rememberSaveable { mutableStateOf<Long?>(null) }        // null = crear
```

- [ ] **Step 4: `App.kt`** — ramas del `when (screen)`

```kotlin
is Screen.Eventos -> {
    BackHandler { ir(Screen.Panel) }
    EventosScreen(
        eventosRepo = deps.eventosRepo,
        onAbrirEvento = { id -> eventoSeleccionado = id; ir(Screen.EventoDetalle) },
        onCrear = { editorEventoId = null; ir(Screen.EditorEvento) },
        onVolver = { ir(Screen.Panel) },
    )
}
is Screen.EventoDetalle -> {
    BackHandler { ir(Screen.Eventos) }
    EventoDetalleScreen(
        eventosRepo = deps.eventosRepo,
        eventoId = eventoSeleccionado ?: run { ir(Screen.Eventos); return@Surface },
        onEditar = { editorEventoId = eventoSeleccionado; ir(Screen.EditorEvento) },
        onBorrado = { ir(Screen.Eventos) },
        onVolver = { ir(Screen.Eventos) },
    )
}
is Screen.EditorEvento -> {
    BackHandler { ir(if (editorEventoId != null) Screen.EventoDetalle else Screen.Eventos) }
    EditorEventoScreen(
        eventosRepo = deps.eventosRepo,
        eventoId = editorEventoId,
        onGuardado = { id -> eventoSeleccionado = id; ir(Screen.EventoDetalle) },
        onVolver = { ir(if (editorEventoId != null) Screen.EventoDetalle else Screen.Eventos) },
    )
}
```

> **Nota:** el `return@Surface` de arriba requiere que el `when` esté dentro del lambda de `Surface { }`. Si el compilador se queja, sustituir por: si `eventoSeleccionado == null`, pintar `LaunchedEffect(Unit) { ir(Screen.Eventos) }` y nada más.

Añadir los `import` de las 3 pantallas.

- [ ] **Step 5: `model/Seccion.kt`** — la sección Eventos deja de ser "próximamente"

```kotlin
add(Seccion("Eventos", "Calendario y organización de las quedadas y fiestas de la peña.", destino = Screen.Eventos))
```

- [ ] **Step 6: Compilar**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: FAIL hasta que existan `EventosScreen`/`EventoDetalleScreen`/`EditorEventoScreen` (Tasks 3–4). **Esta tarea se comitea junto con la Task 3** (no compila sola). Alternativa: crear stubs `@Composable fun EventosScreen(...) {}` en esta tarea y rellenarlos en la 3. Se recomienda **stubs**:

```kotlin
// ui/eventos/EventosScreen.kt (stub)
@Composable
fun EventosScreen(
    eventosRepo: EventosRepository,
    onAbrirEvento: (Long) -> Unit,
    onCrear: () -> Unit,
    onVolver: () -> Unit,
) { /* Task 3 */ }
```

(y análogos para las otras dos, con las firmas de Step 4).

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/model/Seccion.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/
git commit -m "feat(movil): navegación de la sección Eventos (stubs de pantalla)"
```

---

## Task 3: `EventosScreen` — listado

**Files:**
- Modify (rellenar el stub): `ui/eventos/EventosScreen.kt`

**Interfaces:**
- Consumes: `EventosRepository.listar`, `EventosRepository.solicitarCrear`; callbacks de Task 2.

- [ ] **Step 1: Implementar `EventosScreen`** (patrón de `MiembrosScreen` + `AdminSolicitudesScreen`)

Estructura:
- `sealed interface EstadoEventos { Cargando; data class Cargada(ListaEventosResponse); data class Error(String) }`.
- `var estado`, `var pagina` (Int), `var aviso: String?`, `var dialogoSolicitud: Boolean`, `var mensajeSolicitud: String`.
- `suspend fun cargar(p: Int, mostrarCargando: Boolean = true)` → `eventosRepo.listar(p)`.
- `LaunchedEffect(Unit) { cargar(0) }`.
- Cabecera: `BaniterioWordmark()` + "Volver" clicable (`onVolver`).
- Fila de acción: si `Cargada.puedeCrear` → `Button("Crear evento", onClick = onCrear)`; si no y `puedeSolicitar` → `OutlinedButton("Solicitar crear evento") { dialogoSolicitud = true }`.
- `aviso?.let { Text(it, color = BaniterioColors.gold) }`.
- Lista (`Column` con `verticalScroll` o `LazyColumn`): por cada `EventoResumen`, una tarjeta `Modifier.relieveDeCarta(RoundedCornerShape(16.dp)).clickable { onAbrirEvento(e.id) }` con `nombre` (title), `lugar` si hay, `fecha` a la derecha; `alpha = 0.6f` si `e.pasado`.
- Paginación: si `totalPaginas > 1`, fila con `TextButton("Anterior", enabled = pagina > 0)` / `Text("${pagina+1} / $total")` / `TextButton("Siguiente", enabled = pagina+1 < total)` — cada uno hace `scope.launch { cargar(nuevaPagina, mostrarCargando = false) }`.
- Diálogo `if (dialogoSolicitud)` (`androidx.compose.material3.AlertDialog` o `Dialog`): `OutlinedTextField(mensajeSolicitud, ...)` + confirmar → `scope.launch { when (eventosRepo.solicitarCrear(mensajeSolicitud.ifBlank { null })) { Exito -> { dialogoSolicitud=false; aviso="Solicitud enviada."; cargar(pagina, false) } ; Error -> { dialogoSolicitud=false; aviso=it.mensaje; cargar(pagina, false) } } }`.

```kotlin
package com.baniterio.app.ui.eventos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.EventosRepository
import com.baniterio.app.data.ResultadoEvento
import com.baniterio.app.data.dto.EventoResumen
import com.baniterio.app.data.dto.ListaEventosResponse
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark
import com.baniterio.app.ui.comun.relieveDeCarta
import kotlinx.coroutines.launch

private sealed interface EstadoEventos {
    data object Cargando : EstadoEventos
    data class Cargada(val datos: ListaEventosResponse) : EstadoEventos
    data class Error(val mensaje: String) : EstadoEventos
}

@Composable
fun EventosScreen(
    eventosRepo: EventosRepository,
    onAbrirEvento: (Long) -> Unit,
    onCrear: () -> Unit,
    onVolver: () -> Unit,
) {
    var estado by remember { mutableStateOf<EstadoEventos>(EstadoEventos.Cargando) }
    var pagina by remember { mutableStateOf(0) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var dialogoSolicitud by remember { mutableStateOf(false) }
    var mensajeSolicitud by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun cargar(p: Int, mostrarCargando: Boolean = true) {
        if (mostrarCargando) estado = EstadoEventos.Cargando
        estado = when (val r = eventosRepo.listar(p)) {
            is ResultadoEvento.Exito -> { pagina = r.dato.pagina; EstadoEventos.Cargada(r.dato) }
            is ResultadoEvento.Error -> EstadoEventos.Error(r.mensaje)
        }
    }
    LaunchedEffect(Unit) { cargar(0) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BaniterioWordmark()
            Text("Volver", color = BaniterioColors.brandBright, modifier = Modifier.clickable { onVolver() })
        }
        Text("Eventos", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

        when (val e = estado) {
            is EstadoEventos.Cargando -> Text("Cargando…", color = BaniterioColors.muted)
            is EstadoEventos.Error -> {
                Text(e.mensaje, color = BaniterioColors.muted)
                Button(onClick = { scope.launch { cargar(pagina) } }) { Text("Reintentar") }
            }
            is EstadoEventos.Cargada -> {
                val d = e.datos
                if (d.puedeCrear) {
                    Button(onClick = onCrear,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BaniterioColors.brand, contentColor = BaniterioColors.gold)) {
                        Text("Crear evento", fontWeight = FontWeight.Bold)
                    }
                } else if (d.puedeSolicitar) {
                    OutlinedButton(onClick = { mensajeSolicitud = ""; dialogoSolicitud = true }) {
                        Text("Solicitar crear evento")
                    }
                }
                aviso?.let { Text(it, color = BaniterioColors.gold) }

                d.eventos.forEach { ev -> TarjetaEvento(ev) { onAbrirEvento(ev.id) } }
                if (d.eventos.isEmpty()) Text("Todavía no hay eventos.", color = BaniterioColors.muted)

                if (d.totalPaginas > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = pagina > 0,
                            onClick = { scope.launch { cargar(pagina - 1, false) } }) { Text("Anterior") }
                        Text("${pagina + 1} / ${d.totalPaginas}", color = BaniterioColors.muted)
                        TextButton(enabled = pagina + 1 < d.totalPaginas,
                            onClick = { scope.launch { cargar(pagina + 1, false) } }) { Text("Siguiente") }
                    }
                }
            }
        }
    }

    if (dialogoSolicitud) {
        AlertDialog(
            onDismissRequest = { dialogoSolicitud = false },
            title = { Text("Solicitar crear un evento") },
            text = {
                OutlinedTextField(
                    value = mensajeSolicitud, onValueChange = { mensajeSolicitud = it },
                    label = { Text("¿Qué evento quieres organizar? (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val r = eventosRepo.solicitarCrear(mensajeSolicitud.ifBlank { null })
                        dialogoSolicitud = false
                        aviso = when (r) {
                            is ResultadoEvento.Exito -> "Solicitud enviada. Un administrador tiene que autorizarla."
                            is ResultadoEvento.Error -> r.mensaje
                        }
                        cargar(pagina, mostrarCargando = false)
                    }
                }) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { dialogoSolicitud = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TarjetaEvento(e: EventoResumen, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .relieveDeCarta(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .alpha(if (e.pasado) 0.6f else 1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.nombre, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            e.lugar?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted) }
        }
        Text(e.fecha, style = MaterialTheme.typography.bodySmall, color = BaniterioColors.muted)
    }
}
```

- [ ] **Step 2: Compilar**

Run: `./gradlew :androidApp:compileDebugKotlin` → PASS.

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventosScreen.kt
git commit -m "feat(movil): pantalla de listado de eventos con paginado"
```

---

## Task 4: `EventoDetalleScreen` y `EditorEventoScreen`

**Files:**
- Modify (rellenar stubs): `ui/eventos/EventoDetalleScreen.kt`, `ui/eventos/EditorEventoScreen.kt`

**Interfaces:**
- `EventoDetalleScreen(eventosRepo, eventoId: Long, onEditar: () -> Unit, onBorrado: () -> Unit, onVolver: () -> Unit)`
- `EditorEventoScreen(eventosRepo, eventoId: Long?, onGuardado: (Long) -> Unit, onVolver: () -> Unit)`

- [ ] **Step 1: `EventoDetalleScreen`**

- Estado: `Cargando | Cargada(EventoDetalle) | Error`. `var aviso: String?`.
- `LaunchedEffect(eventoId) { estado = when (eventosRepo.detalle(eventoId)) { ... } }`.
- Contenido: tarjeta `relieveDeCarta` con `nombre` (headline, `alpha 0.7` si `pasado`), `fecha` (+ `– fechaFin` si hay, + "· PASADO"), `lugar`, `descripcion` (respeta saltos de línea), "Creado por X" si `creadoPor`.
- `aviso?.let { Text(...) }`.
- Si `puedoEditar`: `Button("Gestionar", onClick = onEditar)`.
- Si `puedoBorrar`: `OutlinedButton(enabled = !borradoPendiente, onClick = { scope.launch { when (val r = eventosRepo.borrar(eventoId)) { is Exito -> if (r.dato == BorradoEvento.BORRADO) onBorrado() else { aviso = "Solicitud de borrado enviada."; /* recargar detalle */ } ; is Error -> aviso = r.mensaje } } })` con texto:
  - `borradoPendiente` → "Borrado pendiente de autorización"
  - `creadoPor != null` → "Solicitar borrado"
  - else → "Borrar"
- Tras `SOLICITUD_CREADA`, recargar el detalle (para que `borradoPendiente` pase a true).
- Cabecera con "Volver" clicable → `onVolver`.

- [ ] **Step 2: `EditorEventoScreen`**

- `val editando = eventoId != null`.
- Estado de campos: `var nombre`, `var descripcion`, `var lugar`, `var fecha`, `var fechaFin` (todos `String`), `var guardando: Boolean`, `var error: String?`.
- Si `editando`: `LaunchedEffect(eventoId) { when (val r = eventosRepo.detalle(eventoId!!)) { is Exito -> { nombre = r.dato.nombre; descripcion = r.dato.descripcion ?: ""; lugar = r.dato.lugar ?: ""; fecha = r.dato.fecha; fechaFin = r.dato.fechaFin ?: "" } ; is Error -> error = r.mensaje } }`.
- Campos: `OutlinedTextField` para nombre, descripción (`minLines = 3`), lugar. Para **fecha** y **fecha fin**: `OutlinedTextField` con `label = { Text("Fecha (aaaa-mm-dd)") }` y `placeholder`. (Un `DatePicker` de Material3 es opcional; para v1 basta el texto con validación de formato `Regex("""\d{4}-\d{2}-\d{2}""")`.)
- Validación cliente antes de enviar: `nombre` no vacío; `fecha` casa la regex; si `fechaFin` no vacía, casa la regex y `fechaFin >= fecha` (comparación de String ISO sirve). Si falla → `error = "..."`, no enviar.
- Guardar: `guardando = true`; `req = GuardarEventoRequest(nombre.trim(), descripcion.trim().ifBlank { null }, lugar.trim().ifBlank { null }, fecha, fechaFin.ifBlank { null })`; `val r = if (editando) eventosRepo.editar(eventoId!!, req) else eventosRepo.crear(req)`; `when (r) { is Exito -> onGuardado(r.dato.id) ; is Error -> { guardando = false; error = r.mensaje } }`.
- Título "Nuevo evento" / "Editar evento". Botón Guardar `enabled = !guardando`. "Volver" → `onVolver`.

```kotlin
// Firma y esqueleto de EditorEventoScreen — el cuerpo sigue la descripción de arriba.
@Composable
fun EditorEventoScreen(
    eventosRepo: EventosRepository,
    eventoId: Long?,
    onGuardado: (Long) -> Unit,
    onVolver: () -> Unit,
) { /* ... */ }
```

- [ ] **Step 3: Compilar**

Run: `./gradlew :androidApp:compileDebugKotlin` → PASS. Luego `./gradlew :shared:testAndroidHostTest` (por si algún test común toca `Screen`).

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EventoDetalleScreen.kt \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/eventos/EditorEventoScreen.kt
git commit -m "feat(movil): pantallas de detalle y editor de evento"
```

---

## Task 5: Bloque de administración — solicitudes de evento

**Files:**
- Modify: `data/dto/AdminDtos.kt`, `data/AdminRepository.kt`, `data/AdminRepositoryImpl.kt`, `ui/admin/AdminSolicitudesScreen.kt`
- Modify (si existe): `commonTest/.../data/AdminRepositoryImplTest.kt`

**Interfaces:**
- Produces:
  - `AdminRepository.solicitudesEvento(estado: String = "PENDIENTE"): ResultadoAdmin<List<SolicitudEventoResumen>>`
  - `AdminRepository.aprobarSolicitudEvento(id: Long): ResultadoAdmin<Unit>`
  - `AdminRepository.rechazarSolicitudEvento(id: Long, motivo: String?): ResultadoAdmin<Unit>`
  - Reutiliza `SolicitudEventoResumen` de `data/dto/EventoDtos.kt` (Task 1) — **moverlo o dejarlo ahí e importarlo desde AdminDtos**; se deja en `EventoDtos.kt`.

- [ ] **Step 1: `AdminRepository.kt`** — añadir las 3 firmas (importar `SolicitudEventoResumen` de `com.baniterio.app.data.dto`).

- [ ] **Step 2: `AdminRepositoryImpl.kt`** — implementar con el helper `peticion { }` existente:

```kotlin
    override suspend fun solicitudesEvento(estado: String): ResultadoAdmin<List<SolicitudEventoResumen>> =
        peticion {
            http.get("$API_BASE_URL/admin/solicitudes-evento") {
                auth()
                parameter("estado", estado)
            }.body()
        }

    override suspend fun aprobarSolicitudEvento(id: Long): ResultadoAdmin<Unit> =
        peticion { http.post("$API_BASE_URL/admin/solicitudes-evento/$id/aprobar") { auth() }; Unit }

    override suspend fun rechazarSolicitudEvento(id: Long, motivo: String?): ResultadoAdmin<Unit> =
        peticion {
            http.post("$API_BASE_URL/admin/solicitudes-evento/$id/rechazar") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(mapOf("motivo" to (motivo ?: "")))
            }
            Unit
        }
```

- [ ] **Step 3: Test** (`AdminRepositoryImplTest.kt`, si existe el fichero; si no, crearlo con el patrón de `PerfilRepositoryImplTest`)

```kotlin
@Test
fun solicitudesEvento_hace_get_con_estado_y_bearer() = runTest {
    val (r, vistas) = repo(cuerpoRespuesta = "[]")
    r.solicitudesEvento()
    assertEquals("/api/v1/admin/solicitudes-evento", vistas[0].path)
    assertTrue(vistas[0].query.contains("estado=PENDIENTE"))
    assertEquals("Bearer jwt-x", vistas[0].auth)
}

@Test
fun aprobarSolicitudEvento_hace_post() = runTest {
    val (r, vistas) = repo(status = HttpStatusCode.NoContent)
    r.aprobarSolicitudEvento(9)
    assertEquals("POST", vistas[0].metodo)
    assertEquals("/api/v1/admin/solicitudes-evento/9/aprobar", vistas[0].path)
}
```

- [ ] **Step 4: `AdminSolicitudesScreen.kt`** — bloque al final de la lista

- Añadir parámetro `esAdmin: Boolean` a `AdminSolicitudesScreen(...)` y pasarlo desde `App.kt`:
  `AdminSolicitudesScreen(adminRepo = deps.adminRepo, esAdmin = deps.repo.usuarioActual?.let { it.rol == "ADMIN" || it.esSuperadmin } == true, onVolver = { ir(Screen.AdminIndex) })`.
  (Confirmar el nombre del campo de rol/superadmin en el DTO de usuario del móvil — `UsuarioActual`/`UsuarioDto`; ajustar.)
- Dentro del `LazyColumn`, tras los `items(...)` de solicitudes de ingreso, si `esAdmin`, un `item { }` con:
  - separador + `Text("Solicitudes de evento", style = titleMedium, fontWeight = Bold)`.
  - carga propia: `var solicEvento by remember { mutableStateOf<List<SolicitudEventoResumen>>(emptyList()) }`, `LaunchedEffect(Unit) { (adminRepo.solicitudesEvento() as? ResultadoAdmin.Exito)?.let { solicEvento = it.dato } }`.
  - por cada una: tarjeta con `"${s.tipo} · ${s.solicitante.nombre} ${s.solicitante.apellidos}"`, `s.evento?.let { "«${it.nombre}» (${it.fecha})" }`, `s.mensaje`, y botones **Aprobar** / **Rechazar** (con `AlertDialog` de motivo, como el `rechazandoId` de ingreso). Tras aprobar/rechazar, recargar `solicEvento` y poner `aviso`.

> **Nota:** para no inflar `AdminSolicitudesScreen`, extraer el bloque a un `@Composable private fun BloqueSolicitudesEvento(adminRepo: AdminRepository)` en el mismo fichero (o en `ui/admin/SolicitudesEventoAdmin.kt`). Recomendado: fichero aparte `ui/admin/SolicitudesEventoAdmin.kt` con `@Composable fun SolicitudesEventoAdmin(adminRepo: AdminRepository)` y llamarlo desde un `item { }` cuando `esAdmin`.

- [ ] **Step 5: Compilar y test**

Run: `./gradlew :shared:testAndroidHostTest` y `./gradlew :androidApp:compileDebugKotlin` → PASS.

- [ ] **Step 6: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/admin/ \
        mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt \
        mobile/shared/src/commonTest/kotlin/com/baniterio/app/data/AdminRepositoryImplTest.kt
git commit -m "feat(movil): bloque de solicitudes de evento en administración"
```

---

## Self-Review

**Spec coverage (sección "Móvil"):**
- `EventosScreen.kt` listado paginado + "Crear evento" / "Solicitar crear evento" (diálogo con mensaje) → Task 3.
- `EventoDetalleScreen.kt` vista + "Gestionar" + "Borrar"/"Solicitar borrado" (deshabilitado si `borradoPendiente`) → Task 4.
- `EditorEventoScreen.kt` crear / editar, campos nombre/descripción/lugar/fecha/fechaFin, validación de formato + rango → Task 4.
- `EventosRepository` + DTOs (`data/`) → Task 1.
- Navegación en `Screen.kt` + `App.kt` (mapeo de claves, guardia de arranque en frío, estado de id) → Task 2. `model/Seccion.kt`: Eventos deja de ser "próximamente" → Task 2.
- Bloque de solicitudes de evento en `AdminSolicitudesScreen` para administradores → Task 5.
- Tests en `:shared:testAndroidHostTest` para el repositorio → Task 1; para el repo de admin → Task 5. La UI Compose se verifica compilando (`:androidApp:compileDebugKotlin`), como en el Plan C de perfiles-miembros.

**Type consistency:** `EventoResumen`/`EventoDetalle`/`ListaEventosResponse`/`GuardarEventoRequest`/`SolicitudEventoResumen` (Task 1) coinciden campo a campo con los DTOs del backend (Plan A) y con los del web (Plan B). `ResultadoEvento` / `BorradoEvento` / `CodigoErrorEvento` (Task 1) usados en Tasks 3–5. Firmas de las 3 pantallas fijadas en Task 2 (stubs) y respetadas en Tasks 3–4. `EventosRepository` métodos: `listar/detalle/crear/editar/borrar/solicitarCrear`.

**Placeholder scan:** Tasks 4 y 5 describen el cuerpo de los `@Composable` en prosa estructurada con las firmas exactas, el estado y el flujo, en vez de volcar todo el código Compose (varios cientos de líneas). `EventosScreen` (Task 3) sí lleva el código completo como referencia del patrón. La decisión "Solicitar borrado" vs "Borrar" por `creadoPor != null` es la misma aproximación consciente que en Plan B. El `return@Surface` de Task 2 Step 4 lleva su alternativa por si el compilador se queja. Ninguna sección queda como "TODO".

**Nota de dependencia entre planes:** `SolicitudEventoResumen` se define una sola vez, en `data/dto/EventoDtos.kt` (Task 1), y lo importa tanto la sección Eventos como el bloque de administración (Task 5).
