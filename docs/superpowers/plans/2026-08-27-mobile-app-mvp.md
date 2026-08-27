# Mobile App MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replicate the web front-end's Login/Registro/Panel/Historia flow in the Kotlin Multiplatform + Compose Multiplatform app (`mobile/`), with the app entering directly on Login (no public landing screen) and a biometric login option.

**Architecture:** All UI lives in `mobile/shared/src/commonMain` (shared between Android and iOS). Navigation is a plain `when` over a `sealed class Screen` held in `remember { mutableStateOf(...) }` at the `App()` root — no navigation library. Biometric auth is an `expect`/`actual` `BiometricAuthenticator`: real `androidx.biometric.BiometricPrompt` on Android, a simulated stub on iOS (compiled later on a Mac mini).

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, Material3 1.11.0-alpha07, `androidx.biometric:biometric:1.1.0` (Android only), `kotlinx.coroutines:1.9.0`.

**Spec:** `docs/superpowers/specs/2026-08-27-mobile-app-mvp-design.md`

## Global Constraints

- No calls to `back/` — there is no identity backend yet. Any non-empty login/registro form submission is treated as successful (mirrors `front/src/app/login/login.ts`).
- No session persistence between app restarts.
- Miembros/Eventos/Cuentas/Inventario/Ropa are placeholder cards only ("PRÓXIMAMENTE"), not tappable — do not build real screens for them in this plan.
- Colors, exact hex values: brand `#372FA5`, brandBright `#6F63D8`, gold `#F8D349`, goldSoft `#FFE896`, ink `#F5F3FF`, muted `#A79FE0`, surface `#120F2E`, panel `#1C1745`, outline `#332C6E`.
- iOS target: code must compile as Kotlin (expect/actual pairing must be syntactically complete), but full iOS toolchain verification happens later on the Mac mini — don't block this plan on it.

---

### Task 1: Color tokens

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/theme/Color.kt`

**Interfaces:**
- Produces: `object BaniterioColors` with `val brand, brandBright, gold, goldSoft, ink, muted, surface, panel, outline: Color`

- [ ] **Step 1: Create the color tokens file**

```kotlin
package com.baniterio.app.theme

import androidx.compose.ui.graphics.Color

object BaniterioColors {
    val brand = Color(0xFF372FA5)
    val brandBright = Color(0xFF6F63D8)
    val gold = Color(0xFFF8D349)
    val goldSoft = Color(0xFFFFE896)
    val ink = Color(0xFFF5F3FF)
    val muted = Color(0xFFA79FE0)
    val surface = Color(0xFF120F2E)
    val panel = Color(0xFF1C1745)
    val outline = Color(0xFF332C6E)
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/theme/Color.kt
git commit -m "feat(mobile): add Bañiterio color tokens"
```

---

### Task 2: Bundle fonts and create typography

**Files:**
- Create: `mobile/shared/src/commonMain/composeResources/font/unbounded_bold.ttf`
- Create: `mobile/shared/src/commonMain/composeResources/font/archivo_regular.ttf`
- Create: `mobile/shared/src/commonMain/composeResources/font/archivo_semibold.ttf`
- Create: `mobile/shared/src/commonMain/composeResources/font/metal_mania_regular.ttf`
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/theme/Type.kt`

**Interfaces:**
- Produces: `data class BaniterioFonts(display, sans, metal: FontFamily)`, `@Composable fun baniterioFonts(): BaniterioFonts`, `@Composable fun baniterioTypography(fonts: BaniterioFonts): Typography`

- [ ] **Step 1: Download the font files**

Run from the repo root (the legacy user-agent forces Google to serve `.ttf` instead of `.woff2`, since `Font()` from Compose resources needs `.ttf`/`.otf`):

```bash
UA="Mozilla/5.0 (Windows NT 6.1) AppleWebKit/534.34 (KHTML, like Gecko) PhantomJS/1.9.0 Safari/534.34"
mkdir -p mobile/shared/src/commonMain/composeResources/font
cd mobile/shared/src/commonMain/composeResources/font

curl -s -A "$UA" "https://fonts.googleapis.com/css?family=Unbounded:700" -o u.css
curl -s "$(grep -o 'https://fonts.gstatic.com/[^)]*' u.css | head -1)" -o unbounded_bold.ttf

curl -s -A "$UA" "https://fonts.googleapis.com/css?family=Archivo:400,600" -o a.css
curl -s "$(grep -o 'https://fonts.gstatic.com/[^)]*' a.css | sed -n '1p')" -o archivo_regular.ttf
curl -s "$(grep -o 'https://fonts.gstatic.com/[^)]*' a.css | sed -n '2p')" -o archivo_semibold.ttf

curl -s -A "$UA" "https://fonts.googleapis.com/css?family=Metal+Mania" -o m.css
curl -s "$(grep -o 'https://fonts.gstatic.com/[^)]*' m.css | head -1)" -o metal_mania_regular.ttf

rm u.css a.css m.css
```

- [ ] **Step 2: Verify the four files exist and are non-empty**

Run: `ls -la mobile/shared/src/commonMain/composeResources/font/`
Expected: `unbounded_bold.ttf`, `archivo_regular.ttf`, `archivo_semibold.ttf`, `metal_mania_regular.ttf`, each several KB to ~1MB (not 0 bytes — a 0-byte file means the grep/curl pipeline picked the wrong line; open the `.css` before deleting it and re-check which line has the right weight).

- [ ] **Step 3: Create the typography file**

```kotlin
package com.baniterio.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import baniterio.shared.generated.resources.Res
import baniterio.shared.generated.resources.archivo_regular
import baniterio.shared.generated.resources.archivo_semibold
import baniterio.shared.generated.resources.metal_mania_regular
import baniterio.shared.generated.resources.unbounded_bold
import org.jetbrains.compose.resources.Font

data class BaniterioFonts(
    val display: FontFamily,
    val sans: FontFamily,
    val metal: FontFamily,
)

@Composable
fun baniterioFonts(): BaniterioFonts {
    val display = FontFamily(Font(Res.font.unbounded_bold, FontWeight.Bold))
    val sans = FontFamily(
        Font(Res.font.archivo_regular, FontWeight.Normal),
        Font(Res.font.archivo_semibold, FontWeight.SemiBold),
    )
    val metal = FontFamily(Font(Res.font.metal_mania_regular, FontWeight.Normal))
    return BaniterioFonts(display = display, sans = sans, metal = metal)
}

@Composable
fun baniterioTypography(fonts: BaniterioFonts): Typography {
    val base = Typography()
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = fonts.display, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = fonts.sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = fonts.sans),
        labelLarge = base.labelLarge.copy(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold),
    )
}
```

- [ ] **Step 4: Verify commonMain compiles (this regenerates the `Res.font.*` accessors from the files in Step 1)**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`. If it fails with "unresolved reference: unbounded_bold" (or similar), the font file names don't match — Compose resource file names must be lowercase `[a-z0-9_]+.ttf`, exactly matching the accessor name used in the import.

- [ ] **Step 5: Commit**

```bash
git add mobile/shared/src/commonMain/composeResources/font/ mobile/shared/src/commonMain/kotlin/com/baniterio/app/theme/Type.kt
git commit -m "feat(mobile): bundle brand fonts and add typography"
```

---

### Task 3: Theme wrapper

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/theme/Theme.kt`

**Interfaces:**
- Consumes: `BaniterioColors` (Task 1), `baniterioFonts()`/`baniterioTypography()` (Task 2)
- Produces: `@Composable fun BaniterioTheme(content: @Composable () -> Unit)`

- [ ] **Step 1: Create the theme file**

```kotlin
package com.baniterio.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BaniterioColorScheme = darkColorScheme(
    primary = BaniterioColors.brand,
    onPrimary = BaniterioColors.gold,
    secondary = BaniterioColors.brandBright,
    onSecondary = BaniterioColors.ink,
    background = BaniterioColors.surface,
    onBackground = BaniterioColors.ink,
    surface = BaniterioColors.panel,
    onSurface = BaniterioColors.ink,
    outline = BaniterioColors.outline,
)

@Composable
fun BaniterioTheme(content: @Composable () -> Unit) {
    val fonts = baniterioFonts()
    MaterialTheme(
        colorScheme = BaniterioColorScheme,
        typography = baniterioTypography(fonts),
        content = content,
    )
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/theme/Theme.kt
git commit -m "feat(mobile): add BaniterioTheme"
```

---

### Task 4: Section model

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/model/Seccion.kt`

**Interfaces:**
- Produces: `data class Seccion(nombre: String, descripcion: String, tieneContenido: Boolean = false)`, `val seccionesPanel: List<Seccion>`

- [ ] **Step 1: Create the model file**

```kotlin
package com.baniterio.app.model

data class Seccion(
    val nombre: String,
    val descripcion: String,
    val tieneContenido: Boolean = false,
)

val seccionesPanel = listOf(
    Seccion("Historia", "Cómo nació el Bañiterio y qué significa su escudo.", tieneContenido = true),
    Seccion("Miembros", "Socios de la peña y sus datos de contacto."),
    Seccion("Eventos", "Calendario y organización de las quedadas y fiestas de la peña."),
    Seccion("Cuentas", "Ingresos, gastos y balance de la peña."),
    Seccion("Inventario", "Material y enseres que tiene la peña."),
    Seccion("Ropa", "Pedidos y tallas del vestuario de la peña."),
)
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/model/Seccion.kt
git commit -m "feat(mobile): add Seccion model and panel section list"
```

---

### Task 5: Navigation state

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt`

**Interfaces:**
- Produces: `sealed class Screen { data object Login; data object Registro; data object Panel; data object Historia }`

- [ ] **Step 1: Create the sealed class**

```kotlin
package com.baniterio.app.nav

sealed class Screen {
    data object Login : Screen()
    data object Registro : Screen()
    data object Panel : Screen()
    data object Historia : Screen()
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/nav/Screen.kt
git commit -m "feat(mobile): add Screen navigation sealed class"
```

---

### Task 6: Biometric authenticator — expect declaration

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/biometric/BiometricAuthenticator.kt`
- Modify: `mobile/gradle/libs.versions.toml`
- Modify: `mobile/shared/build.gradle.kts`

**Interfaces:**
- Produces: `expect class BiometricAuthenticator { val estaDisponible: Boolean; suspend fun autenticar(): Boolean }`, `@Composable expect fun rememberBiometricAuthenticator(): BiometricAuthenticator`

- [ ] **Step 1: Add the coroutines version and library entry**

In `mobile/gradle/libs.versions.toml`, add to `[versions]` (after `kotlin = "2.4.10"`):

```toml
kotlinx-coroutines = "1.9.0"
```

Add to `[libraries]` (after `kotlin-testJunit = ...`):

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
```

- [ ] **Step 2: Add the dependency to commonMain**

In `mobile/shared/build.gradle.kts`, inside `sourceSets { commonMain.dependencies { ... } }`, add:

```kotlin
implementation(libs.kotlinx.coroutines.core)
```

- [ ] **Step 3: Create the expect declaration**

```kotlin
package com.baniterio.app.biometric

import androidx.compose.runtime.Composable

expect class BiometricAuthenticator {
    val estaDisponible: Boolean
    suspend fun autenticar(): Boolean
}

@Composable
expect fun rememberBiometricAuthenticator(): BiometricAuthenticator
```

- [ ] **Step 4: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL` — an `expect` with no `actual` yet does NOT fail metadata compilation (the mismatch is only caught when compiling a concrete target, which Tasks 7 and 8 do).

- [ ] **Step 5: Commit**

```bash
git add mobile/gradle/libs.versions.toml mobile/shared/build.gradle.kts mobile/shared/src/commonMain/kotlin/com/baniterio/app/biometric/BiometricAuthenticator.kt
git commit -m "feat(mobile): declare expect BiometricAuthenticator"
```

---

### Task 7: Biometric authenticator — Android actual

**Files:**
- Create: `mobile/shared/src/androidMain/kotlin/com/baniterio/app/biometric/BiometricAuthenticator.android.kt`
- Modify: `mobile/gradle/libs.versions.toml`
- Modify: `mobile/shared/build.gradle.kts`
- Modify: `mobile/androidApp/build.gradle.kts`
- Modify: `mobile/androidApp/src/main/kotlin/com/baniterio/app/MainActivity.kt`

**Interfaces:**
- Consumes: `expect class BiometricAuthenticator` (Task 6)
- Produces: `actual class BiometricAuthenticator(activity: FragmentActivity)`, `@Composable actual fun rememberBiometricAuthenticator(): BiometricAuthenticator`

- [ ] **Step 1: Add the biometric version and library entry**

In `mobile/gradle/libs.versions.toml`, add to `[versions]`:

```toml
androidx-biometric = "1.1.0"
```

Add to `[libraries]`:

```toml
androidx-biometric = { module = "androidx.biometric:biometric", version.ref = "androidx-biometric" }
```

- [ ] **Step 2: Add the dependency where it's used**

In `mobile/shared/build.gradle.kts`, inside `sourceSets { androidMain.dependencies { ... } }`, add:

```kotlin
implementation(libs.androidx.biometric)
```

In `mobile/androidApp/build.gradle.kts`, inside the top-level `dependencies { ... }` block, add:

```kotlin
implementation(libs.androidx.biometric)
```

(`androidx.biometric` depends on `androidx.fragment:fragment`, which is what makes `FragmentActivity` resolvable in `MainActivity.kt` below. If Step 5's build fails with "unresolved reference: FragmentActivity", add `implementation("androidx.fragment:fragment:<latest stable>")` explicitly to `androidApp/build.gradle.kts` as well — check the current stable version on the AndroidX releases page first.)

- [ ] **Step 3: Change MainActivity to extend FragmentActivity**

In `mobile/androidApp/src/main/kotlin/com/baniterio/app/MainActivity.kt`, replace the whole file:

```kotlin
package com.baniterio.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
```

- [ ] **Step 4: Create the Android actual implementation**

```kotlin
package com.baniterio.app.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

actual class BiometricAuthenticator(private val activity: FragmentActivity) {
    actual val estaDisponible: Boolean
        get() = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    actual suspend fun autenticar(): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) continuation.resume(false)
            }

            override fun onAuthenticationFailed() {
                // El usuario puede reintentar sin que la corrutina se resuelva todavía.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceder a la peña")
            .setSubtitle("Usa tu huella o Face ID")
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(info)
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    val activity = LocalContext.current as FragmentActivity
    return remember(activity) { BiometricAuthenticator(activity) }
}
```

- [ ] **Step 5: Verify the Android app builds**

Run: `cd mobile && ./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add mobile/gradle/libs.versions.toml mobile/shared/build.gradle.kts mobile/androidApp/build.gradle.kts mobile/androidApp/src/main/kotlin/com/baniterio/app/MainActivity.kt mobile/shared/src/androidMain/kotlin/com/baniterio/app/biometric/BiometricAuthenticator.android.kt
git commit -m "feat(mobile): implement real BiometricPrompt on Android"
```

---

### Task 8: Biometric authenticator — iOS actual (stub)

**Files:**
- Create: `mobile/shared/src/iosMain/kotlin/com/baniterio/app/biometric/BiometricAuthenticator.ios.kt`

**Interfaces:**
- Consumes: `expect class BiometricAuthenticator` (Task 6)
- Produces: `actual class BiometricAuthenticator()`, `@Composable actual fun rememberBiometricAuthenticator(): BiometricAuthenticator`

- [ ] **Step 1: Create the iOS stub actual**

```kotlin
package com.baniterio.app.biometric

import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay

actual class BiometricAuthenticator {
    actual val estaDisponible: Boolean = true

    actual suspend fun autenticar(): Boolean {
        // TODO: integrar LocalAuthentication (Face ID/Touch ID) cuando este target
        // se compile en el Mac mini. De momento simula un acceso correcto.
        delay(600)
        return true
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator = BiometricAuthenticator()
```

- [ ] **Step 2: Verify commonMain still compiles (iOS toolchain isn't available on this machine — full iOS compilation is verified later, on the Mac mini)**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`. Also visually re-check this file against Task 6's `expect` block: same member names (`estaDisponible`, `autenticar`), same `rememberBiometricAuthenticator` signature — a mismatch here won't be caught until the iOS target actually compiles.

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/iosMain/kotlin/com/baniterio/app/biometric/BiometricAuthenticator.ios.kt
git commit -m "feat(mobile): add simulated BiometricAuthenticator stub for iOS"
```

---

### Task 9: Login screen

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/login/LoginScreen.kt`

**Interfaces:**
- Consumes: `BaniterioColors` (Task 1), `rememberBiometricAuthenticator()` (Task 6)
- Produces: `@Composable fun LoginScreen(onLoginSuccess: () -> Unit, onIrARegistro: () -> Unit)`

- [ ] **Step 1: Create the screen**

```kotlin
package com.baniterio.app.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baniterio.app.biometric.rememberBiometricAuthenticator
import com.baniterio.app.theme.BaniterioColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onIrARegistro: () -> Unit,
) {
    var telefono by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val biometria = rememberBiometricAuthenticator()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Accede a la peña",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Introduce tu teléfono y contraseña para entrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { if (telefono.isNotBlank() && password.isNotBlank()) onLoginSuccess() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BaniterioColors.brand,
                contentColor = BaniterioColors.gold,
            ),
        ) {
            Text("Entrar", fontWeight = FontWeight.Bold)
        }

        if (biometria.estaDisponible) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (biometria.autenticar()) onLoginSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Entrar con huella / Face ID")
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("¿No tienes cuenta todavía? ", color = BaniterioColors.muted)
            Text(
                text = "Regístrate",
                color = BaniterioColors.brandBright,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onIrARegistro() },
            )
        }
    }
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/login/LoginScreen.kt
git commit -m "feat(mobile): add LoginScreen with biometric option"
```

---

### Task 10: Registro screen

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/registro/RegistroScreen.kt`

**Interfaces:**
- Consumes: `BaniterioColors` (Task 1)
- Produces: `@Composable fun RegistroScreen(onRegistroCompletado: () -> Unit, onVolverALogin: () -> Unit)`

- [ ] **Step 1: Create the screen**

```kotlin
package com.baniterio.app.ui.registro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors

@Composable
fun RegistroScreen(
    onRegistroCompletado: () -> Unit,
    onVolverALogin: () -> Unit,
) {
    var nombre by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var mote by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val formularioValido = nombre.isNotBlank() && apellidos.isNotBlank() &&
        telefono.isNotBlank() && email.contains("@") && password.length >= 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Únete a la peña",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Rellena tus datos para darte de alta.",
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = apellidos,
            onValueChange = { apellidos = it },
            label = { Text("Apellidos") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = mote,
            onValueChange = { mote = it },
            label = { Text("Mote (opcional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { if (formularioValido) onRegistroCompletado() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BaniterioColors.brand,
                contentColor = BaniterioColors.gold,
            ),
        ) {
            Text("Crear cuenta", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))
        Row {
            Text("¿Ya tienes cuenta? ", color = BaniterioColors.muted)
            Text(
                text = "Entra aquí",
                color = BaniterioColors.brandBright,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onVolverALogin() },
            )
        }
    }
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/registro/RegistroScreen.kt
git commit -m "feat(mobile): add RegistroScreen"
```

---

### Task 11: Panel screen

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/panel/PanelScreen.kt`

**Interfaces:**
- Consumes: `Seccion`, `seccionesPanel` (Task 4), `BaniterioColors` (Task 1)
- Produces: `@Composable fun PanelScreen(onAbrirHistoria: () -> Unit)`

- [ ] **Step 1: Create the screen**

```kotlin
package com.baniterio.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.model.Seccion
import com.baniterio.app.model.seccionesPanel
import com.baniterio.app.theme.BaniterioColors

@Composable
fun PanelScreen(onAbrirHistoria: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "¡Bienvenido de vuelta a la Bañiterio!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            Text(
                text = "Desde aquí podrás llevar la historia, los miembros, los eventos, las cuentas, el inventario y la ropa de la peña.",
                style = MaterialTheme.typography.bodyMedium,
                color = BaniterioColors.muted,
            )
        }
        items(seccionesPanel) { seccion ->
            TarjetaSeccion(seccion = seccion, onClick = { if (seccion.tieneContenido) onAbrirHistoria() })
        }
    }
}

@Composable
private fun TarjetaSeccion(seccion: Seccion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BaniterioColors.panel)
            .clickable(enabled = seccion.tieneContenido, onClick = onClick)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = seccion.nombre,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            if (!seccion.tieneContenido) {
                Text(
                    text = "PRÓXIMAMENTE",
                    style = MaterialTheme.typography.labelLarge,
                    color = BaniterioColors.muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = seccion.descripcion,
            style = MaterialTheme.typography.bodyMedium,
            color = BaniterioColors.muted,
        )
    }
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/panel/PanelScreen.kt
git commit -m "feat(mobile): add PanelScreen with section cards"
```

---

### Task 12: Historia screen

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/historia/HistoriaScreen.kt`

**Interfaces:**
- Consumes: `BaniterioColors` (Task 1)
- Produces: `@Composable fun HistoriaScreen(onVolver: () -> Unit)`

- [ ] **Step 1: Create the screen**

```kotlin
package com.baniterio.app.ui.historia

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baniterio.app.theme.BaniterioColors

@Composable
fun HistoriaScreen(onVolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "← Volver",
            color = BaniterioColors.muted,
            modifier = Modifier.clickable { onVolver() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Historia del Bañiterio",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "El Bañiterio nace en Robledo del Mazo, capital del Valle del Gévalo, de la unión de dos peñas con mucha historia propia. Por un lado, El Bañito Bañito, que vistió primero de blanco y después de rojo. Por otro, El Bruterio, fiel al azul desde siempre. De la mezcla de esos dos colores —rojo y azul— nació el morado que hoy nos identifica, y de la unión de su gente, el Bañiterio.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nuestro escudo nace de esa misma unión: el Bruterio llevaba una jarra de cerveza rompiéndose junto al lema «aquí no se friega», y el Bañito Bañito, un chico bañándose de noche. El logo del Bañiterio es la mezcla de los dos: el chico del Bañito tirándose en bomba dentro de la jarra del Bruterio. Y en el hombro izquierdo lleva una rosa, en recuerdo de nuestra amiga Rosi, que ya no está con nosotros pero sigue siendo parte de esta familia.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Pero el Bañiterio es mucho más que las fiestas del pueblo: es un grupo de amigos unido durante todo el año. Organizamos eventos propios, como las Migas Santas y las Chuletas Santas, y colaboramos de forma desinteresada en las fiestas de San Miguel, echando una mano con los juegos y con todo lo que haga falta.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.muted,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Amistad, buen rollo y grandes amigos: así es la familia del Bañiterio.",
            style = MaterialTheme.typography.bodyLarge,
            color = BaniterioColors.goldSoft,
            fontWeight = FontWeight.Bold,
        )
    }
}
```

- [ ] **Step 2: Verify commonMain compiles**

Run: `cd mobile && ./gradlew :shared:compileCommonMainKotlinMetadata`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add mobile/shared/src/commonMain/kotlin/com/baniterio/app/ui/historia/HistoriaScreen.kt
git commit -m "feat(mobile): add HistoriaScreen with the real peña history"
```

---

### Task 13: Wire navigation in App.kt and remove template demo code

**Files:**
- Modify: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/App.kt`
- Delete: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/Greeting.kt`
- Delete: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/GreetingUtil.kt`
- Delete: `mobile/shared/src/commonMain/kotlin/com/baniterio/app/Platform.kt`
- Delete: `mobile/shared/src/androidMain/kotlin/com/baniterio/app/Platform.android.kt`
- Delete: `mobile/shared/src/iosMain/kotlin/com/baniterio/app/Platform.ios.kt`
- Delete: `mobile/shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml`
- Delete: `mobile/shared/src/commonTest/kotlin/com/baniterio/app/SharedCommonTest.kt`
- Delete: `mobile/shared/src/androidHostTest/kotlin/com/baniterio/app/SharedLogicAndroidHostTest.kt`
- Delete: `mobile/shared/src/iosTest/kotlin/com/baniterio/app/SharedLogicIOSTest.kt`

**Interfaces:**
- Consumes: `Screen` (Task 5), `BaniterioTheme` (Task 3), `LoginScreen`/`RegistroScreen`/`PanelScreen`/`HistoriaScreen` (Tasks 9–12)

- [ ] **Step 1: Delete the template demo files**

These are the KMP wizard's placeholder "Click me! / Compose: Hello, Android!" demo — dead code once `App.kt` no longer calls `Greeting()`.

```bash
cd mobile
rm shared/src/commonMain/kotlin/com/baniterio/app/Greeting.kt
rm shared/src/commonMain/kotlin/com/baniterio/app/GreetingUtil.kt
rm shared/src/commonMain/kotlin/com/baniterio/app/Platform.kt
rm shared/src/androidMain/kotlin/com/baniterio/app/Platform.android.kt
rm shared/src/iosMain/kotlin/com/baniterio/app/Platform.ios.kt
rm shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml
rm shared/src/commonTest/kotlin/com/baniterio/app/SharedCommonTest.kt
rm shared/src/androidHostTest/kotlin/com/baniterio/app/SharedLogicAndroidHostTest.kt
rm shared/src/iosTest/kotlin/com/baniterio/app/SharedLogicIOSTest.kt
```

- [ ] **Step 2: Rewrite App.kt**

```kotlin
package com.baniterio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.baniterio.app.nav.Screen
import com.baniterio.app.theme.BaniterioTheme
import com.baniterio.app.ui.historia.HistoriaScreen
import com.baniterio.app.ui.login.LoginScreen
import com.baniterio.app.ui.panel.PanelScreen
import com.baniterio.app.ui.registro.RegistroScreen

@Composable
fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    BaniterioTheme {
        when (screen) {
            is Screen.Login -> LoginScreen(
                onLoginSuccess = { screen = Screen.Panel },
                onIrARegistro = { screen = Screen.Registro },
            )
            is Screen.Registro -> RegistroScreen(
                onRegistroCompletado = { screen = Screen.Panel },
                onVolverALogin = { screen = Screen.Login },
            )
            is Screen.Panel -> PanelScreen(
                onAbrirHistoria = { screen = Screen.Historia },
            )
            is Screen.Historia -> HistoriaScreen(
                onVolver = { screen = Screen.Panel },
            )
        }
    }
}
```

- [ ] **Step 3: Build the Android app**

Run: `cd mobile && ./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manually verify the full flow on an emulator or device**

From Android Studio, hit Run on `androidApp`, or:

```bash
cd mobile && ./gradlew :androidApp:installDebug
```

Then on the device: confirm the app opens directly on **Login** (no landing page); type any phone/password and tap **Entrar** → lands on **Panel**; go back (relaunch or add temporary back-nav if needed) and instead try **Entrar con huella / Face ID** (only shown if the device/emulator has biometrics enrolled) → also lands on **Panel**; on **Panel**, tap **Historia** → shows the real history text with a working **Volver**; tap any of Miembros/Eventos/Cuentas/Inventario/Ropa → nothing happens (they're inert placeholders, as intended); from **Login**, tap **Regístrate** → shows the 6-field form; submitting it (with valid data) also lands on **Panel**.

- [ ] **Step 5: Commit**

```bash
git add -A mobile/shared mobile/androidApp
git commit -m "feat(mobile): wire navigation, remove KMP wizard demo code"
```

---

## Self-review notes

- **Spec coverage:** Login+biometric (Task 9, 6-8), Registro (Task 10), Panel with 6 sections incl. Historia (Task 11, 4), Historia detail with real content (Task 12), shared theme (1-3), no-navigation-library decision honored (manual `Screen` sealed class, Task 5/13), Android `FragmentActivity` change (Task 7), iOS stub (Task 8) — every spec section has a task.
- **Type consistency checked:** `BiometricAuthenticator`'s `estaDisponible`/`autenticar()` names match across the expect (Task 6), Android actual (Task 7), and iOS actual (Task 8). `Seccion(nombre, descripcion, tieneContenido)` used identically in Task 4 and Task 11. Screen callback names (`onLoginSuccess`, `onIrARegistro`, `onRegistroCompletado`, `onVolverALogin`, `onAbrirHistoria`, `onVolver`) match between each screen's signature (Tasks 9–12) and how `App.kt` calls them (Task 13).
- **No placeholders:** every step has literal code or a literal shell command; the one `TODO` (iOS Face ID) is an intentional deferred-work marker documented in the spec, not a stand-in for missing plan content.
