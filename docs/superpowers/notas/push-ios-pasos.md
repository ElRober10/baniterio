# Push iOS — pasos para la sesión en Mac

Prerrequisitos: Mac con Xcode, cuenta Apple Developer, iPhone físico.

## Consola Apple / Firebase
1. Apple Developer → Certificates, IDs & Profiles → Keys → crear una APNs Auth
   Key (.p8). Apuntar Key ID y Team ID.
2. Firebase → añadir app iOS, bundle id `com.baniterio.app.Baniterio`.
   Descargar `GoogleService-Info.plist`.
3. Firebase → Configuración del proyecto → Cloud Messaging → subir la .p8 con
   su Key ID y Team ID.

## Xcode
4. Añadir `GoogleService-Info.plist` a `mobile/iosApp/iosApp/` (target iosApp).
5. Target iosApp → Signing & Capabilities → + Capability:
   - Push Notifications
   - Background Modes → Remote notifications
6. Añadir el paquete SPM `https://github.com/firebase/firebase-ios-sdk` →
   producto `FirebaseMessaging`.

## Código
7. `iOSApp.swift`:
   - `FirebaseApp.configure()` en `init()`.
   - `UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])`.
   - `UIApplication.shared.registerForRemoteNotifications()`.
   - Conformar un `AppDelegate` a `MessagingDelegate`; en
     `messaging(_:didReceiveRegistrationToken:)` llamar a un método del
     framework `Shared` que registre el token (crear
     `PushBridge.registrar(token:)` en `iosMain` que use
     `deps.dispositivoRepo.registrar(token, "IOS")`).
8. `iosMain`: crear `PushBridge.kt` con acceso al `dispositivoRepo` (mismo
   patrón de `deps` que `MainViewController.kt`).
9. Implementar `alIniciarSesion` / `alCerrarSesion` al construir `App(...)` en
   `MainViewController()` — obtener el token de `Messaging.messaging().token`.

## Verificar
10. Ejecutar en iPhone físico, iniciar sesión como admin, mandar una solicitud
    desde otro sitio, comprobar la notificación con la app cerrada.
