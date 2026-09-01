# Perfiles de miembro — Plan B: Web

> **Ejecución:** el usuario ha pedido no usar subagentes para esta parte. Se
> ejecuta en línea, tarea a tarea, con tests reales (`ng test`) y commits por
> tarea — sin dispatch de implementer/reviewer.

**Goal:** editor de perfil (foto/avatar, datos, pareja, hijos) y la sección
Miembros con tarjetas, sobre la API que ya expone el Plan A (mergeado en esta
misma rama).

**Architecture:** Angular 22 standalone components + signals + Reactive Forms,
igual que el resto de `front/`. Un `PerfilService` nuevo habla con
`/api/v1/perfil*` y `/api/v1/miembros`; un guard fuerza el editor si el perfil
no está completo; `EditorPerfil` es el mismo componente tanto para el alta
obligatoria como para "Editar" desde la propia tarjeta.

**Tech Stack:** Angular 22.1 (standalone, `@if`/`@for`/`@switch`), Tailwind 4
(tokens de marca en `styles.css`), `HttpClient` + `HttpTestingController` en
tests, builder de test `@angular/build:unit-test` (`ng test`).

**Spec:** `docs/superpowers/specs/2026-09-01-perfiles-miembros-design.md`

## Global Constraints

- Componentes standalone, `ChangeDetectionStrategy.OnPush` donde el resto del
  código lo usa (revisar caso a caso — `permisos.ts`/`registro.ts` NO lo usan
  explícitamente, así que no es obligatorio en toda la app; usarlo si el
  componente es mayoritariamente reactivo a signals).
- Formularios: `ReactiveFormsModule` + `FormBuilder`, como `registro.ts`.
- Estado de pantalla en signals (`signal<'cargando'|'lista'|'error'>` etc.),
  como `permisos.ts`.
- Estilos: paleta de `styles.css` (`bg-panel`, `border-outline`, `text-muted`,
  `text-ink`, `bg-brand`, `text-gold`, `font-display`). Reutilizar las clases
  ya usadas en `permisos.html`/`registro.html` en vez de inventar nuevas.
- Mensajes de error del backend: mapear por `codigo` (ver `CodigoError` en
  `auth.types.ts`) a texto en español, con un mensaje por defecto para
  cualquier código no mapeado — patrón de `permisos.ts`/`registro.ts`.
- Tests: `HttpTestingController` (`provideHttpClient()` +
  `provideHttpClientTesting()`), sin mockear el servicio real salvo
  dependencias externas (`AuthService` cuando solo hace falta el signal de
  sesión, como en `permisos.spec.ts`). `afterEach(() => httpMock.verify())`.
- Nunca pedir `telefono`/`email` en las tarjetas de miembro (el backend ya no
  los expone; no añadir columnas que los muestren).
- Español en todo el texto de cara al usuario.

## Contrato del backend (Plan A, ya mergeado en esta rama)

```
GET    /api/v1/perfil                → PerfilResponse (siempre 200)
PUT    /api/v1/perfil                → PerfilResponse   body: GuardarPerfilRequest
POST   /api/v1/perfil/foto           → { imagenRef }     multipart "archivo"
GET    /api/v1/perfil/avatares       → AvatarResumen[]
POST   /api/v1/perfil/pareja/aceptar → 204
POST   /api/v1/perfil/pareja/rechazar→ 204
DELETE /api/v1/perfil/pareja         → 204
GET    /api/v1/miembros              → TarjetaMiembroResponse[]  (ya ordenadas)
GET    /api/v1/media/avatares/{id}.png
GET    /api/v1/media/fotos/{archivo}
```

```ts
interface PerfilResponse {
  usuarioId: number; nombre: string; apellidos: string; mote: string | null;
  sobreMi: string | null; imagenTipo: 'FOTO' | 'AVATAR' | null;
  imagenRef: string | null; imagenUrl: string | null; completado: boolean;
  pareja: ParejaEnPerfil | null; hijos: HijoEnPerfil[];
  vinculoPendiente: VinculoPendiente | null;
}
interface ParejaEnPerfil { vinculoId: number; nombre: string; telefono: string;
  estado: 'SIN_CUENTA' | 'PENDIENTE' | 'ACEPTADO' | 'RECHAZADO'; }
interface HijoEnPerfil { id: number; nombre: string; mayorDeEdad: boolean;
  telefono: string | null; visible: boolean; registrado: boolean; }
interface VinculoPendiente { vinculoId: number; solicitanteNombre: string; }
interface GuardarPerfilRequest { nombre: string; apellidos: string;
  mote: string | null; sobreMi: string | null; imagenTipo: 'FOTO' | 'AVATAR';
  imagenRef: string; tienePareja: boolean; parejaNombre: string | null;
  parejaTelefono: string | null; hijos: HijoRequest[]; }
interface HijoRequest { id: number | null; nombre: string; mayorDeEdad: boolean;
  telefono: string | null; visible: boolean; }
interface AvatarResumen { id: string; genero: 'CHICO' | 'CHICA'; }
interface TarjetaMiembroResponse { id: number; nombre: string; apellidos: string;
  mote: string | null; sobreMi: string | null; imagenUrl: string | null;
  parejaNombre: string | null; hijos: string[]; }
```

**Códigos de error nuevos** (además de los ya listados en `CodigoError`):
`AVATAR_INEXISTENTE`(400), `IMAGEN_REF_INVALIDA`(400), `IMAGEN_NO_SOPORTADA`(415),
`IMAGEN_DEMASIADO_GRANDE`(413), `VINCULO_NO_ENCONTRADO`(404),
`TELEFONO_YA_EMPAREJADO`(409), `YA_TIENE_PAREJA`(409),
`TELEFONO_PAREJA_INVALIDO`(400), `NOMBRE_PAREJA_REQUERIDO`(400),
`TELEFONO_HIJO_INVALIDO`(400).

**Landmines de backend a respetar en el front (verificadas leyendo el código):**
- `PUT /perfil` con `tienePareja=false` cuando ya tenías un vínculo `ACEPTADO`
  **rompe la pareja de verdad** (no es un no-op). El formulario NUNCA debe
  mandar `tienePareja=false` por defecto/accidente: se inicializa siempre con
  el valor real que trajo el `GET` y solo cambia si el usuario toca el control.
- Si `pareja.estado === 'ACEPTADO'`, cambiar `parejaTelefono` en el `PUT`
  **rompe el vínculo y declara uno nuevo** con ese teléfono. Por eso, cuando
  `estado === 'ACEPTADO'`, el editor NO ofrece campos editables de
  nombre/teléfono: se enseña de solo lectura + botón "Romper vínculo"
  (`DELETE /perfil/pareja`), y el `PUT` reenvía tal cual `parejaNombre`/
  `parejaTelefono` que llegaron del `GET` (para que el guardado del resto de
  campos —foto, sobre mí, hijos— no toque el vínculo).
- Si el usuario es el lado que **aceptó** el vínculo (no el que lo declaró), el
  backend ignora silenciosamente los campos de pareja del `PUT` — por eso da
  igual si se reenvían tal cual; nunca hay que intentar "adivinar" si soy
  solicitante o aceptante, basta con no mostrar campos editables en `ACEPTADO`.
- `pareja.estado` puede ser `'SIN_CUENTA'` o `'PENDIENTE'` únicamente cuando el
  usuario es el solicitante (el backend nunca lo pone así para el lado
  aceptante) — en esos dos estados sí es seguro dejar editar nombre/teléfono.
- `PerfilResponse.hijos` **ya excluye** los hijos registrados (con cuenta
  propia): el editor no necesita lógica de "hijo protegido", puede tratar
  cada fila como plenamente editable/borrable.

---

## File Structure

**Nuevo directorio `front/src/app/panel/miembros/`**
- `perfil.types.ts` — interfaces de arriba
- `perfil.service.ts` — `PerfilService` (todas las llamadas HTTP de perfil) +
  `urlMedia(imagenUrl: string | null): string | null` (prefija con el origen
  del backend en dev; en prod `apiBaseUrl` ya es relativo)
- `perfil-completo.guard.ts` — `perfilCompletoGuard`
- `selector-avatar/selector-avatar.ts` (+ `.html`, `.css`, `.spec.ts`)
- `editor-perfil/editor-perfil.ts` (+ `.html`, `.css`, `.spec.ts`)
- `tarjeta-miembro/tarjeta-miembro.ts` (+ `.html`, `.css`, `.spec.ts`)
- `miembros.ts` (+ `.html`, `.css`, `.spec.ts`)

**Modificados**
- `front/src/app/app.routes.ts` — rutas `miembros` y `miembros/editar`
- `front/src/app/panel/secciones.ts` — quitar "Miembros" de `SECCIONES`
- `front/src/app/panel/panel.html` / `panel.ts` — enlace real a Miembros en el nav
- `front/src/app/panel/panel.spec.ts` — ajustar si el nav cambia de forma

---

## Task 1 — Tipos + PerfilService

**Files:** crear `perfil.types.ts`, `perfil.service.ts` + `perfil.service.spec.ts`

- [ ] Interfaces del contrato de arriba en `perfil.types.ts`, más
  `type CodigoErrorPerfil = 'AVATAR_INEXISTENTE' | 'IMAGEN_REF_INVALIDA' | 'IMAGEN_NO_SOPORTADA' | 'IMAGEN_DEMASIADO_GRANDE' | 'VINCULO_NO_ENCONTRADO' | 'TELEFONO_YA_EMPAREJADO' | 'YA_TIENE_PAREJA' | 'TELEFONO_PAREJA_INVALIDO' | 'NOMBRE_PAREJA_REQUERIDO' | 'TELEFONO_HIJO_INVALIDO'`
  (unión con el `CodigoError` existente donde haga falta mostrar mensajes).
- [ ] `PerfilService` (`providedIn: 'root'`), mismo estilo que `AdminService`:
  - `miPerfil(): Observable<PerfilResponse>` → `GET /perfil`
  - `guardar(body: GuardarPerfilRequest): Observable<PerfilResponse>` → `PUT /perfil`
  - `subirFoto(archivo: File): Observable<{ imagenRef: string }>` → `POST /perfil/foto`
    con `FormData` (`form.append('archivo', archivo)`)
  - `avatares(): Observable<AvatarResumen[]>` → `GET /perfil/avatares`
  - `aceptarPareja(): Observable<void>` / `rechazarPareja(): Observable<void>` /
    `romperPareja(): Observable<void>`
  - `miembros(): Observable<TarjetaMiembroResponse[]>` → `GET /miembros`
- [ ] `urlMedia` como función exportada suelta (no método de clase, no necesita
  estado): calcula `origen = environment.apiBaseUrl.replace(/\/api\/v1$/, '')`
  una vez a nivel de módulo; devuelve `null` si `imagenUrl` es `null`, si no
  `origen + imagenUrl`.
- [ ] Test: un caso por método (verifica método HTTP + URL + cuerpo/params con
  `HttpTestingController`, patrón de `admin.service.spec.ts` si existe o el
  estilo de `permisos.spec.ts`), más 2-3 casos de `urlMedia` (null, con
  `apiBaseUrl` relativo, con `apiBaseUrl` absoluto — mockear `environment` no
  hace falta, basta con probar la función con distintas `imagenUrl` de entrada
  ya que el origen sale del `environment` real de test).
- [ ] `ng test` verde. Commit: `feat(miembros): PerfilService y tipos del perfil`

## Task 2 — Guard + rutas + nav

**Files:** crear `perfil-completo.guard.ts` (+ `.spec.ts`); modificar
`app.routes.ts`, `secciones.ts`, `panel.html`, `panel.ts` (+ specs si aplica)

- [ ] `perfilCompletoGuard: CanActivateFn` — mismo patrón que `areaGuard`:
  sin sesión → `/login`; con sesión, `PerfilService.miPerfil()` y si
  `completado === false` → `router.createUrlTree(['/panel/miembros/editar'])`;
  si `completado === true` → `true`. Petición fallida (red/401) → tratar como
  `areaGuard` trata un `asegurarYo()` fallido: dejar pasar a `/login` (evita
  bucle si el token ya no vale; el `authGuard`/interceptor ya lo cubren en la
  práctica, pero el guard debe resolver, no colgarse).
- [ ] `app.routes.ts`: añadir dentro de los hijos de `panel`:
  ```ts
  { path: 'miembros', component: Miembros, canActivate: [perfilCompletoGuard] },
  { path: 'miembros/editar', component: EditorPerfil },
  ```
  y añadir `canActivate: [perfilCompletoGuard]` a `''` (PanelInicio),
  `administracion`, `administracion/solicitudes`, `administracion/permisos` —
  **todas las rutas de `/panel` excepto `miembros/editar`**, para que un
  perfil incompleto quede atrapado en el editor.
- [ ] `secciones.ts`: quitar la entrada `Miembros` de `SECCIONES` (deja de ser
  "próximamente").
- [ ] `panel.html`/`panel.ts`: añadir un `routerLink="/panel/miembros"` real en
  el nav (mismo sitio donde antes solo aparecía como texto de "próximamente";
  revisar cómo `panel.html` pinta `seccionesPronto` para no dejarlo duplicado
  ahí y en el nav real).
- [ ] Test del guard: 3 casos (sin sesión → `/login`; `completado=false` →
  `/panel/miembros/editar`; `completado=true` → `true`), patrón
  `area.guard.spec.ts` si existe o el de `auth.guard.ts` (mockear
  `PerfilService` con un objeto `{ miPerfil: () => of(...) }`).
- [ ] `ng test` verde. Commit: `feat(miembros): guard de perfil incompleto + rutas + nav`

## Task 3 — SelectorAvatar

**Files:** `selector-avatar/selector-avatar.ts`, `.html`, `.css`, `.spec.ts`

- [ ] Componente presentacional + un poco de estado propio (el filtro):
  - `input.required<AvatarResumen[]>() avatares`
  - `input<string | null>() seleccionado` (id del avatar elegido, o `null`)
  - `output<string>() elegido` — emite el `id` al hacer click en una tarjeta
  - `protected readonly filtro = signal<'TODOS' | 'CHICO' | 'CHICA'>('TODOS')`
  - `protected readonly visibles = computed(() => filtro() === 'TODOS' ? avatares() : avatares().filter(a => a.genero === filtro()))`
- [ ] Plantilla: 3 botones de filtro (Todos/Chicos/Chicas) + rejilla de
  `<img>` circulares (`urlMedia('/api/v1/media/avatares/' + a.id + '.png')` —
  o mejor, construir la URL directamente aquí con el mismo helper de
  `perfil.service.ts` para no repetirlo) con `[class.ring-2]` o similar en el
  seleccionado y `(click)="elegido.emit(a.id)"`.
- [ ] Test: pinta los avatares recibidos; filtra a "Chicos"/"Chicas" muestra
  solo esos; click en uno emite su id; el seleccionado se marca visualmente
  (comprobar la clase, no un pixel).
- [ ] `ng test` verde. Commit: `feat(miembros): selector de avatar con filtro`

## Task 4 — EditorPerfil

**Files:** `editor-perfil/editor-perfil.ts`, `.html`, `.css`, `.spec.ts`

Componente más grande del plan. Reutilizable para el alta obligatoria y para
"Editar" desde la propia tarjeta — no distingue el caso, siempre carga
`GET /perfil` y guarda con `PUT /perfil`.

- [ ] Estado: `estado = signal<'cargando'|'listo'|'guardando'|'error'>('cargando')`,
  `mensajeError = signal('')`.
- [ ] Al `ngOnInit`: `perfilService.miPerfil()` y `perfilService.avatares()`
  (en paralelo, `forkJoin` o dos suscripciones) para poblar el form y el
  `SelectorAvatar`.
- [ ] `form` (`FormBuilder`):
  ```ts
  {
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    mote: [''],
    sobreMi: ['', Validators.maxLength(500)],
    imagenTipo: [null as 'FOTO' | 'AVATAR' | null, Validators.required],
    imagenRef: [null as string | null, Validators.required],
    tienePareja: [false],           // se fija con el valor real tras el GET
    parejaNombre: [''],
    parejaTelefono: [''],
    hijos: this.formBuilder.array<FormGroup>([]),
  }
  ```
  - Al recibir el `GET`, `form.patchValue({...})` con los datos reales
    (incluido `tienePareja: !!resp.pareja` y, si hay `pareja`, sus
    `parejaNombre`/`parejaTelefono` — **ver la nota de landmines**: si
    `pareja.estado === 'ACEPTADO'` estos dos campos del form quedan
    deshabilitados (`form.get('parejaNombre')!.disable()`, ídem teléfono) para
    que ni un `getRawValue()` descuidado los deje editar; el `guardar()` usa
    `getRawValue()` así que los disabled SÍ viajan con su valor original.
  - Hijos: por cada `HijoEnPerfil` del `GET`, `hijosArray.push` un
    `FormGroup` `{ id: [h.id], nombre: [h.nombre, Validators.required], mayorDeEdad: [h.mayorDeEdad], telefono: [h.telefono], visible: [h.visible] }`.
    Botón "Añadir hijo" hace `push` de uno vacío (`id: [null]`); botón "Quitar"
    hace `removeAt(i)`.
- [ ] Imagen: dos modos, controlados por un signal `modoImagen = signal<'FOTO'|'AVATAR'>(...)`
  (arranca en `resp.imagenTipo ?? 'AVATAR'`):
  - Modo AVATAR: `<app-selector-avatar>` con `(elegido)` → `form.patchValue({ imagenTipo: 'AVATAR', imagenRef: id })`.
  - Modo FOTO: `<input type="file" accept="image/jpeg,image/png">` →
    `onFotoElegida(archivo)`: guarda el `File` en un signal local
    (`fotoPendiente`) y pinta una previsualización con `URL.createObjectURL`
    (revocarla en `ngOnDestroy` si se reemplaza). **No** sube la foto al
    elegir el archivo: la sube justo antes de guardar (ver `guardar()`), así
    no quedan ficheros huérfanos si el usuario cambia de opinión y no llega a
    pulsar "Guardar".
  - Un toggle (dos botones "Subir foto" / "Elegir avatar") cambia `modoImagen`;
    cambiar de modo NO borra lo ya elegido en el otro (por si el usuario
    vuelve atrás), pero `guardar()` solo usa el modo activo.
- [ ] Aviso fijo junto al teléfono de pareja/hijo (mismo texto en los dos
  sitios): *"Lo pedimos para que cuando haya un evento, un solo miembro de la
  familia pueda apuntar a todos."*
- [ ] `guardar()`:
  1. `form.markAllAsTouched()`; si `form.invalid` → no envía.
  2. Si `modoImagen() === 'FOTO'` y hay `fotoPendiente()` sin subir todavía →
     `perfilService.subirFoto(fotoPendiente())`, y con la `imagenRef` que
     devuelve, `form.patchValue({ imagenTipo: 'FOTO', imagenRef })` antes de
     seguir. Si `modoImagen() === 'FOTO'` pero no hay `fotoPendiente()` (el
     usuario no tocó el selector de fichero, ya tenía una FOTO guardada)
     manda la `imagenRef` que ya tenía.
  3. `perfilService.guardar(cuerpoDesdeForm())` — `cuerpoDesdeForm()` arma el
     `GuardarPerfilRequest` desde `form.getRawValue()` (`hijos` mapea cada
     fila a `HijoRequest`; `parejaNombre`/`parejaTelefono` a `null` si
     `!tienePareja`).
  4. Éxito → si venías del alta obligatoria, `router.navigateByUrl('/panel/miembros')`;
     si veías desde "Editar", igual (siempre vuelve a la lista — no hay forma
     de distinguir el origen ni falta hacer).
  5. Error → mapear `codigo` a mensaje (tabla de constantes, patrón de
     `permisos.ts`): `AVATAR_INEXISTENTE`/`IMAGEN_REF_INVALIDA` → "Elige una
     foto o avatar válido."; `IMAGEN_NO_SOPORTADA` → "Ese archivo no es una
     foto válida (usa JPEG o PNG)."; `IMAGEN_DEMASIADO_GRANDE` → "La foto pesa
     demasiado."; `TELEFONO_PAREJA_INVALIDO`/`NOMBRE_PAREJA_REQUERIDO`/
     `TELEFONO_HIJO_INVALIDO`/`TELEFONO_YA_EMPAREJADO` → mensajes concretos;
     resto → genérico.
- [ ] "Romper vínculo" (solo visible si `pareja?.estado === 'ACEPTADO'`):
  `perfilService.romperPareja()` → en éxito, recarga el perfil
  (`ngOnInit`-equivalente) para reflejar el estado limpio.
- [ ] Test (`editor-perfil.spec.ts`), con `HttpTestingController`:
  1. Carga inicial: `GET /perfil` + `GET /perfil/avatares`, precarga el form.
  2. Guardar con avatar: manda `PUT /perfil` con el `imagenRef` elegido.
  3. Guardar con foto nueva: primero `POST /perfil/foto`, luego `PUT /perfil`
     con la `imagenRef` que devolvió la subida.
  4. Guardar sin `tienePareja` cambiado (perfil sin pareja) manda
     `tienePareja: false` — **y si el `GET` inicial trajo `pareja: null`,
     jamás manda `true` sin que el usuario lo haya marcado**.
  5. Con `pareja.estado === 'ACEPTADO'`: los campos de nombre/teléfono de
     pareja aparecen deshabilitados en el DOM y el `PUT` reenvía los mismos
     valores que trajo el `GET` (no vacíos, no editados).
  6. Añadir un hijo, guardar → el `PUT` incluye ese hijo con `id: null`.
  7. Quitar un hijo existente, guardar → el `PUT` ya no lo incluye.
  8. Un error `TELEFONO_YA_EMPAREJADO` en el `PUT` enseña su mensaje.
  9. "Romper vínculo" manda `DELETE /perfil/pareja` y recarga.
- [ ] `ng test` verde. Commit: `feat(miembros): editor de perfil (datos, imagen, pareja, hijos)`

## Task 5 — TarjetaMiembro + Miembros

**Files:** `tarjeta-miembro/tarjeta-miembro.ts` (+ `.html`, `.css`, `.spec.ts`),
`miembros.ts` (+ `.html`, `.css`, `.spec.ts`)

- [ ] `TarjetaMiembro` — presentacional puro:
  - `input.required<TarjetaMiembroResponse>() tarjeta`
  - `input(false) esLaMia: boolean`
  - `output<void>() editar` (solo se emite desde el botón que aparece si
    `esLaMia`)
  - Pinta foto/avatar (`urlMedia(tarjeta().imagenUrl)`, con un avatar/inicial
    de repuesto si es `null`), nombre + apellidos, mote entre comillas si
    hay, `sobreMi` solo si no es `null`, `parejaNombre` solo si no es `null`
    ("Pareja: X"), lista de `hijos` solo si no está vacía ("Hijos: A, B").
    Botón "Editar" → `routerLink="/panel/miembros/editar"` o `(click)="editar.emit()"`
    (más simple: `RouterLink` directo, sin `output`, ya que el destino es
    siempre el mismo).
- [ ] `Miembros`:
  - `ngOnInit`: `perfilService.miembros()` (lista) y `perfilService.miPerfil()`
    (para el `vinculoPendiente` y saber cuál es "mi" tarjeta por
    `auth.usuarioActual()?.id`).
  - Banner si `vinculoPendiente !== null`: *"{{ solicitanteNombre }} dice que
    sois pareja"* con botones Confirmar/Rechazar →
    `perfilService.aceptarPareja()` / `rechazarPareja()`, y tras cualquiera de
    los dos, recarga `miPerfil()` (el banner desaparece) — no hace falta
    recargar la lista de tarjetas para que el banner se quite, pero si el
    usuario aceptó, su propia tarjeta cambiará de orden/contenido en la
    peña de otros, no en la suya propia en este mismo `GET`.
  - `@for (t of tarjetas(); track t.id)` → `<app-tarjeta-miembro [tarjeta]="t" [esLaMia]="t.id === auth.usuarioActual()?.id" />`.
  - Estados `cargando`/`lista`/`error` con reintento, mismo patrón que
    `permisos.ts`.
- [ ] Test `tarjeta-miembro.spec.ts`: pinta los campos condicionales
  correctamente (sin pareja no sale "Pareja:", sin hijos no sale "Hijos:",
  `sobreMi` null no pinta el párrafo); botón Editar solo con `esLaMia=true`.
- [ ] Test `miembros.spec.ts`: pinta la lista en el orden que da el backend
  (no reordena en el cliente); banner de vínculo pendiente aparece con
  `vinculoPendiente` y desaparece tras Confirmar (recarga `GET /perfil` sin
  `vinculoPendiente`); Rechazar manda `POST .../rechazar`; error de red →
  estado de error con reintento.
- [ ] `ng test` verde. Commit: `feat(miembros): sección Miembros con tarjetas y banner de vínculo pendiente`

## Task 6 — Repaso de integración

- [ ] `ng test` completo (toda la suite de `front/`) verde.
- [ ] `ng build` (o `ng build --configuration development`) sin errores de
  compilación/plantilla.
- [ ] Arrancar `ng serve` + backend local en modo normal, login manual, seguir
  el flujo: perfil incompleto → redirige al editor → elegir avatar → guardar →
  entra en Miembros → ver la propia tarjeta primero → Editar → cambiar a foto
  → guardar → la tarjeta se actualiza. Si hay un segundo usuario de prueba,
  probar declarar pareja y aceptar/rechazar desde el otro navegador/perfil.
- [ ] Commit si hace falta algún arreglo del repaso: `chore(miembros): repaso de integración del front`

## Self-Review (hecha al escribir el plan)

- **Cobertura del spec:** editor obligatorio en primer login → guard (T2) +
  editor (T4); foto/avatar → T3+T4; pareja con las landmines del backend
  respetadas → T4; hijos → T4; sección Miembros con tarjetas ordenadas (el
  orden ya lo da el backend, el front no reordena) → T5; banner de vínculo
  pendiente → T5.
- **Huecos conocidos, aceptados:** no hay recorte/zoom de la foto antes de
  subir (se manda el archivo tal cual, el backend recorta centrado); no hay
  aviso de "no guardado" al salir del editor con cambios sin guardar.
- **Fuera de alcance** (como en el spec): teléfono desde contactos (solo
  móvil, Plan C); iOS.
