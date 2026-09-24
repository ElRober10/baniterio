# Bañiterio

Plataforma web y móvil para la gestión de las peñas de las fiestas del pueblo.

## Estructura

* `back/`: API Java con Spring Boot.
* `front/`: aplicación web Angular.
* `mobile/`: aplicación Kotlin Multiplatform + Compose Multiplatform.
* `docs/`: documentación y contrato de la API.

## Requisitos

* Java 17+
* Node.js 20+
* Docker Desktop
* Android Studio para el módulo móvil

## Arranque local

1. Iniciar PostgreSQL:

```
   docker compose up -d postgres
```

2. Iniciar la API:

```
   cd back
   .\mvnw.cmd spring-boot:run
```

   Al arrancar, la API aplica automáticamente las migraciones de Flyway, que siembran la peña «Bañiterio».
   Los teléfonos autorizados de la peña piloto (incluido el del fundador) son datos personales y no están en git: viven en `back/scripts/seed-telefonos-baniterio.local.sql`. Aplícalo a mano una vez, tras arrancar la API por primera vez:

```
   docker exec -i baniterio-postgres psql -U baniterio -d baniterio < back/scripts/seed-telefonos-baniterio.local.sql
```

   Variables de entorno opcionales del backend:
   * `JWT_SECRET`: clave de firma del JWT. Hay un valor por defecto solo para local; con cualquier perfil activo distinto de `dev`/`test` la API se niega a arrancar si no lo defines.
   * `TELEFONO_FUNDADOR`: teléfono que se marca como superadmin al registrarse. Sin valor por defecto: se define por variable de entorno.
   * `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`: conexión a PostgreSQL.
   * Correo (avisos de resolución de solicitudes): `MAIL_MODO` (`log` = solo traza, por defecto; `smtp` = envía de verdad), `MAIL_FROM` (remitente) y `MAIL_ENLACE_REGISTRO` (enlace al registro que va en el correo de aprobación sin contraseña). En modo `smtp` hacen falta además `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` (con Gmail, una app password).

3. Instalar dependencias y arrancar Angular:

```
   cd front
   npm install
   npm start
```

La API estará disponible en `http://localhost:8080` y Angular en `http://localhost:4200`.

## Versión demo

Para probar la aplicación sin necesidad de registro:

| Campo | Valor |
|---|---|
| Teléfono | `666666666` |
| Contraseña | `123456` |

Es un usuario de pruebas con datos ficticios.

## Roles

* `SUPERADMIN`: gestiona todas las peñas y la configuración global.
* `ADMIN`: gestiona su propia peña, autorizaciones y solicitudes.
* `MIEMBRO`: utiliza las funciones permitidas de su peña.

El registro estará restringido a teléfonos previamente autorizados. Los teléfonos no autorizados podrán crear una solicitud de incorporación.

## 🤖 Cómo se ha desarrollado este proyecto

Este proyecto está desarrollado con **asistencia de IA (Claude Code)**, usada como herramienta bajo mi dirección. Yo decido qué se construye, cómo y con qué criterios; la IA ejecuta y automatiza la parte mecánica. Nada entra en el repositorio sin que yo lo haya revisado, entendido y validado.

### Reparto de responsabilidades

| Lo hago yo | Lo automatizo con la IA |
|---|---|
| Definir alcance, roles y reglas de negocio (registro por teléfono autorizado, solicitudes de incorporación, SUPERADMIN/ADMIN/MIEMBRO) | Generar código repetitivo y boilerplate (endpoints, DTOs, componentes, servicios) |
| Decidir la arquitectura: API Spring Boot, front Angular, módulo móvil KMP, contrato de API en `docs/` | Implementar las decisiones ya tomadas siguiendo mis instrucciones |
| Elegir stack e infraestructura (PostgreSQL, Flyway, Docker, JWT) | Escribir configuración inicial y migraciones que yo reviso |
| Decidir qué es dato sensible y qué no entra en git (teléfonos, secretos, variables de entorno) | Proponer y aplicar cambios de configuración bajo mis criterios |
| Dar instrucciones concretas y acotadas, archivo a archivo | Redactar borradores de documentación y contrato de API |
| **Revisar, retocar y adaptar cada archivo** (back, front, mobile y docs) a las necesidades del proyecto | Proponer refactors y mejoras que yo acepto o rechazo |
| **Probar cada archivo** y verificar que hace lo que debe antes de pasar al siguiente | Escribir tests que yo reviso y ejecuto |
| Depurar y decidir cuando algo falla | Proponer hipótesis y aplicar correcciones que yo valido |
| **Ordenar el commit** cuando todo está revisado y verificado | Ejecutar el commit y el push bajo mi orden |

### Flujo de trabajo

1. **Especifico** la funcionalidad y sus restricciones.
2. **Encargo** a la IA una tarea concreta y acotada, archivo a archivo.
3. **Reviso** el archivo generado: lógica, seguridad, estructura y estilo.
4. **Retoco y adapto** lo que haga falta hasta que encaje con lo que necesito.
5. **Pruebo** el resultado (tests y prueba manual) y compruebo que entiendo qué hace y por qué.
6. **No paso al siguiente archivo** hasta cerrar el anterior.
7. **Cuando todo está revisado y verificado, ordeno a Claude que haga el commit.**

### Por qué lo explico

Usar IA es una herramienta legítima y prefiero ser transparente: por eso Claude figura como colaborador en el historial. Lo que aporto yo es lo que la IA no sustituye: criterio, arquitectura, revisión y responsabilidad sobre el resultado. Puedo explicar y defender cada decisión técnica de este repositorio.
