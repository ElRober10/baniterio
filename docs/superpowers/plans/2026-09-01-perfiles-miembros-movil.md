# Perfiles de miembro — Plan C: Móvil

> **Ejecución:** el usuario ha pedido no usar subagentes. Se ejecuta en línea,
> tarea a tarea, con resumen y parada tras cada una. Tests reales de capa de
> datos con `:shared:testAndroidHostTest`; las pantallas Compose se verifican
> por compilación + humo manual (igual que el resto de `mobile/`, que no tiene
> tests de UI).
>
> **Ejecutado 2026-09-02.** Desviaciones respecto a este plan:
> - Coil fijado a **3.2.0** (no 3.6.0): la 3.6 exige compileSdk 37 y el proyecto
>   va por 36 con AGP 9.0.1.
> - Tareas de compilación reales del plugin `com.android.kotlin.multiplatform.library`:
>   `:shared:compileAndroidMain` (no `compileDebugKotlinAndroid`),
>   `:shared:compileKotlinIosSimulatorArm64`, `:androidApp:compileDebugKotlin`.
> - El editor y las tarjetas siguen la **forma de carta** que se rediseñó en la
>   web después del Plan B (composable `CaraDeCarta`, imagen 4:5 con marco
>   dorado); el selector de avatar va en un `Dialog`, no en línea.
> - `SelectorFoto`/`SelectorContacto` (interfaces + `expect`) se crean en Task 7/8
>   (no en Task 2); Task 2 solo deja `FotoElegida` en su propio fichero.

**Goal:** editor de perfil (avatar/foto, datos, pareja, hijos) obligatorio en el
primer login, y la sección Miembros con tarjetas, en la app Kotlin
Multiplatform + Compose, sobre la API del Plan A.

**Architecture:** capa de datos en `commonMain` con el patrón ya establecido
(`AdminRepositoryImpl`): un `PerfilRepository` habla con `/api/v1/perfil*` y
`/api/v1/miembros`, devuelve `ResultadoPerfil<T>` (Éxito / Error con código
traducido / SIN_CONEXION). Pantallas Compose en `commonMain` con el mismo estilo
que `AdminSolicitudesScreen` (estado en `sealed interface`, `remember`,
`rememberCoroutineScope`). Navegación por `Screen` (sealed class) en `App.kt`.
Selectores nativos (foto, contactos) solo en Android, vía un puente en
`MainActivity` con `startActivityForResult` clásico (16 bits). iOS: solo avatar
y teléfono a mano (foto y contactos aparcados con el trabajo de Mac).

**Tech Stack:** Kotlin 2.4, Compose Multiplatform 1.11, Ktor 3.5 client,
kotlinx.serialization, Coil 3 (nuevo) para imágenes de red. Tests: `kotlin.test`
+ `ktor-client-mock` + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-09-01-perfiles-miembros-design.md`

## Global Constraints

- Lógica en `commonMain`; `expect/actual` solo para lo específico de plataforma.
- Repositorios: interfaz + Impl, patrón de `AdminRepositoryImpl` —
  `private fun HttpRequestBuilder.auth()` con el Bearer de `SesionHolder`;
  helper `peticion { }` con `try` acotado (nunca `runCatching`), relanza
  `CancellationException`, traduce `ResponseException` leyendo `codigo` del
  cuerpo con `ErrorResponse`, cualquier otra excepción → `SIN_CONEXION`.
- DTOs `@Serializable` en `com.baniterio.app.data.dto`. Campos opcionales del
  backend con valor por defecto (`= null`) para que `ignoreUnknownKeys` +
  ausencia no rompan.
- Pantallas: estado en `sealed interface` local + `remember { mutableStateOf }`,
  acciones en `rememberCoroutineScope().launch`. Colores de `BaniterioColors`,
  cabecera con `BaniterioWordmark()`, "Volver" en `BaniterioColors.brandBright`,
  errores en `BaniterioColors.error`, botón primario `containerColor = brand` /
  `contentColor = gold`. Nada de `MaterialTheme` colores crudos salvo
  `onBackground`.
- `API_BASE_URL` **ya incluye** `/api/v1`. Las rutas de este plan se
  concatenan como `"$API_BASE_URL/perfil"`, etc. La URL de media que devuelve
  el backend es **relativa** (`/api/v1/media/...`): para pintarla hay que
  prefijar con el origen = `API_BASE_URL` sin el sufijo `/api/v1`.
- `MainActivity` es `FragmentActivity`: su validador de `requestCode` **rechaza
  los códigos de 32 bits** de `registerForActivityResult` /
  `rememberLauncherForActivityResult`. Todo resultado de Activity (foto,
  contacto) usa `startActivityForResult` + `onActivityResult` con
  `requestCode` de 16 bits. Códigos ya usados: `1001` (permiso notif). Nuevos:
  `1002` (foto), `1003` (contacto).
- Español en todo el texto de cara al usuario y en los identificadores de
  dominio.
- Nunca pedir ni mostrar `telefono`/`email` en las tarjetas de Miembros (el
  backend no los expone).

## Contrato del backend (Plan A, ya mergeado en esta rama)

```
GET    /api/v1/perfil                 → PerfilResponse            (siempre 200)
PUT    /api/v1/perfil                 → PerfilResponse            body GuardarPerfilRequest
POST   /api/v1/perfil/foto            → SubirFotoResponse         multipart, campo "archivo"
GET    /api/v1/perfil/avatares        → List<AvatarResumen>
POST   /api/v1/perfil/pareja/aceptar  → 204
POST   /api/v1/perfil/pareja/rechazar → 204
DELETE /api/v1/perfil/pareja          → 204
GET    /api/v1/miembros               → List<TarjetaMiembroResponse>   (ya ordenadas)
GET    /api/v1/media/avatares/{id}.png    (público, sin token)
GET    /api/v1/media/fotos/{uuid}.jpg     (público, sin token)
```

Formas JSON (nombres exactos del backend):

```
PerfilResponse {
  usuarioId: Long, nombre: String, apellidos: String, mote: String?,
  sobreMi: String?, imagenTipo: String?  // "FOTO" | "AVATAR" | null
  imagenRef: String?, imagenUrl: String?, completado: Boolean,
  pareja: ParejaEnPerfil?, hijos: HijoEnPerfil[], vinculoPendiente: VinculoPendiente?
}
ParejaEnPerfil { vinculoId: Long, nombre: String, telefono: String, estado: String }
   // estado ∈ SIN_CUENTA | PENDIENTE | ACEPTADO | RECHAZADO
HijoEnPerfil { id: Long, nombre: String, mayorDeEdad: Boolean, telefono: String?,
               visible: Boolean, registrado: Boolean }
VinculoPendiente { vinculoId: Long, solicitanteNombre: String }

GuardarPerfilRequest {
  nombre: String, apellidos: String, mote: String?, sobreMi: String?,
  imagenTipo: String,  // "FOTO" | "AVATAR"   (enum ImagenPerfil en el backend)
  imagenRef: String, tienePareja: Boolean,
  parejaNombre: String?, parejaTelefono: String?, hijos: HijoRequest[]
}
HijoRequest { id: Long?, nombre: String, mayorDeEdad: Boolean, telefono: String?, visible: Boolean }
AvatarResumen { id: String, genero: String }   // "CHICO" | "CHICA"
SubirFotoResponse { imagenRef: String }
TarjetaMiembroResponse {
  id: Long, nombre: String, apellidos: String, mote: String?, sobreMi: String?,
  imagenUrl: String?, parejaNombre: String?, hijos: String[]
}
```

Códigos de error (cuerpo `{ "codigo": "..." }`):
`AVATAR_INEXISTENTE`(400), `IMAGEN_REF_INVALIDA`(400), `IMAGEN_NO_SOPORTADA`(415),
`IMAGEN_DEMASIADO_GRANDE`(413), `VINCULO_NO_ENCONTRADO`(404),
`TELEFONO_YA_EMPAREJADO`(409), `YA_TIENE_PAREJA`(409),
`TELEFONO_PAREJA_INVALIDO`(400), `NOMBRE_PAREJA_REQUERIDO`(400),
`TELEFONO_HIJO_INVALIDO`(400), `VALIDACION`(400).

**Landmines de backend a respetar (verificadas en el Plan B leyendo el código):**
- `PUT /perfil` con `tienePareja=false` cuando el vínculo estaba `ACEPTADO`
  **rompe la pareja de verdad**. `tienePareja` se inicializa siempre con
  `pareja != null` del `GET` y solo cambia si el usuario toca el control.
- Con `pareja.estado == "ACEPTADO"`, cambiar `parejaTelefono`/`parejaNombre` en
  el `PUT` **rompe el vínculo y declara uno nuevo**. Por eso, en `ACEPTADO` el
  editor NO muestra campos editables de pareja: enseña "Tu pareja: X" +
  "Romper vínculo" (`DELETE /perfil/pareja`) y el `PUT` reenvía tal cual los
  valores que trajo el `GET`.
- `pareja.estado` solo vale `SIN_CUENTA`/`PENDIENTE` cuando el usuario es el
  solicitante → en esos estados sí es seguro editar nombre/teléfono.
- Si el usuario es el lado que aceptó, el backend ignora los campos de pareja
  del `PUT`; da igual reenviarlos.
- `PerfilResponse.hijos` ya excluye los hijos registrados: cada fila es
  plenamente editable/borrable, sin lógica de "hijo protegido".

## File Structure

**Nuevos — `commonMain`**
- `data/NormalizadorTelefono.kt` — `normalizarTelefonoEs(String): String?`
- `data/dto/PerfilDtos.kt` — DTOs de arriba
- `data/ResultadoPerfil.kt` — `ResultadoPerfil<T>` + `CodigoErrorPerfil`
- `data/PerfilRepository.kt` — interfaz
- `data/PerfilRepositoryImpl.kt` — implementación
- `data/Media.kt` — `urlMedia(imagenUrl: String?): String?`
- `data/SelectorFoto.kt` — `FotoElegida`, `SelectorFoto`, `rememberSelectorFoto()` (expect)
- `data/SelectorContacto.kt` — `SelectorContacto`, `rememberSelectorContacto()` (expect)
- `ui/comun/ImagenCircular.kt` — `@Composable fun ImagenCircular(url, tamano, iniciales)`
- `ui/miembros/CargandoSesionScreen.kt` — puerta de perfil incompleto
- `ui/miembros/SelectorAvatar.kt`
- `ui/miembros/EditorPerfilScreen.kt`
- `ui/miembros/MiembrosScreen.kt` (incluye `TarjetaMiembro` privado)

**Nuevos — `androidMain`**
- `data/PuenteNativo.kt` — `object` que conecta MainActivity ↔ composables
- `data/SelectorFoto.android.kt` — actual
- `data/SelectorContacto.android.kt` — actual

**Nuevos — `iosMain`**
- `data/SelectorFoto.ios.kt` — actual: `disponible = false`
- `data/SelectorContacto.ios.kt` — actual: `disponible = false`

**Nuevos — `commonTest`**
- `data/NormalizadorTelefonoTest.kt`
- `data/PerfilRepositoryImplTest.kt`

**Modificados**
- `gradle/libs.versions.toml` — Coil 3
- `shared/build.gradle.kts` — Coil 3 en `commonMain`
- `data/Dependencias.kt` — `perfilRepo`
- `nav/Screen.kt` — `CargandoSesion`, `Miembros`, `EditorPerfil`
- `App.kt` — claves, guardia de arranque, ramas nuevas, Login/Desbloqueo →
  `CargandoSesion`, `setSingletonImageLoaderFactory`
- `model/Seccion.kt` — `Miembros` con `destino = Screen.Miembros`
- `androidApp/.../MainActivity.kt` — puente + `onActivityResult` + RC nuevos

---

## Task 1 — Normalizador de teléfono (commonMain)

**Files:** crear `data/NormalizadorTelefono.kt`, `commonTest/.../data/NormalizadorTelefonoTest.kt`

**Interfaces — Produces:** `fun normalizarTelefonoEs(entrada: String): String?`

- [ ] **Paso 1: test que falla** — `NormalizadorTelefonoTest.kt`:

```kotlin
package com.baniterio.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NormalizadorTelefonoTest {
    @Test fun acepta_movil_de_9_digitos() = assertEquals("611223344", normalizarTelefonoEs("611223344"))
    @Test fun quita_prefijo_mas_34() = assertEquals("611223344", normalizarTelefonoEs("+34611223344"))
    @Test fun quita_prefijo_0034() = assertEquals("711223344", normalizarTelefonoEs("0034711223344"))
    @Test fun quita_espacios_guiones_y_parentesis() =
        assertEquals("611223344", normalizarTelefonoEs(" (611) 22-33-44 "))
    @Test fun rechaza_si_no_empieza_por_6_o_7() = assertNull(normalizarTelefonoEs("911223344"))
    @Test fun rechaza_longitud_incorrecta() = assertNull(normalizarTelefonoEs("61122334"))
    @Test fun rechaza_texto() = assertNull(normalizarTelefonoEs("no soy un telefono"))
}
```

- [ ] **Paso 2: correr y ver fallar** — `./gradlew :shared:testAndroidHostTest --tests "com.baniterio.app.data.NormalizadorTelefonoTest"` → FAIL (no compila, función inexistente).

- [ ] **Paso 3: implementar** — `data/NormalizadorTelefono.kt`:

```kotlin
package com.baniterio.app.data

/**
 * Normaliza un móvil español al formato de 9 dígitos que espera el backend
 * (`^[67]\d{8}$`). Quita espacios, guiones y paréntesis, y un prefijo `+34` o
 * `0034`. Devuelve `null` si tras limpiar no es un móvil válido: el campo
 * conserva lo tecleado y la pantalla muestra el error. El backend valida igual
 * (fuente de verdad) y devuelve 400 por campo.
 */
fun normalizarTelefonoEs(entrada: String): String? {
    val limpio = entrada.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
    val sinPrefijo = limpio.removePrefix("+34").removePrefix("0034")
    return if (Regex("^[67]\\d{8}$").matches(sinPrefijo)) sinPrefijo else null
}
```

- [ ] **Paso 4: correr y ver pasar** — mismo comando → PASS.
- [ ] **Paso 5: commit** — `feat(movil): normalizador de teléfono español`

---

## Task 2 — Capa de datos del perfil (commonMain)

**Files:** crear `data/dto/PerfilDtos.kt`, `data/ResultadoPerfil.kt`,
`data/PerfilRepository.kt`, `data/PerfilRepositoryImpl.kt`, `data/Media.kt`,
`data/SelectorFoto.kt` (solo el tipo `FotoElegida` + la interfaz + `expect`);
modificar `data/Dependencias.kt`; crear
`commonTest/.../data/PerfilRepositoryImplTest.kt`

**Interfaces — Produces:**
- `PerfilRepository` con: `miPerfil()`, `guardar(GuardarPerfilRequest)`,
  `subirFoto(FotoElegida)`, `avatares()`, `aceptarPareja()`, `rechazarPareja()`,
  `romperPareja()`, `miembros()` — todas `suspend`, devuelven
  `ResultadoPerfil<...>`.
- `data class FotoElegida(val bytes: ByteArray, val nombre: String, val tipoMime: String)`
- `fun urlMedia(imagenUrl: String?): String?`
- `Dependencias.perfilRepo: PerfilRepository`

- [ ] **Paso 1: DTOs** — `data/dto/PerfilDtos.kt`:

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PerfilResponse(
    val usuarioId: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val sobreMi: String? = null,
    val imagenTipo: String? = null,
    val imagenRef: String? = null,
    val imagenUrl: String? = null,
    val completado: Boolean,
    val pareja: ParejaEnPerfil? = null,
    val hijos: List<HijoEnPerfil> = emptyList(),
    val vinculoPendiente: VinculoPendiente? = null,
)

@Serializable
data class ParejaEnPerfil(
    val vinculoId: Long,
    val nombre: String,
    val telefono: String,
    val estado: String,
)

@Serializable
data class HijoEnPerfil(
    val id: Long,
    val nombre: String,
    val mayorDeEdad: Boolean,
    val telefono: String? = null,
    val visible: Boolean,
    val registrado: Boolean,
)

@Serializable
data class VinculoPendiente(val vinculoId: Long, val solicitanteNombre: String)

@Serializable
data class GuardarPerfilRequest(
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val sobreMi: String? = null,
    val imagenTipo: String,
    val imagenRef: String,
    val tienePareja: Boolean,
    val parejaNombre: String? = null,
    val parejaTelefono: String? = null,
    val hijos: List<HijoRequest> = emptyList(),
)

@Serializable
data class HijoRequest(
    val id: Long? = null,
    val nombre: String,
    val mayorDeEdad: Boolean,
    val telefono: String? = null,
    val visible: Boolean,
)

@Serializable
data class AvatarResumen(val id: String, val genero: String)

@Serializable
data class SubirFotoResponse(val imagenRef: String)

@Serializable
data class TarjetaMiembroResponse(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val sobreMi: String? = null,
    val imagenUrl: String? = null,
    val parejaNombre: String? = null,
    val hijos: List<String> = emptyList(),
)
```

- [ ] **Paso 2: FotoElegida + interfaces de selector** — `data/SelectorFoto.kt`
  (solo lo que la capa de datos necesita en esta tarea; el `expect fun` y el
  `actual` los completa Task 7, pero declararlos ahora deja el fichero cerrado):

```kotlin
package com.baniterio.app.data

import androidx.compose.runtime.Composable

/** Una foto elegida en el dispositivo, lista para subir a `POST /perfil/foto`. */
data class FotoElegida(val bytes: ByteArray, val nombre: String, val tipoMime: String) {
    // ByteArray no tiene equals/hashCode por contenido; se generan para que
    // las comparaciones en tests y en `remember` funcionen como se espera.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FotoElegida) return false
        return bytes.contentEquals(other.bytes) && nombre == other.nombre && tipoMime == other.tipoMime
    }
    override fun hashCode(): Int =
        bytes.contentHashCode() * 31 * 31 + nombre.hashCode() * 31 + tipoMime.hashCode()
}

/**
 * Selector de foto del dispositivo. Solo disponible en Android (el de iOS
 * —PHPicker— está aparcado con el trabajo de Mac). Cuando `disponible` es
 * `false` el editor no ofrece "Subir foto", solo avatar.
 */
interface SelectorFoto {
    val disponible: Boolean
    /** Abre el selector; llama a [onFoto] con la elección, o con `null` si se cancela. */
    fun elegir(onFoto: (FotoElegida?) -> Unit)
}

@Composable
expect fun rememberSelectorFoto(): SelectorFoto
```

  Y su gemelo `data/SelectorContacto.kt`:

```kotlin
package com.baniterio.app.data

import androidx.compose.runtime.Composable

/**
 * Selector de contacto del dispositivo para rellenar un teléfono de pareja o de
 * un hijo. Solo Android (`ACTION_PICK` sobre la agenda, sin permiso). En iOS
 * `disponible` es `false` y el campo se rellena a mano.
 */
interface SelectorContacto {
    val disponible: Boolean
    /** Abre la agenda; llama a [onTelefono] con el número elegido (sin normalizar), o `null`. */
    fun elegir(onTelefono: (String?) -> Unit)
}

@Composable
expect fun rememberSelectorContacto(): SelectorContacto
```

  > Nota para el ejecutor: con `expect` sin `actual` en Android/iOS el módulo NO
  > compila. Task 2 crea los `actual` **mínimos** ya (un `object` que devuelve
  > `disponible = false` y no hace nada) en `androidMain` e `iosMain`, y Task 7 /
  > Task 8 los sustituyen por la implementación real de Android. Alternativa
  > admitida: mover estos dos ficheros enteros a Task 7/8. Si el ejecutor lo
  > prefiere, que declare aquí solo `FotoElegida` y deje `SelectorFoto` /
  > `SelectorContacto` para más adelante. **Ruling:** dejar `FotoElegida` en
  > Task 2 (la usa el repo) y mover las dos interfaces `Selector*` + sus
  > `expect/actual` a Task 7/8. Actualizar el import del repo en consecuencia
  > (`subirFoto(FotoElegida)` no necesita la interfaz).

- [ ] **Paso 3: ResultadoPerfil** — `data/ResultadoPerfil.kt`, calcado de
  `ResultadoAdmin.kt`:

```kotlin
package com.baniterio.app.data

sealed class ResultadoPerfil<out T> {
    data class Exito<T>(val dato: T) : ResultadoPerfil<T>()
    data class Error(val codigo: CodigoErrorPerfil, val mensaje: String) : ResultadoPerfil<Nothing>()
}

enum class CodigoErrorPerfil {
    AVATAR_INEXISTENTE,
    IMAGEN_REF_INVALIDA,
    IMAGEN_NO_SOPORTADA,
    IMAGEN_DEMASIADO_GRANDE,
    VINCULO_NO_ENCONTRADO,
    TELEFONO_YA_EMPAREJADO,
    YA_TIENE_PAREJA,
    TELEFONO_PAREJA_INVALIDO,
    NOMBRE_PAREJA_REQUERIDO,
    TELEFONO_HIJO_INVALIDO,
    VALIDACION,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorPerfil = when (codigo) {
            "AVATAR_INEXISTENTE" -> AVATAR_INEXISTENTE
            "IMAGEN_REF_INVALIDA" -> IMAGEN_REF_INVALIDA
            "IMAGEN_NO_SOPORTADA" -> IMAGEN_NO_SOPORTADA
            "IMAGEN_DEMASIADO_GRANDE" -> IMAGEN_DEMASIADO_GRANDE
            "VINCULO_NO_ENCONTRADO" -> VINCULO_NO_ENCONTRADO
            "TELEFONO_YA_EMPAREJADO" -> TELEFONO_YA_EMPAREJADO
            "YA_TIENE_PAREJA" -> YA_TIENE_PAREJA
            "TELEFONO_PAREJA_INVALIDO" -> TELEFONO_PAREJA_INVALIDO
            "NOMBRE_PAREJA_REQUERIDO" -> NOMBRE_PAREJA_REQUERIDO
            "TELEFONO_HIJO_INVALIDO" -> TELEFONO_HIJO_INVALIDO
            "VALIDACION" -> VALIDACION
            else -> DESCONOCIDO
        }
    }

    val mensaje: String
        get() = when (this) {
            AVATAR_INEXISTENTE, IMAGEN_REF_INVALIDA -> "Elige una foto o un avatar válido."
            IMAGEN_NO_SOPORTADA -> "Ese archivo no es una foto válida (usa JPEG o PNG)."
            IMAGEN_DEMASIADO_GRANDE -> "La foto pesa demasiado."
            VINCULO_NO_ENCONTRADO -> "Ese vínculo de pareja ya no está."
            TELEFONO_YA_EMPAREJADO -> "Ese teléfono ya tiene pareja en la peña."
            YA_TIENE_PAREJA -> "Ya tienes un vínculo de pareja activo."
            TELEFONO_PAREJA_INVALIDO -> "Revisa el teléfono de tu pareja: no parece un móvil español."
            NOMBRE_PAREJA_REQUERIDO -> "Escribe el nombre de tu pareja."
            TELEFONO_HIJO_INVALIDO -> "Revisa el teléfono de tu hijo: no parece un móvil español."
            VALIDACION -> "Revisa los datos del formulario."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo."
        }
}
```

- [ ] **Paso 4: interfaz** — `data/PerfilRepository.kt`:

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.dto.GuardarPerfilRequest
import com.baniterio.app.data.dto.PerfilResponse
import com.baniterio.app.data.dto.TarjetaMiembroResponse

/**
 * Editor de perfil del miembro y sección Miembros (rutas bajo `/api/v1/perfil`
 * y `/api/v1/miembros`). Misma mecánica que [AdminRepository]: Bearer de
 * [SesionHolder], nunca lanza por errores HTTP esperados, relanza
 * `CancellationException`.
 */
interface PerfilRepository {
    suspend fun miPerfil(): ResultadoPerfil<PerfilResponse>
    suspend fun guardar(req: GuardarPerfilRequest): ResultadoPerfil<PerfilResponse>
    suspend fun subirFoto(foto: FotoElegida): ResultadoPerfil<String>  // devuelve imagenRef
    suspend fun avatares(): ResultadoPerfil<List<AvatarResumen>>
    suspend fun aceptarPareja(): ResultadoPerfil<Unit>
    suspend fun rechazarPareja(): ResultadoPerfil<Unit>
    suspend fun romperPareja(): ResultadoPerfil<Unit>
    suspend fun miembros(): ResultadoPerfil<List<TarjetaMiembroResponse>>
}
```

- [ ] **Paso 5: implementación** — `data/PerfilRepositoryImpl.kt`, calcada de
  `AdminRepositoryImpl` (mismo `auth()`, mismo `peticion { }`):

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.GuardarPerfilRequest
import com.baniterio.app.data.dto.PerfilResponse
import com.baniterio.app.data.dto.SubirFotoResponse
import com.baniterio.app.data.dto.TarjetaMiembroResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

class PerfilRepositoryImpl(
    private val http: HttpClient,
    private val sesion: SesionHolder,
) : PerfilRepository {

    private fun HttpRequestBuilder.auth() {
        sesion.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun miPerfil(): ResultadoPerfil<PerfilResponse> = peticion {
        http.get("$API_BASE_URL/perfil") { auth() }.body()
    }

    override suspend fun guardar(req: GuardarPerfilRequest): ResultadoPerfil<PerfilResponse> = peticion {
        http.put("$API_BASE_URL/perfil") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    override suspend fun subirFoto(foto: FotoElegida): ResultadoPerfil<String> = peticion {
        http.submitFormWithBinaryData(
            url = "$API_BASE_URL/perfil/foto",
            formData = formData {
                append("archivo", foto.bytes, Headers.build {
                    append(HttpHeaders.ContentType, foto.tipoMime)
                    append(HttpHeaders.ContentDisposition, "filename=\"${foto.nombre}\"")
                })
            },
        ) { auth() }.body<SubirFotoResponse>().imagenRef
    }

    override suspend fun avatares(): ResultadoPerfil<List<AvatarResumen>> = peticion {
        http.get("$API_BASE_URL/perfil/avatares") { auth() }.body()
    }

    override suspend fun aceptarPareja(): ResultadoPerfil<Unit> = peticion {
        http.post("$API_BASE_URL/perfil/pareja/aceptar") { auth() }.bodyAsText().let { }
    }

    override suspend fun rechazarPareja(): ResultadoPerfil<Unit> = peticion {
        http.post("$API_BASE_URL/perfil/pareja/rechazar") { auth() }.bodyAsText().let { }
    }

    override suspend fun romperPareja(): ResultadoPerfil<Unit> = peticion {
        http.delete("$API_BASE_URL/perfil/pareja") { auth() }.bodyAsText().let { }
    }

    override suspend fun miembros(): ResultadoPerfil<List<TarjetaMiembroResponse>> = peticion {
        http.get("$API_BASE_URL/miembros") { auth() }.body()
    }

    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoPerfil<T> =
        try {
            ResultadoPerfil.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = try {
                e.response.body<ErrorResponse>().codigo
            } catch (ce: CancellationException) {
                throw ce
            } catch (ignorada: Exception) {
                null
            }
            val cod = CodigoErrorPerfil.deCodigoBackend(codigo)
            ResultadoPerfil.Error(cod, cod.mensaje)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoPerfil.Error(CodigoErrorPerfil.SIN_CONEXION, CodigoErrorPerfil.SIN_CONEXION.mensaje)
        }
}
```

- [ ] **Paso 6: urlMedia** — `data/Media.kt`:

```kotlin
package com.baniterio.app.data

/**
 * Convierte la URL relativa de media que devuelve el backend
 * (`/api/v1/media/...`) en una absoluta que Coil pueda cargar. El origen sale de
 * [API_BASE_URL] quitándole el sufijo `/api/v1`. `null` → `null`.
 */
fun urlMedia(imagenUrl: String?): String? {
    if (imagenUrl == null) return null
    val origen = API_BASE_URL.removeSuffix("/api/v1")
    return origen + imagenUrl
}
```

- [ ] **Paso 7: wire Dependencias** — en `data/Dependencias.kt`:
  - añadir `val perfilRepo: PerfilRepository` al constructor de `Dependencias`
    (tras `dispositivoRepo`).
  - en `crearDependencias`, `perfilRepo = PerfilRepositoryImpl(http, sesion)`.

- [ ] **Paso 8: test** — `commonTest/.../data/PerfilRepositoryImplTest.kt`,
  patrón de `DispositivoRepositoryImplTest` (helper `repo()` con `MockEngine`
  que registra método+ruta, cabecera Auth y cuerpo; `SesionHolder` con
  `token = "jwt-x"`). Casos:
  1. `miPerfil` → `GET`, ruta acaba en `/perfil`, `Bearer jwt-x`; responde un
     JSON de `PerfilResponse` mínimo (`{"usuarioId":1,"nombre":"A","apellidos":"B","completado":false}`)
     y se comprueba que deserializa (`dato.completado == false`).
  2. `guardar` → `PUT` a `/perfil`, cuerpo contiene `"imagenTipo":"AVATAR"` y
     `"tienePareja":false`.
  3. `subirFoto` → `POST` a `/perfil/foto`, `Content-Type` de la petición
     empieza por `multipart/form-data`; responde `{"imagenRef":"x.jpg"}` y el
     método devuelve `"x.jpg"`.
  4. `avatares` → `GET` a `/perfil/avatares`; responde `[{"id":"01_chico","genero":"CHICO"}]`,
     el método devuelve lista de tamaño 1.
  5. `aceptarPareja` / `rechazarPareja` → `POST` a las rutas correctas.
  6. `romperPareja` → `DELETE` a `/perfil/pareja`.
  7. `miembros` → `GET` a `/miembros`.
  8. Un 409 con cuerpo `{"codigo":"TELEFONO_YA_EMPAREJADO"}` en `guardar` →
     `ResultadoPerfil.Error` con `codigo == TELEFONO_YA_EMPAREJADO`.
  9. Un fallo de red (motor que lanza) → `Error` con `SIN_CONEXION`.
  10. `urlMedia`: `null` → `null`; `"/api/v1/media/fotos/x.jpg"` → acaba en
      `"/api/v1/media/fotos/x.jpg"` y **no** contiene `"/api/v1/api/v1"`.

- [ ] **Paso 9: correr** — `./gradlew :shared:testAndroidHostTest` → PASS
  (todos, incluidos los de Task 1 y los ya existentes).
- [ ] **Paso 10: commit** — `feat(movil): PerfilRepository, DTOs y normalizador de media`

---

## Task 3 — Coil 3 + imagen circular

**Files:** modificar `gradle/libs.versions.toml`, `shared/build.gradle.kts`;
crear `ui/comun/ImagenCircular.kt`

**Interfaces — Produces:**
`@Composable fun ImagenCircular(url: String?, tamano: Dp, iniciales: String, modifier: Modifier = Modifier)`

- [ ] **Paso 1: versión** — en `libs.versions.toml`, sección `[versions]`:
  `coil = "3.2.0"`. En `[libraries]`:

```toml
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
```

- [ ] **Paso 2: dependencia** — en `shared/build.gradle.kts`, dentro de
  `commonMain.dependencies { ... }`:

```kotlin
implementation(libs.coil.compose)
implementation(libs.coil.network.ktor)
```

- [ ] **Paso 3: ImageLoader con red** — Coil 3 no trae fetcher de red por
  defecto. En `App.kt` (Task 4 lo integra; aquí solo se deja escrito el helper),
  se registrará con `setSingletonImageLoaderFactory`. Para esta tarea basta el
  composable; la fábrica se añade en Task 4. Crear `ui/comun/ImagenCircular.kt`:

```kotlin
package com.baniterio.app.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.baniterio.app.theme.BaniterioColors

/**
 * Foto o avatar de un miembro, recortado en círculo. Si [url] es `null` o la
 * carga falla, pinta un círculo con las [iniciales]. [url] ya debe venir
 * absoluta (ver `urlMedia`).
 */
@Composable
fun ImagenCircular(url: String?, tamano: Dp, iniciales: String, modifier: Modifier = Modifier) {
    val base = modifier.size(tamano).clip(CircleShape).background(BaniterioColors.brandDark)
    if (url == null) {
        Box(base, contentAlignment = Alignment.Center) {
            Text(iniciales, color = BaniterioColors.gold, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    }
}
```

  > `BaniterioColors.brandDark` no existe hoy en `theme/Color.kt` (sí en el
  > front). **Ruling:** añadir `val brandDark = Color(0xFF241E73)` a
  > `BaniterioColors` en esta tarea (mismo valor que el token web
  > `--color-brand-dark`), para el hueco de la imagen.

- [ ] **Paso 4: compila** — `./gradlew :shared:compileDebugKotlinAndroid` → OK
  (resuelve Coil, `AsyncImage` importa). Si el nombre del artefacto
  `coil-network-ktor3` no resuelve, probar `coil-network-ktor` (Coil 3.x
  renombró el módulo entre versiones; usar el que exista para `3.2.0` según el
  índice de Maven Central; ambos alias apuntan al mismo `version.ref`).
- [ ] **Paso 5: commit** — `feat(movil): Coil 3 e imagen circular de miembro`

---

## Task 4 — Navegación + puerta de perfil incompleto

**Files:** modificar `nav/Screen.kt`, `App.kt`, `model/Seccion.kt`; crear
`ui/miembros/CargandoSesionScreen.kt`

**Interfaces — Consumes:** `Dependencias.perfilRepo` (T2), `ImagenCircular` no
aún. **Produces:** `Screen.CargandoSesion`, `Screen.Miembros`,
`Screen.EditorPerfil`; `App` enruta a `CargandoSesion` tras login/desbloqueo.

- [ ] **Paso 1: Screen** — en `nav/Screen.kt` añadir tres `data object`:
  `CargandoSesion`, `Miembros`, `EditorPerfil`.

- [ ] **Paso 2: CargandoSesionScreen** — `ui/miembros/CargandoSesionScreen.kt`.
  Puerta entre "sesión iniciada" y "panel": pide `GET /perfil` (que además
  dispara en el backend la reconciliación `SIN_CUENTA → PENDIENTE` si esta
  persona acaba de registrarse con un teléfono que alguien declaró como pareja).

```kotlin
package com.baniterio.app.ui.miembros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.PerfilRepository
import com.baniterio.app.data.ResultadoPerfil
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.theme.BaniterioWordmark

/**
 * Entre login/desbloqueo y el panel. Pide el perfil: si está completo entra al
 * panel; si no, manda al editor en modo obligatorio. Un fallo de red muestra
 * reintento (no se deja pasar a ciegas: sin saber si el perfil está completo el
 * panel podría no tener sentido).
 */
@Composable
fun CargandoSesionScreen(
    perfilRepo: PerfilRepository,
    onPerfilCompleto: () -> Unit,
    onPerfilIncompleto: () -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableStateOf(0) }

    LaunchedEffect(intento) {
        error = null
        when (val r = perfilRepo.miPerfil()) {
            is ResultadoPerfil.Exito -> if (r.dato.completado) onPerfilCompleto() else onPerfilIncompleto()
            is ResultadoPerfil.Error -> error = r.mensaje
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BaniterioWordmark()
        Spacer(Modifier.height(16.dp))
        val e = error
        if (e == null) {
            Text("Cargando tu perfil…", color = BaniterioColors.muted)
        } else {
            Text(e, color = BaniterioColors.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Reintentar",
                color = BaniterioColors.brandBright,
                modifier = Modifier.clickable { intento++ },
            )
        }
    }
}
```

- [ ] **Paso 3: App.kt — claves** — añadir constantes y ramas en `aClave` /
  `claveAScreen`:
  `CLAVE_CARGANDO_SESION = "CargandoSesion"`, `CLAVE_MIEMBROS = "Miembros"`,
  `CLAVE_EDITOR_PERFIL = "EditorPerfil"`. Mapear en ambas funciones.

- [ ] **Paso 4: App.kt — guardia de arranque en frío** — añadir
  `Screen.CargandoSesion`, `Screen.Miembros`, `Screen.EditorPerfil` a la lista
  de pantallas del `LaunchedEffect(Unit)` que redirige a Desbloqueo/Login
  cuando `deps.repo.usuarioActual == null`.

- [ ] **Paso 5: App.kt — login/desbloqueo → CargandoSesion** — cambiar los
  callbacks:
  - `DesbloqueoScreen(... onDesbloqueado = { alIniciarSesion(); ir(Screen.CargandoSesion) } ...)`
  - `LoginScreen(... onLoginSuccess = { alIniciarSesion(); ir(Screen.CargandoSesion) } ...)`

- [ ] **Paso 6: App.kt — estado del editor** — junto a `datosSolicitud`:
  `var editorObligatorio by rememberSaveable { mutableStateOf(false) }`.

- [ ] **Paso 7: App.kt — ramas nuevas del `when (screen)`**:

```kotlin
is Screen.CargandoSesion -> CargandoSesionScreen(
    perfilRepo = deps.perfilRepo,
    onPerfilCompleto = { ir(Screen.Panel) },
    onPerfilIncompleto = { editorObligatorio = true; ir(Screen.EditorPerfil) },
)
is Screen.EditorPerfil -> {
    // En modo obligatorio no hay "atrás": el usuario tiene que completar el perfil.
    if (!editorObligatorio) BackHandler { ir(Screen.Miembros) }
    EditorPerfilScreen(
        perfilRepo = deps.perfilRepo,
        obligatorio = editorObligatorio,
        onGuardado = {
            val iba = editorObligatorio
            editorObligatorio = false
            ir(if (iba) Screen.Panel else Screen.Miembros)
        },
        onVolver = { editorObligatorio = false; ir(Screen.Miembros) },
    )
}
is Screen.Miembros -> {
    BackHandler { ir(Screen.Panel) }
    MiembrosScreen(
        perfilRepo = deps.perfilRepo,
        miId = deps.repo.usuarioActual?.id,
        onEditar = { editorObligatorio = false; ir(Screen.EditorPerfil) },
        onVolver = { ir(Screen.Panel) },
    )
}
```

  > `EditorPerfilScreen` y `MiembrosScreen` no existen hasta Task 5/6. Para que
  > Task 4 compile por sí sola: crear **stubs** de ambos composables con la firma
  > exacta de arriba y un `Text("…")` de cuerpo, y sustituirlos en Task 5/6.
  > **Ruling:** stubs en Task 4. Cada stub, 6 líneas, se reemplaza entero
  > después; el commit de Task 4 deja el árbol compilando.

- [ ] **Paso 8: App.kt — ImageLoader de Coil con red** — dentro de
  `BaniterioTheme { ... }`, antes del `Surface`, registrar la fábrica una vez:

```kotlin
setSingletonImageLoaderFactory { ctx ->
    ImageLoader.Builder(ctx)
        .components { add(KtorNetworkFetcherFactory()) }
        .build()
}
```

  Imports: `coil3.compose.setSingletonImageLoaderFactory`, `coil3.ImageLoader`,
  `coil3.network.ktor3.KtorNetworkFetcherFactory` (o `coil3.network.ktor.*`
  según el artefacto que resolviera en Task 3).

- [ ] **Paso 9: Seccion.kt** — cambiar
  `add(Seccion("Miembros", "Socios de la peña y sus datos de contacto."))`
  por
  `add(Seccion("Miembros", "Las tarjetas de los socios de la peña.", destino = Screen.Miembros))`.

- [ ] **Paso 10: compila** — `./gradlew :shared:compileDebugKotlinAndroid` y
  `./gradlew :shared:compileKotlinIosSimulatorArm64` → OK.
- [ ] **Paso 11: commit** — `feat(movil): navegación de Miembros + puerta de perfil incompleto`

---

## Task 5 — SelectorAvatar + EditorPerfilScreen

**Files:** crear `ui/miembros/SelectorAvatar.kt`; sustituir el stub
`ui/miembros/EditorPerfilScreen.kt`

**Interfaces — Consumes:** `PerfilRepository`, `ImagenCircular`,
`normalizarTelefonoEs`, `urlMedia`, DTOs. **Produces:**
`@Composable fun EditorPerfilScreen(perfilRepo, obligatorio: Boolean, onGuardado: () -> Unit, onVolver: () -> Unit)`

- [ ] **Paso 1: SelectorAvatar** — `ui/miembros/SelectorAvatar.kt`. Rejilla con
  filtro. Usa `FlowRow` (de `androidx.compose.foundation.layout`,
  `@OptIn(ExperimentalLayoutApi::class)`) o `LazyVerticalGrid`. La URL de cada
  avatar: `urlMedia("/api/v1/media/avatares/$id.png")`.

```kotlin
package com.baniterio.app.ui.miembros

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.urlMedia
import com.baniterio.app.theme.BaniterioColors
import com.baniterio.app.ui.comun.ImagenCircular

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorAvatar(
    avatares: List<AvatarResumen>,
    seleccionado: String?,
    onElegir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtro by remember { mutableStateOf("TODOS") } // TODOS | CHICO | CHICA
    val visibles = if (filtro == "TODOS") avatares else avatares.filter { it.genero == filtro }

    androidx.compose.foundation.layout.Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TODOS" to "Todos", "CHICO" to "Chicos", "CHICA" to "Chicas").forEach { (clave, etiqueta) ->
                FilterChip(selected = filtro == clave, onClick = { filtro = clave }, label = { Text(etiqueta) })
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visibles.forEach { a ->
                val elegido = a.id == seleccionado
                ImagenCircular(
                    url = urlMedia("/api/v1/media/avatares/${a.id}.png"),
                    tamano = 64.dp,
                    iniciales = "",
                    modifier = Modifier
                        .then(if (elegido) Modifier.border(2.dp, BaniterioColors.gold, CircleShape) else Modifier)
                        .clickable { onElegir(a.id) },
                )
            }
        }
    }
}
```

- [ ] **Paso 2: EditorPerfilScreen — esqueleto y carga**. Estado con
  `sealed interface EstadoEditor { Cargando; Listo; Guardando; Error(msg) }`.
  Al entrar, `coroutineScope`:
  - `perfilRepo.miPerfil()` y `perfilRepo.avatares()` (secuencial está bien; si
    falla cualquiera → `Error`).
  - precargar estado desde `PerfilResponse` (ver Paso 3).
  Campos como `var` sueltos con `remember { mutableStateOf(...) }` /
  `mutableStateListOf` para hijos (patrón `RegistroScreen`, **no** Reactive
  Forms — eso es del front web).

- [ ] **Paso 3: precarga y landmines de pareja**. Estado:
  `nombre, apellidos, mote, sobreMi: String`;
  `imagenRefAvatar: String?` (de `imagenTipo=="AVATAR"` ? `imagenRef` : null);
  `tienePareja: Boolean` = `perfil.pareja != null`;
  `parejaEstado: String?` = `perfil.pareja?.estado`;
  `parejaNombre, parejaTelefono: String` = de `perfil.pareja` o "";
  `hijos = mutableStateListOf<FilaHijo>()` con
  `data class FilaHijo(val id: Long?, var nombre: String, var mayorDeEdad: Boolean, var telefono: String, var visible: Boolean)`
  — como `FilaHijo` lleva `var`, cada fila se edita in situ y la lista se
  recompone; si diera problemas de recomposición, cambiar a
  `mutableStateListOf` de estados independientes por campo. **Ruling:** empezar
  con `FilaHijo` de `var`s + `key` por índice; si el ejecutor ve que un
  `OutlinedTextField` no refleja los cambios, envolver cada fila en su propio
  `remember`.
  - Si `parejaEstado == "ACEPTADO"`: NO se pintan `OutlinedTextField` de pareja.
    Se muestra `Text("Tu pareja: $parejaNombre")` + botón "Romper vínculo". El
    `PUT` reenvía `parejaNombre`/`parejaTelefono` **tal cual** los trajo el
    `GET` (guardados en el estado, nunca editados).
  - Si `parejaEstado` es `null`, `SIN_CUENTA` o `PENDIENTE`: si `tienePareja`,
    se pintan los dos `OutlinedTextField` editables + el aviso.

- [ ] **Paso 4: imagen**. Por ahora **solo avatar** (Task 7 añade foto). Mostrar
  `SelectorAvatar(avatares, imagenRefAvatar, onElegir = { imagenRefAvatar = it })`.
  El hueco superior con `ImagenCircular(urlMedia(perfil.imagenUrl) ó
  url del avatar elegido, 96.dp, iniciales)`.

- [ ] **Paso 5: aviso fijo** — constante en el fichero:
  `private const val AVISO_TELEFONO_FAMILIA = "Lo pedimos para que cuando haya un evento, un solo miembro de la familia pueda apuntar a todos."`
  Se pinta bajo el teléfono de pareja y bajo el de cada hijo, en
  `BaniterioColors.muted`, `typography.bodySmall`.

- [ ] **Paso 6: hijos**. Sección con `hijos.forEachIndexed { i, h -> ... }`:
  `OutlinedTextField` nombre; `Row { Checkbox(h.mayorDeEdad) ; Text("Mayor de 18") }`;
  `OutlinedTextField` teléfono (opcional) + aviso;
  `Row { Checkbox(h.visible) ; Text("Mostrar en la peña") }`;
  `TextButton("Quitar") { hijos.removeAt(i) }`.
  Botón `TextButton("+ Añadir hijo") { hijos.add(FilaHijo(null, "", false, "", false)) }`.

- [ ] **Paso 7: guardar**. `fun guardar()`:
  1. Validación local: `nombre` y `apellidos` no en blanco; `imagenRefAvatar != null`;
     si `tienePareja` y estado editable → `parejaNombre` no en blanco y
     `normalizarTelefonoEs(parejaTelefono) != null`; cada hijo con `nombre` no
     en blanco y, si tiene teléfono, `normalizarTelefonoEs(...) != null`.
     Si algo falla → `estado = Error("Revisa los campos obligatorios.")` y no
     envía. (Mensajes concretos por teléfono inválido opcionales pero
     recomendados.)
  2. `estado = Guardando`; `scope.launch`:

```kotlin
val aceptado = parejaEstado == "ACEPTADO"
val req = GuardarPerfilRequest(
    nombre = nombre.trim(),
    apellidos = apellidos.trim(),
    mote = mote.trim().ifBlank { null },
    sobreMi = sobreMi.trim().ifBlank { null },
    imagenTipo = "AVATAR",
    imagenRef = imagenRefAvatar!!,
    tienePareja = tienePareja,
    parejaNombre = when {
        !tienePareja -> null
        aceptado -> parejaNombre        // reenvía tal cual el del GET
        else -> parejaNombre.trim().ifBlank { null }
    },
    parejaTelefono = when {
        !tienePareja -> null
        aceptado -> parejaTelefono      // reenvía tal cual el del GET
        else -> normalizarTelefonoEs(parejaTelefono)
    },
    hijos = hijos.map {
        HijoRequest(
            id = it.id,
            nombre = it.nombre.trim(),
            mayorDeEdad = it.mayorDeEdad,
            telefono = it.telefono.trim().ifBlank { null }?.let(::normalizarTelefonoEs),
            visible = it.visible,
        )
    },
)
when (val r = perfilRepo.guardar(req)) {
    is ResultadoPerfil.Exito -> onGuardado()
    is ResultadoPerfil.Error -> estado = EstadoEditor.Error(r.mensaje)
}
```

- [ ] **Paso 8: romper vínculo**. Botón visible solo si `parejaEstado == "ACEPTADO"`:
  `scope.launch { when (perfilRepo.romperPareja()) { Exito -> recargar() ; Error -> aviso } }`.
  `recargar()` = volver a pedir `miPerfil()` + `avatares()` y re-precargar
  (deja el estado limpio: `pareja == null`, `tienePareja = false`).

- [ ] **Paso 9: cabecera**. `BaniterioWordmark()` + (si `!obligatorio`)
  `Text("Volver", ... clickable { onVolver() })`. Título
  `if (obligatorio) "Completa tu perfil" else "Editar perfil"`. Si `obligatorio`,
  una línea `Text("Rellena tu perfil para entrar en la peña.", muted)`.
  Todo dentro de un `Column` con `verticalScroll(rememberScrollState())` +
  `imePadding()` (formulario largo, como `RegistroScreen`).

- [ ] **Paso 10: compila** — `:shared:compileDebugKotlinAndroid` +
  `:shared:compileKotlinIosSimulatorArm64` → OK.
- [ ] **Paso 11: commit** — `feat(movil): selector de avatar y editor de perfil`

---

## Task 6 — MiembrosScreen + tarjetas

**Files:** sustituir el stub `ui/miembros/MiembrosScreen.kt`

**Interfaces — Consumes:** `PerfilRepository`, `ImagenCircular`, `urlMedia`,
DTOs. **Produces:**
`@Composable fun MiembrosScreen(perfilRepo, miId: Long?, onEditar: () -> Unit, onVolver: () -> Unit)`

- [ ] **Paso 1: estado y carga**.
  `sealed interface EstadoMiembros { Cargando; Cargada(tarjetas, pendiente: VinculoPendiente?); Error(msg) }`.
  Al entrar y tras cada acción: pedir `perfilRepo.miembros()` y
  `perfilRepo.miPerfil()` (para `vinculoPendiente`). Como en las pantallas de
  admin, un `cargar(mostrarCargando: Boolean = true)` que no parpadee al
  refrescar tras Confirmar/Rechazar.
  Si `miembros()` va bien pero `miPerfil()` falla, seguir con
  `pendiente = null` (el banner es secundario).

- [ ] **Paso 2: banner de vínculo pendiente**. Si `pendiente != null`, una
  tarjeta arriba: `Text("${pendiente.solicitanteNombre} dice que sois pareja")`
  + `Row { Button("Confirmar") { confirmar() } ; TextButton("Rechazar") { rechazar() } }`.
  `confirmar()` → `perfilRepo.aceptarPareja()` → `cargar(false)`;
  `rechazar()` → `perfilRepo.rechazarPareja()` → `cargar(false)`.
  Mientras una está en vuelo, deshabilitar ambos botones (`var procesando`).

- [ ] **Paso 3: lista**. `LazyColumn`, `items(tarjetas, key = { it.id })` →
  `TarjetaMiembro(t, esLaMia = t.id == miId, onEditar = onEditar)`. **No
  reordenar** — el backend ya las manda en orden (yo → pareja → hijos → resto).

- [ ] **Paso 4: TarjetaMiembro** (privado, mismo fichero):

```kotlin
@Composable
private fun TarjetaMiembro(t: TarjetaMiembroResponse, esLaMia: Boolean, onEditar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BaniterioColors.panel).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ImagenCircular(
                url = urlMedia(t.imagenUrl),
                tamano = 56.dp,
                iniciales = ((t.nombre.firstOrNull()?.toString() ?: "") + (t.apellidos.firstOrNull()?.toString() ?: "")).uppercase(),
            )
            Column(Modifier.weight(1f)) {
                Text("${t.nombre} ${t.apellidos}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                t.mote?.takeIf { it.isNotBlank() }?.let {
                    Text("«$it»", color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        t.sobreMi?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp)); Text(it, color = BaniterioColors.muted, style = MaterialTheme.typography.bodyMedium)
        }
        t.parejaNombre?.let {
            Spacer(Modifier.height(8.dp)); Text("Pareja: $it", color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (t.hijos.isNotEmpty()) {
            Spacer(Modifier.height(4.dp)); Text("Hijos: ${t.hijos.joinToString(", ")}", color = BaniterioColors.muted, style = MaterialTheme.typography.bodySmall)
        }
        if (esLaMia) {
            Spacer(Modifier.height(12.dp))
            Text("Editar", color = BaniterioColors.brandBright, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onEditar() })
        }
    }
}
```

- [ ] **Paso 5: cabecera y estados** — `BaniterioWordmark()` + "Volver";
  título "Miembros de la peña"; estados `Cargando` / `Error` + "Reintentar",
  patrón de `AdminSolicitudesScreen`.

- [ ] **Paso 6: compila** — `:shared:compileDebugKotlinAndroid` +
  `:shared:compileKotlinIosSimulatorArm64` → OK.
- [ ] **Paso 7: commit** — `feat(movil): sección Miembros con tarjetas y banner de vínculo`

---

## Task 7 — Selector de foto (Android) + subida

**Files:** crear `data/SelectorFoto.kt` (interfaz + `expect`, si no se hizo en
T2), `androidMain/data/PuenteNativo.kt`, `androidMain/data/SelectorFoto.android.kt`,
`iosMain/data/SelectorFoto.ios.kt`; modificar
`androidApp/.../MainActivity.kt`, `ui/miembros/EditorPerfilScreen.kt`

**Interfaces — Consumes:** `FotoElegida`, `PerfilRepository.subirFoto`.
**Produces:** `rememberSelectorFoto(): SelectorFoto`; `PuenteNativo` (androidMain).

- [ ] **Paso 1: PuenteNativo** — `androidMain/data/PuenteNativo.kt`. Conecta los
  composables (que no pueden sobreescribir `onActivityResult`) con `MainActivity`
  (que sí). `MainActivity.onCreate` rellena `lanzarFoto`/`lanzarContacto`;
  los composables ponen el callback pendiente y lo invocan; `onActivityResult`
  lo consume.

```kotlin
package com.baniterio.app.data

/**
 * Puente Activity ↔ Compose para resultados de Activity que necesitan
 * `startActivityForResult` clásico (16 bits), porque `MainActivity` es
 * `FragmentActivity` y rechaza los códigos de 32 bits del API moderno.
 *
 * `MainActivity.onCreate` fija los `lanzar*`. Un composable, justo antes de
 * lanzar, deja su callback en `pendiente*`; `MainActivity.onActivityResult` lo
 * invoca una vez y lo pone a `null`.
 */
object PuenteNativo {
    var lanzarFoto: (() -> Unit)? = null
    var lanzarContacto: (() -> Unit)? = null
    var pendienteFoto: ((FotoElegida?) -> Unit)? = null
    var pendienteContacto: ((String?) -> Unit)? = null
}
```

- [ ] **Paso 2: actual Android** — `androidMain/data/SelectorFoto.android.kt`:

```kotlin
package com.baniterio.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class SelectorFotoAndroid : SelectorFoto {
    override val disponible = true
    override fun elegir(onFoto: (FotoElegida?) -> Unit) {
        PuenteNativo.pendienteFoto = onFoto
        PuenteNativo.lanzarFoto?.invoke()
    }
}

@Composable
actual fun rememberSelectorFoto(): SelectorFoto = remember { SelectorFotoAndroid() }
```

- [ ] **Paso 3: actual iOS** — `iosMain/data/SelectorFoto.ios.kt`:

```kotlin
package com.baniterio.app.data

import androidx.compose.runtime.Composable

private object SelectorFotoIos : SelectorFoto {
    override val disponible = false
    override fun elegir(onFoto: (FotoElegida?) -> Unit) = onFoto(null)
}

@Composable
actual fun rememberSelectorFoto(): SelectorFoto = SelectorFotoIos
```

- [ ] **Paso 4: MainActivity** — añadir al `companion`:
  `const val RC_FOTO = 1002`. En `onCreate`, tras obtener `deps`:

```kotlin
com.baniterio.app.data.PuenteNativo.lanzarFoto = {
    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(android.content.Intent.CATEGORY_OPENABLE)
    }
    @Suppress("DEPRECATION")
    startActivityForResult(intent, RC_FOTO)
}
```

  Y `override fun onActivityResult`:

```kotlin
@Deprecated("Deprecated in Java")
override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
        RC_FOTO -> {
            val cb = com.baniterio.app.data.PuenteNativo.pendienteFoto
            com.baniterio.app.data.PuenteNativo.pendienteFoto = null
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                cb?.invoke(bytes?.let { com.baniterio.app.data.FotoElegida(it, "foto", mime) })
            } else {
                cb?.invoke(null)
            }
        }
    }
}
```

  > Limpiar los `PuenteNativo.lanzar*` en `onDestroy` para no retener la
  > Activity si el proceso la recrea:
  > `PuenteNativo.lanzarFoto = null; PuenteNativo.lanzarContacto = null`.

- [ ] **Paso 5: editor — modo foto**. En `EditorPerfilScreen`:
  - `val selectorFoto = rememberSelectorFoto()`
  - si `selectorFoto.disponible`: un toggle `Row` con dos botones
    "Elegir avatar" / "Subir foto" que fija `var modoImagen by remember { mutableStateOf(if (perfil.imagenTipo == "FOTO") "FOTO" else "AVATAR") }`.
  - modo FOTO: botón "Elegir foto del teléfono" →
    `selectorFoto.elegir { if (it != null) fotoPendiente = it }`;
    previsualización con `ImagenCircular` — para bytes en memoria, envolver en
    `rememberAsyncImagePainter` no aplica a `ByteArray` directamente en KMP; usar
    `AsyncImage(model = fotoPendiente?.bytes, ...)` (Coil 3 acepta `ByteArray`).
    Si ya había foto guardada y no se elige otra, se muestra
    `urlMedia(perfil.imagenUrl)`.
  - En `guardar()`: si `modoImagen == "FOTO"`:
    - si hay `fotoPendiente`: `perfilRepo.subirFoto(fotoPendiente!!)` →
      con la `imagenRef` que devuelve, `imagenTipo = "FOTO"`, `imagenRef = esa`.
    - si no hay `fotoPendiente` pero el perfil ya era FOTO: `imagenTipo = "FOTO"`,
      `imagenRef = perfil.imagenRef!!`.
    - error de subida → `estado = Error(mensaje)`, no sigue con el `PUT`.
  - validación: en modo FOTO, "imagen elegida" = hay `fotoPendiente` **o** el
    perfil ya tenía `imagenRef` de tipo FOTO.
  - La foto no se sube al elegirla, solo al pulsar "Guardar" (no dejar ficheros
    huérfanos).

- [ ] **Paso 6: compila** — `:shared:compileDebugKotlinAndroid`,
  `:shared:compileKotlinIosSimulatorArm64`, `:androidApp:compileDebugKotlin` →
  OK.
- [ ] **Paso 7: commit** — `feat(movil): subir foto de perfil desde el teléfono (Android)`

---

## Task 8 — Selector de contacto (Android)

**Files:** crear `data/SelectorContacto.kt` (interfaz + `expect`, si no se hizo
en T2), `androidMain/data/SelectorContacto.android.kt`,
`iosMain/data/SelectorContacto.ios.kt`; modificar `MainActivity.kt`,
`ui/miembros/EditorPerfilScreen.kt`

**Interfaces — Produces:** `rememberSelectorContacto(): SelectorContacto`.

- [ ] **Paso 1: actual Android** — `androidMain/data/SelectorContacto.android.kt`:

```kotlin
package com.baniterio.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class SelectorContactoAndroid : SelectorContacto {
    override val disponible = true
    override fun elegir(onTelefono: (String?) -> Unit) {
        PuenteNativo.pendienteContacto = onTelefono
        PuenteNativo.lanzarContacto?.invoke()
    }
}

@Composable
actual fun rememberSelectorContacto(): SelectorContacto = remember { SelectorContactoAndroid() }
```

- [ ] **Paso 2: actual iOS** — `iosMain/data/SelectorContacto.ios.kt`:
  `disponible = false`, `elegir { onTelefono(null) }` (calcado del de foto).

- [ ] **Paso 3: MainActivity** — `const val RC_CONTACTO = 1003`. En `onCreate`:

```kotlin
com.baniterio.app.data.PuenteNativo.lanzarContacto = {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_PICK,
        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
    )
    @Suppress("DEPRECATION")
    startActivityForResult(intent, RC_CONTACTO)
}
```

  En `onActivityResult`, otra rama del `when`:

```kotlin
RC_CONTACTO -> {
    val cb = com.baniterio.app.data.PuenteNativo.pendienteContacto
    com.baniterio.app.data.PuenteNativo.pendienteContacto = null
    val uri = data?.data
    var numero: String? = null
    if (resultCode == RESULT_OK && uri != null) {
        contentResolver.query(
            uri,
            arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) numero = c.getString(0) }
    }
    cb?.invoke(numero)
}
```

  `ACTION_PICK` sobre `Phone.CONTENT_URI` **no** necesita `READ_CONTACTS` — no
  se toca el manifiesto.

- [ ] **Paso 4: editor**. `val selectorContacto = rememberSelectorContacto()`.
  Junto al `OutlinedTextField` del teléfono de pareja (cuando es editable) y al
  de cada hijo, si `selectorContacto.disponible`, un `TextButton("Elegir de
  contactos") { selectorContacto.elegir { n -> if (n != null) parejaTelefono = normalizarTelefonoEs(n) ?: n } }`.
  (Para el hijo: `hijos[i] = hijos[i].copy(telefono = ...)` o mutar `h.telefono`.)
  Se guarda lo normalizado si se puede; si no, el crudo (el usuario lo corrige
  y la validación de guardado lo vuelve a comprobar).

- [ ] **Paso 5: compila** — `:shared:compileDebugKotlinAndroid`,
  `:shared:compileKotlinIosSimulatorArm64`, `:androidApp:compileDebugKotlin` →
  OK.
- [ ] **Paso 6: commit** — `feat(movil): elegir teléfono de pareja/hijo desde contactos (Android)`

---

## Task 9 — Repaso de integración

**Files:** ninguno nuevo; arreglos si el repaso los pide; memoria

- [ ] **Paso 1: tests** — `./gradlew :shared:testAndroidHostTest` → todo verde
  (nuevos de Task 1-2 + los existentes de dispositivo).
- [ ] **Paso 2: compilación completa**:
  - `./gradlew :shared:assembleDebug`
  - `./gradlew :androidApp:assembleDebug`
  - `./gradlew :shared:compileKotlinIosSimulatorArm64` (verifica los `actual` de
    iOS y que `commonMain` no usa API solo-Android).
- [ ] **Paso 3: humo manual** (con backend local + emulador Android):
  - Login con un usuario sin perfil → cae en `CargandoSesion` → editor
    obligatorio (sin "Volver", sin atrás).
  - Elegir avatar, nombre, apellidos → Guardar → entra al Panel.
  - Panel → tarjeta "Miembros" → lista, la propia tarjeta va primero, con
    "Editar".
  - "Editar" → cambiar a "Subir foto" → elegir del emulador → Guardar → la
    tarjeta muestra la foto.
  - Declarar pareja con un teléfono de otro miembro de prueba → ese miembro, al
    entrar, ve el banner "X dice que sois pareja" → Confirmar → las dos
    tarjetas se ven como pareja; en el editor del primero, los campos de pareja
    salen de solo lectura con "Romper vínculo".
  - Añadir un hijo con "mostrar en la peña" activado → aparece en la tarjeta;
    sin activar → no aparece para los demás.
  - Añadir teléfono de hijo desde contactos → tras Guardar, ese teléfono queda
    autorizado (comprobable en el panel de admin web).
- [ ] **Paso 4: memoria** — actualizar `baniterio_perfiles_miembros.md`
  (Plan C completo), `MEMORY.md`, y `baniterio_push_ios_pendiente.md` (añadir:
  selector de foto de iOS —PHPicker— también aparcado hasta tener Mac; en iOS el
  editor solo ofrece avatar).
- [ ] **Paso 5: commit** si hubo arreglos — `chore(movil): repaso de integración de perfiles`

---

## Self-Review (hecha al escribir el plan)

**Cobertura del spec:**
- Editor obligatorio en primer login → `CargandoSesionScreen` (T4) +
  `EditorPerfilScreen obligatorio` (T5).
- Foto (procesada en servidor) o avatar → avatar T5, foto Android T7; iOS solo
  avatar (aparcado con Mac, decisión del usuario).
- Vínculo de pareja con aceptación, landmines respetadas → T5 (editor) + T6
  (banner Confirmar/Rechazar).
- Hijos, check +18 informativo, "mostrar en la peña" → T5.
- Alta automática en autorizados → la hace el backend al guardar; el móvil solo
  manda los teléfonos (normalizados) en el `PUT` / crea el vínculo → T5 + T8.
- Sección Miembros con tarjetas ordenadas (orden del backend, sin reordenar
  cliente) → T6.
- Push de eventos de pareja → los emite el backend; el móvil ya recibe push
  (FCM, feature anterior), no hay trabajo nuevo.
- Selector de contactos Android, sin permiso, `startActivityForResult` 16 bits →
  T8, con el puente en `MainActivity`.
- Normalización de teléfono común → T1.
- Coil 3 nueva dependencia → T3.

**Huecos conocidos y aceptados:**
- iOS: sin selector de foto ni de contactos (Mac). El editor iOS oculta "Subir
  foto"; los teléfonos se teclean. Nota en la memoria de pendientes de Mac.
- Sin recorte/zoom de la foto antes de subir (el backend recorta centrado a
  512×512).
- Sin aviso de "cambios sin guardar" al salir del editor.
- Las pantallas Compose no llevan test automatizado (igual que el resto de
  `mobile/`): se cubren por compilación multiplataforma + humo manual.
- `PuenteNativo` es un `object` con estado mutable de proceso: vale porque hay
  una sola `MainActivity` y un solo editor visible a la vez; se documenta.

**Consistencia de tipos:** `PerfilRepository` (T2) se consume en T4-T8 con las
firmas de T2; `FotoElegida` (T2) la usan T5/T7; `ImagenCircular` (T3) la usan
T5/T6; `Screen.*` (T4) los usa `App.kt` en T4-T6. `normalizarTelefonoEs` (T1) en
T5/T8. Sin choques de nombres detectados.

**Global Constraints:** todos los repos siguen el patrón `AdminRepositoryImpl`;
`requestCode` de 16 bits en T7/T8; español en todo; sin exponer teléfono/email
en tarjetas.
