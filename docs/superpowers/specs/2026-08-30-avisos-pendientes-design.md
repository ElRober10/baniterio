# Avisos de pendientes (campanita) — Diseño

**Fecha:** 2026-08-30
**Rama:** `feature/avisos-pendientes` (corta, desde `main`)
**Estado:** aprobado para plan

## Objetivo

Que un admin/superadmin vea de un vistazo cuánto trabajo tiene sin atender:

1. En la **home del panel**, la tarjeta "Administración" muestra una campanita
   con el **total** de pendientes de todas sus áreas.
2. En el **índice de administración**, cada tarjeta de sección muestra una
   campanita con los pendientes **de esa área**.

Hoy el único pendiente es una solicitud de ingreso sin resolver. El diseño deja
un **contrato extensible**: cualquier área futura que genere trabajo de
aprobar/verificar (p. ej. confirmar pagos) aporta su cuenta sin tocar el resto.

## Contexto actual (lo que ya existe)

- **Back** (`back/.../admin/`): `AdminController` (`GET /api/v1/admin/solicitudes`,
  `POST .../aprobar`, `POST .../rechazar`, `GET /miembros`, `PUT /miembros/...`),
  `AdminService`, `ServicioPermisos` (`areasDe(usuarioId): Set<AreaProtegida>`,
  `puede(usuarioId, area)`). `SolicitudIngresoRepository` tiene
  `findByPenaIdAndEstado`; **no** tiene un `count`. `EstadoSolicitud`
  = `PENDIENTE`/`APROBADA`/`RECHAZADA`. `AreaProtegida` = `ADMIN_SOLICITUDES`,
  `ADMIN_PERMISOS`.
- **Front** (`front/src/app/`):
  - `panel/inicio/` — pinta las secciones "próximamente" y, si
    `auth.usuarioActual()?.areas?.length`, una tarjeta-enlace "Administración".
    Componente sin HTTP.
  - `admin/indice/` — `SECCIONES_ADMIN` (área + descripción + ruta), filtra a las
    áreas del usuario (`auth.tieneArea`), pinta una tarjeta-enlace por sección.
  - `admin/solicitudes/` — lista las pendientes y las aprueba/rechaza; tras
    resolver recarga su lista.
  - `admin/admin.service.ts` — un método por endpoint, `providedIn: 'root'`.
  - `admin/admin.types.ts` — `Area`, `AREAS` (etiquetas), `MiembroResumen`, etc.
  - `shared/volver/` — único componente compartido hasta ahora.
- **Tests back**: `*IT` con Testcontainers (`AuthControllerIT`, etc.), `mvn verify`.
- **Tests front**: vitest + `HttpTestingController`, `ng test --watch=false`.

## Decisiones tomadas (brainstorming)

| Tema | Decisión |
|---|---|
| Origen del número | **Endpoint nuevo** que agrega por área. Contrato pensado para crecer (pagos, etc.); "haz uno o varios endpoints según convenga" → uno basta. |
| Refresco | **Al cargar/navegar y además tras resolver.** Contador en un signal compartido; aprobar/rechazar una solicitud lo refresca sin recargar la página. |
| Estado cero | La campanita con `0` **no pinta nada**. El aviso solo aparece cuando hay trabajo. |
| Nombre del componente | `app-aviso-pendientes`. |
| Campana | SVG pequeño en `currentColor` (coherente con el resto; los glifos de texto se usan para flechas, no para un elemento de UI con estado). |

## Arquitectura

### Backend — contrato "pendientes por área"

**Interfaz** (`back/.../admin/ContadorPendientes.java`):

```java
public interface ContadorPendientes {
    AreaProtegida area();
    long contar();   // pendientes en toda la peña (alcance: una sola peña)
}
```

> **Nota post-revisión (2026-08-31):** la firma final es `long contar(Long penaId)`.
> `AdminService.pendientesPorArea` resuelve el `penaId` una vez y lo pasa a cada
> contador, para no repetir la resolución por slug en cada implementación.

**Implementación hoy** (`back/.../admin/SolicitudesPendientesContador.java`,
`@Component`):
- `area()` → `AreaProtegida.ADMIN_SOLICITUDES`.
- `contar()` → `solicitudes.countByPenaIdAndEstado(penaId(), EstadoSolicitud.PENDIENTE)`.
- `penaId()` por slug `"baniterio"` (mismo patrón que `AdminService`/`ServicioPermisos`).

**Repo** — método nuevo en `SolicitudIngresoRepository`:
```java
long countByPenaIdAndEstado(Long penaId, EstadoSolicitud estado);
```

**Servicio** — `AdminService.pendientesPorArea(Long usuarioId): Map<AreaProtegida, Long>`:
1. `Set<AreaProtegida> mias = servicioPermisos.areasDe(usuarioId)`.
2. Spring inyecta `List<ContadorPendientes>`. Para cada uno con `mias.contains(c.area())`,
   calcula `long n = c.contar()`; si `n > 0`, `mapa.put(c.area(), n)`.
3. Devuelve el mapa (vacío si el usuario no tiene áreas o no hay nada pendiente).
   Serializa como `{"ADMIN_SOLICITUDES": 3}`.

**Controlador** — `AdminController`:
```java
@GetMapping("/pendientes")
public Map<AreaProtegida, Long> pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
    return adminService.pendientesPorArea(principal.id());
}
```
Sin `exigirArea`: la respuesta ya se autofiltra a las áreas del usuario; sin
áreas devuelve `{}`. Requiere estar autenticado (lo impone `SecurityConfig`).

### Frontend — componente `app-aviso-pendientes`

`front/src/app/shared/aviso-pendientes/aviso-pendientes.ts` — presentacional
puro, sin inyección, `imports: []`.

- `cuenta = input.required<number>()`.
- Plantilla: `@if (cuenta() > 0) { <span role="status" [attr.aria-label]="cuenta() + ' pendientes'"> <svg…campana…/> <span>{{ mostrada() }}</span> </span> }`.
- `mostrada = computed(() => cuenta() > 99 ? '99+' : String(cuenta()))`.
- `cuenta() <= 0` → no pinta nada.
- Estilo: globo redondo pequeño con el color de marca (`bg-brand-bright`/texto
  `gold`), en línea; la campana hereda el color del contenedor.

### Frontend — estado compartido `AdminAvisosService`

`front/src/app/admin/admin-avisos.service.ts`, `@Injectable({ providedIn: 'root' })`.

- `private readonly _pendientes = signal<PendientesPorArea>({})`.
- `readonly pendientes = this._pendientes.asReadonly()`.
- `readonly total = computed(() => Object.values(this._pendientes()).reduce((a, b) => a + b, 0))`.
- `refrescar(): void` → `http.get<PendientesPorArea>(\`${base}/admin/pendientes\`)`
  `.subscribe({ next: m => this._pendientes.set(m), error: () => {} })`.
  Ante error deja el último valor conocido (no rompe la pantalla).
- `admin.types.ts`: `export type PendientesPorArea = Partial<Record<Area, number>>;`

Se deja en `admin/` (no en `shared/`) porque es el estado del panel de
administración; el componente tonto sí es `shared/` porque no sabe de dominio.

### Frontend — cableado

| Sitio | Cambio |
|---|---|
| `panel/inicio` | Inyecta `AdminAvisosService`; en `ngOnInit`, si `auth.usuarioActual()?.areas?.length`, llama `avisos.refrescar()`. En el `<h2>` de la tarjeta "Administración": `<app-aviso-pendientes [cuenta]="avisos.total()" />`. Pasa a implementar `OnInit` e importar el componente. |
| `admin/indice` | Inyecta el servicio; `ngOnInit` → `avisos.refrescar()`. En cada tarjeta: `<app-aviso-pendientes [cuenta]="avisos.pendientes()[seccion.area] ?? 0" />`. Pasa a `implements OnInit`. |
| `admin/solicitudes` | Inyecta el servicio; tras aprobar/rechazar con éxito (donde hoy recarga la lista) añade `avisos.refrescar()`. |

## Flujo de datos

1. El usuario entra en `/panel` → `PanelInicio.ngOnInit` → `avisos.refrescar()` →
   `GET /admin/pendientes` → signal poblado → la campanita de la tarjeta muestra
   `total()`.
2. Entra en `/panel/administracion` → `AdminIndice.ngOnInit` → `refrescar()` de
   nuevo (datos frescos) → cada tarjeta muestra su área.
3. Entra en Solicitudes, aprueba una → el componente recarga su lista y llama
   `avisos.refrescar()` → el signal baja → si vuelve al índice/home, el número
   ya está actualizado (mismo signal, sin recarga).

## Manejo de errores

- **Back**: `pendientesPorArea` no lanza; si un `ContadorPendientes` fallara,
  se propaga como 500 normal (traducido por `ApiExceptionHandler`). No hay
  caso de negocio de error.
- **Front**: `refrescar()` traga el error (`error: () => {}`). La campanita es
  información secundaria: si no carga, no se enseña número, y las pantallas
  siguen funcionando. El interceptor de auth ya redirige al login si el token
  caduca.

## Alcance / YAGNI

- **No** hay polling ni tiempo real: se refresca al navegar y tras resolver.
- **No** se toca el móvil (Kotlin) en esta tarea.
- **No** se añade `AreaProtegida` nueva ni migración: no hay cambios de esquema.
- **No** se listan las áreas a 0 en la respuesta: el front trata "ausente" como 0.
- El endpoint devuelve el total de la peña (alcance de una sola peña, coherente
  con `AdminService`/`ServicioPermisos`).

## Plan de pruebas

### Backend
Tests de integración con Testcontainers (patrón de `support/IntegrationTest`),
en un `AdminPendientesIT` nuevo (junto a `AdminSolicitudesIT` /
`AdminAutorizacionIT`):
- usuario con `ADMIN_SOLICITUDES` y 2 solicitudes `PENDIENTE` (+ 1 `APROBADA`)
  → `GET /api/v1/admin/pendientes` = `{"ADMIN_SOLICITUDES": 2}`.
- usuario sin ninguna área → `{}`.
- área concedida pero 0 pendientes → esa clave no aparece.
- sin token → 401 (añadir caso si encaja fácil con el patrón de `SecurityIT`).

### Frontend
- **`aviso-pendientes.spec.ts`**: `cuenta=3` pinta "3" y `aria-label="3 pendientes"`;
  `cuenta=0` no pinta nada (`textContent` vacío, sin `[role=status]`);
  `cuenta=120` pinta "99+".
- **`admin-avisos.service.spec.ts`**: `refrescar()` hace `GET /admin/pendientes`,
  puebla `pendientes()` y `total()` suma los valores; error del backend no
  revienta y deja el valor anterior.
- **`inicio.spec.ts`** (nuevo/ampliado): con áreas, en `ngOnInit` pide
  `/admin/pendientes`; con la respuesta `{ADMIN_SOLICITUDES:2}` la tarjeta
  "Administración" muestra "2"; sin áreas no llama al endpoint.
- **`indice.spec.ts`**: pide `/admin/pendientes` en init; la tarjeta de
  Solicitudes muestra el número de su área y la de Permisos (0) no muestra nada.
- **`solicitudes.spec.ts`**: tras aprobar una solicitud con éxito, se vuelve a
  pedir `/admin/pendientes` (además de la recarga de la lista que ya hace).

## Archivos

**Nuevos**
- `back/src/main/java/com/baniterio/api/admin/ContadorPendientes.java`
- `back/src/main/java/com/baniterio/api/admin/SolicitudesPendientesContador.java`
- `front/src/app/shared/aviso-pendientes/aviso-pendientes.ts`
- `front/src/app/shared/aviso-pendientes/aviso-pendientes.spec.ts`
- `front/src/app/admin/admin-avisos.service.ts`
- `front/src/app/admin/admin-avisos.service.spec.ts`
- tests backend según el plan

**Modificados**
- `back/.../identidad/SolicitudIngresoRepository.java` (método `countBy…`)
- `back/.../admin/AdminService.java` (`pendientesPorArea`)
- `back/.../admin/AdminController.java` (`GET /pendientes`)
- `front/src/app/admin/admin.types.ts` (`PendientesPorArea`)
- `front/src/app/panel/inicio/inicio.ts` + `.html` (+ `inicio.spec.ts`)
- `front/src/app/admin/indice/indice.ts` + `.html` (+ `indice.spec.ts`)
- `front/src/app/admin/solicitudes/solicitudes.ts` (+ `solicitudes.spec.ts`)

**Tests backend nuevos**
- `back/src/test/java/com/baniterio/api/admin/AdminPendientesIT.java`
