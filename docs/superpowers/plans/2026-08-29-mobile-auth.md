# Autenticación real en la app móvil — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la app móvil (`mobile/`) haga registro, login y solicitud de ingreso de verdad contra el backend, con desbloqueo biométrico en cada arranque.

**Architecture:** Se añade una capa de datos a `mobile/shared` (`commonMain`): Ktor Client + `kotlinx.serialization`, un `AuthRepository` con el token JWT en memoria, y un `AlmacenCredenciales` (`expect/actual`) para guardar teléfono+contraseña cifrados (Android `EncryptedSharedPreferences`, iOS Keychain). El estado de cada pantalla se lleva con `remember` + `rememberCoroutineScope` llamando al repo (el patrón que ya usa `LoginScreen`), **sin ViewModels** (el spec lo permite como camino de menor riesgo). Navegación: `App.kt` decide entre `Desbloqueo` y `Login` según haya credenciales guardadas, y cablea a mano `HttpClient` + repo + almacén.

**Tech Stack:** Kotlin Multiplatform 2.4.10, Compose Multiplatform 1.11.1, Ktor Client 3.x, kotlinx-serialization 1.x, androidx.security-crypto (Android), Keychain vía cinterop (iOS), Gradle 9.1.

**Spec:** `docs/superpowers/specs/2026-08-29-mobile-auth-design.md`

## Global Constraints

- **Solo se toca `mobile/`.** El backend (`back/`) y la web (`front/`) no se modifican.
- **Paquete raíz:** `com.baniterio.app`. La capa de datos nueva vive en
  `com.baniterio.app.data` (`commonMain` + `androidMain` + `iosMain`).
- **Modelo de sesión:** el token JWT vive SOLO en memoria (campo en
  `AuthRepositoryImpl`). Nunca se escribe a disco. Lo que se persiste
  (cifrado) es teléfono+contraseña, y solo si el usuario acepta la oferta de
  biometría.
- **URL base:** `expect val API_BASE_URL: String` →
  `"http://10.0.2.2:8080/api/v1"` (androidMain),
  `"http://localhost:8080/api/v1"` (iosMain). Sufijo `/api/v1` incluido; los
  endpoints se piden como `"$API_BASE_URL/auth/login"` etc.
- **Formato de teléfono:** `^[67]\d{8}$`. Contraseña ≥ 6. Igual que la web.
- **Códigos de error del backend** (campo `codigo` del JSON de error):
  `TELEFONO_NO_AUTORIZADO` (403), `YA_REGISTRADO` (409),
  `CREDENCIALES_INVALIDAS` (401), `VALIDACION` (400),
  `SOLICITUD_YA_PENDIENTE` (409), `TELEFONO_YA_AUTORIZADO` (409).
- **Nombres de campo JSON** (deben coincidir con el backend): registro →
  `{telefono,email,password,nombre,apellidos,mote}`; login →
  `{telefono,password}` → `{token, usuario:{id,nombre,apellidos,mote,esSuperadmin}}`;
  solicitud → `{telefono,email,nombre,apellidos,motivo,relacion,conocidos}`.
- **Español** en todo el texto de UI y en los mensajes de error.
- **Verificación:** cada task que toca código Kotlin termina con
  `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid` en verde (la
  primera vez descarga dependencias, puede tardar). iOS **no se compila**
  (necesita macOS); el código iOS se escribe con cuidado y se marca como
  no verificado. La prueba en emulador/dispositivo la hace el usuario.
- **Sin framework de DI.** Cableado manual en `App.kt` y los entry points.
- **Sin ViewModels** (`androidx.lifecycle.ViewModel`). Estado con `remember`
  + `rememberCoroutineScope`, llamadas `suspend` al repo.
- **Commits frecuentes**, conventional commits, mensaje en español, cada uno
  terminando con: `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- **Rama:** `feature/mobile-auth` (ya creada; el spec ya está commiteado en ella).

## File Structure

### Nuevo — `mobile/shared/src/commonMain`

| Fichero | Responsabilidad |
|---|---|
| `.../app/data/ApiConfig.kt` | `expect val API_BASE_URL: String`. |
| `.../app/data/dto/AuthDtos.kt` | `@Serializable` DTOs: `RegistroRequest`, `LoginRequest`, `LoginResponse`, `UsuarioResponse`, `SolicitudIngresoRequest`, `ErrorResponse`. |
| `.../app/data/ResultadoAuth.kt` | `sealed class ResultadoAuth<out T>` (`Exito`, `Error`), `enum class CodigoErrorAuth`. |
| `.../app/data/AuthRepository.kt` | Interfaz: `registro`, `login`, `solicitarAcceso`, `logout`, `usuarioActual`, `credencialesUsadas`. |
| `.../app/data/AuthRepositoryImpl.kt` | Implementación con Ktor. Token en memoria. Mapeo de errores. |
| `.../app/data/HttpClientFactory.kt` | `expect fun crearHttpClient(): HttpClient` (motor por plataforma) + config común (JSON, logging opcional, timeout). |
| `.../app/data/AlmacenCredenciales.kt` | `expect class AlmacenCredenciales` + `data class Credenciales(telefono, password)`. |
| `.../app/data/Dependencias.kt` | `class Dependencias(val repo: AuthRepository, val almacen: AlmacenCredenciales)` + `expect fun` / composable para construirlo con lo específico de plataforma. |
| `.../app/ui/auth/desbloqueo/DesbloqueoScreen.kt` | Pantalla nueva. |
| `.../app/ui/auth/solicitaracceso/SolicitarAccesoScreen.kt` | Pantalla nueva. |

### Nuevo — `androidMain` / `iosMain`

| Fichero | Responsabilidad |
|---|---|
| `androidMain/.../app/data/ApiConfig.android.kt` | `actual val API_BASE_URL = "http://10.0.2.2:8080/api/v1"`. |
| `androidMain/.../app/data/HttpClientFactory.android.kt` | `actual fun crearHttpClient()` con `OkHttp`. |
| `androidMain/.../app/data/AlmacenCredenciales.android.kt` | `actual class` con `EncryptedSharedPreferences`. |
| `iosMain/.../app/data/ApiConfig.ios.kt` | `actual val API_BASE_URL = "http://localhost:8080/api/v1"`. |
| `iosMain/.../app/data/HttpClientFactory.ios.kt` | `actual fun crearHttpClient()` con `Darwin`. |
| `iosMain/.../app/data/AlmacenCredenciales.ios.kt` | `actual class` con Keychain (`platform.Security.*`). |

### Nuevo — `androidApp`

| Fichero | Responsabilidad |
|---|---|
| `androidApp/src/main/res/xml/network_security_config.xml` | Permite tráfico en claro a `10.0.2.2` (solo debug). |

### Modificado

| Fichero | Cambio |
|---|---|
| `mobile/gradle/libs.versions.toml` | Versiones + libs de Ktor, serialization, security-crypto; plugin serialization. |
| `mobile/shared/build.gradle.kts` | Plugin serialization; deps por source set. |
| `mobile/androidApp/src/main/AndroidManifest.xml` | `android:networkSecurityConfig` + (si hace falta) `usesCleartextTraffic` en debug. |
| `mobile/shared/.../app/nav/Screen.kt` | +`Desbloqueo`, +`SolicitarAcceso`. |
| `mobile/shared/.../app/App.kt` | Cableado de `Dependencias`, pantalla inicial, rutas nuevas, paso de `datosSolicitud`. |
| `mobile/shared/.../app/MainViewController.kt` (iosMain) | Construir `Dependencias` de iOS y pasarlas a `App(...)`. |
| `mobile/androidApp/.../MainActivity.kt` | Construir `Dependencias` de Android (necesita `Context`) y pasarlas a `App(...)`. |
| `mobile/shared/.../app/ui/auth/login/LoginScreen.kt` | Reescrita: llama al repo, estados, sin botón biométrico, oferta de biometría. |
| `mobile/shared/.../app/ui/auth/registro/RegistroScreen.kt` | Reescrita: llama al repo, estados, `NoAutorizado` + botón. |
| `mobile/shared/.../app/ui/panel/PanelScreen.kt` | `onCerrarSesion` hace logout real. |

---

## Task 1: Dependencias Gradle

**Files:**
- Modify: `mobile/gradle/libs.versions.toml`
- Modify: `mobile/shared/build.gradle.kts`

**Interfaces:**
- Produces: disponibles en `commonMain` los paquetes `io.ktor.client.*`,
  `kotlinx.serialization.*`; en `androidMain` `io.ktor.client.engine.okhttp.*`
  y `androidx.security.crypto.*`; en `iosMain` `io.ktor.client.engine.darwin.*`.

- [ ] **Step 1: `libs.versions.toml` — versiones**

En `[versions]` añade:
```toml
ktor = "3.3.0"
kotlinx-serialization = "1.7.3"
androidx-securityCrypto = "1.1.0-alpha06"
```

- [ ] **Step 2: `libs.versions.toml` — libraries**

En `[libraries]`:
```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-contentNegotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "androidx-securityCrypto" }
```

- [ ] **Step 3: `libs.versions.toml` — plugin serialization**

En `[plugins]`:
```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 4: `shared/build.gradle.kts` — plugin + deps**

Añade al bloque `plugins { }`:
```kotlin
alias(libs.plugins.kotlinSerialization)
```

En `sourceSets`:
```kotlin
commonMain.dependencies {
    // ...lo que ya hay...
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
}
androidMain.dependencies {
    // ...lo que ya hay...
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.security.crypto)
}
iosMain.dependencies {
    implementation(libs.ktor.client.darwin)
}
```
Si no existe el bloque `iosMain.dependencies { }` en `sourceSets`, créalo
(al mismo nivel que `androidMain.dependencies`).

- [ ] **Step 5: Compilar**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. (Primera vez: descarga Ktor y demás, puede tardar
varios minutos.) Si falla por incompatibilidad de versión de Ktor con Kotlin
2.4.10, baja Ktor a la 3.2.x o sube a la última 3.x y reintenta; anota la
versión que funcione.

- [ ] **Step 6: Commit**

```bash
git add mobile/gradle/libs.versions.toml mobile/shared/build.gradle.kts
git commit -m "build(mobile): Ktor, kotlinx-serialization y security-crypto

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 2: DTOs y tipos de resultado

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/dto/AuthDtos.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ResultadoAuth.kt`

**Interfaces:**
- Produces:
  - `RegistroRequest(telefono, email, password, nombre, apellidos, mote: String)` `@Serializable`
  - `LoginRequest(telefono, password)` `@Serializable`
  - `UsuarioResponse(id: Long, nombre, apellidos, mote: String?, esSuperadmin: Boolean)` `@Serializable`
  - `LoginResponse(token: String, usuario: UsuarioResponse)` `@Serializable`
  - `SolicitudIngresoRequest(telefono, email, nombre, apellidos, motivo, relacion, conocidos)` `@Serializable`
  - `ErrorResponse(codigo: String? = null)` `@Serializable` (ignora campos extra)
  - `sealed class ResultadoAuth<out T> { data class Exito<T>(val dato: T); data class Error(val codigo: CodigoErrorAuth, val mensaje: String) }`
  - `enum class CodigoErrorAuth { TELEFONO_NO_AUTORIZADO, YA_REGISTRADO, CREDENCIALES_INVALIDAS, VALIDACION, SOLICITUD_YA_PENDIENTE, TELEFONO_YA_AUTORIZADO, SIN_CONEXION, DESCONOCIDO }`

- [ ] **Step 1: `AuthDtos.kt`**

```kotlin
package com.baniterio.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegistroRequest(
    val telefono: String,
    val email: String,
    val password: String,
    val nombre: String,
    val apellidos: String,
    val mote: String,
)

@Serializable
data class LoginRequest(val telefono: String, val password: String)

@Serializable
data class UsuarioResponse(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val mote: String? = null,
    val esSuperadmin: Boolean,
)

@Serializable
data class LoginResponse(val token: String, val usuario: UsuarioResponse)

@Serializable
data class SolicitudIngresoRequest(
    val telefono: String,
    val email: String,
    val nombre: String,
    val apellidos: String,
    val motivo: String,
    val relacion: String,
    val conocidos: String,
)

@Serializable
data class ErrorResponse(val codigo: String? = null)
```

- [ ] **Step 2: `ResultadoAuth.kt`**

```kotlin
package com.baniterio.app.data

sealed class ResultadoAuth<out T> {
    data class Exito<T>(val dato: T) : ResultadoAuth<T>()
    data class Error(val codigo: CodigoErrorAuth, val mensaje: String) : ResultadoAuth<Nothing>()
}

enum class CodigoErrorAuth {
    TELEFONO_NO_AUTORIZADO,
    YA_REGISTRADO,
    CREDENCIALES_INVALIDAS,
    VALIDACION,
    SOLICITUD_YA_PENDIENTE,
    TELEFONO_YA_AUTORIZADO,
    SIN_CONEXION,
    DESCONOCIDO;

    companion object {
        fun deCodigoBackend(codigo: String?): CodigoErrorAuth = when (codigo) {
            "TELEFONO_NO_AUTORIZADO" -> TELEFONO_NO_AUTORIZADO
            "YA_REGISTRADO" -> YA_REGISTRADO
            "CREDENCIALES_INVALIDAS" -> CREDENCIALES_INVALIDAS
            "VALIDACION" -> VALIDACION
            "SOLICITUD_YA_PENDIENTE" -> SOLICITUD_YA_PENDIENTE
            "TELEFONO_YA_AUTORIZADO" -> TELEFONO_YA_AUTORIZADO
            else -> DESCONOCIDO
        }
    }

    /** Mensaje por defecto para mostrar al usuario. Las pantallas pueden
     *  sobreescribir alguno (p. ej. registro trata TELEFONO_NO_AUTORIZADO aparte). */
    val mensaje: String
        get() = when (this) {
            TELEFONO_NO_AUTORIZADO -> "Tu teléfono no está autorizado en la peña."
            YA_REGISTRADO -> "Ya existe una cuenta con ese teléfono o ese email."
            CREDENCIALES_INVALIDAS -> "Teléfono o contraseña incorrectos."
            VALIDACION -> "Revisa los datos del formulario."
            SOLICITUD_YA_PENDIENTE -> "Ya hay una solicitud pendiente para ese teléfono."
            TELEFONO_YA_AUTORIZADO -> "Ese teléfono ya está autorizado. Regístrate directamente."
            SIN_CONEXION -> "No se pudo conectar con el servidor."
            DESCONOCIDO -> "Algo ha ido mal. Inténtalo de nuevo más tarde."
        }
}
```

- [ ] **Step 3: Compilar**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/
git commit -m "feat(mobile): DTOs de auth y tipo ResultadoAuth

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 3: URL base y tráfico en claro (Android)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ApiConfig.kt`
- Create: `mobile/shared/src/androidMain/kotlin/com/baniterio/app/data/ApiConfig.android.kt`
- Create: `mobile/shared/src/iosMain/kotlin/com/baniterio/app/data/ApiConfig.ios.kt`
- Create: `mobile/androidApp/src/main/res/xml/network_security_config.xml`
- Modify: `mobile/androidApp/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `com.baniterio.app.data.API_BASE_URL: String`.

- [ ] **Step 1: `ApiConfig.kt` (commonMain)**

```kotlin
package com.baniterio.app.data

/** URL base de la API, incluido el prefijo /api/v1. Distinta por plataforma:
 *  el emulador de Android ve el localhost del PC en 10.0.2.2; el simulador de
 *  iOS lo ve en localhost. Un dispositivo físico necesita la IP LAN del PC. */
expect val API_BASE_URL: String
```

- [ ] **Step 2: actual Android**

`androidMain/.../data/ApiConfig.android.kt`:
```kotlin
package com.baniterio.app.data

actual val API_BASE_URL: String = "http://10.0.2.2:8080/api/v1"
```

- [ ] **Step 3: actual iOS**

`iosMain/.../data/ApiConfig.ios.kt`:
```kotlin
package com.baniterio.app.data

actual val API_BASE_URL: String = "http://localhost:8080/api/v1"
```

- [ ] **Step 4: `network_security_config.xml`**

`androidApp/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

- [ ] **Step 5: Manifest**

En `AndroidManifest.xml`, al `<application>`:
```
android:networkSecurityConfig="@xml/network_security_config"
```

- [ ] **Step 6: Compilar**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (verifica que el `expect/actual` casa).

- [ ] **Step 7: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ApiConfig.kt \
        mobile/shared/src/androidMain mobile/shared/src/iosMain \
        mobile/androidApp/src/main/res/xml/network_security_config.xml \
        mobile/androidApp/src/main/AndroidManifest.xml
git commit -m "feat(mobile): URL base de la API por plataforma + cleartext a 10.0.2.2

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 4: HttpClient y AuthRepository

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/HttpClientFactory.kt`
- Create: `mobile/shared/src/androidMain/kotlin/com/baniterio/app/data/HttpClientFactory.android.kt`
- Create: `mobile/shared/src/iosMain/kotlin/com/baniterio/app/data/HttpClientFactory.ios.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AuthRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AuthRepositoryImpl.kt`

**Interfaces:**
- Consumes: DTOs y `ResultadoAuth` de Task 2, `API_BASE_URL` de Task 3.
- Produces:
  - `expect fun crearHttpClient(): HttpClient`
  - `interface AuthRepository`:
    - `suspend fun registro(r: RegistroRequest): ResultadoAuth<UsuarioResponse>`
    - `suspend fun login(telefono: String, password: String): ResultadoAuth<UsuarioResponse>`
    - `suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit>`
    - `fun logout()`
    - `val usuarioActual: UsuarioResponse?`
  - `class AuthRepositoryImpl(private val http: HttpClient) : AuthRepository`

- [ ] **Step 1: `HttpClientFactory.kt` (commonMain)**

```kotlin
package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun crearHttpClient(): HttpClient

/** Configuración común de Ktor: JSON tolerante y timeouts razonables. */
fun HttpClientConfigComun(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
```
> Nota: el `expect fun` toma el motor por plataforma; el bloque común
> (`ContentNegotiation`, `HttpTimeout`) se aplica dentro de cada `actual`
> reutilizando `HttpClientConfigComun()`. Si prefieres, mete la config común
> en una función `HttpClientConfig<*>.() -> Unit` compartida y pásasela a
> `HttpClient(motor) { comun() }` en cada actual — decídelo al implementar,
> lo importante es no duplicar la config de JSON/timeout.

- [ ] **Step 2: actual Android (OkHttp)**

```kotlin
package com.baniterio.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual fun crearHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(HttpClientConfigComun()) }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
    }
}
```

- [ ] **Step 3: actual iOS (Darwin)**

Igual que Android pero `import io.ktor.client.engine.darwin.Darwin` y
`HttpClient(Darwin) { ... }`. (No se compila aquí; escribir con cuidado.)

- [ ] **Step 4: `AuthRepository.kt`**

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.RegistroRequest
import com.baniterio.app.data.dto.SolicitudIngresoRequest
import com.baniterio.app.data.dto.UsuarioResponse

interface AuthRepository {
    val usuarioActual: UsuarioResponse?
    suspend fun registro(r: RegistroRequest): ResultadoAuth<UsuarioResponse>
    suspend fun login(telefono: String, password: String): ResultadoAuth<UsuarioResponse>
    suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit>
    fun logout()
}
```

- [ ] **Step 5: `AuthRepositoryImpl.kt`**

```kotlin
package com.baniterio.app.data

import com.baniterio.app.data.dto.ErrorResponse
import com.baniterio.app.data.dto.LoginRequest
import com.baniterio.app.data.dto.LoginResponse
import com.baniterio.app.data.dto.RegistroRequest
import com.baniterio.app.data.dto.SolicitudIngresoRequest
import com.baniterio.app.data.dto.UsuarioResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class AuthRepositoryImpl(private val http: HttpClient) : AuthRepository {

    private var token: String? = null
    private var _usuario: UsuarioResponse? = null
    override val usuarioActual: UsuarioResponse? get() = _usuario

    override suspend fun registro(r: RegistroRequest): ResultadoAuth<UsuarioResponse> =
        peticion {
            http.post("$API_BASE_URL/auth/registro") {
                contentType(ContentType.Application.Json)
                setBody(r)
            }.body<UsuarioResponse>()
        }

    override suspend fun login(telefono: String, password: String): ResultadoAuth<UsuarioResponse> {
        val res = peticion {
            http.post("$API_BASE_URL/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(telefono, password))
            }.body<LoginResponse>()
        }
        if (res is ResultadoAuth.Exito) {
            token = res.dato.token
            _usuario = res.dato.usuario
            return ResultadoAuth.Exito(res.dato.usuario)
        }
        return res as ResultadoAuth.Error
    }

    override suspend fun solicitarAcceso(r: SolicitudIngresoRequest): ResultadoAuth<Unit> =
        peticion {
            http.post("$API_BASE_URL/auth/solicitudes") {
                contentType(ContentType.Application.Json)
                setBody(r)
            }
            Unit
        }

    override fun logout() {
        token = null
        _usuario = null
    }

    /**
     * Ejecuta [bloque]; traduce las excepciones de Ktor a ResultadoAuth.Error.
     * Un ResponseException (4xx/5xx) → se lee `codigo` del cuerpo; cualquier
     * otra excepción (red, timeout, serialización) → SIN_CONEXION/DESCONOCIDO.
     */
    private suspend fun <T> peticion(bloque: suspend () -> T): ResultadoAuth<T> =
        try {
            ResultadoAuth.Exito(bloque())
        } catch (e: ResponseException) {
            val codigo = runCatching { e.response.body<ErrorResponse>().codigo }.getOrNull()
            val cod = CodigoErrorAuth.deCodigoBackend(codigo)
            ResultadoAuth.Error(cod, cod.mensaje)
        } catch (e: Exception) {
            ResultadoAuth.Error(CodigoErrorAuth.SIN_CONEXION, CodigoErrorAuth.SIN_CONEXION.mensaje)
        }
}
```
> Nota: Ktor lanza `ResponseException` (subclases `ClientRequestException`
> 4xx / `ServerResponseException` 5xx) solo si `expectSuccess = true`. Ponlo
> en la config común (`expectSuccess = true`) o comprueba `response.status`
> a mano. El plan asume `expectSuccess = true` en `crearHttpClient`.
> **Actualiza Task 4 Step 2/3 para añadir `expectSuccess = true`.**

- [ ] **Step 6: Añadir `expectSuccess = true`**

En los dos `actual fun crearHttpClient()` (Android e iOS), dentro del bloque
`HttpClient(motor) { ... }`, añade: `expectSuccess = true`.

- [ ] **Step 7: Compilar**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Si hay imports de Ktor que no resuelven, ajusta
según la versión de Ktor que quedó fijada en Task 1.

- [ ] **Step 8: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/ mobile/shared/src/androidMain mobile/shared/src/iosMain
git commit -m "feat(mobile): HttpClient (Ktor) y AuthRepository con el token en memoria

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 5: AlmacenCredenciales (expect/actual)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AlmacenCredenciales.kt`
- Create: `mobile/shared/src/androidMain/kotlin/com/baniterio/app/data/AlmacenCredenciales.android.kt`
- Create: `mobile/shared/src/iosMain/kotlin/com/baniterio/app/data/AlmacenCredenciales.ios.kt`

**Interfaces:**
- Produces:
  - `data class Credenciales(val telefono: String, val password: String)`
  - `expect class AlmacenCredenciales` con:
    - `fun guardar(telefono: String, password: String)`
    - `fun leer(): Credenciales?`
    - `fun borrar()`
    - `val hayCredenciales: Boolean`
  - Android: `class AlmacenCredenciales(context: Context)`.
  - iOS: `class AlmacenCredenciales()`.

- [ ] **Step 1: `AlmacenCredenciales.kt` (commonMain)**

```kotlin
package com.baniterio.app.data

data class Credenciales(val telefono: String, val password: String)

/**
 * Guarda de forma segura el teléfono y la contraseña para poder re-autenticarse
 * tras un desbloqueo biométrico. Android: EncryptedSharedPreferences. iOS: Keychain.
 * El token JWT NO se guarda aquí (vive solo en memoria en AuthRepository).
 */
expect class AlmacenCredenciales {
    fun guardar(telefono: String, password: String)
    fun leer(): Credenciales?
    fun borrar()
    val hayCredenciales: Boolean
}
```

- [ ] **Step 2: actual Android**

```kotlin
package com.baniterio.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class AlmacenCredenciales(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "baniterio_credenciales",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    actual fun guardar(telefono: String, password: String) {
        prefs.edit().putString("telefono", telefono).putString("password", password).apply()
    }

    actual fun leer(): Credenciales? {
        val t = prefs.getString("telefono", null) ?: return null
        val p = prefs.getString("password", null) ?: return null
        return Credenciales(t, p)
    }

    actual fun borrar() {
        prefs.edit().clear().apply()
    }

    actual val hayCredenciales: Boolean get() = prefs.contains("telefono")
}
```

- [ ] **Step 3: actual iOS (Keychain)**

`iosMain/.../data/AlmacenCredenciales.ios.kt` — implementación con
`platform.Security.SecItemAdd/CopyMatching/Delete` y
`platform.Foundation.NSData`. Servicio `"baniterio_credenciales"`, dos
cuentas (`"telefono"`, `"password"`) o un único item JSON. **No se compila
aquí**; escribir siguiendo un patrón conocido de Keychain en KMP y marcar
con un comentario `// TODO: verificar al compilar en Mac`.

```kotlin
package com.baniterio.app.data

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*

@OptIn(ExperimentalForeignApi::class)
actual class AlmacenCredenciales {
    private val servicio = "baniterio_credenciales"

    actual fun guardar(telefono: String, password: String) {
        set("telefono", telefono)
        set("password", password)
    }

    actual fun leer(): Credenciales? {
        val t = get("telefono") ?: return null
        val p = get("password") ?: return null
        return Credenciales(t, p)
    }

    actual fun borrar() {
        listOf("telefono", "password").forEach { cuenta ->
            val query = mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to servicio,
                kSecAttrAccount to cuenta,
            ).toCFDictionary()
            SecItemDelete(query)
        }
    }

    actual val hayCredenciales: Boolean get() = get("telefono") != null

    // set/get/toCFDictionary helpers con CoreFoundation — detallar al implementar.
}
```
> Si el interop de Keychain resulta demasiado frágil a ciegas, un
> `AlmacenCredenciales` iOS de respaldo temporal con `NSUserDefaults` (no
> cifrado) es aceptable para desbloquear el resto; anótalo como deuda y
> `// TODO seguridad`. Decídelo al implementar.

- [ ] **Step 4: Compilar Android**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/AlmacenCredenciales.kt mobile/shared/src/androidMain mobile/shared/src/iosMain
git commit -m "feat(mobile): AlmacenCredenciales seguro (EncryptedSharedPreferences / Keychain)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 6: Grafo de dependencias y navegación base

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/data/Dependencias.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`
- Modify: `mobile/androidApp/src/main/kotlin/com/baniterio/app/MainActivity.kt`
- Modify: `mobile/shared/src/iosMain/kotlin/com/baniterio/app/MainViewController.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `AuthRepositoryImpl`, `crearHttpClient`,
  `AlmacenCredenciales` (Tasks 4-5).
- Produces:
  - `class Dependencias(val repo: AuthRepository, val almacen: AlmacenCredenciales)`
  - `Screen.Desbloqueo`, `Screen.SolicitarAcceso`
  - `fun App(deps: Dependencias)` (firma cambiada; el `App()` sin args
    desaparece — actualizar previews).

- [ ] **Step 1: `Dependencias.kt`**

```kotlin
package com.baniterio.app.data

class Dependencias(
    val repo: AuthRepository,
    val almacen: AlmacenCredenciales,
)
```
(La construcción concreta se hace en cada entry point porque el almacén de
Android necesita `Context`.)

- [ ] **Step 2: `Screen.kt`**

```kotlin
package com.baniterio.app.nav

sealed class Screen {
    data object Desbloqueo : Screen()
    data object Login : Screen()
    data object Registro : Screen()
    data object SolicitarAcceso : Screen()
    data object Panel : Screen()
    data object Historia : Screen()
}
```

- [ ] **Step 3: `App.kt` — firma, claves, pantalla inicial**

- `fun App(deps: Dependencias)`.
- `aClave()` / `claveAScreen()`: añade `CLAVE_DESBLOQUEO = "Desbloqueo"` y
  `CLAVE_SOLICITAR = "SolicitarAcceso"`.
- Pantalla inicial:
  ```kotlin
  var screenKey by rememberSaveable {
      mutableStateOf(if (deps.almacen.hayCredenciales) CLAVE_DESBLOQUEO else CLAVE_LOGIN)
  }
  ```
- `var datosSolicitud by remember { mutableStateOf<SolicitudPrecarga?>(null) }`
  donde `data class SolicitudPrecarga(val nombre: String, val apellidos: String, val telefono: String, val email: String)`
  (defínela en `App.kt` o junto a `SolicitarAccesoScreen`).
- `when (screen)` — deja `Login`, `Registro`, `Panel`, `Historia` como
  están de momento (siguen simulando); añade ramas vacías `is Screen.Desbloqueo -> {}`
  y `is Screen.SolicitarAcceso -> {}` para que compile. Las Tasks 8-12 las
  rellenan.
- **No** cambies todavía la lógica de `Login`/`Registro`/`Panel` (eso es de
  las Tasks 9-12). Este task solo cambia la firma y el andamiaje.

- [ ] **Step 4: `MainActivity.kt`**

```kotlin
setContent {
    val deps = remember {
        val http = crearHttpClient()
        Dependencias(
            repo = AuthRepositoryImpl(http),
            almacen = AlmacenCredenciales(applicationContext),
        )
    }
    App(deps)
}
```
Elimina/ajusta `AppAndroidPreview` (ya no hay `App()` sin args; puedes
borrar la preview o pasarle unas `Dependencias` fake — lo más simple es
borrarla, no aporta).

- [ ] **Step 5: `MainViewController.kt` (iosMain)**

```kotlin
fun MainViewController() = ComposeUIViewController {
    val deps = remember {
        Dependencias(
            repo = AuthRepositoryImpl(crearHttpClient()),
            almacen = AlmacenCredenciales(),
        )
    }
    App(deps)
}
```
(No se compila aquí.)

- [ ] **Step 6: Compilar**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid && ./gradlew :androidApp:compileDebugKotlin`
Expected: ambos BUILD SUCCESSFUL. (El segundo verifica `MainActivity`.)

- [ ] **Step 7: Commit**

```bash
git add mobile/shared mobile/androidApp
git commit -m "feat(mobile): grafo de dependencias y rutas Desbloqueo/SolicitarAcceso

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 7: LoginScreen real

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/login/LoginScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`

**Interfaces:**
- Consumes: `AuthRepository` (via parámetro), `AlmacenCredenciales`.
- Produces: `LoginScreen(repo, almacen, onLoginSuccess, onIrARegistro)`.
  - En login correcto: si `!almacen.hayCredenciales`, muestra un `AlertDialog`
    "¿Entrar con huella la próxima vez?" (Sí → `almacen.guardar(telefono, password)`);
    luego `onLoginSuccess()`.

- [ ] **Step 1: Reescribir `LoginScreen.kt`**

- Firma: `fun LoginScreen(repo: AuthRepository, almacen: AlmacenCredenciales, onLoginSuccess: () -> Unit, onIrARegistro: () -> Unit)`.
- Estado local: `telefono`, `password`, `var estado by remember { mutableStateOf<EstadoLogin>(EstadoLogin.Editando) }` donde
  `sealed interface EstadoLogin { data object Editando; data object Enviando; data class Error(val mensaje: String) }`,
  `var ofrecerBiometria by remember { mutableStateOf(false) }`.
- Quita el bloque `if (biometria.estaDisponible) { ... }` y el import/uso de
  `rememberBiometricAuthenticator` (la biometría pasa a `DesbloqueoScreen`).
- Botón "Entrar":
  ```kotlin
  onClick = {
      if (telefono.isNotBlank() && password.isNotBlank()) {
          estado = EstadoLogin.Enviando
          scope.launch {
              when (val r = repo.login(telefono, password)) {
                  is ResultadoAuth.Exito ->
                      if (almacen.hayCredenciales) onLoginSuccess()
                      else ofrecerBiometria = true
                  is ResultadoAuth.Error -> estado = EstadoLogin.Error(r.mensaje)
              }
          }
      }
  }
  ```
- Deshabilita el botón y cámbiale el texto cuando `estado is EstadoLogin.Enviando`.
- Muestra el mensaje si `estado is EstadoLogin.Error` (Text rojo, como en la web).
- `if (ofrecerBiometria) { AlertDialog(...) }`:
  - confirmar → `almacen.guardar(telefono, password); onLoginSuccess()`
  - descartar → `onLoginSuccess()`
- Mantén el enlace "Regístrate".

- [ ] **Step 2: `App.kt` — pasar deps a LoginScreen**

```kotlin
is Screen.Login -> LoginScreen(
    repo = deps.repo,
    almacen = deps.almacen,
    onLoginSuccess = { ir(Screen.Panel) },
    onIrARegistro = { ir(Screen.Registro) },
)
```

- [ ] **Step 3: Compilar**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/login/LoginScreen.kt mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt
git commit -m "feat(mobile): LoginScreen contra el backend + oferta de biometría

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 8: DesbloqueoScreen

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/desbloqueo/DesbloqueoScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `AlmacenCredenciales`, `rememberBiometricAuthenticator()`.
- Produces: `DesbloqueoScreen(repo, almacen, onDesbloqueado, onUsarOtraCuenta)`.

- [ ] **Step 1: `DesbloqueoScreen.kt`**

- Layout: `BaniterioWordmark`, título "Desbloquea para entrar", botón
  "Entrar con huella / Face ID", enlace "Usar otra cuenta".
- `val biometria = rememberBiometricAuthenticator()`.
- Estado: `sealed interface EstadoDesbloqueo { Inicial; Autenticando; data class Error(mensaje) }`.
- Botón:
  ```kotlin
  scope.launch {
      estado = Autenticando
      if (!biometria.autenticar()) { estado = Error("No se pudo verificar tu identidad."); return@launch }
      val c = almacen.leer()
      if (c == null) { onUsarOtraCuenta(); return@launch }
      when (val r = repo.login(c.telefono, c.password)) {
          is ResultadoAuth.Exito -> onDesbloqueado()
          is ResultadoAuth.Error -> estado = Error(r.mensaje)
      }
  }
  ```
- Opcional: lanzar la biometría automáticamente al entrar
  (`LaunchedEffect(Unit) { ... }`) además del botón — decídelo al implementar;
  si se hace, que un fallo no entre en bucle.
- "Usar otra cuenta" → `almacen.borrar(); onUsarOtraCuenta()`.

- [ ] **Step 2: `App.kt`**

```kotlin
is Screen.Desbloqueo -> DesbloqueoScreen(
    repo = deps.repo,
    almacen = deps.almacen,
    onDesbloqueado = { ir(Screen.Panel) },
    onUsarOtraCuenta = { ir(Screen.Login) },
)
```

- [ ] **Step 3: Compilar + Commit**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/desbloqueo/ mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt
git commit -m "feat(mobile): DesbloqueoScreen (biometría → login silencioso)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 9: RegistroScreen real + estado NoAutorizado

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/registro/RegistroScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`

**Interfaces:**
- Produces: `RegistroScreen(repo, onRegistroCompletado, onVolverALogin, onSolicitarAcceso: (SolicitudPrecarga) -> Unit)`.

- [ ] **Step 1: Reescribir `RegistroScreen.kt`**

- Estado: `sealed interface EstadoRegistro { Editando; Enviando; Ok; NoAutorizado; data class Error(mensaje) }`.
- Validación cliente: teléfono `Regex("^[67]\\d{8}$")`, password ≥ 6, email `contains("@")`, nombre/apellidos no vacíos.
- Botón "Crear cuenta":
  ```kotlin
  scope.launch {
      estado = Enviando
      val r = repo.registro(RegistroRequest(telefono, email, password, nombre, apellidos, mote))
      estado = when (r) {
          is ResultadoAuth.Exito -> Ok
          is ResultadoAuth.Error -> when (r.codigo) {
              CodigoErrorAuth.TELEFONO_NO_AUTORIZADO -> NoAutorizado
              else -> Error(r.mensaje)
          }
      }
  }
  ```
- `when (estado)`:
  - `Ok` → `Text("¡Cuenta creada!")` + `LaunchedEffect { delay(1200); onRegistroCompletado() }`.
  - `NoAutorizado` → `Text("Teléfono no autorizado: tu número no está en la lista de la peña.")` (rojo) + `Button("Solicitar acceso a la peña") { onSolicitarAcceso(SolicitudPrecarga(nombre, apellidos, telefono, email)) }`.
  - `Error` → mensaje rojo, deja reintentar.
  - resto → el formulario.
- Deshabilita el botón mientras `Enviando`.

- [ ] **Step 2: `App.kt`**

```kotlin
is Screen.Registro -> {
    BackHandler { ir(Screen.Login) }
    RegistroScreen(
        repo = deps.repo,
        onRegistroCompletado = { ir(Screen.Login) },
        onVolverALogin = { ir(Screen.Login) },
        onSolicitarAcceso = { datos -> datosSolicitud = datos; ir(Screen.SolicitarAcceso) },
    )
}
```

- [ ] **Step 3: Compilar + Commit**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/registro/RegistroScreen.kt mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt
git commit -m "feat(mobile): RegistroScreen contra el backend + estado no autorizado

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 10: SolicitarAccesoScreen

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/solicitaracceso/SolicitarAccesoScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `SolicitudPrecarga`.
- Produces: `SolicitarAccesoScreen(repo, precarga: SolicitudPrecarga?, onEnviada, onVolver)`.

- [ ] **Step 1: `SolicitarAccesoScreen.kt`**

- Campos: `nombre`, `apellidos`, `telefono`, `email` (inicializados desde
  `precarga`), `motivo`, `relacion`, `conocidos` (3 `OutlinedTextField`
  multilínea, `minLines = 3`).
- Validación: identidad como en registro; los 3 textos ≥ 10 caracteres.
- Estado: `sealed interface EstadoSolicitud { Editando; Enviando; Ok; data class Error(mensaje) }`.
- Botón "Enviar solicitud":
  ```kotlin
  repo.solicitarAcceso(SolicitudIngresoRequest(telefono, email, nombre, apellidos, motivo, relacion, conocidos))
  ```
  - `Exito` → `Ok`.
  - `Error` → `Error(r.mensaje)` (el `mensaje` del enum ya cubre
    `SOLICITUD_YA_PENDIENTE` y `TELEFONO_YA_AUTORIZADO`).
- `Ok` → `Text("Solicitud enviada. Un administrador la revisará.")` + `Button("Volver") { onVolver() }`.

- [ ] **Step 2: `App.kt`**

```kotlin
is Screen.SolicitarAcceso -> {
    BackHandler { ir(Screen.Registro) }
    SolicitarAccesoScreen(
        repo = deps.repo,
        precarga = datosSolicitud,
        onEnviada = { ir(Screen.Login) },
        onVolver = { ir(Screen.Registro) },
    )
}
```

- [ ] **Step 3: Compilar + Commit**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid`
```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/auth/solicitaracceso/ mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt
git commit -m "feat(mobile): SolicitarAccesoScreen

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Task 11: PanelScreen — logout real + cierre

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/panel/PanelScreen.kt`
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`
- Modify: `mobile/README.md`

**Interfaces:**
- Produces: `PanelScreen(onAbrirSeccion, onCerrarSesion)` sin cambio de firma;
  el `onCerrarSesion` que le pasa `App.kt` hace `repo.logout()` +
  `almacen.borrar()`.

- [ ] **Step 1: `App.kt`**

```kotlin
is Screen.Panel -> PanelScreen(
    onAbrirSeccion = { destino -> ir(destino) },
    onCerrarSesion = {
        deps.repo.logout()
        deps.almacen.borrar()
        ir(Screen.Login)
    },
)
```
(No hace falta tocar `PanelScreen.kt` si el texto "Cerrar sesión" ya llama a
`onCerrarSesion`. Verifícalo; si no, ajústalo.)

- [ ] **Step 2: README**

Añade a `mobile/README.md` una nota:
- La app habla con el backend en `10.0.2.2:8080` (emulador Android) /
  `localhost:8080` (simulador iOS). Arranca el backend antes.
- Dispositivo físico: cambia `API_BASE_URL` en
  `shared/src/androidMain/.../data/ApiConfig.android.kt` por la IP LAN del PC.
- iOS: `AlmacenCredenciales.ios.kt` y el `BiometricAuthenticator` de iOS
  están sin verificar; revisar al compilar en Mac.

- [ ] **Step 3: Compilación completa**

Run: `cd mobile && ./gradlew :shared:compileDebugKotlinAndroid :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL. Se genera el APK en
`mobile/androidApp/build/outputs/apk/debug/`.

- [ ] **Step 4: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/panel/PanelScreen.kt mobile/README.md
git commit -m "feat(mobile): logout real desde el panel + notas de arranque

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review

**1. Cobertura del spec:**

| Requisito del spec | Task |
|---|---|
| Ktor + kotlinx.serialization en `shared` | Task 1 |
| DTOs `@Serializable` espejo del backend | Task 2 |
| `ResultadoAuth` / `CodigoErrorAuth` + mapeo | Task 2 |
| URL base `expect/actual` (10.0.2.2 / localhost) | Task 3 |
| Cleartext a 10.0.2.2 en Android | Task 3 |
| `HttpClient` con motor por plataforma | Task 4 |
| `AuthRepository` (registro/login/solicitud/logout), token en memoria | Task 4 |
| Plugin Bearer para `/yo` | Task 4 — **hueco**: el plan no añade el plugin de Authorization. Ver nota abajo. |
| `AlmacenCredenciales` expect/actual (EncryptedSharedPreferences / Keychain) | Task 5 |
| `Credenciales(telefono, password)` | Task 5 |
| `Screen` + `Desbloqueo` + `SolicitarAcceso` | Task 6 |
| `App.kt` cablea deps, decide pantalla inicial | Task 6 |
| Entry points construyen `Dependencias` | Task 6 |
| `DesbloqueoScreen` (biometría → login silencioso, "usar otra cuenta") | Task 8 |
| `LoginScreen` real, sin botón biométrico, oferta de biometría | Task 7 |
| `RegistroScreen` real, `NoAutorizado` + botón | Task 9 |
| `SolicitarAccesoScreen` (precarga + 3 textos) | Task 10 |
| `PanelScreen` logout real | Task 11 |
| Tabla de mensajes de error | Task 2 (`CodigoErrorAuth.mensaje`) |
| Verificación: compila Android; iOS no | todos los tasks + Task 11 `assembleDebug` |
| README con notas de arranque / IP / iOS | Task 11 |

**Hueco corregido:** el plugin de `Authorization: Bearer` no tiene task
propia. Como ningún endpoint del móvil lo necesita todavía (registro, login
y solicitud son públicos; `/yo` no se llama), **se difiere**: cuando el
móvil tenga que llamar a un endpoint protegido, se añade en `crearHttpClient`
un `install(Auth) { bearer { loadTokens { ... } } }` o una cabecera por
defecto leyendo el token del repo. Anotado como follow-up en el README
(Task 11 Step 2).

**2. Placeholders:** las notas "decídelo al implementar" en Task 4 (forma
exacta de compartir la config de Ktor) y Task 5 (fallback iOS) son
decisiones acotadas con un camino por defecto indicado, no huecos. El código
iOS se marca explícitamente como no verificado.

**3. Consistencia de tipos:**
- `AuthRepository.login` devuelve `ResultadoAuth<UsuarioResponse>` en todas
  las tasks que lo consumen (7, 8).
- `SolicitudPrecarga` se define en Task 6 y se usa en 9 y 10 con la misma
  forma `(nombre, apellidos, telefono, email)`.
- `Dependencias(repo, almacen)` — misma forma en Task 6 (def), MainActivity,
  MainViewController, y todos los `App.kt` `when`.
- `crearHttpClient()` sin args — Task 4 def, Task 6 uso.
- Firma de `App`: pasa de `App()` a `App(deps: Dependencias)` en Task 6;
  Tasks 7-11 asumen ya esa firma. Previews de Android ajustadas en Task 6.
