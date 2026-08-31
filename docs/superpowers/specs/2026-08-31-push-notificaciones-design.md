# Notificaciones push — diseño

**Fecha:** 2026-08-31
**Estado:** aprobado (pendiente de escribir el plan)

## Objetivo

Cuando entra algo que un administrador tiene que atender (hoy: una nueva
solicitud de acceso a la peña), enviar una **notificación push** al móvil de
las personas correspondientes, de modo que la vean en la bandeja del sistema
aunque la app esté cerrada.

## Alcance

- **Incluye:** backend completo (registro de dispositivos + envío vía Firebase
  Cloud Messaging + evento de dominio genérico) y la parte **Android** del
  móvil, verificada de punta a punta.
- **Pendiente (no bloqueante):** la parte **iOS**. Necesita un Mac con Xcode
  (restricción de Apple). El diseño la contempla y el plan dejará las tareas
  iOS escritas (Swift + Kotlin `iosMain` + pasos de Xcode documentados) para
  ejecutarlas y verificarlas en una sesión corta en Mac más adelante. Ver
  memoria `baniterio_push_ios_pendiente`.
- **Fuera:** deep-linking desde la notificación (al tocarla solo se abre la
  app), agrupación/limpieza de notificaciones, preferencias por usuario de
  qué recibir, otros disparadores distintos de la solicitud de acceso (se
  añadirán publicando nuevos eventos, sin tocar la infraestructura).

## Coste

Firebase Cloud Messaging es gratuito e ilimitado en cualquier plan. El SDK de
Firebase Admin en el backend no tiene coste. APNs (para el iOS futuro) también
es gratis. Único coste recurrente: la cuenta de Apple Developer (99 $/año) que
el usuario ya tiene.

## Global Constraints

- **Backend:** Java 17, Spring Boot 4.1.x, Maven. Migraciones Flyway
  `V*__descripcion.sql` (la siguiente es `V9`). Hibernate `ddl-auto: validate`
  (las entidades deben cuadrar con lo que crea Flyway). `open-in-view: false`.
- **Config propia** tipada en `AppProperties` (`@ConfigurationProperties("app")`),
  patrón `${VARIABLE:valor-por-defecto}` en `application.yml`.
- **Sin credenciales para desarrollar ni testear:** con `app.push.modo: log`
  (valor por defecto) no se toca Firebase. Igual que `app.email.modo`.
- **Seguridad:** rutas nuevas bajo `/api/v1/**` quedan autenticadas por
  defecto (`anyRequest().authenticated()`); no añadir nada a la lista
  `permitAll`. El principal es `UsuarioPrincipal(Long id, boolean esSuperadmin)`
  vía `@AuthenticationPrincipal`.
- **Móvil:** Kotlin Multiplatform, `commonMain` sin dependencias de plataforma.
  Ktor 3.5 con `expectSuccess = true` (un 4xx/5xx lanza `ResponseException`).
  El token JWT vive solo en memoria (`SesionHolder`); las credenciales, en
  `AlmacenCredenciales` (EncryptedSharedPreferences / Keychain).
- **Android:** `applicationId = com.baniterio.app`, `minSdk 24`, `targetSdk 36`,
  AGP 9, Kotlin 2.4. El grafo de dependencias (`Dependencias`) vive en
  `BaniterioApp` (ámbito de proceso).
- **Respuestas siempre en español** en la conversación; código y commits en
  español siguiendo el estilo del repo.

## Arquitectura

Tres bloques que se comunican por contratos estrechos:

```
                 (nueva solicitud de acceso)
AuthService.solicitarIngreso
   │  publica  AvisoPushEvent(audiencia=Administradores, titulo, cuerpo)
   ▼
ManejadorAvisoPush   @TransactionalEventListener(AFTER_COMMIT)
   │  resuelve audiencia → List<Long> usuarioId
   │  carga dispositivos de esos usuarios
   ▼
ServicioPush.enviar(tokens, titulo, cuerpo)
   │
   ├─ modo=log → PushEnLog: escribe en el log y nada más
   └─ modo=fcm → PushFirebase: FirebaseMessaging.sendEachForMulticast(...)
                 y poda los tokens que vuelvan UNREGISTERED / inválidos
```

En el móvil:

```
Android (androidApp)                         shared/commonMain
─────────────────────                        ─────────────────
BaniterioMessagingService                    DispositivoRepository
  onNewToken(token) ───────────────────────►   registrar(token, "ANDROID")
  onMessageReceived(msg) → Notificación         eliminar(token)
MainActivity                                 SesionHolder (token JWT en memoria)
  pide permiso POST_NOTIFICATIONS            AlmacenCredenciales
  al hacer login coge el token FCM            + guarda el último token push
```

---

## Backend

### 1. Entidad y tabla `dispositivo`

Migración `V9__create_dispositivo.sql`:

```sql
CREATE TABLE dispositivo (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token         TEXT NOT NULL UNIQUE,
    plataforma    TEXT NOT NULL,           -- 'ANDROID' | 'IOS'
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dispositivo_usuario ON dispositivo(usuario_id);
```

Entidad `Dispositivo` (paquete `com.baniterio.api.identidad`, estilo Lombok
`@Builder` como las demás). Enum `PlataformaDispositivo { ANDROID, IOS }`
(`@Enumerated(EnumType.STRING)`).

`DispositivoRepository extends JpaRepository<Dispositivo, Long>`:
- `Optional<Dispositivo> findByToken(String token)`
- `List<Dispositivo> findByUsuarioIdIn(Collection<Long> usuarioIds)`
- `void deleteByToken(String token)`
- `void deleteByTokenIn(Collection<String> tokens)`

### 2. Endpoints de registro de dispositivo

Controlador nuevo `DispositivoController` (`@RequestMapping("/api/v1/dispositivos")`),
autenticado por defecto.

- `POST /api/v1/dispositivos` — cuerpo `RegistrarDispositivoRequest { @NotBlank
  String token, @NotNull PlataformaDispositivo plataforma }`. Responde `204`.
  Lógica (`DispositivoService.registrar(usuarioId, token, plataforma)`,
  `@Transactional`): si el token ya existe → actualizar `usuario_id`,
  `plataforma` y `actualizado_en`; si no → insertar. (Un token identifica una
  instalación; si otra persona inicia sesión en ese móvil, el token pasa a ser
  suyo.)
- `DELETE /api/v1/dispositivos/{token}` — responde `204` siempre (idempotente).
  Borra la fila de ese token **solo si pertenece al usuario del token JWT**
  (evita que A borre el dispositivo de B). Se llama al cerrar sesión.

### 3. Envío: `ServicioPush` + configuración

`AppProperties` gana un record:

```java
public record Push(String modo, String credencialesJson) {}
```

`application.yml`:

```yaml
app:
  push:
    modo: ${PUSH_MODO:log}                 # log = solo traza · fcm = envía de verdad
    credenciales-json: ${PUSH_CREDENCIALES:} # ruta al service-account.json (solo modo fcm)
```

Interfaz:

```java
public interface ServicioPush {
    /** Envía la misma notificación a todos los tokens. Devuelve los tokens
     *  que el proveedor ha rechazado por estar muertos, para que quien llama
     *  los borre. Nunca lanza: un fallo de envío se registra, no revienta. */
    List<String> enviar(List<String> tokens, String titulo, String cuerpo);
}
```

- `PushEnLog implements ServicioPush` — `@Component` `@ConditionalOnProperty(
  name = "app.push.modo", havingValue = "log", matchIfMissing = true)`. Escribe
  `log.info("[push:log] a {} dispositivos · {} — {}", ...)` y devuelve lista
  vacía.
- `PushFirebase implements ServicioPush` — `@ConditionalOnProperty(name =
  "app.push.modo", havingValue = "fcm")`. En el constructor inicializa
  `FirebaseApp` desde `credencialesJson` (`GoogleCredentials.fromStream`).
  `enviar` construye un `MulticastMessage` (`Notification` con título/cuerpo)
  y llama a `FirebaseMessaging.getInstance().sendEachForMulticast(msg)`.
  Recorre `BatchResponse`; por cada respuesta fallida con
  `MessagingErrorCode.UNREGISTERED` o `INVALID_ARGUMENT` añade su token a la
  lista de "muertos" que devuelve. FCM limita a 500 tokens por multicast; con
  el tamaño de una peña no se alcanza, pero el servicio trocea en lotes de 500
  por si acaso.

Dependencia nueva en `pom.xml`: `com.google.firebase:firebase-admin:9.4.3`
(o la última 9.x).

### 4. Audiencia y evento de dominio

```java
public sealed interface Audiencia {
    record UsuarioUnico(Long usuarioId) implements Audiencia {}
    record TodaLaPena() implements Audiencia {}
    record Administradores() implements Audiencia {}
}
```

`ResolutorAudiencia` (`@Service`) → `List<Long> resolver(Audiencia a)`:
- `UsuarioUnico` → `List.of(usuarioId)`.
- `TodaLaPena` → ids de las membresías activas de la peña.
- `Administradores` → de esas membresías, las de rol `ADMIN`, más los
  `usuario` con `es_superadmin = true` (unión sin duplicados). Se apoya en los
  repos existentes (`MembresiaRepository`, `UsuarioRepository`); si hace falta
  un método derivado nuevo (p. ej. `findByPenaIdAndActivaTrue`), se añade.

Evento:

```java
public record AvisoPushEvent(Audiencia audiencia, String titulo, String cuerpo) {}
```

`ManejadorAvisoPush` (`@Component`, espejo de `ManejadorCorreoSolicitud`):

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void alAviso(AvisoPushEvent e) {
    try {
        List<Long> usuarios = resolutor.resolver(e.audiencia());
        List<Dispositivo> disp = dispositivos.findByUsuarioIdIn(usuarios);
        if (disp.isEmpty()) return;
        List<String> tokens = disp.stream().map(Dispositivo::getToken).toList();
        List<String> muertos = servicioPush.enviar(tokens, e.titulo(), e.cuerpo());
        if (!muertos.isEmpty()) dispositivos.deleteByTokenIn(muertos);
    } catch (Exception ex) {
        log.error("No se pudo enviar el aviso push: {}", ex.toString());
    }
}
```

El borrado de tokens muertos va en su propia transacción (el listener corre
después del commit): anotar el método o un helper con
`@Transactional(propagation = REQUIRES_NEW)`.

### 5. Publicar el evento al crear la solicitud

`AuthService` recibe `ApplicationEventPublisher` por constructor.
`solicitarIngreso`, tras `solicitudes.save(solicitud)`:

```java
publisher.publishEvent(new AvisoPushEvent(
        new Audiencia.Administradores(),
        "Nueva solicitud de acceso",
        req.nombre() + " " + req.apellidos() + " quiere entrar en la peña"));
```

`solicitarIngreso` ya es `@Transactional`, así que el `AFTER_COMMIT` dispara
cuando la solicitud está persistida.

### 6. Tests backend

- `ResolutorAudienciaTest` (unitario, Mockito): las tres audiencias devuelven
  los ids correctos; `Administradores` une admins y superadmins sin duplicar.
- `ManejadorAvisoPushTest` (unitario): dado un evento, llama a `ServicioPush.
  enviar` con los tokens de los dispositivos de la audiencia; si `enviar`
  devuelve tokens muertos, se borran; si la audiencia no tiene dispositivos,
  no se llama a `enviar`.
- `PushEnLogTest` (unitario): no lanza y devuelve lista vacía.
- `DispositivoServiceTest` (unitario): `registrar` inserta si el token es
  nuevo y reasigna `usuario_id` si ya existía.
- `DispositivoControllerIT` (Testcontainers, patrón de `AdminSolicitudesIT`):
  `POST` sin token → 401; con token → 204 y fila en BBDD; `POST` repetido con
  otro usuario → reasigna, no duplica; `DELETE` del propio token → 204 y
  desaparece; `DELETE` del token de otro → 204 pero la fila sigue.
- `SolicitudDisparaAvisoPushIT` (Testcontainers): `POST /api/v1/auth/solicitudes`
  con `app.push.modo=log` no rompe y (si es fácil de espiar) registra el envío.
  Como mínimo, verifica que crear la solicitud sigue devolviendo 201 con la
  infraestructura de push conectada.

---

## Móvil — Android

### 7. Dependencias y proyecto Firebase

- `mobile/gradle/libs.versions.toml`: añadir
  `firebase-bom = "34.x"`, `google-services` plugin (`com.google.gms.google-services`),
  y `firebase-messaging = { module = "com.google.firebase:firebase-messaging" }`.
- `mobile/build.gradle.kts`: `alias(libs.plugins.googleServices) apply false`.
- `mobile/androidApp/build.gradle.kts`: aplicar `google-services`, añadir
  `implementation(platform(libs.firebase.bom))` + `implementation(libs.firebase.messaging)`.
- **Paso manual del usuario (guiado):** crear el proyecto en la consola de
  Firebase, registrar la app Android con `applicationId com.baniterio.app`,
  descargar `google-services.json` y colocarlo en `mobile/androidApp/`. El
  fichero va a `.gitignore` (contiene ids del proyecto; no es secreto crítico
  pero no aporta tenerlo en el repo). Documentar en `mobile/README.md`.

### 8. Servicio de mensajería y permiso

- `BaniterioMessagingService : FirebaseMessagingService` en
  `mobile/androidApp/src/main/kotlin/com/baniterio/app/`:
  - `onNewToken(token)`: guarda el token en `deps.almacen` (nuevo campo) y, si
    `deps.repo.usuarioActual != null`, lanza una corrutina que llama a
    `deps.dispositivoRepo.registrar(token, "ANDROID")`.
  - `onMessageReceived(msg)`: solo llega con la app en primer plano (los
    mensajes *notification* con la app en segundo plano los pinta el sistema).
    Construye una `NotificationCompat` en un canal `"avisos"` y la muestra.
  - Registrar el servicio en `AndroidManifest.xml` con el
    `intent-filter` `com.google.firebase.MESSAGING_EVENT`.
  - Crear el canal de notificación `"avisos"` en `BaniterioApp.onCreate`.
- Permiso `POST_NOTIFICATIONS` en `AndroidManifest.xml`. `MainActivity` lo
  pide con un `ActivityResultLauncher` (`RequestPermission`) tras el primer
  login (o al arrancar si ya hay sesión), solo en `SDK_INT >= 33`.

### 9. `DispositivoRepository` (shared/commonMain)

```kotlin
interface DispositivoRepository {
    suspend fun registrar(token: String, plataforma: String): Boolean
    suspend fun eliminar(token: String): Boolean
}
```

`DispositivoRepositoryImpl(http: HttpClient, sesion: SesionHolder)`:
- `registrar` → `POST $API_BASE_URL/dispositivos` con `auth()` y cuerpo
  `RegistrarDispositivoRequest(token, plataforma)`. `true` si 2xx, `false` si
  falla (patrón `try`/`ResponseException` acotado como en `AdminRepositoryImpl`).
- `eliminar` → `DELETE $API_BASE_URL/dispositivos/$token` con `auth()`.

DTO en `data/dto/DispositivoDtos.kt`:
`@Serializable data class RegistrarDispositivoRequest(val token: String, val plataforma: String)`.

`Dependencias` gana `val dispositivoRepo: DispositivoRepository`;
`crearDependencias` lo construye con el `http`/`sesion` compartidos.

### 10. Ciclo de vida del token

- `AlmacenCredenciales` (expect/actual) gana:
  `fun guardarTokenPush(token: String)`, `fun leerTokenPush(): String?`,
  `fun borrarTokenPush()`. Android: en las mismas EncryptedSharedPreferences.
  iOS (pendiente): Keychain.
- **Tras login con éxito** (en `App.kt`, `onLoginSuccess` de `LoginScreen`, y
  `onDesbloqueado` de `DesbloqueoScreen`): pedir a la plataforma el token FCM
  actual y llamar a `deps.dispositivoRepo.registrar(...)`. El "pedir el token a
  la plataforma" se hace con un `expect fun tokenPushActual(): String?`
  (`actual` Android: `FirebaseMessaging.getInstance().token.await()`; `actual`
  iOS pendiente). Como es `suspend`-friendly, envolver en corrutina.
- **Al cerrar sesión** (`onCerrarSesion` en `App.kt`): antes de `logout()`,
  `deps.almacen.leerTokenPush()?.let { deps.dispositivoRepo.eliminar(it) }`.

### 11. Tests Android

- `DispositivoRepositoryImplTest` en `commonTest` con un `MockEngine` de Ktor:
  `registrar` hace `POST /dispositivos` con la cabecera Bearer y el cuerpo
  correcto y devuelve `true` en 204; `false` en 500. `eliminar` hace `DELETE`.
  (Es el primer test del módulo shared con `MockEngine`; añade
  `ktor-client-mock` a `commonTest`.)
- Verificación manual (guion en el plan): con `app.push.modo=fcm` y el backend
  real, crear una solicitud de acceso desde otro teléfono/incógnito y
  comprobar que llega la notificación al móvil con sesión de admin, app
  cerrada.

---

## Móvil — iOS (PENDIENTE, requiere Mac)

Queda todo escrito en el plan pero no se ejecuta hasta tener Xcode:

- Añadir `FirebaseMessaging` por SPM al proyecto `iosApp.xcodeproj`.
- `GoogleService-Info.plist` (descargado de la consola, app iOS con bundle id
  `com.baniterio.app.Baniterio`) en `mobile/iosApp/iosApp/`.
- Capabilities: *Push Notifications*, *Background Modes → Remote notifications*.
- Subir la clave de autenticación APNs (`.p8`) a la consola de Firebase.
- `iOSApp.swift`: `FirebaseApp.configure()` en el init;
  `UNUserNotificationCenter.current().requestAuthorization`,
  `UIApplication.shared.registerForRemoteNotifications()`;
  `Messaging.messaging().delegate` → en `messaging(_:didReceiveRegistrationToken:)`
  puentear a un método del framework Kotlin que llame a
  `dispositivoRepo.registrar(token, "IOS")`.
- `AlmacenCredenciales.ios.kt`: implementar los `guardar/leer/borrarTokenPush`
  contra Keychain.
- `tokenPushActual()` actual iOS.
- Verificación en iPhone físico.

---

## Riesgos y decisiones

- **Token muerto que no se poda:** si el envío va en `modo=log` o falla la
  llamada a FCM, los tokens inválidos se quedan. Aceptable: FCM los ignora y
  el siguiente envío en `modo=fcm` sí los limpia.
- **Doble notificación (correo + push):** hoy el correo es al solicitante y el
  push al admin, no se solapan. Cuando haya push al solicitante habrá que
  revisar.
- **`google-services.json` en git:** se deja fuera. El plan documenta que cada
  quien lo descarga de la consola. Para CI se añadiría como secreto más
  adelante.
- **Sin permiso de notificaciones (Android 13+):** si el usuario lo deniega,
  el token se registra igual pero el sistema no muestra nada. No se insiste;
  se puede reintentar desde ajustes de la app.
- **Orden de arranque:** el token FCM puede tardar en estar listo; por eso el
  registro se dispara tras login y también en `onNewToken`, que cubre el caso
  de que el token llegue después.
- **`firebase-admin` vs Spring Boot 4:** el SDK arrastra `google-http-client`,
  Guava y Netty. La primera tarea del backend (añadir dependencia + arrancar el
  contexto con `modo=log`) valida que no rompe el classpath; si hubiera choque
  de versiones, se acota con `<exclusions>` o alineando versiones, sin cambiar
  el diseño.
- **Nombres `DispositivoRepository`:** hay dos, uno en el backend
  (`com.baniterio.api.identidad`, JPA) y otro en el móvil
  (`com.baniterio.app.data`, Ktor). Es el mismo patrón que ya siguen
  `AdminRepository` / `AuthRepository` en ambos lados; no se renombra.
