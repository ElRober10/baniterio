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
