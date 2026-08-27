# Registro y login reales — diseño

## Contexto

El front (`front/`) ya tiene las pantallas de Registro y Login, pero simulan el éxito sin
llamar a ningún backend (`front/src/app/login/login.ts` y `front/src/app/registro/registro.ts`
aceptan cualquier valor no vacío). El backend (`back/`) ya tiene el modelo de identidad
completo a nivel de esquema — entidades JPA, repositorios y migraciones Flyway V1-V5
(`pena`, `usuario`, `telefono_autorizado`, `membresia`, `solicitud_ingreso`) — pero nunca se
ha verificado contra un Postgres real, y no existe ni un controlador ni lógica de
autenticación: `SecurityConfig` permite todas las peticiones (`permitAll()`), y no hay
ningún JWT en el proyecto.

Este documento cubre lo necesario para que el registro y el login funcionen de verdad,
de punta a punta (Postgres real → Spring Boot → Angular).

**Fuera de alcance, deliberadamente:** "recordar contraseña" (se decidió por email, pero
es una pieza independiente — su propia tabla de tokens, su propio servicio de email, su
propio endpoint — que no bloquea que registro/login funcionen; va en un plan/spec aparte).
El panel de administración para autorizar teléfonos tampoco entra aquí — se autorizan a
mano por SQL/migración hasta que exista.

## Alcance

Dentro:
- Levantar Postgres (docker compose) y verificar que Flyway aplica V1-V5 correctamente.
- Migración `V6__seed_baniterio.sql`: crea la peña "Bañiterio" y autoriza el teléfono del
  usuario fundador.
- `POST /api/v1/auth/registro`: valida y crea un `usuario` si el teléfono está autorizado.
- `POST /api/v1/auth/login`: valida credenciales y emite un JWT.
- Filtro de Spring Security que valida el JWT en peticiones a rutas protegidas.
- `front/src/app/registro/registro.ts` y `login.ts` llamando a la API real vía `HttpClient`.
- Validación de teléfono (9 dígitos, empieza por 6 o 7) en Angular (el input no deja teclear
  otra cosa) y en el backend (por si alguien llama a la API directamente).

Fuera:
- "Recordar contraseña" (plan/spec separado).
- Panel de administración para gestionar `telefono_autorizado`/`solicitud_ingreso`.
- Refresh tokens — un único JWT con caducidad de 7 días es suficiente para esta fase.
- El formulario de "solicitud de ingreso" cuando el teléfono no está autorizado: el backend
  sí devuelve un error identificable para ese caso (ver más abajo), pero construir el
  formulario de solicitud completo (y su aprobación por un admin) queda para más adelante.
  Por ahora, el front solo muestra el mensaje de error correspondiente.

## Base de datos

`docker compose up -d postgres` desde la raíz del repo levanta Postgres 17 con las
credenciales ya definidas en `docker-compose.yml` (`baniterio`/`baniterio-local`). Con
`spring.flyway.enabled: true` (ya está así en `application.yml`), al arrancar
`BaniterioApiApplication` Flyway aplica V1-V5 automáticamente la primera vez, y quedan
registradas en la tabla `flyway_schema_history` para no volver a aplicarse.

**Nueva migración V6** (`back/src/main/resources/db/migration/V6__seed_baniterio.sql`):
inserta la fila de `pena` "Bañiterio" (si no existe ya una del bootstrap) y una fila en
`telefono_autorizado` con el teléfono del usuario fundador, apuntando a esa peña. El
teléfono concreto se decide al escribir el plan de implementación (no se hardcodea aquí).

## Backend

**Nueva dependencia:** `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (versión a
fijar en el plan) — librería ligera para firmar/verificar JWT sin necesitar un servidor
OAuth2 completo, apropiada para un JWT auto-emitido como este.

**`POST /api/v1/auth/registro`** — request: `telefono` (string, 9 dígitos, `^[67]\d{8}$`),
`email`, `password` (mínimo 6 caracteres), `nombre`, `apellidos`, `mote` (opcional).
Lógica:
1. Si el teléfono no aparece en `telefono_autorizado` con `usado = false`, responde
   `403 Forbidden` con un cuerpo identificable (p. ej. `{ "codigo": "TELEFONO_NO_AUTORIZADO" }`)
   para que el front distinga este caso de un error genérico.
2. Si está autorizado: crea el `usuario` (contraseña con `BCryptPasswordEncoder`), crea una
   `membresia` (rol `MIEMBRO`) hacia la `pena_id` de ese `telefono_autorizado`, y marca
   `usado = true` en la misma transacción.
3. Responde `201 Created` (sin JWT todavía — el usuario hace login después, como en la web
   actual, que ya separa "Registrarse" de "Entrar").

**`POST /api/v1/auth/login`** — request: `telefono`, `password`. Busca el `usuario` por
teléfono, compara la contraseña con `BCryptPasswordEncoder.matches(...)`, y si es válida
emite un JWT firmado (claims mínimos: `sub` = id de usuario, `esSuperadmin`; expiración 7
días). Si el teléfono no existe o la contraseña no coincide, `401 Unauthorized` genérico
(no revelar cuál de los dos falló, práctica estándar).

**Spring Security:** se añade un filtro (`OncePerRequestFilter`) que lee el header
`Authorization: Bearer <token>`, lo valida contra la clave de firma, y si es válido registra
la autenticación en el `SecurityContext`. Las rutas de `/api/v1/auth/**` y `/api/v1/health`
siguen siendo públicas; el resto de rutas quedan detrás del filtro (aunque hoy no haya
ninguna ruta protegida real todavía más allá de estas).

## Frontend

`login.ts`: sustituir la navegación directa a `/panel` por una llamada real
`POST /api/v1/auth/login`; en éxito, guardar el JWT (`localStorage`) y navegar a `/panel`;
en error, mostrar un mensaje (credenciales incorrectas).

`registro.ts`: sustituir el mensaje simulado por una llamada real
`POST /api/v1/auth/registro`; en éxito, navegar a `/login` (o directamente intentar login);
en `403 TELEFONO_NO_AUTORIZADO`, mostrar el mensaje que ya existe sobre solicitar el
ingreso (sin construir el formulario todavía, per el alcance de arriba); en otros errores,
mensaje genérico.

`registro.html`/`login.html`: el campo de teléfono cambia su validación de
`Validators.required` a algo como `Validators.pattern(/^[67]\d{8}$/)` (más `required`), y se
le pone `maxlength="9"` para que el propio input no deje teclear de más.

## Testing

El backend ya tiene un perfil de test con H2 (`application-test.yml`) que, según la nota
existente en memoria del proyecto, no ejercita el SQL real de Flyway (usa
`ddl-auto: create-drop` a partir de las entidades). Para este trabajo, conviene añadir al
menos un test de integración que sí levante Postgres real (vía Testcontainers, que no está
en el `pom.xml` todavía) para los dos endpoints nuevos — se decide el detalle exacto al
escribir el plan de implementación. El front no tiene tests hoy; verificación manual en el
navegador, como el resto del proyecto hasta ahora.
