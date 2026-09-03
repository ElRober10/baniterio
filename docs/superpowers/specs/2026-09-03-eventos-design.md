# Eventos — diseño

**Fecha:** 2026-09-03
**Estado:** aprobado (pendiente de escribir el plan)

## Objetivo

La sección **Eventos** (hoy "próximamente") pasa a ser real. Los eventos viven
en base de datos. La primera pantalla es un **listado**: cada evento es un
botón/tarjeta desde el que se puede **ver** el evento (solo lectura) o
**gestionarlo** (editar/borrar). Hay un botón de **crear evento**.

Los administradores y el superadministrador crean, editan y borran eventos sin
restricción. Un miembro normal necesita **autorización** para crear un evento
(un "crédito" por evento) y también para borrar uno que haya creado; en ambos
casos se genera una solicitud que un administrador aprueba o rechaza desde el
panel de administración, con aviso push por medio.

## Alcance

- **Incluye:**
  - Tabla `evento` + entidad + endpoints CRUD (back).
  - Tabla `solicitud_evento` (tipos `CREAR` y `BORRAR`) + flujo de
    aprobación/rechazo por administradores, con push.
  - Siembra de 3 eventos para la peña piloto.
  - Sección Eventos en el **panel web** (Angular): listado paginado, vista de
    detalle, editor de crear/editar, diálogos de solicitud.
  - Sección Eventos en la **app móvil** (KMP/Compose): listado, detalle, editor.
  - Bloque "Solicitudes de evento" dentro de la pantalla
    `administracion/solicitudes` (web y móvil), visible solo para
    administradores/superadministradores.
- **Fuera (foreshadowing, no se implementa):**
  - Apuntarse a un evento, pago por días, gestión de asistentes ("la gente paga
    según los días que asista"). El modelo deja `fecha` + `fecha_fin` para
    soportarlo después; por ahora un evento solo tiene datos descriptivos.
  - Categorías/tipos de evento, adjuntos, cartel/imagen del evento.
  - Área de panel concedible `ADMIN_EVENTOS`: **no se crea**. Las solicitudes de
    evento las resuelve solo quien es administrador de verdad (rol `ADMIN` o
    `es_superadmin`), no un miembro con un área concedida.
  - La **campanita** de pendientes (`GET /admin/pendientes`) no cuenta las
    solicitudes de evento en esta versión (está indexada por `AreaProtegida` y
    no hay área nueva). El administrador se entera por push y al abrir la
    pantalla de solicitudes.
  - Recordatorios automáticos de "se acerca la fecha".

## Global Constraints

- **Backend:** Java 17, Spring Boot 4.1.x, Maven. Migraciones Flyway
  `V*__descripcion.sql` — las siguientes libres son **V13, V14, V15**.
  Hibernate `ddl-auto: validate`. `open-in-view: false` (cargar en el servicio,
  dentro de `@Transactional`, lo que el controlador serializa). Config tipada en
  `AppProperties` si hiciera falta.
- **Entidades:** Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor
  @Builder`), paquete `com.baniterio.api.identidad`. PK
  `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Timestamps con
  `@CreationTimestamp` / `@UpdateTimestamp`.
- **Peña única:** una sola peña por ahora (slug `"baniterio"`). El código nuevo
  resuelve la peña igual que `ResolutorAudiencia` / `AdminService` (por slug, no
  se hardcodea el id).
- **Seguridad:** JWT Bearer, stateless. Todas las rutas nuevas autenticadas. El
  servidor es la autoridad sobre los permisos (crear / editar / borrar): el
  cliente solo esconde botones, nunca decide.
- **Frontend:** Angular (componentes standalone, `ChangeDetectionStrategy.OnPush`,
  signals), Tailwind, colores de marca `#372FA5` / `#F8D349`. Tests con Jasmine
  (`*.spec.ts`).
- **Móvil:** Kotlin Multiplatform + Compose Multiplatform. Lógica en
  `commonMain`; `expect/actual` solo para lo específico de plataforma. Tests en
  `:shared:testAndroidHostTest`.
- **Idioma:** todo el texto de cara al usuario y los identificadores de dominio
  en español.

## Modelo de datos

### `evento` (V13)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | PK identity | |
| `pena_id` | FK `pena(id)` NOT NULL | |
| `nombre` | `varchar(120)` NOT NULL | |
| `descripcion` | `varchar(2000)` NULL | texto libre; se muestra solo si no está vacío |
| `lugar` | `varchar(160)` NULL | opcional |
| `fecha` | `date` NOT NULL | fecha de inicio; **es la que se muestra y por la que se ordena** |
| `fecha_fin` | `date` NULL | opcional; `CHECK (fecha_fin IS NULL OR fecha_fin >= fecha)` |
| `creado_por` | FK `usuario(id)` NOT NULL | quién lo creó (admin o miembro con crédito). `ON DELETE` no cascada: si el usuario se borra, el evento queda |
| `created_at` / `updated_at` | `timestamptz` NOT NULL DEFAULT now() | |

- Índice: `idx_evento_pena_fecha (pena_id, fecha DESC)`.
- **Pasado / futuro** a efectos de agrupar: un evento es "pasado" cuando
  `COALESCE(fecha_fin, fecha) < CURRENT_DATE`; si no, es "próximo".
- **Orden del listado:** `ORDER BY fecha DESC, id DESC`. Con este orden, los
  próximos salen antes que los pasados de forma natural, y dentro de cada grupo
  van de más reciente a más antiguo (lo pedido). No hace falta un `ORDER BY`
  con `CASE`.

### `solicitud_evento` (V14)

Espejo de `solicitud_ingreso`. Reúne los dos flujos de autorización que puede
pedir un miembro normal: crear un evento y borrar uno que creó.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | PK identity | |
| `pena_id` | FK `pena(id)` NOT NULL | |
| `solicitante_id` | FK `usuario(id)` NOT NULL | |
| `tipo` | `varchar(8)` NOT NULL | enum `TipoSolicitudEvento` = `CREAR` \| `BORRAR`. `CHECK (tipo IN ('CREAR','BORRAR'))` |
| `evento_id` | FK `evento(id)` NULL | `BORRAR`: obligatorio (el evento a borrar). `CREAR`: `NULL` hasta que el crédito se consume; luego apunta al evento creado. `ON DELETE SET NULL` |
| `mensaje` | `varchar(500)` NULL | por qué lo pide (opcional) |
| `estado` | `varchar(12)` NOT NULL | reutiliza `EstadoSolicitud` = `PENDIENTE` \| `APROBADA` \| `RECHAZADA` |
| `motivo_rechazo` | `varchar(500)` NULL | |
| `resuelta_por` | FK `usuario(id)` NULL | admin que la resolvió |
| `resuelta_at` | `timestamptz` NULL | |
| `created_at` | `timestamptz` NOT NULL DEFAULT now() | |

- Índice único parcial **una solicitud viva por (solicitante, tipo, evento)**:
  `CREATE UNIQUE INDEX uk_solicitud_evento_viva ON solicitud_evento
  (solicitante_id, tipo, COALESCE(evento_id, 0)) WHERE estado = 'PENDIENTE';`
  Impide dos solicitudes `CREAR` a la vez del mismo miembro, y dos `BORRAR`
  pendientes del mismo evento por la misma persona.
- Para `BORRAR` conviene además que no haya **dos** `BORRAR` pendientes del
  mismo evento de personas distintas: se comprueba en servicio (no con índice)
  antes de insertar → 409 si ya hay una.
- `APROBADA` / `RECHAZADA` son terminales e históricas.

**Crédito de creación.** Un miembro normal *puede crear un evento* si tiene una
`solicitud_evento` con `tipo='CREAR'`, `estado='APROBADA'` y `evento_id IS
NULL` (crédito sin consumir). Al crear el evento, esa fila se actualiza con el
`evento_id` recién creado (crédito consumido). *Puede solicitar* un crédito si
no tiene ninguna `CREAR` en `PENDIENTE` ni ninguna `CREAR` `APROBADA` sin
consumir. Los administradores y el superadministrador no usan créditos.

### `V15__seed_eventos.sql`

Inserta 3 eventos para la peña `baniterio`, con `creado_por` = usuario
fundador (mismo teléfono que siembra V6: `616985168`, resuelto por subconsulta).
Idempotente por `(pena_id, nombre)` — no hay constraint única, así que el
`INSERT ... SELECT ... WHERE NOT EXISTS`.

| nombre | fecha | fecha_fin | lugar | descripción |
|---|---|---|---|---|
| Fiestas de San Miguel 2026 | 2026-09-25 | 2026-09-26 | (vacío) | (vacío) |
| Chuletas Santas 2027 | 2027-03-26 | NULL | (vacío) | (vacío) |
| Migas Santas 2027 | 2027-03-27 | NULL | (vacío) | (vacío) |

Las fechas varían cada año; se editan por la UI en cada edición del evento.

## Permisos

| Acción | Admin / superadmin | Creador (miembro normal) | Resto de miembros |
|---|---|---|---|
| Ver listado y detalle | Sí | Sí | Sí |
| Crear evento | Sí, directo | Solo con crédito `CREAR` aprobado sin consumir | No (puede solicitar crédito) |
| Editar evento | Sí, directo | Sí, directo (solo el suyo) | No |
| Borrar evento | Sí, directo | Genera `solicitud_evento` `BORRAR` (no borra) | No |
| Resolver solicitudes de evento | Sí | No | No |

"Administrador de verdad" = `servicioPermisos.esAdministrador(id)` (rol `ADMIN`
o `es_superadmin`). Un miembro con un área de panel concedida **no** cuenta.

## API (backend)

Base `/api/v1`. Todas autenticadas.

### Eventos — `EventoController` (`/eventos`)

- `GET /eventos?pagina=0`
  → `{ eventos: EventoResumen[], pagina, totalPaginas, puedeCrear, puedeSolicitar }`
  - `eventos`: página de 8, orden `fecha DESC, id DESC`.
  - `EventoResumen`: `{ id, nombre, fecha, fechaFin, lugar, pasado }`.
  - `puedeCrear`: el usuario puede crear ya (admin, o crédito sin consumir).
  - `puedeSolicitar`: no puede crear pero puede pedir crédito (siempre `false`
    para admins).
- `GET /eventos/{id}` → `EventoDetalle`
  `{ id, nombre, descripcion, lugar, fecha, fechaFin, pasado, creadoPor: { id, nombre },
     puedoEditar, puedoBorrar, borradoPendiente }`
  - `puedoEditar` / `puedoBorrar`: para pintar los botones de gestión.
  - `borradoPendiente`: hay una `solicitud_evento` `BORRAR` `PENDIENTE` para
    este evento (se avisa en la vista y se desactiva "solicitar borrado").
- `POST /eventos` — body `GuardarEventoRequest`
  `{ nombre, descripcion?, lugar?, fecha, fechaFin? }`
  - Validación: `nombre` no vacío (≤120), `fecha` presente,
    `fechaFin` nula o `>= fecha`, `descripcion` ≤2000, `lugar` ≤160.
  - Autorización: admin → crea. Miembro con crédito → crea y **consume** el
    crédito (misma transacción). Miembro sin crédito → 409 `SIN_CREDITO_EVENTO`.
  - → 201 `EventoDetalle`.
- `PUT /eventos/{id}` — mismo body.
  - Autorización: admin o `evento.creado_por == usuario`. Si no → 403
    `SIN_PERMISO_EVENTO`.
  - → 200 `EventoDetalle`.
- `DELETE /eventos/{id}`
  - Admin → borra el evento (204). Las `solicitud_evento` que lo referencian
    quedan con `evento_id = NULL` (`ON DELETE SET NULL`); una `BORRAR`
    `PENDIENTE` sobre ese evento se marca `APROBADA` implícitamente (resuelta
    por el admin) — o se deja como está; **decisión: se marca `APROBADA`** para
    que el solicitante reciba el push de "hecho".
  - Creador no admin → **no borra**: crea `solicitud_evento { tipo: BORRAR,
    evento_id }`, publica push a administradores, → 202 `{ estado: "PENDIENTE" }`.
    Si ya hay una `BORRAR` `PENDIENTE` para ese evento → 409
    `SOLICITUD_EVENTO_YA_PENDIENTE`.
  - Otro → 403 `SIN_PERMISO_EVENTO`.
- `POST /eventos/solicitudes` — body `{ mensaje? }`
  - Crea `solicitud_evento { tipo: CREAR }`. 409 `SOLICITUD_EVENTO_YA_PENDIENTE`
    si ya tiene una `CREAR` pendiente; 409 `CREDITO_SIN_CONSUMIR` si ya tiene un
    crédito aprobado sin usar; 409 si el solicitante ya es admin
    (`no_aplica`). → 201 `{ id, estado: "PENDIENTE" }`.
  - Publica push a administradores.

### Solicitudes de evento (admin) — `SolicitudEventoAdminController` (`/admin/solicitudes-evento`)

Gated por `servicioPermisos.esAdministrador(principal.id())` → si no, 403
`SIN_PERMISO`.

- `GET /admin/solicitudes-evento?estado=PENDIENTE`
  → `SolicitudEventoResumen[]`
  `{ id, tipo, estado, solicitante: { id, nombre, apellidos },
     evento: { id, nombre, fecha } | null, mensaje, createdAt }`
  ordenadas por `created_at` ascendente.
- `POST /admin/solicitudes-evento/{id}/aprobar`
  - `CREAR`: `estado = APROBADA` (deja `evento_id` NULL: es un crédito). Push al
    solicitante: "Ya puedes crear tu evento".
  - `BORRAR`: `estado = APROBADA` **y se borra el evento** en la misma
    transacción. Push al solicitante: "Se ha borrado el evento X".
  - 409 `SOLICITUD_EVENTO_YA_RESUELTA` si no está `PENDIENTE`.
- `POST /admin/solicitudes-evento/{id}/rechazar` — body `{ motivo? }`
  - `estado = RECHAZADA`, guarda `motivo_rechazo` (texto por defecto si vacío).
  - Push al solicitante con el motivo.
  - 409 `SOLICITUD_EVENTO_YA_RESUELTA` si no está `PENDIENTE`.

Todos los errores se traducen en `ApiExceptionHandler` (mismo patrón que
`SolicitudYaResueltaException`, `SinPermisoException`, etc.).

### Notificaciones push

Reutiliza `AvisoPushEvent` + `ManejadorAvisoPush` (envío tras commit).

| Disparo | Audiencia | Título / cuerpo (aprox.) |
|---|---|---|
| Miembro pide crédito `CREAR` | `Administradores` | "Solicitud de evento" / "{nombre} quiere crear un evento" |
| Miembro pide `BORRAR` | `Administradores` | "Solicitud de borrado" / "{nombre} quiere borrar «{evento}»" |
| Admin aprueba `CREAR` | `UsuarioUnico(solicitante)` | "Evento autorizado" / "Ya puedes crear tu evento" |
| Admin aprueba `BORRAR` | `UsuarioUnico(solicitante)` | "Evento borrado" / "Se ha borrado «{evento}»" |
| Admin rechaza (cualquiera) | `UsuarioUnico(solicitante)` | "Solicitud rechazada" / motivo |

Patrón: un `record ...Event` de dominio publicado dentro de la transacción y un
`@TransactionalEventListener(phase = AFTER_COMMIT)` que llama a `ServicioPush` /
publica el `AvisoPushEvent`. No se manda correo (a diferencia de las solicitudes
de ingreso).

## Backend — ficheros

- `db/migration/V13__create_evento.sql`, `V14__create_solicitud_evento.sql`,
  `V15__seed_eventos.sql`.
- `identidad/Evento.java`, `identidad/EventoRepository.java`.
- `identidad/SolicitudEvento.java`, `identidad/TipoSolicitudEvento.java`,
  `identidad/SolicitudEventoRepository.java`.
- `evento/EventoController.java`, `evento/EventoService.java`.
- `evento/SolicitudEventoAdminController.java`,
  `evento/SolicitudEventoService.java` (o un único `EventoService` si queda
  manejable; se decide al escribir el plan).
- `evento/dto/`: `EventoResumen`, `EventoDetalle`, `GuardarEventoRequest`,
  `ListaEventosResponse`, `SolicitudEventoRequest`, `SolicitudEventoResumen`,
  `RechazoRequest` (reusar el de admin si encaja).
- `evento/` excepciones: `EventoNoEncontradoException` (404),
  `SinPermisoEventoException` (403), `SinCreditoEventoException` (409),
  `SolicitudEventoYaPendienteException` (409),
  `SolicitudEventoYaResueltaException` (409),
  `CreditoSinConsumirException` (409).
- Eventos de dominio + listeners push en `evento/` o `push/`.
- `ApiExceptionHandler`: añadir los mapeos.

## Web — `front/src/app/panel/eventos/`

- `eventos.ts` / `eventos.html` — **listado**.
  - Llama `GET /eventos?pagina`. Pinta cada evento como botón/tarjeta
    (nombre + fecha; atenuado si `pasado`). Al pulsar → `/panel/eventos/{id}`.
  - Paginado: 8 por página, controles anterior/siguiente con `totalPaginas`.
  - Botón **"Crear evento"** si `puedeCrear` → `/panel/eventos/nuevo`.
  - Botón **"Solicitar crear evento"** si `puedeSolicitar` → diálogo con
    `mensaje` opcional → `POST /eventos/solicitudes`; al volver, mensaje de
    confirmación y el botón se desactiva.
- `evento-detalle/evento-detalle.ts` — **vista**.
  - `GET /eventos/{id}`. Muestra nombre, fecha(s), lugar, descripción, quién lo
    creó. Botón **"Gestionar"** si `puedoEditar` → editor. Botón **"Borrar"** /
    **"Solicitar borrado"** si `puedoBorrar` (texto según sea admin o creador);
    desactivado con aviso si `borradoPendiente`.
- `editor-evento/editor-evento.ts` — **crear / editar**.
  - Formulario: nombre, descripción (textarea), lugar, fecha, fecha fin
    (opcional). Validación cliente que refleja la del server.
  - `POST` o `PUT` según haya `id` en la ruta. Maneja 409 `SIN_CREDITO_EVENTO`
    (no debería llegar si la UI está bien, pero se muestra el error).
- `eventos.service.ts` + `eventos.types.ts` (contra `/api/v1/eventos` y
  `/api/v1/admin/solicitudes-evento`).
- Rutas hijas de `panel` en `app.routes.ts`, todas con `perfilCompletoGuard`:
  `eventos`, `eventos/nuevo`, `eventos/:id`, `eventos/:id/editar`.
- `secciones.ts`: quitar la entrada `Eventos` de `SECCIONES` ("próximamente").
- `panel.ts` / `panel.html`: añadir "Eventos" como enlace real del nav (junto a
  Inicio / Miembros), no en `seccionesPronto`.
- `admin/solicitudes/`: nuevo bloque "Solicitudes de evento" bajo el de
  ingreso, visible solo si el usuario en sesión es admin/superadmin
  (`auth.usuarioActual()` con rol admin o `esSuperadmin`). Reusa el estilo de
  tarjeta de solicitud; botones Aprobar / Rechazar (con motivo) contra
  `/admin/solicitudes-evento`.
- Tests `*.spec.ts` de cada componente y del service.

## Móvil — `mobile/shared/src/commonMain/kotlin/com/baniterio/app/`

- `ui/eventos/EventosScreen.kt` — listado paginado (lista o rejilla simple de
  botones), botón "Crear evento" / "Solicitar crear evento".
- `ui/eventos/EventoDetalleScreen.kt` — vista + acciones de gestión.
- `ui/eventos/EditorEventoScreen.kt` — crear / editar (selector de fecha de
  Compose Multiplatform o campos de texto con validación; se decide en el plan).
- `data/EventosRepository.kt` + DTOs en `data/` (kotlinx.serialization),
  contra los mismos endpoints.
- Navegación: entradas en `ui/panel/PanelScreen.kt` y en el grafo de navegación
  (misma mecánica que Miembros).
- `ui/admin/AdminSolicitudesScreen.kt`: bloque de solicitudes de evento para
  administradores.
- Tests en `:shared:testAndroidHostTest` para el repositorio y la lógica de
  `puedeCrear` / `puedeSolicitar` / agrupado pasado-próximo.

## Flujo: crear un evento (miembro normal)

```
1. Miembro abre Eventos. No tiene crédito → ve "Solicitar crear evento".
2. Pulsa, escribe un mensaje opcional → POST /eventos/solicitudes
   → solicitud_evento { tipo: CREAR, estado: PENDIENTE }.
   → push a administradores.
3. Admin abre administracion/solicitudes → bloque "Solicitudes de evento".
   Aprueba → estado APROBADA, evento_id NULL (crédito).  → push al miembro.
   (o Rechaza con motivo → push al miembro, fin.)
4. Miembro abre Eventos → ahora ve "Crear evento".
   Rellena el formulario → POST /eventos.
   → se crea el evento (creado_por = miembro) y la solicitud pasa a
     evento_id = <nuevo> (crédito consumido).
5. Para otro evento, vuelve al paso 1.
```

## Flujo: borrar un evento (creador no admin)

```
1. Creador abre el detalle de su evento → "Solicitar borrado".
   → DELETE /eventos/{id}  (el server ve que no es admin)
   → solicitud_evento { tipo: BORRAR, evento_id, estado: PENDIENTE }
   → push a administradores.   El evento NO se borra.
2. En el detalle del evento queda "Borrado pendiente de autorización".
3. Admin aprueba → el evento se borra + solicitud APROBADA → push al creador.
   Admin rechaza → el evento sigue + solicitud RECHAZADA con motivo → push.
```

Un admin que pulsa "Borrar" en cualquier evento lo borra directamente (204),
sin solicitud.

## Errores (códigos de `ApiExceptionHandler`)

| Situación | HTTP | código |
|---|---|---|
| Evento inexistente | 404 | `EVENTO_NO_ENCONTRADO` |
| Editar/borrar sin ser admin ni creador | 403 | `SIN_PERMISO_EVENTO` |
| Resolver solicitud de evento sin ser admin | 403 | `SIN_PERMISO` |
| Crear evento sin crédito (miembro) | 409 | `SIN_CREDITO_EVENTO` |
| Segunda solicitud del mismo tipo pendiente | 409 | `SOLICITUD_EVENTO_YA_PENDIENTE` |
| Pedir crédito teniendo uno aprobado sin usar | 409 | `CREDITO_SIN_CONSUMIR` |
| Aprobar/rechazar una solicitud ya resuelta | 409 | `SOLICITUD_EVENTO_YA_RESUELTA` |
| `fechaFin` anterior a `fecha` | 400 | validación |

## Plan de ejecución

Rama `feature/eventos` desde `main`. Tres planes, como en perfiles-miembros:

- **Plan A — backend:** migraciones, entidades, repos, servicios, controladores,
  DTOs, excepciones, push, tests. Se puede probar con `curl` / los tests.
- **Plan B — web:** service + tipos, listado, detalle, editor, diálogos de
  solicitud, nav, bloque admin, tests.
- **Plan C — móvil:** repositorio + DTOs, pantallas, navegación, bloque admin,
  tests.

`feature/perfiles-miembros` sigue pendiente de merge a `main`; se cierra antes
de arrancar esta rama (o se ramifica desde ella si el usuario lo prefiere).
