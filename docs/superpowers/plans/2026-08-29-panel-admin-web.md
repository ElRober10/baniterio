# Panel de administración — Web (Angular) — Plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usa superpowers:subagent-driven-development.
> Los pasos usan checkbox (`- [ ]`).

**Goal:** Añadir al front la sección de administración: `Panel` pasa a ser un
layout con rutas hijas, aparece un grupo "Administración" al final del nav
(visible solo si el usuario tiene áreas), y dos pantallas nuevas —Solicitudes y
Permisos— que consumen `/api/v1/admin/*`.

**Architecture:** Carpeta nueva `front/src/app/admin/` (servicio, tipos, guard,
componentes). `AuthService` aprende a leer `GET /auth/yo` y guarda el usuario
actual (con `rol` y `areas`) en un signal. `areaGuard(area)` protege cada ruta
hija. `Panel` se divide en layout (`panel/`) + contenido de bienvenida
(`panel/inicio/`).

**Tech Stack:** Angular 22 (standalone components, signals, functional guards,
functional interceptors), Tailwind 4 (tokens de tema en `front/src/styles.css`:
`surface`, `panel`, `outline`, `ink`, `muted`, `brand`, `brand-bright`, `gold`,
fuentes `font-display`/`font-metal`). Tests: `@angular/build:unit-test` (vitest +
jsdom), `npm test` desde `front/`.

**Spec:** `docs/superpowers/specs/2026-08-29-panel-administracion-design.md`
**Depende de:** el plan de backend (contrato de `/api/v1/admin/*` y de `/auth/yo`
con `rol`/`areas`). Ese contrato está descrito abajo en cada tarea; no hace falta
tener el backend corriendo para implementar, pero el humo manual final sí lo
necesita.

## Global Constraints

- **Standalone components**, sin NgModules. `imports: [...]` en cada `@Component`.
- **Signals** para estado de componente; `HttpClient` + RxJS para IO.
- **Nada de `any`** salvo lo ya existente. Tipar las respuestas del backend en
  `admin.types.ts` / `auth.types.ts`.
- **Estilo visual:** copiar el de `panel.html` / `login.html` (clases Tailwind con
  los tokens del tema, tono oscuro morado). Cabecera de comentario explicativa en
  cada fichero nuevo (proyecto de aprendizaje).
- **La sesión del front** sigue siendo el JWT en `localStorage`
  (`baniterio.token`). `rol`/`areas` NO van en el token: se leen de `/auth/yo` o
  de la respuesta de `/login`.
- **Idioma:** identificadores, textos de UI y comentarios en español.
- **El "panel de administración" va SIEMPRE el último** del nav. Si en el futuro
  hay más secciones, se insertan antes.
- **Tests:** las specs nuevas pasan con `npm test`. Nota: `app.spec.ts` trae un
  test heredado (`should render title` esperando "Hello, front") que ya está roto
  porque la plantilla es solo `<router-outlet />`; arréglalo (assert de que crea
  el componente) en la primera tarea que toque el arranque.

---

## File Structure

**Nuevos:**
- `front/src/app/admin/admin.types.ts` — tipos del contrato `/api/v1/admin/*`
- `front/src/app/admin/admin.service.ts` — un método por endpoint
- `front/src/app/admin/area.guard.ts` — `areaGuard(area)` factory de `CanActivateFn`
- `front/src/app/admin/solicitudes/solicitudes.{ts,html,css}` — pantalla Solicitudes
- `front/src/app/admin/permisos/permisos.{ts,html,css}` — pantalla Permisos
- `front/src/app/panel/inicio/inicio.{ts,html,css}` — contenido de bienvenida (extraído de `panel.html`)
- `front/src/app/admin/admin.service.spec.ts`, `front/src/app/admin/area.guard.spec.ts`

**Modificados:**
- `front/src/app/auth/auth.service.ts` — `yo()`, `usuarioActual` signal, `asegurarYo()`, `tieneArea()`; guardar usuario en `login()` y limpiarlo en `cerrarSesion()`
- `front/src/app/auth/auth.types.ts` — `UsuarioDto.rol`/`areas`; `CodigoError` nuevos
- `front/src/app/auth/auth.interceptor.ts` — `/auth/solicitudes` como pública (menor)
- `front/src/app/app.routes.ts` — rutas hijas de `panel`
- `front/src/app/panel/panel.ts` + `panel.html` + `panel.css` — layout con `<router-outlet>` + grupo "Administración" en el nav
- `front/src/app/auth/registro/registro.ts` — arrastrar `password` a solicitar-acceso
- `front/src/app/auth/solicitar-acceso/solicitar-acceso.ts` — enviar `password` si llegó en el state

---

## Task 1: `AuthService` aprende `rol` + `areas`

**Files:**
- Modify: `front/src/app/auth/auth.types.ts`
- Modify: `front/src/app/auth/auth.service.ts`
- Modify: `front/src/app/auth/auth.interceptor.ts`
- Create: `front/src/app/auth/auth.service.spec.ts` (si no existe)

**Interfaces:**
- Consumes (contrato backend): `GET /api/v1/auth/yo` → `200` con
  `{ id, nombre, apellidos, mote, esSuperadmin, rol, areas }` donde
  `rol: "ADMIN" | "MIEMBRO" | null` y `areas: string[]` (p.ej.
  `["ADMIN_SOLICITUDES","ADMIN_PERMISOS"]`); `401` si el token no vale o el
  usuario está inactivo. `POST /login` devuelve `{ token, usuario }` con `usuario`
  de esa misma forma.
- Produces:
  - `UsuarioDto` con `rol: string | null` y `areas: string[]`
  - `AuthService.usuarioActual` → `Signal<UsuarioDto | null>`
  - `AuthService.yo(): Observable<UsuarioDto>` (GET /auth/yo, actualiza el signal)
  - `AuthService.asegurarYo(): Observable<UsuarioDto | null>` — si el signal ya
    tiene valor lo devuelve (`of(...)`), si no llama a `yo()`; en error devuelve `of(null)`
  - `AuthService.tieneArea(area: string): boolean` — `usuarioActual()?.areas?.includes(area) ?? false`
  - `CodigoError` amplía con `'SIN_PERMISO' | 'SOLICITUD_YA_RESUELTA' | 'ULTIMO_ADMIN'
    | 'NO_TE_PUEDES_DEGRADAR' | 'NO_TE_PUEDES_DESACTIVAR' | 'SOLO_EL_SUPERADMIN'`

- [ ] **Step 1: Ampliar `auth.types.ts`**

```ts
export interface UsuarioDto {
  id: number;
  nombre: string;
  apellidos: string;
  mote: string | null;
  esSuperadmin: boolean;
  rol: 'ADMIN' | 'MIEMBRO' | null;
  areas: string[];
}
```

Y añadir a la unión `CodigoError` los seis códigos nuevos listados arriba.

- [ ] **Step 2: Escribir el test `auth.service.spec.ts`**

Con `provideHttpClientTesting()` / `HttpTestingController`:
- `yo()` hace `GET` a `${apiBaseUrl}/auth/yo`, y tras la respuesta
  `usuarioActual()` refleja el `rol` y las `areas`.
- `tieneArea('ADMIN_PERMISOS')` es `true` tras un `yo()` que devolvió esa área, y
  `false` para un área que no está.
- `cerrarSesion()` deja `usuarioActual()` en `null`.
- `asegurarYo()` NO vuelve a llamar al backend si el signal ya tiene valor
  (segundo `expectNone`).

- [ ] **Step 3: Ejecutar — debe fallar**

Run: `cd front && npm test`. Expected: FAIL (métodos/campos nuevos no existen).

- [ ] **Step 4: Implementar en `auth.service.ts`**

- Importar `signal` de `@angular/core`, `of` de `rxjs`, `map`/`catchError`.
- `private readonly _usuarioActual = signal<UsuarioDto | null>(null);`
  `readonly usuarioActual = this._usuarioActual.asReadonly();`
- En `login()`, dentro del `tap`, además de guardar el token:
  `this._usuarioActual.set(res.usuario);`
- `cerrarSesion()`: tras `removeItem`, `this._usuarioActual.set(null);`
- Métodos:

```ts
yo(): Observable<UsuarioDto> {
  return this.http
    .get<UsuarioDto>(`${this.base}/auth/yo`)
    .pipe(tap((u) => this._usuarioActual.set(u)));
}

asegurarYo(): Observable<UsuarioDto | null> {
  const actual = this._usuarioActual();
  if (actual) return of(actual);
  return this.yo().pipe(catchError(() => of(null)));
}

tieneArea(area: string): boolean {
  return this._usuarioActual()?.areas?.includes(area) ?? false;
}
```

- [ ] **Step 5: Interceptor — `/auth/solicitudes` es pública**

En `auth.interceptor.ts`, ampliar `esPublica`:
`req.url.includes('/auth/login') || req.url.includes('/auth/registro') || req.url.includes('/auth/solicitudes')`.
(Un 403 `SIN_PERMISO` de `/admin/*` NO debe cerrar sesión: el interceptor solo
actúa en 401, así que ya está bien; no tocar esa parte.)

- [ ] **Step 6: Verificar y commit**

Run: `cd front && npm test`. Expected: PASS.

```bash
git add front/src/app/auth/ && git commit -m "feat(front): AuthService lee rol y areas de /auth/yo"
```

---

## Task 2: `Panel` pasa a layout con rutas hijas + grupo "Administración" en el nav

**Files:**
- Create: `front/src/app/panel/inicio/inicio.ts` + `inicio.html` + `inicio.css`
- Modify: `front/src/app/panel/panel.ts`, `panel.html`, `panel.css`
- Modify: `front/src/app/app.routes.ts`
- Create: `front/src/app/admin/area.guard.ts` (solo el archivo, se usa en la Task 3; aquí se referencia con placeholders si hace falta — mejor: crear las rutas hijas apuntando a componentes que se crean en Task 3, así que **esta tarea deja las rutas hijas de administración comentadas / TODO y las activa la Task 3**)

**Decisión de secuenciación (ruling):** para que cada tarea compile sola, esta
Task 2 crea el layout + `PanelInicio` + la ruta `''` hija, y el grupo
"Administración" en el nav con enlaces `routerLink` a
`/panel/administracion/solicitudes` y `/permisos`. Las **rutas** hijas de
administración y su `areaGuard` las añade la Task 3 (cuando existan los
componentes). Mientras tanto esos enlaces darán 404 si se pulsan — aceptable
dentro de la rama, se cierra en Task 3. Coste si se olvida: un enlace roto, lo
caza el humo manual.

**Interfaces:**
- Consumes: `AuthService.usuarioActual` / `asegurarYo` (Task 1).
- Produces:
  - `Panel` = componente layout: barra lateral + header móvil + `<main><router-outlet/></main>`
  - `PanelInicio` = el contenido de bienvenida actual (tarjetas de secciones "próximamente")
  - Ruta: `{ path: 'panel', component: Panel, canActivate: [authGuard], children: [
      { path: '', component: PanelInicio } ] }`

- [ ] **Step 1: Crear `PanelInicio`**

Mover a `panel/inicio/inicio.html` el bloque `<main>...</main>` actual de
`panel.html` (el `<span>` de "Robledo del Mazo", el `<h1>` de bienvenida, el
párrafo y el `grid` de tarjetas de `secciones`). `inicio.ts`: componente
standalone `PanelInicio`, con el array `secciones` (mover desde `panel.ts`),
`selector: 'app-panel-inicio'`. `inicio.css` vacío.

- [ ] **Step 2: `Panel` como layout**

`panel.ts`: quitar `secciones` (se fue a `PanelInicio`); mantener `salir()`.
Inyectar `AuthService` como `protected auth` para el template. En el constructor
o `ngOnInit`: `this.auth.asegurarYo().subscribe();` (para que el nav sepa las
áreas). `imports: [RouterLink, RouterOutlet, RouterLinkActive]`.

`panel.html`: dejar la barra lateral (`<aside>`) y el header/nav móvil, pero:
- El `@for (seccion of secciones)` de la barra lateral y del nav móvil ahora
  itera una lista local mínima de "secciones próximamente" (mueve ese array a
  `panel.ts` como `protected readonly seccionesPronto` — son las mismas 5 menos
  Historia, o simplemente déjalas como estaban salvo que `PanelInicio` ya las
  pinta; para el nav basta con los nombres). **Simplificación:** el nav lateral
  puede seguir mostrando los nombres de secciones "pronto" como hasta ahora.
- El enlace "Inicio" apunta a `routerLink="/panel"` con
  `routerLinkActive` + `[routerLinkActiveOptions]="{ exact: true }"`.
- Sustituir el `<main>...</main>` entero por:
  ```html
  <main class="flex-1 px-6 py-10 sm:px-10">
    <div class="mx-auto max-w-5xl">
      <router-outlet />
    </div>
  </main>
  ```
- **Tras** el `@for` de secciones (tanto en `<aside>` como en el nav móvil),
  añadir el grupo de administración:
  ```html
  @if (auth.usuarioActual()?.areas?.length) {
    <div class="mt-2 border-t border-outline pt-2">
      <span class="px-4 text-[0.65rem] font-semibold uppercase tracking-wide text-muted/60">Administración</span>
      @if (auth.tieneArea('ADMIN_SOLICITUDES')) {
        <a class="mt-1 block rounded-xl px-4 py-2.5 text-muted transition hover:text-ink"
           routerLink="/panel/administracion/solicitudes" routerLinkActive="bg-brand/15 text-ink">Solicitudes</a>
      }
      @if (auth.tieneArea('ADMIN_PERMISOS')) {
        <a class="mt-1 block rounded-xl px-4 py-2.5 text-muted transition hover:text-ink"
           routerLink="/panel/administracion/permisos" routerLinkActive="bg-brand/15 text-ink">Permisos</a>
      }
    </div>
  }
  ```
  (adaptar clases al patrón "pill" en el nav móvil).

- [ ] **Step 3: Rutas hijas (solo `''`)**

`app.routes.ts`:
```ts
{
  path: 'panel',
  component: Panel,
  canActivate: [authGuard],
  children: [
    { path: '', component: PanelInicio },
    // administracion/* → Task 3
  ],
},
```
Importar `PanelInicio`.

- [ ] **Step 4: Verificar**

Run: `cd front && npm test` y `cd front && npm run build`. Expected: PASS / build OK.
Comprobar manualmente que `/panel` sigue mostrando la bienvenida.

- [ ] **Step 5: Commit**

```bash
git add front/src/app/panel/ front/src/app/app.routes.ts
git commit -m "refactor(front): Panel como layout con router-outlet + grupo Administración en el nav"
```

---

## Task 3: `AdminService`, `admin.types.ts`, `areaGuard` y rutas de administración

**Files:**
- Create: `front/src/app/admin/admin.types.ts`
- Create: `front/src/app/admin/admin.service.ts`
- Create: `front/src/app/admin/area.guard.ts`
- Create: `front/src/app/admin/admin.service.spec.ts`
- Create: `front/src/app/admin/area.guard.spec.ts`
- Modify: `front/src/app/app.routes.ts` (añadir las rutas hijas de administración — apuntando a los componentes de Task 4 y 5; para que compile, **crear stubs mínimos** de `AdminSolicitudes` y `AdminPermisos` en esta tarea y rellenarlos en 4 y 5)

**Interfaces:**
- Consumes (contrato backend, del plan de backend):
  - `GET /api/v1/admin/solicitudes?estado=PENDIENTE` →
    `SolicitudResumen[]` = `{ id:number, nombre, apellidos, telefono, email, motivo,
    relacion, conocidos, traeContrasena:boolean, estado:string, createdAt:string }`
  - `POST /api/v1/admin/solicitudes/{id}/aprobar` → `{ resultado: 'CUENTA_CREADA' | 'TELEFONO_AUTORIZADO' }`
  - `POST /api/v1/admin/solicitudes/{id}/rechazar` body `{ motivo?: string }` → `204`
  - `GET /api/v1/admin/miembros` → `MiembroResumen[]` = `{ id:number, nombre,
    apellidos, mote:string|null, telefono, rol:'ADMIN'|'MIEMBRO', activo:boolean,
    esSuperadmin:boolean, areas:string[] }`
  - `PUT /api/v1/admin/miembros/{id}/rol` body `{ rol:'ADMIN'|'MIEMBRO' }` → `204`
  - `PUT /api/v1/admin/miembros/{id}/activo` body `{ activo:boolean }` → `204`
  - `PUT /api/v1/admin/miembros/{id}/areas` body `{ areas:string[] }` → `204`
  - errores: `{ codigo: CodigoError }` con `403 SIN_PERMISO`, `409
    SOLICITUD_YA_RESUELTA | ULTIMO_ADMIN | NO_TE_PUEDES_DEGRADAR |
    NO_TE_PUEDES_DESACTIVAR | SOLO_EL_SUPERADMIN | YA_REGISTRADO`, `400 VALIDACION`.
- Produces:
  - `AdminService` (`@Injectable({providedIn:'root'})`) con:
    `listarSolicitudes(estado?)`, `aprobarSolicitud(id)`, `rechazarSolicitud(id, motivo?)`,
    `listarMiembros()`, `cambiarRol(id, rol)`, `cambiarActivo(id, activo)`, `cambiarAreas(id, areas)`
  - `areaGuard(area: string): CanActivateFn`
  - `AREAS` const con las áreas conocidas y su etiqueta legible:
    `{ ADMIN_SOLICITUDES: 'Solicitudes', ADMIN_PERMISOS: 'Permisos' }`

- [ ] **Step 1: `admin.types.ts`**

Definir `SolicitudResumen`, `MiembroResumen`, `AprobarResultado =
'CUENTA_CREADA' | 'TELEFONO_AUTORIZADO'`, `Rol = 'ADMIN' | 'MIEMBRO'`, y
`export const AREAS: Record<string, string> = { ADMIN_SOLICITUDES: 'Solicitudes',
ADMIN_PERMISOS: 'Permisos' };`

- [ ] **Step 2: `admin.service.spec.ts` (falla primero)**

Con `HttpTestingController`: cada método pega a la URL correcta con el verbo y el
cuerpo correctos y devuelve lo esperado. Un `aprobarSolicitud` que responde `403
{codigo:'SIN_PERMISO'}` propaga el error (el componente lo maneja).

- [ ] **Step 3: `admin.service.ts`**

```ts
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listarSolicitudes(estado = 'PENDIENTE'): Observable<SolicitudResumen[]> {
    return this.http.get<SolicitudResumen[]>(`${this.base}/admin/solicitudes`, { params: { estado } });
  }
  aprobarSolicitud(id: number): Observable<{ resultado: AprobarResultado }> {
    return this.http.post<{ resultado: AprobarResultado }>(`${this.base}/admin/solicitudes/${id}/aprobar`, {});
  }
  rechazarSolicitud(id: number, motivo?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes/${id}/rechazar`, motivo ? { motivo } : {});
  }
  listarMiembros(): Observable<MiembroResumen[]> {
    return this.http.get<MiembroResumen[]>(`${this.base}/admin/miembros`);
  }
  cambiarRol(id: number, rol: Rol): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/miembros/${id}/rol`, { rol });
  }
  cambiarActivo(id: number, activo: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/miembros/${id}/activo`, { activo });
  }
  cambiarAreas(id: number, areas: string[]): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/miembros/${id}/areas`, { areas });
  }
}
```

- [ ] **Step 4: `area.guard.ts` + spec**

```ts
export function areaGuard(area: string): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.sesionActiva()) return router.createUrlTree(['/login']);
    return auth.asegurarYo().pipe(
      map((u) => (u?.areas?.includes(area) ? true : router.createUrlTree(['/panel']))),
    );
  };
}
```
Spec: sin sesión → UrlTree a `/login`; con sesión y sin el área → UrlTree a
`/panel`; con el área → `true`.

- [ ] **Step 5: Stubs de componentes + rutas hijas**

Crear `AdminSolicitudes` y `AdminPermisos` como componentes standalone mínimos
(`<p>Solicitudes</p>` / `<p>Permisos</p>`), para que las rutas compilen. En
`app.routes.ts`, dentro de `children` de `panel`:

```ts
{
  path: 'administracion/solicitudes',
  component: AdminSolicitudes,
  canActivate: [areaGuard('ADMIN_SOLICITUDES')],
},
{
  path: 'administracion/permisos',
  component: AdminPermisos,
  canActivate: [areaGuard('ADMIN_PERMISOS')],
},
```

- [ ] **Step 6: Verificar y commit**

Run: `cd front && npm test`. Expected: PASS.

```bash
git add front/src/app/admin/ front/src/app/app.routes.ts
git commit -m "feat(front): AdminService + areaGuard + rutas de administración (stubs de pantallas)"
```

---

## Task 4: Pantalla "Solicitudes"

**Files:**
- Modify: `front/src/app/admin/solicitudes/solicitudes.ts` + `.html` + `.css`

**Interfaces:**
- Consumes: `AdminService.listarSolicitudes`, `aprobarSolicitud`, `rechazarSolicitud`.

- [ ] **Step 1: Componente `AdminSolicitudes`**

`solicitudes.ts`: signals `solicitudes = signal<SolicitudResumen[]>([])`,
`estado = signal<'cargando'|'lista'|'error'>('cargando')`, `mensaje = signal('')`,
`rechazando = signal<number | null>(null)` (id de la solicitud cuyo textarea de
motivo está abierto), `motivoRechazo = signal('')`.

`ngOnInit()` → `cargar()`. `cargar()` llama a `listarSolicitudes()` y rellena
signals; en error `estado='error'`.

`aprobar(s: SolicitudResumen)`: `adminService.aprobarSolicitud(s.id).subscribe({
next: (r) => { this.avisar(r.resultado === 'CUENTA_CREADA' ? 'Cuenta creada y
correo enviado.' : 'Teléfono autorizado y correo enviado.'); this.cargar(); },
error: (e) => this.avisarError(e) })`.

`abrirRechazo(id)` / `confirmarRechazo(s)`: `rechazarSolicitud(s.id,
this.motivoRechazo() || undefined)` → recargar; `error` → mensaje. Cerrar el
textarea al terminar.

`avisarError(e: HttpErrorResponse)`: mapear `e.error?.codigo`:
`SOLICITUD_YA_RESUELTA` → "Esa solicitud ya la había resuelto alguien. Recargo la
lista." + `cargar()`; `SIN_PERMISO` → "No tienes permiso para esto."; `status
=== 0` → "Sin conexión con el servidor."; `YA_REGISTRADO` → "Ya existe una cuenta
con ese teléfono o email."; default → "No se pudo completar la acción."

- [ ] **Step 2: Plantilla `solicitudes.html`**

Título "Solicitudes de acceso" + subtítulo. `@switch (estado())`:
- `cargando` → texto "Cargando…"
- `error` → mensaje + botón "Reintentar" (`cargar()`)
- `lista`:
  - si `solicitudes().length === 0` → "No hay solicitudes pendientes."
  - `@for (s of solicitudes(); track s.id)` → tarjeta (`rounded-2xl border
    border-outline bg-panel p-6`):
    - cabecera: `{{ s.nombre }} {{ s.apellidos }}` + chip `s.telefono` + chip
      `s.traeContrasena ? 'Trae contraseña' : 'Sin contraseña'`
    - `s.email`
    - bloques "Motivo", "Relación", "Conocidos" con los textos
    - acciones: botón **Aprobar** (`(click)="aprobar(s)"`, estilo primario gold)
      y botón **Rechazar** (`(click)="abrirRechazo(s.id)"`).
    - si `rechazando() === s.id`: `<textarea [(ngModel)]…>` (o
      `[value]`/`(input)` con signal) placeholder "Motivo (opcional). Si lo
      dejas en blanco se enviará un mensaje estándar." + botón "Confirmar
      rechazo" + "Cancelar".
- Banda de `mensaje()` arriba cuando no está vacío (aria-live).

Para el `textarea` con signals sin `FormsModule`: usar
`(input)="motivoRechazo.set($any($event.target).value)"` y `[value]="motivoRechazo()"`.

- [ ] **Step 3: Verificar y commit**

Run: `cd front && npm test` + `npm run build`. Expected: PASS.

```bash
git add front/src/app/admin/solicitudes/
git commit -m "feat(front): pantalla de administración de solicitudes (aprobar/rechazar)"
```

---

## Task 5: Pantalla "Permisos"

**Files:**
- Modify: `front/src/app/admin/permisos/permisos.ts` + `.html` + `.css`

**Interfaces:**
- Consumes: `AdminService.listarMiembros`, `cambiarRol`, `cambiarActivo`,
  `cambiarAreas`; `AREAS` de `admin.types.ts`.

- [ ] **Step 1: Componente `AdminPermisos`**

Signals: `miembros = signal<MiembroResumen[]>([])`, `estado`, `mensaje`.
`areasConocidas = Object.entries(AREAS)` para pintar los checkboxes.

`ngOnInit` → `cargar()`.

Acciones (cada una llama al endpoint y en éxito recarga la fila / toda la lista;
en error muestra mensaje y NO cambia el estado local):
- `ponerRol(m, rol)` → `cambiarRol(m.id, rol)`
- `activar(m, activo)` → `cambiarActivo(m.id, activo)`
- `alternarArea(m, area, incluir)` → calcula el nuevo array
  (`incluir ? [...m.areas, area] : m.areas.filter(a => a !== area)`) y llama a
  `cambiarAreas(m.id, nuevas)`.

`avisarError` mapea: `ULTIMO_ADMIN` → "No puedes dejar la peña sin ningún
administrador."; `NO_TE_PUEDES_DEGRADAR` → "No puedes quitarte a ti mismo el rol
de admin."; `NO_TE_PUEDES_DESACTIVAR` → "No puedes desactivarte a ti mismo.";
`SOLO_EL_SUPERADMIN` → "A esa persona solo puede tocarla ella misma (es la
fundadora)."; `SIN_PERMISO` → "No tienes permiso."; `VALIDACION` → "Área no
válida."; `status===0` → "Sin conexión."; default → "No se pudo guardar el
cambio." Tras cualquier error, `cargar()` para volver al estado real del backend.

- [ ] **Step 2: Plantilla `permisos.html`**

Título "Permisos de la peña". `@switch (estado())` como en Solicitudes. En
`lista`, una tarjeta por miembro:
- `{{ m.nombre }} {{ m.apellidos }}` + `m.mote` si lo hay + chip `m.telefono`
- si `m.esSuperadmin` → chip "Fundadora" y **acceso total** (los checkboxes de
  área salen deshabilitados con nota "acceso total")
- selector de rol: dos botones/segmented `Admin` / `Miembro`
  (`[class]` activo según `m.rol`), `(click)="ponerRol(m, 'ADMIN'|'MIEMBRO')"`
- toggle Activo: botón que muestra "Activo"/"Inactivo",
  `(click)="activar(m, !m.activo)"`
- checkboxes de áreas: `@for (par of areasConocidas; track par[0])` →
  `<label><input type="checkbox" [checked]="m.areas.includes(par[0])"
  [disabled]="m.rol === 'ADMIN' || m.esSuperadmin"
  (change)="alternarArea(m, par[0], $any($event.target).checked)"> {{ par[1] }}</label>`
  (si es admin, ve todas de forma implícita → deshabilitar y marcar todas con una
  nota "acceso total por rol").
- Banda `mensaje()` (aria-live).

- [ ] **Step 3: Verificar y commit**

Run: `cd front && npm test` + `npm run build`. Expected: PASS.

```bash
git add front/src/app/admin/permisos/
git commit -m "feat(front): pantalla de administración de permisos (rol/activo/áreas)"
```

---

## Task 6: Arrastrar la contraseña del registro a "solicitar acceso"

**Files:**
- Modify: `front/src/app/auth/registro/registro.ts`
- Modify: `front/src/app/auth/solicitar-acceso/solicitar-acceso.ts`
- Modify: `front/src/app/auth/auth.types.ts` (`SolicitudIngresoBody.password?`)

**Interfaces:**
- Consumes (contrato backend, del plan de backend): `POST /api/v1/auth/solicitudes`
  acepta un campo `password` **opcional** (string, 6-72). Si no se envía, la
  solicitud se crea igual.
- Produces: `SolicitudIngresoBody` con `password?: string`.

- [ ] **Step 1: Test**

En un spec de `SolicitarAcceso` (crear si no existe), o en `registro.spec.ts`:
comprobar que `irASolicitarAcceso()` incluye `password` en el `state` de la
navegación, y que `SolicitarAcceso`, si recibe `password` en `history.state`, lo
manda en el cuerpo de `solicitarAcceso(...)`. (Con `HttpTestingController` sobre
`AuthService.solicitarAcceso` o espiando el servicio.)

- [ ] **Step 2: `registro.ts` — incluir `password` en el state**

En `irASolicitarAcceso()`:
```ts
    this.router.navigate(['/solicitar-acceso'], {
      state: {
        nombre: v.nombre, apellidos: v.apellidos, telefono: v.telefono,
        email: v.email, password: v.password,
      },
    });
```

- [ ] **Step 3: `solicitar-acceso.ts` — reenviar `password` si llegó**

Ampliar `PrecargaRegistro` con `password?: string`. Guardar el valor recibido en
un campo privado `private readonly passwordArrastrada: string | null` (leído en el
constructor del mismo `state`; NO se mete en el form ni se muestra). En
`enviar()`, incluir en el objeto que se pasa a `solicitarAcceso(...)`:
`...(this.passwordArrastrada ? { password: this.passwordArrastrada } : {})`.

`auth.types.ts`: `SolicitudIngresoBody` gana `password?: string;`

- [ ] **Step 4: Verificar y commit**

Run: `cd front && npm test`. Expected: PASS.

```bash
git add front/src/app/auth/
git commit -m "feat(front): arrastra la contraseña del registro a la solicitud de acceso"
```

---

## Self-review

- **Cobertura del spec §5:** `AuthService` rol/areas (T1); `Panel` layout + nav
  "Administración" al final (T2); `areaGuard` + rutas + `AdminService` (T3);
  pantalla Solicitudes (T4); pantalla Permisos (T5); contraseña arrastrada (T6).
- **Consistencia de tipos:** `UsuarioDto` (T1) usada por guard y nav.
  `SolicitudResumen`/`MiembroResumen` definidas una vez en `admin.types.ts` (T3)
  y consumidas en T4/T5. `AREAS` es la única fuente de etiquetas de área.
- **Secuenciación:** T2 crea el nav con enlaces antes de que existan las rutas
  (ruling anotado); T3 crea stubs de pantalla + rutas; T4/T5 rellenan las
  pantallas. Cada tarea compila y `npm test` pasa.
- **Fuera de alcance:** histórico de solicitudes con filtros, badges de
  pendientes (spec §"Fuera de alcance").
- **Riesgo:** `Panel` → layout es un cambio estructural; el humo manual debe
  confirmar que `/panel` y el nav móvil siguen bien. `ngModel` no está disponible
  (no se importa `FormsModule` en los componentes nuevos) → los textarea/checkbox
  usan `[value]`/`[checked]` + eventos, no `[(ngModel)]`.
