# Asistencia a eventos 3a — RSVP + "Mandar notificación" — diseño

**Fecha:** 2026-09-03
**Estado:** borrador (pendiente de revisión del usuario)

## Contexto

Pieza 3 del subsistema **Cuentas** (ver `memory/baniterio_cuentas.md`). Se parte en tres:

- **3a (este spec)** — RSVP + notificación, para **todos los eventos**.
- **3b** — ficha de bebida + cálculo de cuota de **San Miguel** (eventos con `cuota_maxima`).
- **3c** — pareja, hijos (≥18, con aviso de cuota), invitados y "el invitado quiere ser peñista".

## Objetivo

Cada evento futuro puede tener una **convocatoria**: un administrador o quien
organiza el evento pulsa **"Mandar notificación"** (con un texto libre) y llega
un push a toda la peña. Cada persona responde **Me apunto / No voy / En duda**.
Quien no tiene notificaciones activadas o no ha respondido se encuentra, al
abrir la app, una **pantalla que bloquea** el resto de la app hasta que
responde. El que organiza puede **reenviar** la notificación pasadas 48 h, y solo
le llega a quien aún no ha contestado. El administrador y el organizador pueden
**añadir asistentes a mano** (invitados o gente sin la app). La respuesta se
puede **cambiar hasta el día del evento**.

## Alcance

- **Incluye:**
  - Tabla `asistencia_evento` + entidad. Una fila por persona y evento, con
    `estado` (APUNTADO / NO_VOY / EN_DUDA). Soporta personas **sin usuario**
    (añadidas a mano): `usuario_id` NULL + `nombre`.
  - Tabla `notificacion_evento` — registro de cada envío (texto, quién, cuándo).
    Sirve para la regla de las 48 h y para saber "quién no ha contestado".
  - Nueva `Audiencia.SinRespuestaEvento(eventoId)` en el módulo push.
  - Endpoints (back):
    - `PUT  /api/v1/eventos/{id}/asistencia` — mi respuesta.
    - `POST /api/v1/eventos/{id}/notificacion` — mandar / reenviar (admin u organizador).
    - `POST /api/v1/eventos/{id}/asistencias` — añadir a mano (admin u organizador).
    - `DELETE /api/v1/eventos/{id}/asistencias/{asistenciaId}` — quitar una añadida a mano.
    - `GET  /api/v1/eventos/pendientes-respuesta` — eventos con notificación enviada y sin respuesta mía (alimenta la pantalla bloqueante).
  - `EventoDetalle` gana: `miAsistencia` (estado o `null`), `puedeNotificar`,
    `notificacionReenviableAt` (instante a partir del cual se puede reenviar, o
    `null` si aún no se ha enviado nunca), y un recuento
    `asistencia { apuntados, noVoy, enDuda, sinContestar }`.
  - **Web**: en el detalle del evento, botón "Mandar notificación" (diálogo con
    textarea), botones de respuesta, bloque "Añadir a mano". Guard que redirige a
    una pantalla bloqueante `/panel/responder` mientras haya eventos pendientes.
  - **Móvil**: misma lógica. Al arrancar sesión (después de `CargandoSesion`,
    igual que el perfil obligatorio) se consulta `pendientes-respuesta`; si hay,
    se muestra `RespuestaEventoScreen` sin "atrás" hasta responder a todos.
- **Fuera (lo hace 3b / 3c):**
  - Formulario de bebida, modalidades y cálculo de cuota. En 3a, "añadir a mano"
    solo pide **nombre + estado**; 3b añadirá los campos de bebida a ese mismo
    formulario.
  - La **lista completa** de asistentes con sus fichas y la marca de pago (eso es
    la "sección peñista" dentro de la Cuenta — pieza 4). 3a solo expone el
    **recuento**.
  - Recordatorios automáticos. El reenvío es **manual**.
  - Deep-link del push a la pantalla de respuesta. El push abre la app y la
    pantalla bloqueante recoge al usuario.
  - Contar los eventos pendientes en la campanita del panel.

## Global Constraints

- **Backend:** Java 17, Spring Boot 4.1.x, Maven. Migraciones Flyway
  `V*__descripcion.sql` — las siguientes libres son **V20, V21**. Hibernate
  `ddl-auto: validate`, `open-in-view: false`. Peña piloto por slug `baniterio`.
  Errores → `ApiExceptionHandler` con `codigo` estable.
- **Web:** Angular 22, componentes standalone, signals, Tailwind. Tests con
  Vitest (`ng test`).
- **Móvil:** Kotlin Multiplatform + Compose Multiplatform. Navegación por
  `Screen` sellado + `when` en `App.kt` (sin librería de nav). Repos que
  devuelven `Resultado*`, nunca lanzan por errores HTTP esperados. Tests
  `:shared:testAndroidHostTest`.
- **Push:** ya existe `AvisoPushEvent` + `ResolutorAudiencia` + FCM. El envío es
  best-effort (si el push falla no revienta la petición).

## Modelo de datos

### `asistencia_evento` (V20)

| columna            | tipo          | notas |
|--------------------|---------------|-------|
| id                 | BIGINT PK     | |
| evento_id          | BIGINT NOT NULL FK `evento(id)` ON DELETE CASCADE | |
| usuario_id         | BIGINT NULL FK `usuario(id)` ON DELETE CASCADE | NULL = persona sin app / añadida a mano |
| nombre             | VARCHAR(120) NULL | obligatorio (a nivel servicio) cuando `usuario_id` es NULL; se ignora si hay usuario (se usa su nombre) |
| estado             | VARCHAR(16) NOT NULL | `APUNTADO` \| `NO_VOY` \| `EN_DUDA` |
| registrado_por_id  | BIGINT NULL FK `usuario(id)` ON DELETE SET NULL | quién creó la fila; NULL = el propio usuario respondió |
| created_at         | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| updated_at         | TIMESTAMPTZ NOT NULL DEFAULT now() | |

- Índice único parcial: `UNIQUE (evento_id, usuario_id) WHERE usuario_id IS NOT NULL`
  (una persona con app solo tiene una respuesta por evento; las añadidas a mano
  pueden repetir nombre sin chocar).
- Índice `(evento_id)`.

### `notificacion_evento` (V21)

| columna         | tipo          | notas |
|-----------------|---------------|-------|
| id              | BIGINT PK     | |
| evento_id       | BIGINT NOT NULL FK `evento(id)` ON DELETE CASCADE | |
| texto           | VARCHAR(500) NULL | el texto libre que escribió quien convoca |
| enviada_por_id  | BIGINT NOT NULL FK `usuario(id)` | |
| enviada_at      | TIMESTAMPTZ NOT NULL DEFAULT now() | |

- Índice `(evento_id, enviada_at DESC)`.

## Reglas

### Responder (`PUT /eventos/{id}/asistencia`)

- Cuerpo: `{ "estado": "APUNTADO" | "NO_VOY" | "EN_DUDA" }`.
- Cualquier miembro activo de la peña.
- Solo si el evento **no ha pasado**: `evento.fecha >= hoy` (la fecha de inicio;
  se elige la de inicio a propósito — "hasta el día del evento").
  Si ya pasó → 409 `EVENTO_YA_PASADO`.
- Upsert de la fila `(evento_id, usuario_id = yo)`; `registrado_por_id` queda NULL.
- Cambiar la respuesta = volver a llamar. Sin límite de cambios hasta la fecha.

### Mandar / reenviar notificación (`POST /eventos/{id}/notificacion`)

- Cuerpo: `{ "texto"?: string }` (máx. 500; se recorta; vacío → NULL).
- Autor: **administrador/superadministrador** o `evento.creadoPor` (el organizador).
  Si no → 403 `SIN_PERMISO_EVENTO`.
- Solo si el evento no ha pasado (`evento.fecha >= hoy`) → si no, 409 `EVENTO_YA_PASADO`.
- **Primera vez**: siempre permitido.
- **Reenvío**: solo si han pasado **≥ 48 h** desde `max(enviada_at)` de ese
  evento → si no, 409 `NOTIFICACION_REENVIO_PRONTO`.
- Efecto:
  1. Inserta una fila en `notificacion_evento`.
  2. Publica `AvisoPushEvent(new Audiencia.SinRespuestaEvento(eventoId),
     "Eventos", cuerpo)` donde `cuerpo` = el texto si lo hay, si no
     `"«" + evento.nombre + "» — ¿te apuntas? Entra en la app y responde."`.
- `Audiencia.SinRespuestaEvento`: miembros **activos** de la peña cuyo
  `usuario.id` **no** aparece en `asistencia_evento` para ese evento. (Los que
  dijeron "No voy" ya tienen fila → no reciben nada: eso cubre "el que dice no
  voy deja de recibir notificaciones de ese evento".) En el primer envío, nadie
  ha respondido → llega a toda la peña, incluido quien la manda.

### Añadir a mano (`POST /eventos/{id}/asistencias`)

- Cuerpo: `{ "nombre": string, "estado": "APUNTADO" | "NO_VOY" | "EN_DUDA" }`.
- Autor: administrador/superadministrador o `evento.creadoPor` → si no, 403.
- Crea una fila con `usuario_id = NULL`, `nombre`, `estado`,
  `registrado_por_id = yo`. No hay unicidad por nombre (puede haber dos "Primo de
  Juan").
- (3b añadirá aquí los campos de bebida.)

### Quitar añadida a mano (`DELETE /eventos/{id}/asistencias/{asistenciaId}`)

- Autor: admin/superadmin o `evento.creadoPor` → si no, 403.
- Solo filas con `usuario_id IS NULL` → borrar la respuesta de un usuario con
  app no se hace desde aquí (409 `ASISTENCIA_NO_MANUAL`).
- 404 `ASISTENCIA_NO_ENCONTRADA` si no existe o no es de ese evento.

### Pantalla bloqueante (`GET /eventos/pendientes-respuesta`)

- Devuelve la lista de eventos (resúmenes) tales que:
  - `evento.fecha >= hoy`, y
  - existe al menos una `notificacion_evento` para ese evento, y
  - **no** tengo fila en `asistencia_evento`.
- Ordenados por `fecha` ascendente.
- El cliente muestra la pantalla de respuesta para el primero; al responder,
  recarga; cuando la lista queda vacía, sigue a la app normal.
- Nota: responder "No voy" **cuenta como responder** → sale de la lista.

## Contrato de API (resumen)

```
PUT  /api/v1/eventos/{id}/asistencia
  req:  { estado: "APUNTADO" | "NO_VOY" | "EN_DUDA" }
  200:  EventoDetalle (con miAsistencia actualizado)
  409:  EVENTO_YA_PASADO

POST /api/v1/eventos/{id}/notificacion
  req:  { texto?: string }         // <= 500
  204
  403:  SIN_PERMISO_EVENTO
  409:  EVENTO_YA_PASADO | NOTIFICACION_REENVIO_PRONTO

POST /api/v1/eventos/{id}/asistencias
  req:  { nombre: string, estado: "APUNTADO" | "NO_VOY" | "EN_DUDA" }
  201:  AsistenciaResumen { id, nombre, estado, esManual: true }
  403:  SIN_PERMISO_EVENTO

DELETE /api/v1/eventos/{id}/asistencias/{asistenciaId}
  204
  403:  SIN_PERMISO_EVENTO
  404:  ASISTENCIA_NO_ENCONTRADA
  409:  ASISTENCIA_NO_MANUAL

GET  /api/v1/eventos/pendientes-respuesta
  200:  { eventos: EventoResumen[] }
```

`EventoDetalle` (añadidos):
```
miAsistencia: "APUNTADO" | "NO_VOY" | "EN_DUDA" | null
puedeNotificar: boolean            // soy admin u organizador y el evento no ha pasado
notificacionReenviableAt: string | null   // ISO instant; null si nunca se ha enviado
asistencia: { apuntados: number, noVoy: number, enDuda: number, sinContestar: number }
```

## Componentes

### Backend — paquete `com.baniterio.api.evento`

- `identidad/AsistenciaEvento` (entidad) + `AsistenciaEventoRepository`.
- `identidad/NotificacionEvento` (entidad) + `NotificacionEventoRepository`.
- `identidad/EstadoAsistencia` (enum: APUNTADO, NO_VOY, EN_DUDA).
- `AsistenciaService` — responder, notificar, añadir/quitar a mano, pendientes,
  y el recuento + `miAsistencia` que consume `EventoService.aDetalle`.
- `AsistenciaController` — los 5 endpoints (los 3 de `/eventos/{id}/asistencia*`
  y `/notificacion` cuelgan del `EventoController` o de uno nuevo; se decide en
  el plan).
- `push/Audiencia.SinRespuestaEvento` + rama en `ResolutorAudiencia`.
- Excepciones nuevas: `EventoYaPasadoException` (409 `EVENTO_YA_PASADO`),
  `NotificacionReenvioProntoException` (409), `AsistenciaNoEncontradaException`
  (404), `AsistenciaNoManualException` (409).
- `EventoService.aDetalle` pasa a pedir a `AsistenciaService` los datos extra.

### Web — `front/src/app/panel`

- `eventos/eventos.service.ts` + tipos: `responder`, `mandarNotificacion`,
  `anadirAsistente`, `quitarAsistente`, `pendientesRespuesta`.
- `eventos/evento-detalle/`: botones de respuesta, diálogo "Mandar notificación"
  (textarea + estado del botón de reenvío), bloque "Añadir a mano", recuento.
- `panel/responder/` (nuevo componente) — pantalla bloqueante.
- Guard `respuestaPendienteGuard` en las rutas hijas de `/panel` (excepto la
  propia `/panel/responder`), análogo a `perfilCompletoGuard`.

### Móvil — `mobile/shared/.../`

- `data/AsistenciaRepository` (+ Impl) + DTOs + `ResultadoAsistencia`.
- `data/dto/EventoDtos`: `EventoDetalle` gana los campos nuevos.
- `nav/Screen.ResponderEvento`; cableado en `App.kt` (chequeo tras
  `CargandoSesion`, como `EditorPerfil` obligatorio).
- `ui/eventos/EventoDetalleScreen`: respuesta + notificación + añadir a mano + recuento.
- `ui/eventos/ResponderEventoScreen` (bloqueante).

## Testing

- **Back (IT):** responder crea/actualiza fila; responder evento pasado → 409;
  notificar primera vez → 204 + fila en `notificacion_evento`; reenvío < 48 h →
  409; reenvío ≥ 48 h → 204; `SinRespuestaEvento` excluye a los que ya
  respondieron (incluidos los NO_VOY); no admin ni organizador → 403; añadir a
  mano → fila con `usuario_id` NULL; borrar a mano ok; borrar la de un usuario →
  409; `pendientes-respuesta` solo trae eventos con notificación y sin respuesta
  mía; `EventoDetalle` trae el recuento correcto.
- **Web:** el detalle pinta los 3 botones y marca el elegido; el diálogo de
  notificación hace POST; el guard redirige a `/panel/responder` cuando hay
  pendientes y deja pasar cuando no.
- **Móvil:** `AsistenciaRepositoryImpl` pega a los endpoints correctos con
  Bearer; `App.kt` muestra `ResponderEvento` cuando `pendientes-respuesta` no
  está vacío.

## Decisiones abiertas (confirmar en revisión)

1. "Hasta el día del evento" = `evento.fecha` (inicio), no `fecha_fin`. ¿OK?
2. Los endpoints de asistencia, ¿en `EventoController` o en un
   `AsistenciaController` nuevo? (no cambia el contrato; preferencia de
   organización).
3. El push de notificación no lleva deep-link; ¿suficiente con que la pantalla
   bloqueante recoja al usuario al abrir la app?
4. Reenvío: la ventana de 48 h se mide desde el **último** envío. ¿OK aunque en
   medio nadie haya contestado?
