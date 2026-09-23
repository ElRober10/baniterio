# Registro de eventos y errores (auditoría + depuración) — Diseño

**Fecha:** 2026-09-23
**Rama:** `desarrollo`
**Contexto:** surge al depurar un error real de móvil (subir un recibo en
Cuentas fallaba sin dar pista útil). El usuario pide dos cosas a la vez:
poder depurar errores que ocurren en producción, y saber "quién hizo qué" en
la app. Se resuelven con el mismo mecanismo.

## Objetivo

Una única tabla de eventos que registra:
- Toda petición que escribe algo en la API (quién, cuándo, qué endpoint, si
  salió bien o mal) — cubre la auditoría de acciones.
- Los errores que ocurren en el cliente (móvil o web) y nunca llegan a
  golpear el backend (sin conexión, excepción antes de mandar la petición) —
  cubre el caso que disparó esto (recibo con error genérico).

Consultable desde una pantalla nueva en el panel de administración, en web y
en móvil, solo para superadmin.

## Decisiones tomadas (brainstorming 2026-09-23)

- **Una sola tabla**, no acción y error por separado: cada fila es una
  petición de escritura; si falló, la misma fila lleva el código de error.
  Menos tablas, menos JOINs, y "qué pasó con esta petición" es una fila.
- **Automático a nivel HTTP**, no instrumentado a mano en cada servicio: un
  filtro de Spring engancha todo `POST/PUT/PATCH/DELETE` a `/api/v1/**`.
  Cubre funcionalidad futura sin acordarse de añadir el log.
- **Errores que no llegan al backend**: endpoint público
  `POST /api/v1/logs/cliente`, llamado de forma centralizada desde el cliente
  HTTP (plugin de Ktor en móvil, interceptor HTTP ya existente en Angular),
  no desde cada pantalla/repositorio.
- **Solo superadmin ve el log** (`ServicioPermisos.esAdministrador`), no es
  un área delegable (`AreaProtegida`) — es información sensible de quién hizo
  qué.
- **Sin retención/purga automática** por ahora (peña pequeña, bajo volumen).
  Se puede añadir después si hace falta.
- **"Fire and forget" en el cliente**: si `/logs/cliente` falla, no se
  reintenta ni se le muestra nada al usuario — no debe poder romper un flujo
  por culpa del propio logging.
- **El filtro no debe poder romper la petición real**: cualquier fallo al
  guardar el log se atrapa y se ignora dentro del propio filtro.
- Rutas excluidas del filtro: login/registro (público, ya se sabe qué es) y
  el propio `/api/v1/logs/**` (evita bucle y ruido).

## Global Constraints

Igual que el resto de la app: Java 17, Spring Boot, Flyway (Postgres),
Lombok, JUnit 5 + `RestTestClient` para IT. Angular standalone + signals +
control flow, Vitest. Kotlin Multiplatform (Compose) en móvil, mismo patrón
de `Repository` + `Resultado<T>` sellado que el resto de secciones. Textos de
UI en español. Errores de dominio → `{ "codigo": "<CODE>" }` vía
`ApiExceptionHandler`.

## Modelo de datos

Migración Flyway nueva (`V63__log_evento.sql`, siguiente libre tras `V62`).

```sql
CREATE TABLE log_evento (
    id BIGSERIAL PRIMARY KEY,
    pena_id BIGINT NOT NULL REFERENCES pena(id),
    usuario_id BIGINT NULL REFERENCES usuario(id),
    origen VARCHAR(20) NOT NULL,       -- BACKEND / WEB / MOBILE
    metodo VARCHAR(10) NULL,           -- NULL cuando origen=cliente y nunca llegó a construirse la petición
    ruta VARCHAR(300) NULL,
    estado INTEGER NULL,               -- código HTTP de la respuesta; NULL si nunca hubo respuesta (error de cliente)
    codigo_error VARCHAR(60) NULL,     -- el mismo "codigo" que ya usa ApiExceptionHandler, o algo libre desde el cliente
    mensaje TEXT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_log_evento_pena_creado ON log_evento (pena_id, creado_en DESC);
CREATE INDEX idx_log_evento_usuario ON log_evento (usuario_id);
```

`pena_id` sigue el mismo patrón multi-tenant que el resto (`PenaPilotoService`):
un log de la peña demo no se mezcla con el de la real.

## Backend

### Captura de acciones — `LogEventoFilter`

`OncePerRequestFilter` registrado tras el filtro de autenticación JWT (para
poder leer `UsuarioPrincipal` si lo hay). Solo actúa si el método es
`POST/PUT/PATCH/DELETE` y la ruta empieza por `/api/v1/` y no por
`/api/v1/logs`, `/api/v1/auth/registro` ni `/api/v1/auth/login`.

Flujo:
1. Deja pasar la petición (`filterChain.doFilter`).
2. Tras completarse (éxito o excepción ya resuelta por `ApiExceptionHandler`,
   que corre antes en la cadena de Spring MVC), lee el `HttpServletResponse`
   para el código de estado.
3. Si el estado es 4xx/5xx, intenta recuperar el `codigo` que
   `ApiExceptionHandler` ya calculó (se deja en un atributo del `request`,
   `ApiExceptionHandler` lo setea con `request.setAttribute(...)` justo antes
   de devolver el cuerpo de error).
4. Guarda una fila (`origen=BACKEND`, `usuario_id` = el autenticado o null).
5. Cualquier excepción durante el guardado se captura y se loguea con
   `Logger.warn` normal (SLF4J) — nunca se propaga.

`ApiExceptionHandler` gana una línea en cada `@ExceptionHandler`:
`request.setAttribute("logEvento.codigo", "RECIBO_NO_VALIDO")` (o se
centraliza en un método común `error(...)` que ya existe, para no repetirlo
en cada handler).

### Errores de cliente — `POST /api/v1/logs/cliente`

Nuevo `LogClienteController`. Sin `@AuthenticationPrincipal` obligatorio
(puede no haber sesión); si el JWT es válido y presente, se guarda el
`usuario_id`, si no, `null`.

```java
public record LogClienteRequest(String origen, String pantalla, String mensaje) {}
```
- `origen`: "WEB" o "MOBILE" (viene del cliente).
- `pantalla`: identificador libre corto (p. ej. "cuentas.crearMovimiento").
- `mensaje`: texto corto del error.

Guarda con `estado=null`, `metodo=null`, `ruta=pantalla` (reutilizando la
columna para no añadir otra). Responde `202 Accepted` siempre (no hay nada
que pueda fallar del lado del cliente que valga la pena reportar como 4xx).

### Consulta — `GET /api/v1/logs`

Solo superadmin (`exigirAdmin`, igual que el resto de endpoints solo-admin).
Paginado (`Pageable` de Spring Data), filtros opcionales por
`usuarioId`, `desde`/`hasta` (fecha), `origen`. Devuelve
`Page<LogEventoDto>` con el nombre del usuario ya resuelto (join), no solo el
id.

## Frontend web

- `front/src/app/panel/admin/logs/` — pantalla nueva, mismo patrón que
  las demás tablas de admin (signals, standalone). Filtros simples
  (selector de usuario, rango de fechas, origen) y tabla paginada.
- Enlace nuevo en el índice de admin, visible solo si `esAdministrador`
  (igual que "Permisos").
- `auth.interceptor.ts`: en la rama de error donde ya se detecta que la
  petición no llegó a tener respuesta (`error.status === 0` / error de red),
  además de lo que ya hace, dispara un `POST /api/v1/logs/cliente` best-effort
  (sin `await`, sin bloquear el flujo, con su propio `catch` vacío).

## Móvil

- `PrecioBebidaRepository`-style: `LogsRepository` con `listar(filtros)`
  (repos de solo lectura ya existen con este patrón).
- Pantalla nueva `LogsScreen` en `ui/admin/`, listada desde
  `AdminIndexScreen` solo si el usuario es superadmin (mismo `puedoEditar`/
  flag que ya se consulta para pintar el resto de accesos de admin). Lista
  simple con scroll, sin filtros complejos en v1 (se puede añadir un
  selector de fecha después si hace falta).
- **Captura centralizada**: un `HttpClient` plugin de Ktor (`install(...)`
  en `Dependencias.kt`, donde ya se configura el cliente) que, cuando una
  petición lanza una excepción que no es `ResponseException` (es decir, no
  hubo respuesta del servidor: timeout, sin red, DNS...), dispara —en una
  corrutina aparte, sin esperar ni propagar su resultado— un
  `POST /logs/cliente` con `origen=MOBILE`, `pantalla` = el path de la URL
  que falló, `mensaje` = el nombre de la excepción. No sustituye el manejo de
  error ya existente (`peticion { }` sigue devolviendo `SIN_CONEXION` igual
  que ahora); solo añade el reporte en paralelo.

## Manejo de errores

- Filtro backend: cualquier fallo al escribir el log se traga (SLF4J warn),
  nunca tumba la petición real que se estaba sirviendo.
- Cliente (móvil/web): "fire and forget", sin reintentos, sin feedback visual.
- El propio endpoint `/logs/cliente` no debe poder fallar de forma visible:
  cualquier excepción interna se captura y responde `202` igualmente (salvo
  que la conexión a BBDD esté caída del todo, caso ya cubierto por el manejo
  genérico de errores 5xx que existe hoy).

## Testing

- **IT del filtro**: una petición de escritura autenticada (p. ej.
  `POST /cuentas/{id}/movimientos`) deja una fila en `log_evento` con el
  usuario correcto y `estado=200`; una que falla (403 por `SinPermisoException`)
  deja una fila con `estado=403` y el `codigo_error` correspondiente.
- **Test del endpoint `/logs/cliente`**: guarda con `origen` correcto y
  `usuario_id` null si no hay JWT.
- **Test de `GET /logs`**: 403 si no es superadmin; filtros por usuario y
  rango de fechas funcionan; paginación correcta.
- Móvil/web: no hace falta test end-to-end del "fire and forget" — se
  verifica con un test unitario de que el plugin/interceptor llama al
  endpoint correcto ante una excepción de red simulada.
