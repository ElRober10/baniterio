# Eventos — Plan B: Web (Angular)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sección Eventos en el panel web: listado paginado (cada evento un botón → ver / gestionar), botón de crear evento, diálogo de "solicitar crear evento", editor de crear/editar, y el bloque de "Solicitudes de evento" dentro de `administracion/solicitudes` para administradores.

**Architecture:** Componentes standalone Angular con signals y `ChangeDetectionStrategy.OnPush`. Un `EventosService` con un método por endpoint (mismo patrón que `PerfilService`/`AdminService`), tipos en `eventos.types.ts`. Rutas hijas de `/panel` con `perfilCompletoGuard`. El bloque de administración se añade a la pantalla de solicitudes existente y se muestra solo si el usuario en sesión es admin/superadmin.

**Tech Stack:** Angular 22 (standalone, signals), Tailwind, RxJS, Jasmine + `@angular/build:unit-test` (`*.spec.ts`).

**Spec:** `docs/superpowers/specs/2026-09-03-eventos-design.md`
**Depende de:** Plan A (backend) — los endpoints `/api/v1/eventos*` y `/api/v1/admin/solicitudes-evento*` deben existir.

## Global Constraints

- **Componentes standalone**, `imports: [...]` en el decorador, `templateUrl`/`styleUrl` en ficheros aparte, `changeDetection: ChangeDetectionStrategy.OnPush` en los presentacionales. Estado con `signal()`; sin `FormsModule` para cosas simples (enlace a mano `[value]` + `(input)`), `ReactiveFormsModule` para el editor.
- **Tailwind** con los tokens de marca del proyecto (`bg-brand`, `text-gold`, `border-outline`, `bg-panel`, `text-muted`, `bg-surface`, `text-ink`, `brand-bright`, `gold-soft`). Reutilizar clases de `miembros.html` / `solicitudes.html`.
- **HTTP**: `environment.apiBaseUrl` como base; el `authGuard`/interceptor ya ponen el Bearer. Los servicios NO capturan errores: los propaga y cada pantalla traduce el `codigo` del backend (patrón `Partial<Record<CodigoError, string>>`).
- **Rutas** en `front/src/app/app.routes.ts`, `component:` con import directo (el proyecto no usa lazy loading). Todas las hijas de `panel` llevan `canActivate: [perfilCompletoGuard]`.
- **Idioma**: todo el texto de usuario en español.
- **Tests**: un `*.spec.ts` por servicio (un caso por método, patrón `perfil.service.spec.ts`) y por componente (render + interacción con `HttpTestingController`). Tras cada tarea: `npx ng test --no-watch` verde.
- **Nav**: al conectar la sección de verdad, **quitar** `Eventos` de `SECCIONES` en `front/src/app/panel/secciones.ts` y añadir el enlace real en `panel.html` (las dos navs: lateral y móvil) y, si hace falta, en `panel.ts`.

---

## File Structure

**Nuevos** (`front/src/app/panel/eventos/`)
- `eventos.types.ts` — contratos de `/api/v1/eventos*` y `/api/v1/admin/solicitudes-evento*`
- `eventos.service.ts` + `eventos.service.spec.ts`
- `eventos.ts` / `eventos.html` / `eventos.css` / `eventos.spec.ts` — listado
- `evento-detalle/evento-detalle.ts` / `.html` / `.css` / `.spec.ts` — vista de un evento
- `editor-evento/editor-evento.ts` / `.html` / `.css` / `.spec.ts` — crear / editar
- `admin/` — NO; el bloque de administración vive en `front/src/app/admin/solicitudes-evento/` (ver Task 5)

**Nuevos** (`front/src/app/admin/solicitudes-evento/`)
- `solicitudes-evento.ts` / `.html` / `.css` / `.spec.ts` — bloque embebido en la pantalla de solicitudes

**Modificados**
- `front/src/app/app.routes.ts` — 4 rutas hijas de `panel`
- `front/src/app/panel/secciones.ts` — quitar `Eventos`
- `front/src/app/panel/panel.html` — enlace real "Eventos" en las dos navs
- `front/src/app/admin/solicitudes/solicitudes.ts` + `.html` — montar `<app-solicitudes-evento>` cuando el usuario es admin
- `front/src/app/admin/admin.service.ts` + `admin.types.ts` — 3 métodos y 1 tipo para solicitudes de evento

---

## Task 1: Contrato — `eventos.types.ts` y `eventos.service.ts`

**Files:**
- Create: `front/src/app/panel/eventos/eventos.types.ts`
- Create: `front/src/app/panel/eventos/eventos.service.ts`
- Test: `front/src/app/panel/eventos/eventos.service.spec.ts`

**Interfaces:**
- Produces:
  - Tipos: `EventoResumen`, `EventoDetalle`, `ListaEventosResponse`, `GuardarEventoRequest`, `CodigoErrorEvento`.
  - `EventosService` con: `listar(pagina: number)`, `detalle(id: number)`, `crear(body)`, `editar(id, body)`, `borrar(id)`, `solicitarCrear(mensaje?: string)`.

- [ ] **Step 1: Escribir `eventos.types.ts`**

```ts
/**
 * Tipos del contrato con `/api/v1/eventos*`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.evento.dto`). `*Response` = lo que recibimos;
 * `*Request` = lo que enviamos.
 */

export interface EventoResumen {
  id: number;
  nombre: string;
  fecha: string;          // ISO yyyy-MM-dd
  fechaFin: string | null;
  lugar: string | null;
  pasado: boolean;
}

export interface EventoDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  lugar: string | null;
  fecha: string;
  fechaFin: string | null;
  pasado: boolean;
  creadoPor: { id: number; nombre: string } | null;
  puedoEditar: boolean;
  puedoBorrar: boolean;
  borradoPendiente: boolean;
}

/** `GET /api/v1/eventos?pagina=N`. */
export interface ListaEventosResponse {
  eventos: EventoResumen[];
  pagina: number;
  totalPaginas: number;
  puedeCrear: boolean;
  puedeSolicitar: boolean;
}

/** Cuerpo de `POST` y `PUT` de un evento. `fechaFin` opcional. */
export interface GuardarEventoRequest {
  nombre: string;
  descripcion: string | null;
  lugar: string | null;
  fecha: string;            // yyyy-MM-dd
  fechaFin: string | null;
}

/** Códigos de error propios de eventos (ver ApiExceptionHandler.java). */
export type CodigoErrorEvento =
  | 'EVENTO_NO_ENCONTRADO'
  | 'SIN_PERMISO_EVENTO'
  | 'SIN_CREDITO_EVENTO'
  | 'SOLICITUD_EVENTO_YA_PENDIENTE'
  | 'CREDITO_SIN_CONSUMIR'
  | 'SOLICITUD_EVENTO_NO_APLICA'
  | 'SOLICITUD_EVENTO_YA_RESUELTA'
  | 'SIN_PERMISO'
  | 'VALIDACION';
```

- [ ] **Step 2: Escribir el test que falla**

`eventos.service.spec.ts` (patrón exacto de `perfil.service.spec.ts`):

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { EventosService } from './eventos.service';
import { GuardarEventoRequest } from './eventos.types';

describe('EventosService', () => {
  let service: EventosService;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EventosService);
    httpMock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => httpMock.verify());

  it('listar() hace GET a /eventos con el parámetro pagina', () => {
    service.listar(2).subscribe();
    const req = httpMock.expectOne(`${base}/eventos?pagina=2`);
    expect(req.request.method).toBe('GET');
    req.flush({ eventos: [], pagina: 2, totalPaginas: 3, puedeCrear: false, puedeSolicitar: true });
  });

  it('detalle() hace GET a /eventos/:id', () => {
    service.detalle(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('crear() hace POST a /eventos con el cuerpo', () => {
    const body: GuardarEventoRequest = {
      nombre: 'Cena', descripcion: null, lugar: null, fecha: '2027-12-01', fechaFin: null,
    };
    service.crear(body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('editar() hace PUT a /eventos/:id', () => {
    const body: GuardarEventoRequest = {
      nombre: 'Cena 2', descripcion: null, lugar: null, fecha: '2027-12-02', fechaFin: null,
    };
    service.editar(7, body).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('borrar() hace DELETE a /eventos/:id', () => {
    service.borrar(7).subscribe();
    const req = httpMock.expectOne(`${base}/eventos/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('solicitarCrear() hace POST a /eventos/solicitudes con el mensaje', () => {
    service.solicitarCrear('quiero organizar la cena').subscribe();
    const req = httpMock.expectOne(`${base}/eventos/solicitudes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ mensaje: 'quiero organizar la cena' });
    req.flush({ id: 1, estado: 'PENDIENTE' });
  });
});
```

- [ ] **Step 3: Ejecutar y ver fallar**

Run: `npx ng test --no-watch`
Expected: FAIL — `EventosService` no existe.

- [ ] **Step 4: Escribir `eventos.service.ts`**

```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  EventoDetalle,
  GuardarEventoRequest,
  ListaEventosResponse,
} from './eventos.types';

/**
 * Llamadas de la sección Eventos (`/api/v1/eventos*`). Un método por endpoint;
 * no maneja errores, los deja propagar para que cada pantalla traduzca el
 * `codigo` del backend (mismo patrón que `PerfilService` y `AdminService`).
 */
@Injectable({ providedIn: 'root' })
export class EventosService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listar(pagina: number): Observable<ListaEventosResponse> {
    return this.http.get<ListaEventosResponse>(`${this.base}/eventos`, {
      params: { pagina: String(pagina) },
    });
  }

  detalle(id: number): Observable<EventoDetalle> {
    return this.http.get<EventoDetalle>(`${this.base}/eventos/${id}`);
  }

  crear(body: GuardarEventoRequest): Observable<EventoDetalle> {
    return this.http.post<EventoDetalle>(`${this.base}/eventos`, body);
  }

  editar(id: number, body: GuardarEventoRequest): Observable<EventoDetalle> {
    return this.http.put<EventoDetalle>(`${this.base}/eventos/${id}`, body);
  }

  /** 204 si lo borra un admin; 202 `{ estado: 'PENDIENTE' }` si genera solicitud. */
  borrar(id: number): Observable<{ estado: string } | null> {
    return this.http.delete<{ estado: string } | null>(`${this.base}/eventos/${id}`);
  }

  solicitarCrear(mensaje?: string): Observable<{ id: number; estado: string }> {
    return this.http.post<{ id: number; estado: string }>(
      `${this.base}/eventos/solicitudes`,
      mensaje ? { mensaje } : {},
    );
  }
}
```

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `npx ng test --no-watch`
Expected: PASS (6 casos nuevos).

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/eventos/eventos.types.ts \
        front/src/app/panel/eventos/eventos.service.ts \
        front/src/app/panel/eventos/eventos.service.spec.ts
git commit -m "feat(eventos): EventosService y tipos del contrato (web)"
```

---

## Task 2: Listado de eventos + navegación

**Files:**
- Create: `front/src/app/panel/eventos/eventos.ts` / `eventos.html` / `eventos.css`
- Test: `front/src/app/panel/eventos/eventos.spec.ts`
- Modify: `front/src/app/app.routes.ts`, `front/src/app/panel/secciones.ts`, `front/src/app/panel/panel.html`

**Interfaces:**
- Consumes: `EventosService.listar`, `EventosService.solicitarCrear` (Task 1); `Router` para navegar a `/panel/eventos/:id` y `/panel/eventos/nuevo`.
- Produces: ruta `panel/eventos` → `Eventos`.

- [ ] **Step 1: Escribir el test que falla**

`eventos.spec.ts` — monta el componente con `HttpTestingController`, comprueba:

```ts
// - al iniciar hace GET /eventos?pagina=0
// - pinta un botón por evento con su nombre
// - si puedeCrear=true, muestra "Crear evento"; si puedeSolicitar=true, "Solicitar crear evento"
// - con totalPaginas > 1, los controles "Anterior/Siguiente" cambian la página y re-piden
```

Usar el patrón de `miembros.spec.ts` (TestBed con `provideHttpClient()` + `provideHttpClientTesting()` + `provideRouter([])`).

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { Eventos } from './eventos';

describe('Eventos (listado)', () => {
  let fixture: ComponentFixture<Eventos>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Eventos],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(Eventos);
    httpMock = TestBed.inject(HttpTestingController);
  });
  afterEach(() => httpMock.verify());

  function responder(extra: Partial<Record<string, unknown>> = {}) {
    httpMock.expectOne(`${base}/eventos?pagina=0`).flush({
      eventos: [{ id: 1, nombre: 'Migas Santas 2027', fecha: '2027-03-27', fechaFin: null, lugar: null, pasado: false }],
      pagina: 0, totalPaginas: 1, puedeCrear: false, puedeSolicitar: true, ...extra,
    });
  }

  it('carga la primera página y pinta un botón por evento', () => {
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Migas Santas 2027');
    expect(txt).toContain('Solicitar crear evento');
  });

  it('con puedeCrear muestra el botón Crear evento', () => {
    fixture.detectChanges();
    responder({ puedeCrear: true, puedeSolicitar: false });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Crear evento');
  });
});
```

- [ ] **Step 2: Ejecutar y ver fallar**

Run: `npx ng test --no-watch` → FAIL (no existe `Eventos`).

- [ ] **Step 3: Escribir `eventos.ts`**

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CodigoErrorEvento, EventoResumen } from './eventos.types';
import { EventosService } from './eventos.service';

const MENSAJES: Partial<Record<CodigoErrorEvento, string>> = {
  SOLICITUD_EVENTO_YA_PENDIENTE: 'Ya tienes una solicitud de evento pendiente.',
  CREDITO_SIN_CONSUMIR: 'Ya tienes un evento autorizado sin crear. Créalo antes de pedir otro.',
  SOLICITUD_EVENTO_NO_APLICA: 'Como administrador puedes crear eventos directamente.',
};

/**
 * Listado de eventos de la peña. Cada evento es un botón que lleva a su detalle
 * (`/panel/eventos/:id`). Arriba, según los permisos que devuelve el backend:
 * "Crear evento" (`puedeCrear`) o "Solicitar crear evento" (`puedeSolicitar`,
 * abre un diálogo con un mensaje opcional). Paginado de 8 (lo pagina el backend).
 */
@Component({
  selector: 'app-eventos',
  imports: [RouterLink],
  templateUrl: './eventos.html',
  styleUrl: './eventos.css',
})
export class Eventos implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly eventos = signal<EventoResumen[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(1);
  protected readonly puedeCrear = signal(false);
  protected readonly puedeSolicitar = signal(false);

  protected readonly dialogoSolicitud = signal(false);
  protected readonly mensajeSolicitud = signal('');
  protected readonly aviso = signal('');

  ngOnInit(): void {
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.estado.set('cargando');
    this.eventosService.listar(pagina).subscribe({
      next: (r) => {
        this.eventos.set(r.eventos);
        this.pagina.set(r.pagina);
        this.totalPaginas.set(r.totalPaginas);
        this.puedeCrear.set(r.puedeCrear);
        this.puedeSolicitar.set(r.puedeSolicitar);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected anterior(): void {
    if (this.pagina() > 0) this.cargar(this.pagina() - 1);
  }

  protected siguiente(): void {
    if (this.pagina() + 1 < this.totalPaginas()) this.cargar(this.pagina() + 1);
  }

  protected abrir(e: EventoResumen): void {
    this.router.navigate(['/panel/eventos', e.id]);
  }

  protected crear(): void {
    this.router.navigate(['/panel/eventos/nuevo']);
  }

  protected abrirDialogoSolicitud(): void {
    this.mensajeSolicitud.set('');
    this.dialogoSolicitud.set(true);
  }

  protected enviarSolicitud(): void {
    this.eventosService.solicitarCrear(this.mensajeSolicitud() || undefined).subscribe({
      next: () => {
        this.dialogoSolicitud.set(false);
        this.puedeSolicitar.set(false);
        this.aviso.set('Solicitud enviada. Un administrador tiene que autorizarla.');
      },
      error: (e: HttpErrorResponse) => {
        const codigo = e.error?.codigo as CodigoErrorEvento | undefined;
        this.dialogoSolicitud.set(false);
        this.aviso.set((codigo && MENSAJES[codigo]) || 'No se pudo enviar la solicitud.');
        this.cargar(this.pagina());
      },
    });
  }
}
```

- [ ] **Step 4: Escribir `eventos.html`**

Estructura (clases al estilo de `miembros.html`):

```html
<section class="mx-auto w-full max-w-3xl">
  <div class="flex items-center justify-between gap-3">
    <h1 class="font-display text-2xl font-extrabold leading-tight">Eventos</h1>
    @if (puedeCrear()) {
      <button type="button" (click)="crear()"
        class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-gold transition hover:bg-brand-dark">
        Crear evento
      </button>
    } @else if (puedeSolicitar()) {
      <button type="button" (click)="abrirDialogoSolicitud()"
        class="rounded-xl border border-outline bg-surface px-4 py-2 text-sm font-medium text-ink transition hover:border-gold hover:text-gold">
        Solicitar crear evento
      </button>
    }
  </div>

  @if (aviso()) {
    <p role="status" class="mt-4 rounded-xl border border-brand-bright/30 bg-brand/15 px-4 py-3 text-sm text-gold-soft">
      {{ aviso() }}
    </p>
  }

  <div class="mt-6">
    @switch (estado()) {
      @case ('cargando') { <p class="text-sm text-muted">Cargando…</p> }
      @case ('error') {
        <div class="rounded-2xl border border-red-400/30 bg-red-400/10 p-6">
          <p class="text-sm text-red-300">No se han podido cargar los eventos.</p>
          <button type="button" (click)="cargar(pagina())"
            class="mt-4 rounded-xl border border-outline bg-surface px-4 py-2 text-sm font-medium text-ink hover:border-gold hover:text-gold">
            Reintentar
          </button>
        </div>
      }
      @case ('lista') {
        <ul class="flex flex-col gap-3">
          @for (e of eventos(); track e.id) {
            <li>
              <button type="button" (click)="abrir(e)"
                class="carta-relieve flex w-full items-center justify-between gap-4 rounded-2xl px-5 py-4 text-left transition hover:brightness-110"
                [class.opacity-60]="e.pasado">
                <span class="min-w-0">
                  <span class="block truncate font-display text-lg font-bold">{{ e.nombre }}</span>
                  @if (e.lugar) { <span class="block truncate text-xs text-muted">{{ e.lugar }}</span> }
                </span>
                <span class="shrink-0 text-sm text-muted">{{ e.fecha }}</span>
              </button>
            </li>
          } @empty {
            <p class="text-sm text-muted">Todavía no hay eventos.</p>
          }
        </ul>

        @if (totalPaginas() > 1) {
          <div class="mt-6 flex items-center justify-center gap-4 text-sm">
            <button type="button" (click)="anterior()" [disabled]="pagina() === 0"
              class="rounded-lg border border-outline px-3 py-1.5 disabled:opacity-40">Anterior</button>
            <span class="text-muted">{{ pagina() + 1 }} / {{ totalPaginas() }}</span>
            <button type="button" (click)="siguiente()" [disabled]="pagina() + 1 >= totalPaginas()"
              class="rounded-lg border border-outline px-3 py-1.5 disabled:opacity-40">Siguiente</button>
          </div>
        }
      }
    }
  </div>

  @if (dialogoSolicitud()) {
    <div class="fixed inset-0 z-20 flex items-center justify-center bg-black/50 p-4">
      <div class="w-full max-w-md rounded-2xl border border-outline bg-panel p-5">
        <h2 class="font-display text-lg font-bold">Solicitar crear un evento</h2>
        <p class="mt-1 text-sm text-muted">Cuenta brevemente qué evento quieres organizar. Un administrador lo autoriza.</p>
        <textarea rows="3" [value]="mensajeSolicitud()" (input)="mensajeSolicitud.set($any($event.target).value)"
          class="mt-3 w-full rounded-xl border border-outline bg-surface px-3 py-2 text-sm"></textarea>
        <div class="mt-4 flex justify-end gap-2">
          <button type="button" (click)="dialogoSolicitud.set(false)"
            class="rounded-xl border border-outline px-4 py-2 text-sm">Cancelar</button>
          <button type="button" (click)="enviarSolicitud()"
            class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-gold">Enviar</button>
        </div>
      </div>
    </div>
  }
</section>
```

`eventos.css` vacío (o mínimos ajustes).

- [ ] **Step 5: Registrar la ruta y arreglar la navegación**

En `app.routes.ts`, dentro de `children` de `panel` (importar `Eventos`):

```ts
{ path: 'eventos', component: Eventos, canActivate: [perfilCompletoGuard] },
```

En `secciones.ts`, borrar el objeto `{ nombre: 'Eventos', ... }` de `SECCIONES`.

En `panel.html`, añadir tras el enlace de "Miembros", en **las dos** navs (lateral y móvil), copiando el estilo del de Miembros:

```html
<a class="rounded-xl px-4 py-2.5 text-muted transition hover:text-gold"
   routerLink="/panel/eventos" routerLinkActive="bg-brand/15 text-ink">Eventos</a>
```

(en la nav móvil, con las clases `shrink-0 rounded-full px-4 py-1.5 ...` como el resto).

- [ ] **Step 6: Ejecutar y ver pasar**

Run: `npx ng test --no-watch`
Expected: PASS. Revisar además que `panel.spec.ts` sigue verde (comprueba la lista de secciones — puede que haya que quitar `'Eventos'` de una aserción).

- [ ] **Step 7: Commit**

```bash
git add front/src/app/panel/eventos/eventos.ts front/src/app/panel/eventos/eventos.html \
        front/src/app/panel/eventos/eventos.css front/src/app/panel/eventos/eventos.spec.ts \
        front/src/app/app.routes.ts front/src/app/panel/secciones.ts front/src/app/panel/panel.html \
        front/src/app/panel/panel.spec.ts
git commit -m "feat(eventos): listado paginado + enlace real en el nav (web)"
```

---

## Task 3: Detalle de un evento

**Files:**
- Create: `front/src/app/panel/eventos/evento-detalle/evento-detalle.ts` / `.html` / `.css` / `.spec.ts`
- Modify: `front/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `EventosService.detalle`, `EventosService.borrar`; `ActivatedRoute` (`id`), `Router`.
- Produces: ruta `panel/eventos/:id` → `EventoDetalleComponent`.

- [ ] **Step 1: Test que falla** (`evento-detalle.spec.ts`)

```ts
// - GET /eventos/:id al iniciar, pinta nombre / fecha / lugar / descripción / creador
// - si puedoEditar, muestra botón "Gestionar" que navega a /panel/eventos/:id/editar
// - si puedoBorrar y NO es admin (creadoPor presente), el botón dice "Solicitar borrado";
//   al pulsarlo hace DELETE y, con 202, muestra "Solicitud de borrado enviada"
// - si borradoPendiente, el botón de borrado está deshabilitado con un aviso
```

Usar `provideRouter` con una ruta ficticia y `ActivatedRoute` con `{ snapshot: { paramMap: convertToParamMap({ id: '5' }) } }` (o `provideRouter([{ path: 'panel/eventos/:id', component: EventoDetalleComponent }])` y navegar).

- [ ] **Step 2: Escribir `evento-detalle.ts`**

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CodigoErrorEvento, EventoDetalle } from '../eventos.types';
import { EventosService } from '../eventos.service';

/**
 * Vista de un evento (solo lectura). Según los permisos que devuelve el backend
 * muestra:
 *  - "Gestionar" → editor, si `puedoEditar`.
 *  - "Borrar" (admin) o "Solicitar borrado" (creador), si `puedoBorrar`.
 *    El texto sale de si hay `creadoPor` y de si el usuario es el creador; para
 *    simplificar: si `borradoPendiente`, el botón se deshabilita con un aviso.
 */
@Component({
  selector: 'app-evento-detalle',
  imports: [Volver],
  templateUrl: './evento-detalle.html',
  styleUrl: './evento-detalle.css',
})
export class EventoDetalleComponent implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly evento = signal<EventoDetalle | null>(null);
  protected readonly aviso = signal('');
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.eventosService.detalle(this.id).subscribe({
      next: (e) => {
        this.evento.set(e);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected gestionar(): void {
    this.router.navigate(['/panel/eventos', this.id, 'editar']);
  }

  protected borrar(): void {
    this.eventosService.borrar(this.id).subscribe({
      next: (r) => {
        if (r && r.estado === 'PENDIENTE') {
          this.aviso.set('Solicitud de borrado enviada. Un administrador tiene que autorizarla.');
          this.cargar();
        } else {
          this.router.navigate(['/panel/eventos']);
        }
      },
      error: (e: HttpErrorResponse) => {
        const codigo = e.error?.codigo as CodigoErrorEvento | undefined;
        this.aviso.set(
          codigo === 'SOLICITUD_EVENTO_YA_PENDIENTE'
            ? 'Ya hay una solicitud de borrado pendiente para este evento.'
            : 'No se pudo borrar el evento.',
        );
        this.cargar();
      },
    });
  }
}
```

- [ ] **Step 3: Escribir `evento-detalle.html`**

```html
<section class="mx-auto w-full max-w-2xl">
  <div class="mb-4"><app-volver destino="/panel/eventos" /></div>

  @switch (estado()) {
    @case ('cargando') { <p class="text-sm text-muted">Cargando…</p> }
    @case ('error') { <p class="text-sm text-red-300">No se ha podido cargar el evento.</p> }
    @case ('listo') {
      @if (evento(); as e) {
        <article class="carta-relieve rounded-2xl p-6">
          <h1 class="font-display text-2xl font-extrabold" [class.opacity-70]="e.pasado">{{ e.nombre }}</h1>
          <p class="mt-1 text-sm text-muted">
            {{ e.fecha }}@if (e.fechaFin) { &nbsp;–&nbsp;{{ e.fechaFin }} }
            @if (e.pasado) { · <span class="uppercase tracking-wide">pasado</span> }
          </p>
          @if (e.lugar) { <p class="mt-2 text-sm">{{ e.lugar }}</p> }
          @if (e.descripcion) { <p class="mt-4 whitespace-pre-line text-sm leading-relaxed">{{ e.descripcion }}</p> }
          @if (e.creadoPor) { <p class="mt-4 text-xs text-muted">Creado por {{ e.creadoPor.nombre }}</p> }
        </article>

        @if (aviso()) {
          <p role="status" class="mt-4 rounded-xl border border-brand-bright/30 bg-brand/15 px-4 py-3 text-sm text-gold-soft">
            {{ aviso() }}
          </p>
        }

        @if (e.puedoEditar || e.puedoBorrar) {
          <div class="mt-6 flex flex-wrap gap-3">
            @if (e.puedoEditar) {
              <button type="button" (click)="gestionar()"
                class="rounded-xl bg-brand px-4 py-2 text-sm font-bold text-gold hover:bg-brand-dark">Gestionar</button>
            }
            @if (e.puedoBorrar) {
              <button type="button" (click)="borrar()" [disabled]="e.borradoPendiente"
                class="rounded-xl border border-outline px-4 py-2 text-sm font-medium text-ink hover:border-red-400/50 hover:text-red-300 disabled:opacity-50">
                @if (e.borradoPendiente) { Borrado pendiente de autorización }
                @else if (e.creadoPor) { Solicitar borrado }
                @else { Borrar }
              </button>
            }
          </div>
        }
      }
    }
  }
</section>
```

> **Nota:** el texto "Solicitar borrado" vs "Borrar" se decide de forma aproximada por `e.creadoPor != null`. Un admin viendo un evento con creador verá "Solicitar borrado" pero el backend lo borrará directo igualmente (204 → navega a la lista). Es cosmético; si molesta, añadir `esAdmin` al `EventoDetalle` en Plan A. Se deja así por YAGNI.

- [ ] **Step 4: Registrar la ruta**

```ts
{ path: 'eventos/:id', component: EventoDetalleComponent, canActivate: [perfilCompletoGuard] },
```

(colocarla **después** de `eventos/nuevo` cuando exista — ver Task 4 — para que `nuevo` no caiga en `:id`).

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `npx ng test --no-watch` → PASS.

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/eventos/evento-detalle/ front/src/app/app.routes.ts
git commit -m "feat(eventos): pantalla de detalle con gestionar / solicitar borrado (web)"
```

---

## Task 4: Editor de evento (crear y editar)

**Files:**
- Create: `front/src/app/panel/eventos/editor-evento/editor-evento.ts` / `.html` / `.css` / `.spec.ts`
- Modify: `front/src/app/app.routes.ts`

**Interfaces:**
- Consumes: `EventosService.crear`, `EventosService.editar`, `EventosService.detalle`; `ReactiveFormsModule`, `ActivatedRoute`, `Router`.
- Produces: rutas `panel/eventos/nuevo` y `panel/eventos/:id/editar` → `EditorEvento`.

- [ ] **Step 1: Test que falla** (`editor-evento.spec.ts`)

```ts
// modo crear (ruta sin id):
//   - formulario vacío; "Guardar" deshabilitado si nombre o fecha vacíos
//   - al enviar: POST /eventos con { nombre, descripcion, lugar, fecha, fechaFin }
//   - 409 SIN_CREDITO_EVENTO → muestra mensaje
// modo editar (ruta con id):
//   - GET /eventos/:id, rellena el formulario
//   - al enviar: PUT /eventos/:id
```

- [ ] **Step 2: Escribir `editor-evento.ts`**

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CodigoErrorEvento, GuardarEventoRequest } from '../eventos.types';
import { EventosService } from '../eventos.service';

const MENSAJES: Partial<Record<CodigoErrorEvento, string>> = {
  SIN_CREDITO_EVENTO: 'No tienes ningún evento autorizado sin crear.',
  SIN_PERMISO_EVENTO: 'No puedes editar este evento.',
  EVENTO_NO_ENCONTRADO: 'Ese evento ya no existe.',
};

/**
 * Editor de evento. Sin `id` en la ruta → crear (`POST`); con `id` → editar
 * (`PUT`, precarga con `GET /eventos/:id`). Campos: nombre, descripción, lugar,
 * fecha (obligatoria), fecha fin (opcional). La validación de "fecha fin no
 * anterior a fecha" también la hace el backend.
 */
@Component({
  selector: 'app-editor-evento',
  imports: [ReactiveFormsModule, Volver],
  templateUrl: './editor-evento.html',
  styleUrl: './editor-evento.css',
})
export class EditorEvento implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly eventosService = inject(EventosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly idParam = this.route.snapshot.paramMap.get('id');
  protected readonly editando = this.idParam !== null;
  protected readonly guardando = signal(false);
  protected readonly error = signal('');

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', Validators.maxLength(2000)],
    lugar: ['', Validators.maxLength(160)],
    fecha: ['', Validators.required],
    fechaFin: [''],
  });

  ngOnInit(): void {
    if (this.editando) {
      this.eventosService.detalle(Number(this.idParam)).subscribe({
        next: (e) => this.form.patchValue({
          nombre: e.nombre,
          descripcion: e.descripcion ?? '',
          lugar: e.lugar ?? '',
          fecha: e.fecha,
          fechaFin: e.fechaFin ?? '',
        }),
        error: () => this.error.set('No se ha podido cargar el evento.'),
      });
    }
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: GuardarEventoRequest = {
      nombre: v.nombre.trim(),
      descripcion: v.descripcion.trim() || null,
      lugar: v.lugar.trim() || null,
      fecha: v.fecha,
      fechaFin: v.fechaFin || null,
    };
    if (body.fechaFin && body.fechaFin < body.fecha) {
      this.error.set('La fecha de fin no puede ser anterior a la de inicio.');
      return;
    }
    this.guardando.set(true);
    const peticion = this.editando
      ? this.eventosService.editar(Number(this.idParam), body)
      : this.eventosService.crear(body);
    peticion.subscribe({
      next: (e) => this.router.navigate(['/panel/eventos', e.id]),
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        const codigo = err.error?.codigo as CodigoErrorEvento | undefined;
        this.error.set((codigo && MENSAJES[codigo]) || 'No se pudo guardar el evento.');
      },
    });
  }
}
```

- [ ] **Step 3: Escribir `editor-evento.html`**

Formulario `[formGroup]="form"` con inputs `formControlName` para nombre (text), descripción (textarea), lugar (text), fecha (`type="date"`), fechaFin (`type="date"`). Título "Nuevo evento" / "Editar evento" según `editando`. Botón Guardar `[disabled]="form.invalid || guardando()"`. Banda de error con `@if (error())`. `<app-volver [destino]="editando ? '/panel/eventos/' + idParam : '/panel/eventos'" />` — para simplificar, `destino="/panel/eventos"` siempre.

- [ ] **Step 4: Registrar las rutas** (orden importa: `nuevo` y `:id/editar` antes de `:id`)

```ts
{ path: 'eventos/nuevo', component: EditorEvento, canActivate: [perfilCompletoGuard] },
{ path: 'eventos/:id/editar', component: EditorEvento, canActivate: [perfilCompletoGuard] },
{ path: 'eventos/:id', component: EventoDetalleComponent, canActivate: [perfilCompletoGuard] },
```

(y `eventos` de Task 2 antes de todas ellas).

- [ ] **Step 5: Ejecutar y ver pasar**

Run: `npx ng test --no-watch` → PASS.

- [ ] **Step 6: Commit**

```bash
git add front/src/app/panel/eventos/editor-evento/ front/src/app/app.routes.ts
git commit -m "feat(eventos): editor de crear/editar evento (web)"
```

---

## Task 5: Bloque de administración — solicitudes de evento

**Files:**
- Modify: `front/src/app/admin/admin.service.ts`, `front/src/app/admin/admin.types.ts`
- Create: `front/src/app/admin/solicitudes-evento/solicitudes-evento.ts` / `.html` / `.css` / `.spec.ts`
- Modify: `front/src/app/admin/solicitudes/solicitudes.ts`, `front/src/app/admin/solicitudes/solicitudes.html`
- Modify: `front/src/app/admin/admin.service.spec.ts` (3 casos)

**Interfaces:**
- Consumes: `AdminService` (métodos nuevos), `AuthService.usuarioActual()` (para el gate admin).
- Produces:
  - `admin.types.ts`: `SolicitudEventoResumen { id; tipo: 'CREAR'|'BORRAR'; estado: string; solicitante: { id; nombre; apellidos }; evento: { id; nombre; fecha } | null; mensaje: string | null; createdAt: string }`.
  - `AdminService.listarSolicitudesEvento(estado?)`, `.aprobarSolicitudEvento(id)`, `.rechazarSolicitudEvento(id, motivo?)`.
  - Componente `<app-solicitudes-evento>` (selector), autónomo: carga y pinta su propia lista.

- [ ] **Step 1: Añadir tipo y métodos + tests que fallan**

En `admin.types.ts`:

```ts
/** Fila del bloque "Solicitudes de evento" (solo la ven admin/superadmin). */
export interface SolicitudEventoResumen {
  id: number;
  tipo: 'CREAR' | 'BORRAR';
  estado: string;
  solicitante: { id: number; nombre: string; apellidos: string };
  evento: { id: number; nombre: string; fecha: string } | null;
  mensaje: string | null;
  createdAt: string;
}
```

En `admin.service.ts`:

```ts
  listarSolicitudesEvento(estado = 'PENDIENTE'): Observable<SolicitudEventoResumen[]> {
    return this.http.get<SolicitudEventoResumen[]>(`${this.base}/admin/solicitudes-evento`, {
      params: { estado },
    });
  }

  aprobarSolicitudEvento(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes-evento/${id}/aprobar`, {});
  }

  rechazarSolicitudEvento(id: number, motivo?: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/admin/solicitudes-evento/${id}/rechazar`,
      motivo ? { motivo } : {},
    );
  }
```

En `admin.service.spec.ts`, 3 casos nuevos (patrón de los existentes):

```ts
it('listarSolicitudesEvento() hace GET a /admin/solicitudes-evento', () => {
  service.listarSolicitudesEvento().subscribe();
  const req = httpMock.expectOne((r) => r.url === `${base}/admin/solicitudes-evento`);
  expect(req.request.method).toBe('GET');
  expect(req.request.params.get('estado')).toBe('PENDIENTE');
  req.flush([]);
});

it('aprobarSolicitudEvento() hace POST a /admin/solicitudes-evento/:id/aprobar', () => {
  service.aprobarSolicitudEvento(3).subscribe();
  const req = httpMock.expectOne(`${base}/admin/solicitudes-evento/3/aprobar`);
  expect(req.request.method).toBe('POST');
  req.flush(null, { status: 204, statusText: 'No Content' });
});

it('rechazarSolicitudEvento() hace POST con el motivo', () => {
  service.rechazarSolicitudEvento(3, 'tarde').subscribe();
  const req = httpMock.expectOne(`${base}/admin/solicitudes-evento/3/rechazar`);
  expect(req.request.body).toEqual({ motivo: 'tarde' });
  req.flush(null, { status: 204, statusText: 'No Content' });
});
```

- [ ] **Step 2: Escribir `solicitudes-evento.ts`**

```ts
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { CodigoError } from '../../auth/auth.types';
import { AdminService } from '../admin.service';
import { SolicitudEventoResumen } from '../admin.types';

/**
 * Bloque "Solicitudes de evento" que se embebe en la pantalla de solicitudes de
 * ingreso. Solo lo monta el contenedor si el usuario es admin/superadmin (el
 * backend además lo exige: 403 SIN_PERMISO si no). Lista las de tipo CREAR y
 * BORRAR pendientes y deja aprobar / rechazar (con motivo opcional).
 */
@Component({
  selector: 'app-solicitudes-evento',
  templateUrl: './solicitudes-evento.html',
  styleUrl: './solicitudes-evento.css',
})
export class SolicitudesEvento implements OnInit {
  private readonly adminService = inject(AdminService);

  protected readonly solicitudes = signal<SolicitudEventoResumen[]>([]);
  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly mensaje = signal('');
  protected readonly rechazandoId = signal<number | null>(null);
  protected readonly motivo = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.adminService.listarSolicitudesEvento().subscribe({
      next: (l) => {
        this.solicitudes.set(l);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected aprobar(s: SolicitudEventoResumen): void {
    this.adminService.aprobarSolicitudEvento(s.id).subscribe({
      next: () => {
        this.mensaje.set(s.tipo === 'CREAR' ? 'Crédito concedido.' : 'Evento borrado.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.avisar(e),
    });
  }

  protected abrirRechazo(id: number): void {
    this.rechazandoId.set(id);
    this.motivo.set('');
  }

  protected confirmarRechazo(s: SolicitudEventoResumen): void {
    this.adminService.rechazarSolicitudEvento(s.id, this.motivo() || undefined).subscribe({
      next: () => {
        this.rechazandoId.set(null);
        this.mensaje.set('Solicitud rechazada.');
        this.cargar();
      },
      error: (e: HttpErrorResponse) => this.avisar(e),
    });
  }

  private avisar(e: HttpErrorResponse): void {
    const codigo = e.error?.codigo as CodigoError | 'SOLICITUD_EVENTO_YA_RESUELTA' | undefined;
    if (codigo === 'SOLICITUD_EVENTO_YA_RESUELTA') {
      this.mensaje.set('Esa solicitud ya la resolvió alguien. Recargo la lista.');
      this.cargar();
      return;
    }
    this.mensaje.set(codigo === 'SIN_PERMISO' ? 'No tienes permiso.' : 'No se pudo completar la acción.');
  }
}
```

- [ ] **Step 3: Escribir `solicitudes-evento.html`**

Lista con una tarjeta por solicitud (estilo de `solicitudes.html`): "**{tipo}** — {solicitante.nombre} {solicitante.apellidos}", si `evento` → "«{evento.nombre}» ({evento.fecha})", `mensaje` si lo hay, y botones Aprobar / Rechazar (+ textarea de motivo cuando `rechazandoId() === s.id`). `@empty` → "No hay solicitudes de evento.". Cabecera `<h2>Solicitudes de evento</h2>` y separador respecto al bloque de ingreso.

- [ ] **Step 4: Montarlo en la pantalla de solicitudes**

En `solicitudes.ts`: `imports: [Volver, SolicitudesEvento]`, e inyectar `AuthService` como `protected readonly auth = inject(AuthService)`. Añadir un getter:

```ts
protected readonly esAdmin = computed(() =>
  this.auth.usuarioActual()?.rol === 'ADMIN' || this.auth.usuarioActual()?.esSuperadmin === true);
```

En `solicitudes.html`, al final del `<section>`:

```html
@if (esAdmin()) {
  <div class="mt-10 border-t border-outline pt-8">
    <app-solicitudes-evento />
  </div>
}
```

- [ ] **Step 5: Test del componente** (`solicitudes-evento.spec.ts`)

```ts
// - GET /admin/solicitudes-evento al iniciar; pinta tipo + solicitante
// - "Aprobar" → POST .../aprobar y recarga
// - "Rechazar" abre textarea; "Confirmar" → POST .../rechazar con { motivo }
```

- [ ] **Step 6: Ejecutar y ver pasar**

Run: `npx ng test --no-watch`
Expected: PASS. Revisar que `solicitudes.spec.ts` sigue verde (ahora inyecta `AuthService`; puede necesitar `provideHttpClient` ya presente).

- [ ] **Step 7: Commit**

```bash
git add front/src/app/admin/
git commit -m "feat(eventos): bloque de solicitudes de evento en el panel de administración (web)"
```

---

## Self-Review

**Spec coverage (sección "Web"):**
- `eventos.ts/html` listado, cada evento un botón → detalle, paginado 8, "Crear evento" (`puedeCrear`) / "Solicitar crear evento" (`puedeSolicitar`, diálogo con `mensaje`) → Task 2.
- `evento-detalle/` vista + "Gestionar" (`puedoEditar`) + "Borrar"/"Solicitar borrado" (`puedoBorrar`, deshabilitado si `borradoPendiente`) → Task 3.
- `editor-evento/` crear (`POST`) / editar (`PUT`), campos nombre/descripción/lugar/fecha/fechaFin, validación reflejada → Task 4. Maneja `SIN_CREDITO_EVENTO`.
- `eventos.service.ts` + `eventos.types.ts` contra `/api/v1/eventos` → Task 1.
- Rutas hijas de `panel` con `perfilCompletoGuard` (`eventos`, `eventos/nuevo`, `eventos/:id`, `eventos/:id/editar`) → Tasks 2–4, orden correcto (`nuevo`/`:id/editar` antes de `:id`).
- `secciones.ts` quita `Eventos` de "próximamente"; `panel.html` añade el enlace real en las dos navs → Task 2.
- `admin/solicitudes/` con bloque "Solicitudes de evento" visible solo si admin/superadmin → Task 5.
- Tests `*.spec.ts` de cada componente y servicio → una tarea cada uno.

**Type consistency:** `EventoResumen`/`EventoDetalle`/`ListaEventosResponse`/`GuardarEventoRequest`/`CodigoErrorEvento` (Task 1) usados igual en Tasks 2–4. `SolicitudEventoResumen` (Task 5) coincide con el DTO del backend Plan A Task 8 (`id, tipo, estado, solicitante{id,nombre,apellidos}, evento{id,nombre,fecha}|null, mensaje, createdAt`). `EventosService` métodos: `listar/detalle/crear/editar/borrar/solicitarCrear` — mismos nombres en el spec (Task 1) y en los componentes.

**Placeholder scan:** `evento-detalle.html` decide "Solicitar borrado" vs "Borrar" por `creadoPor != null` — decisión consciente y anotada (alternativa: añadir `esAdmin` al DTO; se descarta por YAGNI). `editor-evento.html` se describe en prosa con la lista exacta de campos y bindings, sin código HTML completo — es un formulario estándar `[formGroup]`; aceptable. El resto lleva código real.
