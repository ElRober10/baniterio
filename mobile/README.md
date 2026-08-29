This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Backend / autenticación

La app móvil habla con el backend de `back/`. Arráncalo antes de usar la app:

```
cd back && ./mvnw spring-boot:run
```

Queda escuchando en `localhost:8080`.

**URL base por plataforma** (en `shared/src/{android,ios}Main/kotlin/com/baniterio/app/data/ApiConfig.*.kt`):

- Android **emulador**: `http://10.0.2.2:8080/api/v1` (`10.0.2.2` es el `localhost`
  del PC visto desde el emulador). Ya configurado.
- iOS **simulador**: `http://localhost:8080/api/v1`. Ya configurado.
- **Dispositivo físico** (Android o iOS): cambia `API_BASE_URL` en el `ApiConfig`
  de esa plataforma por la IP LAN del PC (p. ej. `http://192.168.1.50:8080/api/v1`)
  y asegúrate de que el móvil y el PC están en la misma red.

**iOS – tráfico en claro**: iOS bloquea HTTP sin cifrar por defecto (App Transport
Security). Para desarrollo, añade a `iosApp/iosApp/Info.plist` una excepción
`NSAppTransportSecurity` → `NSAllowsLocalNetworking` (o `NSExceptionDomains` para
`localhost`). Android ya lo permite vía `network_security_config.xml`.

**iOS – sin verificar en este entorno**: `AlmacenCredenciales.ios.kt` (Keychain) y
el `BiometricAuthenticator` de iOS (hoy simulado, `delay(600); return true`) se han
escrito pero solo se han *compilado* (Kotlin/Native), no ejecutado. Revísalos al
abrir el proyecto en Xcode en un Mac.

**Modelo de sesión del móvil**: el token JWT vive solo en memoria. Tras el primer
login la app ofrece guardar las credenciales (cifradas) para entrar con huella /
Face ID; cada arranque siguiente pide biométrico y re-autentica. "Cerrar sesión"
borra las credenciales guardadas.

**Follow-up**: ningún endpoint que llama el móvil hoy (registro, login, solicitud
de ingreso) necesita `Authorization: Bearer`. Cuando el móvil tenga que llamar a un
endpoint protegido, hay que añadir en `crearHttpClient` un
`install(Auth) { bearer { loadTokens { ... } } }` (o una cabecera por defecto
leyendo el token del repo).

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…