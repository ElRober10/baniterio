# Panel de administración — Diseño

**Fecha:** 2026-08-29
**Rama:** `feature/panel-administracion`
**Estado:** aprobado para plan

## Objetivo

Añadir a Bañiterio una sección de **administración** (web + móvil), siempre la
última del panel, con dos áreas:

1. **Solicitudes** — un admin revisa las peticiones de acceso pendientes y las
   aprueba o rechaza; al resolverlas se envía un correo al solicitante.
2. **Permisos** — un admin gestiona el rol (admin/miembro) y el estado
   (activo/inactivo) de cada miembro, y concede a miembros normales acceso a
   áreas protegidas concretas.

Incluye un **modelo de permisos por área** reutilizable por futuras secciones y
el envío de **correo transaccional real** (Gmail SMTP).

## Contexto actual (lo que ya existe)

- **Identidad** (`back/.../identidad/`): `Usuario` (con `esSuperadmin`, `activo`),
  `Pena`, `Membresia` (`rol` = `ADMIN`/`MIEMBRO`, `activa`), `TelefonoAutorizado`
  (`usado`, `autorizadoPor`), `SolicitudIngreso` (`estado` PENDIENTE/APROBADA/
  RECHAZADA, `motivo`/`relacion`/`conocidos`, `motivoRechazo`, `resueltaPor`,
  `resueltaAt`). PKs autonuméricas (`Long`).
- **Auth** (`back/.../auth/`): `POST /api/v1/auth/registro` (403
  `TELEFONO_NO_AUTORIZADO` si el teléfono no está en la lista), `POST /login`
  (JWT HS256, `sub` + `esSuperadmin`, 7 días), `GET /yo` (protegido, valida
  `activo` en cada petición), `POST /solicitudes` (público, crea la
  `SolicitudIngreso` PENDIENTE). Errores traducidos en `ApiExceptionHandler`
  con un `codigo` estable.
- **SecurityConfig**: stateless, rutas públicas explícitas, resto autenticado,
  `JwtAuthenticationFilter` puebla el `UsuarioPrincipal` (`id`, `esSuperadmin`).
- **Front** (`front/src/app/`): `auth/` (login, registro, solicitar-acceso,
  `auth.service.ts`, `auth.guard.ts` con `authGuard`/`invitadoGuard`,
  `auth.interceptor.ts`), `panel/` (pantalla única que lista secciones
  "próximamente"), `home/`. La "sesión" es el JWT en `localStorage`
  (`baniterio.token`); `AuthService` decodifica el `exp`. Rutas en
  `app.routes.ts`.
- **Móvil** (`mobile/shared/.../`): `App.kt` (navegación por `screenKey`
  `rememberSaveable`), `nav/Screen.kt` (sealed class), `ui/panel/PanelScreen.kt`
  + `model/Seccion.kt` (`seccionesPanel`), `data/` (`AuthRepository[Impl]` con
  token JWT solo en memoria, `Dependencias`, `ApiConfig`, DTOs
  `@Serializable`). Modelo de sesión: cada arranque pide biométrico y
  re-loguea con las credenciales guardadas.
- **Migraciones**: V1–V7 aplicadas (inmutables). La siguiente es **V8**.
- **Tests backend**: `support/` (Testcontainers), `AuthControllerIT`,
  `SecurityIT`, `JwtServiceTest`, `FlywayMigrationIT`. `mvn verify` corre los
  `*IT` vía `maven-failsafe-plugin`.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Aprobar solicitud | El admin revisa; si es válida pulsa "aprobar" y el usuario **se crea automáticamente** (ya trae usuario y contraseña de cuando intentó registrarse). Correo de aprobación. Si se rechaza, correo de rechazo con motivo. |
| Contraseña del solicitante | El **front la lleva en memoria** desde el formulario de registro hasta el envío de la solicitud. Si se pierde (refresco), la solicitud se envía igual **sin contraseña** (sin error). |
| Correo | **SMTP real de Gmail** (contraseña de aplicación, variables de entorno). Fallback a implementación de log si no hay `spring.mail.host` (dev). |
| Modelo de permisos | **Áreas protegidas + concesiones por usuario**. De momento las áreas son solo las dos del panel de administración. Se añaden más según se desarrollen otras secciones. |
| Quién administra | **Admin y superadmin pueden todo** (incluido nombrar/quitar admins), con salvaguardas contra auto-bloqueo y "último admin". |
| Nav "Administración" | Web: grupo desplegable en la barra lateral con las sub-secciones dentro. Móvil: tarjeta al final que abre una pantalla índice con las sub-secciones accesibles. |
| Alcance | Web **y** móvil en esta tanda. Un spec; plan en 2 partes (backend, luego clientes). |

## Arquitectura

### 1. Modelo de permisos (backend)

**Enum `AreaProtegida`** (`back/.../identidad/AreaProtegida.java`):

```java
public enum AreaProtegida {
    ADMIN_SOLICITUDES,
    ADMIN_PERMISOS
}
```

Añadir un valor = nueva área protegida; aparece sola en la pantalla de permisos
y en `GET /yo`.

**Entidad `PermisoArea`** (`back/.../identidad/PermisoArea.java`) + tabla
`permiso_area`:

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGINT` identity | PK |
| `usuario_id` | `BIGINT` | FK → `usuario`, `ON DELETE CASCADE` |
| `area` | `VARCHAR(40)` | valor del enum como texto |
| `concedido_por` | `BIGINT` NULL | FK → `usuario` |
| `created_at` | `TIMESTAMP` | `@CreationTimestamp` |

Única `(usuario_id, area)`. Repo `PermisoAreaRepository`:
`findByUsuarioId(Long)`, `deleteByUsuarioId(Long)`,
`existsByUsuarioIdAndArea(Long, AreaProtegida)`.

**`ServicioPermisos`** (`back/.../auth/ServicioPermisos.java`) — punto único de
verdad:

```java
/** ¿El usuario tiene acceso efectivo a esta área? */
boolean puede(Long usuarioId, AreaProtegida area);

/** Conjunto de áreas a las que el usuario tiene acceso (para GET /yo). */
Set<AreaProtegida> areasDe(Long usuarioId);

/** Rol del usuario en la peña piloto (ADMIN/MIEMBRO), o null si no es miembro. */
RolMembresia rolDe(Long usuarioId);

/** Atajo: es superadmin o tiene rol ADMIN en la peña. */
boolean esAdministrador(Long usuarioId);
```

Regla de `puede`: `usuario.esSuperadmin` → `true`; membresía activa con
`rol = ADMIN` → `true`; si no, `permisoAreaRepository.existsByUsuarioIdAndArea`.
`areasDe`: si `esAdministrador` → todas las del enum; si no → las de
`permiso_area`.

**Autorización de endpoints admin**: cada endpoint declara el área que exige. Se
comprueba con `ServicioPermisos.puede(principal.id(), AREA)` al principio del
método del controlador (helper `exigirArea(principal, area)` que lanza
`SinPermisoException`). No se usa `@PreAuthorize` (el proyecto no tiene method
security montado; mantenerlo simple y explícito). El filtro JWT y la cadena de
Security no cambian salvo para permitir el prefijo `/api/v1/admin/**` como
"autenticado" (ya lo está por `anyRequest().authenticated()`).

**`GET /api/v1/auth/yo`** y **`LoginResponse.usuario`** amplían `UsuarioResponse`:

```java
public record UsuarioResponse(
    Long id, String nombre, String apellidos, String mote,
    boolean esSuperadmin,
    String rol,            // "ADMIN" | "MIEMBRO" | null
    List<String> areas     // p.ej. ["ADMIN_SOLICITUDES","ADMIN_PERMISOS"]
) { ... }
```

`rol` y `areas` **no** van en el JWT (el token vive 7 días; un cambio de
permisos debe aplicarse ya). Van en la respuesta de `/login` y `/yo`, que
consultan la BBDD en vivo. Los endpoints admin siempre re-consultan vía
`ServicioPermisos`, no se fían del cliente.

### 2. Solicitudes: aprobar / rechazar + correo

**Cambio en `SolicitudIngreso`** (V8): columna `password_hash VARCHAR(72) NULL`
(hash BCrypt de la contraseña que el solicitante puso al intentar registrarse;
puede faltar).

**Cambio en `POST /api/v1/auth/solicitudes`**: `SolicitudIngresoRequest` gana un
campo `password` **opcional** (`@Size(min = 6, max = 72)` cuando viene, nulo
permitido). Si viene, `AuthService.solicitarIngreso` guarda
`passwordEncoder.encode(password)` en `solicitud.passwordHash`. Si no,
`passwordHash = null`. Nunca es motivo de error.

**Nuevo paquete `back/.../admin/`** con `AdminController`, `AdminService`, DTOs y
excepciones. Prefijo `/api/v1/admin`.

#### `GET /api/v1/admin/solicitudes?estado=PENDIENTE` — área `ADMIN_SOLICITUDES`

Devuelve `List<SolicitudResumen>`:

```java
record SolicitudResumen(
    Long id, String nombre, String apellidos, String telefono, String email,
    String motivo, String relacion, String conocidos,
    boolean traeContrasena,      // passwordHash != null
    String estado, Instant createdAt
)
```

`estado` por defecto `PENDIENTE`; acepta `APROBADA`/`RECHAZADA` para histórico.
Repo: `SolicitudIngresoRepository.findByPenaSlugAndEstadoOrderByCreatedAtAsc` (o
equivalente; la peña se resuelve por el slug piloto `baniterio`).

#### `POST /api/v1/admin/solicitudes/{id}/aprobar` — área `ADMIN_SOLICITUDES`

`@Transactional`. Sin cuerpo. Reglas:

1. Cargar solicitud; si no `PENDIENTE` → 409 `SOLICITUD_YA_RESUELTA`.
2. Si ya existe `Usuario` con ese teléfono o email → 409 `YA_REGISTRADO`
   (defensa; no debería pasar).
3. Resolver/crear `TelefonoAutorizado` de la peña para ese teléfono:
   - si `solicitud.passwordHash != null`:
     - crear `Usuario` (`activo = true`, `passwordHash` de la solicitud,
       `nombre`/`apellidos`/`email`/`telefono` de la solicitud, `mote = null`,
       `esSuperadmin = false`).
     - crear `Membresia` (`rol = MIEMBRO`, `activa = true`).
     - crear `TelefonoAutorizado` (`usado = true`, `autorizadoPor` = admin
       actual).
     - resultado: `CUENTA_CREADA`.
   - si `solicitud.passwordHash == null`:
     - crear `TelefonoAutorizado` (`usado = false`, `autorizadoPor` = admin).
     - resultado: `TELEFONO_AUTORIZADO`.
4. `solicitud.estado = APROBADA`, `resueltaPor` = admin, `resueltaAt = now()`.
5. Encolar correo (ver §4) **para enviar tras el commit** (`TransactionSynchronization`
   / `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`):
   - `CUENTA_CREADA` → plantilla "aprobada, cuenta creada".
   - `TELEFONO_AUTORIZADO` → plantilla "aprobada, completa tu registro".

Respuesta `200 { resultado: "CUENTA_CREADA" | "TELEFONO_AUTORIZADO" }`.

#### `POST /api/v1/admin/solicitudes/{id}/rechazar` — área `ADMIN_SOLICITUDES`

Body `{ "motivo": string opcional }`. Si no PENDIENTE → 409
`SOLICITUD_YA_RESUELTA`. `estado = RECHAZADA`, `motivoRechazo` = motivo dado o el
texto por defecto:

> "No hemos podido confirmar tu vinculación con la peña, así que de momento no
> podemos darte acceso. Si crees que es un error, habla con alguien de la peña."

`resueltaPor`/`resueltaAt`. Correo de rechazo tras commit. Respuesta `204`.

### 3. Gestión de miembros (backend)

Todos bajo `/api/v1/admin/miembros`, área **`ADMIN_PERMISOS`**. "Miembro" =
`Usuario` con `Membresia` en la peña piloto.

#### `GET /api/v1/admin/miembros`

`List<MiembroResumen>` ordenada por apellidos, nombre:

```java
record MiembroResumen(
    Long id, String nombre, String apellidos, String mote, String telefono,
    String rol, boolean activo, boolean esSuperadmin,
    List<String> areas          // concesiones explícitas de permiso_area
)
```

Para un admin/superadmin `areas` se muestra vacía (tiene todas implícitas); la
UI lo indica con un distintivo "acceso total".

#### `PUT /api/v1/admin/miembros/{id}/rol` — body `{ "rol": "ADMIN" | "MIEMBRO" }`

Cambia `Membresia.rol`. Salvaguardas → 409:
- `ULTIMO_ADMIN`: bajar a MIEMBRO al último `ADMIN` activo de la peña.
- `NO_TE_PUEDES_DEGRADAR`: el admin actual intentando quitarse su propio ADMIN.
- `SOLO_EL_SUPERADMIN`: intentar cambiar el rol del fundador/superadmin sin ser
  ese mismo usuario.

#### `PUT /api/v1/admin/miembros/{id}/activo` — body `{ "activo": bool }`

Cambia `Usuario.activo` (y `Membresia.activa` en paralelo). Salvaguardas → 409:
- `NO_TE_PUEDES_DESACTIVAR`: el admin actual sobre sí mismo.
- `ULTIMO_ADMIN`: desactivar al último admin activo.
- `SOLO_EL_SUPERADMIN`: desactivar al superadmin sin serlo.

Desactivar surte efecto inmediato: `GET /yo` ya valida `activo` en cada
petición, así que el JWT del desactivado deja de servir.

#### `PUT /api/v1/admin/miembros/{id}/areas` — body `{ "areas": ["ADMIN_SOLICITUDES", ...] }`

Reemplaza el conjunto de `permiso_area` del usuario por el dado (borra las que
sobran, inserta las que faltan, `concedidoPor` = admin actual). Valida que cada
string sea un valor de `AreaProtegida` → 400 `VALIDACION` si no. Sobre un
admin/superadmin es no-op semántico (ya tiene todas); se permite guardar
concesiones "latentes" por si luego se le baja el rol — decisión: **se guardan**.

### 4. Correo (SMTP Gmail)

**Dependencia**: `spring-boot-starter-mail` en `back/pom.xml`.

**Config** (`application.yml`), todo por variables de entorno con defaults
vacíos:

```yaml
spring:
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
app:
  email:
    from: ${MAIL_FROM:Bañiterio <no-reply@baniterio>}
    enlace-registro: ${MAIL_ENLACE_REGISTRO:http://localhost:4200/registro}
```

`AppProperties` gana `record Email(String from, String enlaceRegistro)`.

**`ServicioEmail`** (interfaz, `back/.../email/ServicioEmail.java`):

```java
void enviar(String destinatario, String asunto, String cuerpo);
```

Selección por un flag propio explícito (evita la ambigüedad de
`@ConditionalOnProperty` con string vacío): `app.email.modo` =
`smtp` | `log`, default `${MAIL_MODO:log}`.
- **`EmailSmtp`** (`@ConditionalOnProperty(name = "app.email.modo", havingValue
  = "smtp")`): usa `JavaMailSender`, `SimpleMailMessage`, `from` de la config.
  Loggea éxito.
- **`EmailLog`** (`@ConditionalOnProperty(name = "app.email.modo", havingValue =
  "log", matchIfMissing = true)`): escribe `INFO` con destinatario, asunto y
  cuerpo. Permite arrancar y desarrollar sin credenciales.

`AppProperties.Email` gana `String modo`. Perfil `dev`/local: `modo: log`.
Producción: `MAIL_MODO=smtp` + las 3 variables `MAIL_*`.

**`PlantillasCorreo`** (`back/.../email/PlantillasCorreo.java`) — funciones puras
que devuelven `record Correo(String asunto, String cuerpo)`. Texto plano,
español, tono cercano de la peña:
- `aprobacionCuentaCreada(nombre)` — "tu solicitud ha sido aprobada y ya te
  hemos creado el usuario; entra con tu teléfono y la contraseña que pusiste".
- `aprobacionCompletaRegistro(nombre, enlaceRegistro)` — "aprobada; entra en
  `{enlace}` y crea tu cuenta con tu número de teléfono".
- `rechazo(nombre, motivo)` — motivo incluido.

**Robustez**: el envío ocurre en un `@TransactionalEventListener(phase =
AFTER_COMMIT)` que llama a `ServicioEmail` dentro de try/catch; un fallo de SMTP
se loggea a `ERROR` pero **no** revierte la aprobación/rechazo (ya
confirmados). No hay reintentos en esta versión (follow-up).

### 5. Panel web (Angular)

**`AuthService`**: nuevo método `yo(): Observable<UsuarioDto>` → `GET /auth/yo`.
`UsuarioDto` gana `rol: string | null` y `areas: string[]`. Nueva señal
`usuarioActual = signal<UsuarioDto | null>(null)` que se rellena tras login y en
`refrescarYo()`. Helper `tieneArea(area: string): boolean`.

**`AdminService`** (`front/src/app/admin/admin.service.ts`) — un método por
endpoint de §2 y §3. Tipos en `front/src/app/admin/admin.types.ts`.

**Guard** `areaGuard(area: string): CanActivateFn`
(`front/src/app/admin/area.guard.ts`): asegura sesión, hace `refrescarYo()` si
`usuarioActual()` es null, y deja pasar solo si `tieneArea(area)`; si no,
`createUrlTree(['/panel'])`.

**Rutas** (`app.routes.ts`): `panel` pasa a tener hijos —

```
{ path: 'panel', component: Panel, canActivate: [authGuard], children: [
    { path: '', component: PanelInicio },
    { path: 'administracion/solicitudes', component: AdminSolicitudes,
      canActivate: [areaGuard('ADMIN_SOLICITUDES')] },
    { path: 'administracion/permisos', component: AdminPermisos,
      canActivate: [areaGuard('ADMIN_PERMISOS')] },
] }
```

`Panel` pasa a ser layout (barra lateral + `<router-outlet>`); su contenido
actual de bienvenida se mueve a `PanelInicio`.

**Nav** (`panel.html`): tras las secciones "próximamente", si
`auth.usuarioActual()?.areas?.length`, un bloque **"Administración"**
(desplegable, abierto por defecto en las rutas hijas) con un enlace por
sub-área accesible. Siempre el último del nav.

**`AdminSolicitudes`** (`front/src/app/admin/solicitudes/`): tabla/lista de
pendientes. Cada fila: datos + `motivo`/`relacion`/`conocidos` (expandibles) +
distintivo "trae contraseña" / "sin contraseña". Botón **Aprobar** (confirma;
muestra el resultado `CUENTA_CREADA`/`TELEFONO_AUTORIZADO`). Botón **Rechazar**
(abre un `textarea` de motivo, opcional). Refresco de la lista tras cada acción.
Estados de carga y error.

**`AdminPermisos`** (`front/src/app/admin/permisos/`): lista de miembros. Por
fila: selector rol (Admin/Miembro), toggle Activo, checkboxes de áreas
(deshabilitados con nota "acceso total" si es admin/superadmin). Guardado por
acción (llamada inmediata al endpoint correspondiente). Los errores de
salvaguarda (`ULTIMO_ADMIN`, etc.) se muestran como mensaje claro y se revierte
el control al valor anterior.

**`auth.interceptor.ts`**: ya añade el Bearer a rutas no públicas; `/api/v1/admin`
encaja sin cambios. El 401/403 en admin no cierra sesión salvo 401 (sí lo hace
hoy); un **403 `SIN_PERMISO`** se deja pasar para que el componente muestre el
error.

### 6. Panel móvil (Compose)

**`Screen`** gana `AdminIndex`, `AdminSolicitudes`, `AdminPermisos` (+ claves en
`App.kt`, `aClave`/`claveAScreen`).

**DTOs** (`data/dto/`): `UsuarioResponse` gana `rol: String? = null`,
`areas: List<String> = emptyList()`. Nuevos DTOs para admin en
`data/dto/AdminDtos.kt`.

**`AuthRepositoryImpl`**: `_usuario` ya guarda `UsuarioResponse`; al traer `rol`
y `areas` en `LoginResponse.usuario` quedan disponibles vía `usuarioActual`.
Helper `fun tieneArea(area: String): Boolean`.

**`AdminRepository`** + `AdminRepositoryImpl` (`data/`): un método suspend por
endpoint, mismo patrón `peticion { }` que `AuthRepositoryImpl`, traduciendo
errores a un `ResultadoAdmin` (o se reusa `ResultadoAuth` renombrando mental —
decisión: **nuevo `ResultadoAdmin`** con sus códigos: `SIN_PERMISO`,
`SOLICITUD_YA_RESUELTA`, `ULTIMO_ADMIN`, `CONFLICTO`, `SIN_CONEXION`).
**Interceptor Bearer**: `AuthRepositoryImpl` expone el token (o se mueve el
token a un `SesionHolder` compartido); `HttpClientFactory.configComun` añade un
`bearerAuth` dinámico. Se añade en esta tanda (follow-up pendiente).
`Dependencias` gana `adminRepo`.

**`Seccion`**/`seccionesPanel`: `seccionesPanel` pasa de `val` a
`fun seccionesPanel(tieneAdmin: Boolean): List<Seccion>` que añade al final
`Seccion("Administración", "Solicitudes de acceso y permisos de la peña.",
destino = Screen.AdminIndex)` cuando `tieneAdmin` (usuario con al menos un
área). `PanelScreen` recibe ese booleano desde `App.kt`
(`deps.repo.usuarioActual?.areas?.isNotEmpty()`).

**`AdminIndexScreen`**: lista las sub-secciones accesibles (Solicitudes /
Permisos) según `areas`; navega a cada una. `BackHandler` → Panel.

**`AdminSolicitudesScreen`** / **`AdminPermisosScreen`**: equivalentes a la web
adaptadas a móvil (LazyColumn de tarjetas, no tablas; diálogos para el motivo de
rechazo y para editar áreas). Estados de carga/error con los tokens de color
existentes (`BaniterioColors.error`, etc.).

### 7. Migraciones y datos

**`V8__permiso_area_y_password_solicitud.sql`**:

```sql
CREATE TABLE permiso_area (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    usuario_id    BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    area          VARCHAR(40) NOT NULL,
    concedido_por BIGINT REFERENCES usuario(id),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_permiso_area UNIQUE (usuario_id, area)
);

ALTER TABLE solicitud_ingreso ADD COLUMN password_hash VARCHAR(72);
```

No se tocan V1–V7. `FlywayMigrationIT` se amplía para cubrir V8.

El fundador/superadmin ya tiene `rol = ADMIN`, así que ve el panel desde el
primer momento sin sembrar nada.

### 8. Errores nuevos (`ApiExceptionHandler`)

| Excepción | HTTP | `codigo` |
|---|---|---|
| `SinPermisoException` | 403 | `SIN_PERMISO` |
| `SolicitudYaResueltaException` | 409 | `SOLICITUD_YA_RESUELTA` |
| `UltimoAdminException` | 409 | `ULTIMO_ADMIN` |
| `AutoModificacionException` | 409 | `NO_TE_PUEDES_DEGRADAR` / `NO_TE_PUEDES_DESACTIVAR` (campo `codigo` en la excepción) |
| `SoloSuperadminException` | 409 | `SOLO_EL_SUPERADMIN` |

Front `CodigoError` y móvil `CodigoErrorAuth`/`CodigoErrorAdmin` amplían la
unión con estos valores y su mensaje en español.

## Plan de pruebas

### Backend (Testcontainers, `mvn verify`)

- **`ServicioPermisosTest`** (unit, con repos mock o slice): superadmin ve todas;
  admin ve todas; miembro con `permiso_area` ve solo esa; miembro sin nada no ve
  ninguna.
- **`AdminSolicitudesIT`**: aprobar con `password_hash` → crea usuario + membresía
  + teléfono usado + `ServicioEmail` (mock/spy) recibe la plantilla
  "cuenta creada"; login posterior del solicitante funciona. Aprobar sin
  `password_hash` → crea teléfono sin usar + plantilla "completa registro".
  Rechazar → estado RECHAZADA + `motivoRechazo` + plantilla rechazo. Doble
  resolución → 409 `SOLICITUD_YA_RESUELTA`. Sin área → 403 `SIN_PERMISO`.
- **`AdminMiembrosIT`**: cambiar rol; `ULTIMO_ADMIN` al degradar al único admin;
  `NO_TE_PUEDES_DESACTIVAR`; `PUT /areas` reemplaza el set; string de área
  inválida → 400.
- **`SolicitudConPasswordIT`** (amplía `AuthControllerIT`): `POST /solicitudes`
  con `password` guarda hash; sin `password` no falla; `traeContrasena` correcto
  en el `GET` admin.
- **`FlywayMigrationIT`**: V8 crea `permiso_area` y la columna.
- `ServicioEmail` se mockea en los IT (bean `@TestConfiguration` que sustituye
  por un spy en memoria). Test aparte de `PlantillasCorreo` (puro).

### Front / móvil

- El usuario compila y ejecuta; yo reviso.
- Front: test de `areaGuard` (redirige a `/panel` sin área; deja pasar con
  área). Test de `AdminService` con `HttpTestingController`.
- Humo manual guiado: fundador aprueba/rechaza una solicitud real, cambia un
  rol, concede un área a un miembro y ese miembro ve la sub-sección.

## Fuera de alcance (follow-ups)

- Reintentos / cola de correos fallidos.
- Histórico de solicitudes con filtros y paginación en la UI (el endpoint ya
  acepta `estado`, pero la UI solo muestra pendientes).
- Notificaciones push/badge de "solicitudes pendientes".
- Multipeña (todo se resuelve por el slug piloto `baniterio`).
- iOS: pantallas nuevas compilan (Kotlin/Native) pero no se ejecutan hasta tener
  Mac.
- Auditoría general de cambios de permisos (solo se guarda `concedido_por` /
  `resuelta_por`).
- `EstadoDesbloqueo.Inicial` muerto y demás follow-ups previos de móvil.

## Riesgos

- **Credenciales Gmail**: requiere que el usuario cree una contraseña de
  aplicación y la meta como variable de entorno. Mientras tanto, `EmailLog`
  mantiene el desarrollo desbloqueado.
- **`Panel` pasa a layout con rutas hijas**: cambio estructural en el front que
  toca `panel.html`/`panel.ts` y `app.routes.ts`; el contenido de bienvenida se
  mueve a `PanelInicio`. Riesgo de regresión en el nav móvil-web del panel.
- **Interceptor Bearer en móvil**: mover el token a un holder compartido toca el
  modelo de sesión en memoria; hay que mantener que `logout()` lo limpia.
- **Contraseña en memoria en el front**: si el usuario tarda >1 h o refresca,
  la solicitud va sin contraseña y la aprobación crea solo el teléfono
  autorizado (camino ya contemplado, pero es una diferencia de UX a comunicar
  en la pantalla).
