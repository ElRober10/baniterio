# Bañiterio

Plataforma web y móvil para la gestión de las peñas de las fiestas del pueblo.

## Estructura

- `back/`: API Java con Spring Boot.
- `front/`: aplicación web Angular.
- `mobile/`: aplicación Kotlin Multiplatform + Compose Multiplatform.
- `docs/`: documentación y contrato de la API.

## Requisitos

- Java 17+
- Node.js 20+
- Docker Desktop
- Android Studio para el módulo móvil

## Arranque local

1. Iniciar PostgreSQL:

   ```powershell
   docker compose up -d postgres
   ```

2. Iniciar la API:

   ```powershell
   cd back
   .\mvnw.cmd spring-boot:run
   ```

   Al arrancar, la API aplica automáticamente las migraciones de Flyway, incluida `V6`, que siembra la peña «Bañiterio» y el teléfono del fundador.

   El resto de teléfonos autorizados de la peña piloto son datos personales y **no están en git**: viven en `back/scripts/seed-telefonos-baniterio.local.sql`. Aplícalo a mano una vez, tras arrancar la API por primera vez:

   ```powershell
   docker exec -i baniterio-postgres psql -U baniterio -d baniterio < back/scripts/seed-telefonos-baniterio.local.sql
   ```

   Variables de entorno opcionales del backend:

   - `JWT_SECRET`: clave de firma del JWT. Hay un valor por defecto **solo para local**; con cualquier perfil activo distinto de `dev`/`test` la API se niega a arrancar si no lo defines.
   - `TELEFONO_FUNDADOR`: teléfono que se marca como superadmin al registrarse (por defecto `616985168`).
   - `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`: conexión a PostgreSQL.
   - Correo (avisos de resolución de solicitudes): `MAIL_MODO` (`log` = solo traza, por defecto; `smtp` = envía de verdad), `MAIL_FROM` (remitente) y `MAIL_ENLACE_REGISTRO` (enlace al registro que va en el correo de aprobación sin contraseña). En modo `smtp` hacen falta además `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` (con Gmail, una *app password*).

3. Instalar dependencias y arrancar Angular:

   ```powershell
   cd front
   npm install
   npm start
   ```

La API estará disponible en `http://localhost:8080` y Angular en `http://localhost:4200`.

## Roles

- `SUPERADMIN`: gestiona todas las peñas y la configuración global.
- `ADMIN`: gestiona su propia peña, autorizaciones y solicitudes.
- `MIEMBRO`: utiliza las funciones permitidas de su peña.

El registro estará restringido a teléfonos previamente autorizados. Los teléfonos no autorizados podrán crear una solicitud de incorporación.
