# Autenticación real en la app móvil — diseño

## Contexto

La web (`front/`) ya tiene registro, login, sesión (guard + interceptor) y
solicitud de ingreso funcionando de punta a punta contra el backend
(`back/`, `POST /api/v1/auth/registro | login | solicitudes`, `GET
/api/v1/auth/yo`). La app móvil (`mobile/`, Kotlin Multiplatform + Compose
Multiplatform) tiene las pantallas `LoginScreen`, `RegistroScreen`,
`PanelScreen`, `HistoriaScreen` pero **simulan el éxito**: `LoginScreen`
llama a `onLoginSuccess()` si teléfono y contraseña no están vacíos, sin
tocar ningún backend. No hay cliente HTTP, ni serialización, ni
persistencia, ni capa de datos.

Este documento cubre llevar el móvil a paridad funcional con la web para
autenticación, con un **modelo de sesión propio del móvil** (desbloqueo
biométrico en cada arranque, sin sesión persistente de días).

## Alcance

**Dentro:**

- Cliente HTTP (Ktor) + `kotlinx.serialization` en el módulo `shared`.
- `AuthRepository` en `commonMain`: `registro`, `login`, `solicitarAcceso`,
  `logout`. Mapea los códigos de error del backend a un tipo sellado.
- URL base de la API por plataforma (`expect/actual`).
- Almacenamiento seguro de credenciales (`expect class AlmacenCredenciales`):
  Android `EncryptedSharedPreferences`, iOS Keychain.
- Modelo de sesión: token JWT solo en memoria; cada arranque, si hay
  credenciales guardadas → desbloqueo biométrico → login silencioso →
  Panel.
- Pantalla nueva `DesbloqueoScreen`.
- Pantalla nueva `SolicitarAccesoScreen` (equivalente a `/solicitar-acceso`
  de la web).
- `LoginScreen` y `RegistroScreen` conectadas al `AuthRepository` real, con
  estado de carga y de error.
- Un `ViewModel` por pantalla (las dependencias de
  `lifecycle-viewmodel-compose` ya están en el proyecto).
- Navegación: `Screen` gana `Desbloqueo` y `SolicitarAcceso`; `App.kt`
  decide la pantalla inicial y cablea a mano el grafo de dependencias.

**Fuera:**

- Refresh tokens / sesión persistente de días (el móvil no la quiere).
- Decodificar el `exp` del JWT en el cliente (cada arranque pilla token
  nuevo, no hace falta).
- Integración real de Face ID/Touch ID en iOS: el `BiometricAuthenticator`
  de iOS sigue simulado (`delay(600); return true`) hasta que ese target se
  compile en un Mac. El diseño lo deja preparado.
- Almacenar el token en el enclave seguro con enlace criptográfico a la
  autenticación de usuario (`setUserAuthenticationRequired`): v1 usa cifrado
  en reposo + gate biométrico por UX. Endurecimiento posterior.
- Framework de inyección de dependencias (Koin, etc.): se cablea a mano.
- Panel de administración de solicitudes/teléfonos (feature siguiente,
  común a web y móvil).
- Tests instrumentados / de UI en el móvil. Verificación: compilación del
  target Android + prueba manual en dispositivo por parte del usuario.

## Modelo de sesión

Distinto de la web a propósito.

1. **Token en memoria.** Tras un login correcto, `AuthRepository` guarda el
   JWT en una variable en memoria. No se persiste en disco. Al cerrar la app
   se pierde.
2. **Primer uso en el dispositivo.** No hay credenciales guardadas →
   `App.kt` arranca en `Login`. El usuario se registra (`RegistroScreen` →
   backend → al terminar va a `Login`) o entra directamente
   (`LoginScreen`).
3. **Oferta de biometría.** Tras el primer login correcto, antes de ir al
   Panel, la app pregunta *"¿Entrar con huella/Face ID la próxima vez?"*. Si
   acepta: `AlmacenCredenciales.guardar(telefono, password)`.
4. **Arranques siguientes.** `AlmacenCredenciales.leer() != null` → `App.kt`
   arranca en `Desbloqueo`. Botón "Entrar con huella" →
   `BiometricAuthenticator.autenticar()` → si `true`, `AuthRepository.login`
   con las credenciales leídas → Panel. Si el login falla (p. ej. la
   contraseña cambió, el usuario fue desactivado) → mensaje y botón "Usar
   otra cuenta".
5. **"Usar otra cuenta"** (en `Desbloqueo`) y **"Cerrar sesión"** (en
   `Panel`) → `AlmacenCredenciales.borrar()` + token en memoria a null →
   `Login`.

Con este modelo, el móvil nunca deja una sesión abierta sin biometría de
por medio, y el usuario no vuelve a teclear la contraseña tras el primer
alta.

## Capa de red

**Dependencias nuevas** (`mobile/gradle/libs.versions.toml` +
`shared/build.gradle.kts`), versiones a fijar en el plan:

- `io.ktor:ktor-client-core`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json` en `commonMain`.
- `io.ktor:ktor-client-okhttp` en `androidMain`.
- `io.ktor:ktor-client-darwin` en `iosMain`.
- `org.jetbrains.kotlinx:kotlinx-serialization-json` en `commonMain`.
- Plugin `org.jetbrains.kotlin.plugin.serialization`.

**URL base** — `expect val API_BASE_URL: String`:

- `androidMain`: `"http://10.0.2.2:8080/api/v1"` (el emulador de Android
  mapea `10.0.2.2` al `localhost` del host).
- `iosMain`: `"http://localhost:8080/api/v1"` (el simulador de iOS comparte
  la red del host).
- Un dispositivo físico necesita la IP LAN del PC; queda como ajuste manual
  documentado, no configurable en la UI todavía.
- Android: `http://` en claro requiere `usesCleartextTraffic` /
  `network-security-config` para `10.0.2.2` en debug — el plan lo incluye.

**`AuthRepository`** (`commonMain`, interfaz + una implementación con Ktor):

```
suspend fun registro(datos: RegistroDatos): ResultadoAuth<Usuario>
suspend fun login(telefono: String, password: String): ResultadoAuth<Usuario>
suspend fun solicitarAcceso(datos: SolicitudDatos): ResultadoAuth<Unit>
fun logout()
val usuarioActual: Usuario?   // en memoria, tras login
```

- `login` y `registro` guardan/actualizan el token y el `Usuario` en
  memoria (login devuelve `{token, usuario}`; registro devuelve solo el
  usuario y NO inicia sesión, igual que la web).
- `ResultadoAuth<T>` = sellado: `Exito<T>(dato)`, `Error(codigo:
  CodigoErrorAuth, mensaje: String)`. `CodigoErrorAuth` enum:
  `TELEFONO_NO_AUTORIZADO`, `YA_REGISTRADO`, `CREDENCIALES_INVALIDAS`,
  `VALIDACION`, `SOLICITUD_YA_PENDIENTE`, `TELEFONO_YA_AUTORIZADO`,
  `SIN_CONEXION`, `DESCONOCIDO`. El mapeo: se lee `codigo` del cuerpo JSON
  de error; una excepción de red → `SIN_CONEXION`; cualquier otra →
  `DESCONOCIDO`.
- Un plugin de Ktor añade `Authorization: Bearer <token>` cuando hay token
  en memoria (para `/yo` y futuros endpoints protegidos).

**DTOs** (`commonMain`, `@Serializable`): `RegistroRequest`,
`LoginRequest`, `LoginResponse`, `UsuarioResponse`, `SolicitudIngresoRequest`
— espejo de los records del backend. Los nombres de campo deben coincidir
con el JSON del backend (`esSuperadmin`, `motivo`, `relacion`, `conocidos`,
etc.).

## Almacenamiento seguro

```
expect class AlmacenCredenciales {
    fun guardar(telefono: String, password: String)
    fun leer(): Credenciales?     // Credenciales(telefono, password)
    fun borrar()
    val hayCredenciales: Boolean
}
```

- **Android** (`androidMain`): `EncryptedSharedPreferences` de
  `androidx.security:security-crypto` con `MasterKey` respaldada por el
  Android Keystore. Dependencia nueva. El gate biométrico lo aporta
  `BiometricAuthenticator.autenticar()` en `DesbloqueoScreen` antes de
  llamar a `leer()`.
- **iOS** (`iosMain`): Keychain (`SecItemAdd` / `SecItemCopyMatching` /
  `SecItemDelete`) vía interop de Foundation/Security. Sin dependencias
  externas.
- El constructor necesita contexto de plataforma (Android: `Context`; iOS:
  ninguno). Se instancia en `App.kt` / `MainActivity` / `MainViewController`
  y se pasa a `commonMain` — mismo patrón `expect fun
  rememberBiometricAuthenticator()` que ya existe, o inyección manual.

## Pantallas y navegación

**`nav/Screen.kt`:**

```
sealed class Screen {
    data object Desbloqueo : Screen()
    data object Login : Screen()
    data object Registro : Screen()
    data object SolicitarAcceso : Screen()
    data object Panel : Screen()
    data object Historia : Screen()
}
```

`App.kt` — `aClave()` / `claveAScreen()` ganan las dos nuevas.

**`App.kt`:**

- Crea una sola vez (recordado en la composición raíz): `HttpClient`,
  `AuthRepository`, `AlmacenCredenciales`.
- Pantalla inicial: `if (almacen.hayCredenciales) Screen.Desbloqueo else
  Screen.Login`.
- Pasa el `AuthRepository` / `AlmacenCredenciales` a los ViewModels de cada
  pantalla (fábrica de ViewModel manual).

**`DesbloqueoScreen`** (nueva):

- Logo + texto "Desbloquea para entrar".
- Botón **"Entrar con huella / Face ID"** → ViewModel: `biometria.autenticar()`
  → si ok, `repo.login(credenciales)` → `onDesbloqueado()` (a Panel) ; si el
  login falla, muestra el error y deja reintentar.
- Enlace **"Usar otra cuenta"** → `almacen.borrar()` → `onUsarOtraCuenta()`
  (a Login).
- Estado: `Inicial | Autenticando | Error(mensaje)`.

**`LoginScreen`:**

- ViewModel con estado `Editando | Enviando | Error(mensaje)`.
- Botón "Entrar" → `repo.login(telefono, password)`:
  - Éxito → si NO había credenciales guardadas, diálogo "¿Entrar con huella
    la próxima vez?" (Sí → `almacen.guardar` ) → `onLoginSuccess()`.
  - `CREDENCIALES_INVALIDAS` → "Teléfono o contraseña incorrectos."
  - `SIN_CONEXION` → "No se pudo conectar con el servidor."
  - otro → mensaje genérico.
- Se **elimina el botón biométrico de esta pantalla** (la biometría vive
  ahora en `DesbloqueoScreen`); el `LoginScreen` es solo teléfono+contraseña.
- Enlace "Regístrate" → `onIrARegistro()`.

**`RegistroScreen`:**

- ViewModel con estado `Editando | Enviando | Ok | NoAutorizado |
  Error(mensaje)`.
- Campos: nombre, apellidos, mote (opcional), teléfono, email, contraseña.
  Validación de cliente equivalente a la web (teléfono `^[67]\d{8}$`,
  contraseña ≥ 6, email).
- Botón "Crear cuenta" → `repo.registro(...)`:
  - Éxito → mensaje "Cuenta creada" → `onRegistroCompletado()` (a Login).
  - `TELEFONO_NO_AUTORIZADO` → estado `NoAutorizado`: texto "Teléfono no
    autorizado" + botón **"Solicitar acceso a la peña"** →
    `onSolicitarAcceso(datosTecleados)`.
  - `YA_REGISTRADO` / `VALIDACION` / `SIN_CONEXION` / otro → su mensaje.

**`SolicitarAccesoScreen`** (nueva):

- Recibe los datos de identidad ya tecleados en el registro (precarga).
- Campos: nombre, apellidos, teléfono, email + 3 campos de texto
  (`motivo`, `relacion`, `conocidos`), mínimo 10 caracteres cada uno.
- ViewModel estado `Editando | Enviando | Ok | Error(mensaje)`.
- Botón "Enviar solicitud" → `repo.solicitarAcceso(...)`:
  - Éxito → "Solicitud enviada. Un administrador la revisará." + botón
    "Volver".
  - `SOLICITUD_YA_PENDIENTE` → "Ya hay una solicitud pendiente para ese
    teléfono."
  - `TELEFONO_YA_AUTORIZADO` → "Ese teléfono ya está autorizado. Regístrate
    directamente."
  - `VALIDACION` / `SIN_CONEXION` / otro → su mensaje.

**`PanelScreen`:**

- "Cerrar sesión" → `repo.logout()` + `almacen.borrar()` →
  `onCerrarSesion()` (a Login).

**Navegación en `App.kt`** (resumen del `when`):

- `Desbloqueo` → `onDesbloqueado` = ir a `Panel`; `onUsarOtraCuenta` = ir a
  `Login`.
- `Login` → `onLoginSuccess` = ir a `Panel`; `onIrARegistro` = ir a
  `Registro`.
- `Registro` (con `BackHandler` a `Login`) → `onRegistroCompletado` = ir a
  `Login`; `onVolverALogin` = ir a `Login`; `onSolicitarAcceso(datos)` = ir
  a `SolicitarAcceso` llevando los datos.
- `SolicitarAcceso` (con `BackHandler` a `Registro`) → `onVolver` = ir a
  `Registro`.
- `Panel` → `onAbrirSeccion`, `onCerrarSesion` = ir a `Login`.
- `Historia` sin cambios.

Cómo se pasan los "datos tecleados" de Registro a SolicitarAcceso: un
`var datosSolicitud: SolicitudPrecarga?` a nivel de `App()` (como el
`screenKey`), no en el `Screen` (que se guarda como String).

## Errores — mapeo backend → mensaje

| `codigo` backend | HTTP | Mensaje en el móvil |
|---|---|---|
| `TELEFONO_NO_AUTORIZADO` | 403 | (registro) estado NoAutorizado con botón de solicitud |
| `YA_REGISTRADO` | 409 | "Ya existe una cuenta con ese teléfono o ese email." |
| `CREDENCIALES_INVALIDAS` | 401 | "Teléfono o contraseña incorrectos." |
| `VALIDACION` | 400 | "Revisa los datos del formulario." |
| `SOLICITUD_YA_PENDIENTE` | 409 | "Ya hay una solicitud pendiente para ese teléfono." |
| `TELEFONO_YA_AUTORIZADO` | 409 | "Ese teléfono ya está autorizado. Regístrate directamente." |
| (excepción de red) | — | "No se pudo conectar con el servidor." |
| (cualquier otro) | — | "Algo ha ido mal. Inténtalo de nuevo más tarde." |

## Testing y verificación

- **Automatizado:** ninguno nuevo en el móvil (el proyecto no tiene tests
  de UI ni instrumentados). El backend ya está cubierto por sus IT.
- **Compilación:** el plan se cierra con `./gradlew :shared:compileDebugKotlinAndroid`
  (o `:androidApp:assembleDebug`) en verde. iOS no se compila (necesita macOS).
- **Prueba manual (usuario):** emulador o dispositivo Android — primer alta,
  login, oferta de biometría, arranque con desbloqueo biométrico, "teléfono
  no autorizado" → solicitud, cerrar sesión. iOS lo prueba el usuario por su
  cuenta.
- El backend debe estar corriendo y accesible (emulador Android: en
  `10.0.2.2:8080`).

## Riesgos / notas

- **`EncryptedSharedPreferences`** está en modo mantenimiento en AndroidX;
  sigue siendo la opción estándar y suficiente para v1. Alternativa futura:
  DataStore + Tink, o Keystore directo con enlace a autenticación.
- **iOS Keychain vía cinterop**: el código se escribe pero no se compila en
  este entorno; hay riesgo de ajustes al compilarlo en Mac.
- **`10.0.2.2` en claro**: solo para debug. Release usará HTTPS contra el
  backend desplegado (fuera de alcance).
- Los ViewModels de Compose Multiplatform (`androidx.lifecycle` KMP) son
  relativamente nuevos; si dan problemas en `commonMain`, el fallback es
  estado `remember` + el repositorio compartido (como hace la web).
