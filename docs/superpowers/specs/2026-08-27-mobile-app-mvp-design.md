# Mobile app MVP — diseño

## Contexto

El front web (`front/`) ya tiene Home, Login, Registro y el Panel autenticado (`/panel`) con
tema oscuro morado/dorado, tipografías Unbounded/Archivo/Metal Mania, y autenticación
simulada (cualquier teléfono/contraseña navega al panel, porque el backend de identidad
no existe todavía).

El proyecto Kotlin Multiplatform + Compose Multiplatform (`mobile/`) ya está scaffolded
(creado desde Android Studio, targets Android + iOS, UI compartida). Este documento
define cómo replicar esas mismas pantallas en `mobile/shared`.

**Diferencias deliberadas frente a la web** (pedidas explícitamente por el usuario):
- La app no tiene landing pública: arranca directamente en Login.
- Login incluye un botón de acceso biométrico (huella/Face ID).
- "Historia" deja de ser contenido público de portada y pasa a ser una sección más
  dentro del Panel — con contenido real, a diferencia de las demás secciones
  (Miembros/Eventos/Cuentas/Inventario/Ropa), que siguen siendo placeholders "Próximamente".

## Alcance

Dentro:
- Pantallas: Login, Registro, Panel (con lista de secciones), Historia (detalle).
- Tema visual compartido (colores, tipografías empaquetadas como recursos).
- Navegación simple sin librería externa.
- Biometría: real en Android (`androidx.biometric`), simulada en iOS.

Fuera (explícitamente, no construir esto ahora):
- Cualquier llamada real a `back/` (no existe backend de identidad todavía).
- Persistencia de sesión entre reinicios de la app.
- Las pantallas de Miembros/Eventos/Cuentas/Inventario/Ropa (siguen como placeholders).
- Guard de navegación real (igual que en la web, `Panel` es alcanzable sin pasar por Login).

## Arquitectura

Todo el código de UI vive en `shared/src/commonMain/kotlin/com/baniterio/app/`:

```
theme/
  Color.kt        — tokens de color (mismos valores que front/src/styles.css)
  Type.kt         — FontFamily por rol (display/sans/metal) cargadas desde composeResources/font
  Theme.kt         — BaniterioTheme (MaterialTheme envuelto con los tokens anteriores)
nav/
  Screen.kt        — sealed class Screen { Login, Registro, Panel, Historia }
ui/
  login/LoginScreen.kt
  registro/RegistroScreen.kt
  panel/PanelScreen.kt      — reutiliza el mismo modelo `Seccion` que front/panel.ts
  historia/HistoriaScreen.kt
biometric/
  BiometricAuthenticator.kt        — expect
App.kt                              — estado de navegación (var screen by remember) + BaniterioTheme
```

**Navegación:** un `var screen by remember { mutableStateOf<Screen>(Screen.Login) }` en `App.kt`,
con un `when` que renderiza la pantalla activa y le pasa callbacks (`onLoginSuccess`, `onIrARegistro`,
`onAbrirSeccion`, `onVolver`). Sin pila de retroceso más allá de "Historia → Panel" y
"Registro → Login"; es toda la complejidad que hace falta para 4 pantallas planas.
No se añade `navigation-compose` (evita un riesgo de versión con Compose Multiplatform 1.11.1 /
Kotlin 2.4.10, que son versiones muy recientes) — si el árbol de pantallas crece mucho se puede
migrar más adelante.

**Biometría** (`expect`/`actual`, mismo patrón que el `Platform.kt` que trae la plantilla):

```kotlin
// commonMain
expect class BiometricAuthenticator {
    val estaDisponible: Boolean
    suspend fun autenticar(): Boolean
}

@Composable
expect fun rememberBiometricAuthenticator(): BiometricAuthenticator
```

- **Android (`androidMain`):** `rememberBiometricAuthenticator` toma `LocalContext.current`
  (casteado a `FragmentActivity`) y construye un `BiometricPrompt` real con
  `BiometricPrompt.PromptInfo`. Requiere cambiar `MainActivity` de `ComponentActivity` a
  `androidx.fragment.app.FragmentActivity` (sigue siendo compatible con `setContent {}`) y
  añadir la dependencia `androidx.biometric:biometric` en `androidApp` (o en el `androidMain`
  de `shared`, para mantener todo el código de la pantalla junto).
- **iOS (`iosMain`):** `actual` que no toca `LocalAuthentication` todavía — simplemente simula
  éxito tras un pequeño delay, con `estaDisponible = true`, dejando un comentario `TODO` para
  cuando se compile en el Mac mini.

**Tema visual:** los mismos tokens que `front/src/styles.css`, con nombres análogos:

| Web (`--color-*`) | Mobile (`BaniterioColors.*`) |
|---|---|
| `--color-brand` `#372fa5` | `brand` |
| `--color-brand-bright` `#6f63d8` | `brandBright` |
| `--color-gold` `#f8d349` | `gold` |
| `--color-gold-soft` `#ffe896` | `goldSoft` |
| `--color-ink` `#f5f3ff` | `ink` |
| `--color-muted` `#a79fe0` | `muted` |
| `--color-surface` `#120f2e` | `surface` |
| `--color-panel` `#1c1745` | `panel` |
| `--color-outline` `#332c6e` | `outline` |

Tipografías: se descargan los `.ttf` de Unbounded (700), Archivo (400/600) y Metal Mania (400)
desde Google Fonts (mismo método que se usó para generar el icono `logo-mark.png`: `curl` con
user-agent antiguo para forzar la respuesta en `.ttf`) y se colocan en
`shared/src/commonMain/composeResources/font/`. `Type.kt` expone `FontFamily.Metal` (equivalente
a `font-metal` en Tailwind), usado solo en el nombre "Bañiterio" allá donde aparezca, en dorado
— igual que en la web.

## Pantallas

**Login:** campos Teléfono/Contraseña, botón "Entrar" (acepta cualquier valor no vacío → 
`onLoginSuccess`), botón secundario "Entrar con huella / Face ID" que llama a
`BiometricAuthenticator.autenticar()` y, si devuelve `true`, también dispara `onLoginSuccess`.
Enlace inferior a Registro.

**Registro:** los mismos 6 campos que `front/src/app/registro/registro.html` (nombre, apellidos,
mote opcional, teléfono, email, contraseña), mismas validaciones básicas (obligatorios, email con
`@`, contraseña ≥ 6). Al enviar, mismo comportamiento simulado que Login (no hay "solicitud de
ingreso" todavía, igual que en la web).

**Panel:** cabecera de bienvenida + lista/grid de 6 tarjetas de sección (`Seccion(nombre, descripcion,
tieneContenido)`), en este orden: Historia, Miembros, Eventos, Cuentas, Inventario, Ropa. Las
cinco últimas muestran una etiqueta "Próximamente" y no navegan a ningún sitio al tocarlas (igual
que en la web); "Historia" no lleva etiqueta y navega a `Screen.Historia`.

**Historia:** el mismo texto que ya existe en `front/src/app/home/home.html` (unión de peñas,
origen del logo/jarra/rosa de Rosi, Migas Santas/Chuletas Santas), adaptado a un layout de
Compose de una sola columna con scroll. Botón "Volver" hacia el Panel.

## Testing

No hay tests automatizados de UI en este MVP (el propio `front/` tampoco los tiene). Verificación:
compilar y ejecutar en un emulador/dispositivo Android (`./gradlew :androidApp:installDebug` o
Run desde Android Studio) y revisar visualmente las 4 pantallas y la navegación entre ellas. iOS no
se compila en esta sesión (se hará en el Mac mini más adelante).
