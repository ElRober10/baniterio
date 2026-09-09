import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../shared/volver/volver';
import { InventarioService } from './inventario.service';
import {
  ArticuloInventario,
  CATEGORIAS,
  CategoriaInventario,
  EventoAbierto,
  OpcionCategoria,
} from './inventario.types';

/** Valor del desplegable de nombres que activa el input de "nombre nuevo". */
const NOMBRE_NUEVO = '__nuevo__';

type Borrador = Record<number, { nombre: string; tamano: string; cantidad: number }>;

/**
 * Listado de UNA categoría del inventario (`/panel/inventario/:categoria`). El
 * `:categoria` es el slug (alcohol, cerveza, …); si no cuadra con ninguno,
 * `estado` queda en 'error'.
 *
 * Pide el inventario entero (`GET /api/v1/inventario`) y se queda con su
 * categoría. Si el backend dice `puedoEditar`, aparece "Editar": convierte las
 * filas en campos y "Guardar" manda un PUT por cada fila cambiada de ESTA
 * categoría.
 */
@Component({
  selector: 'app-inventario-categoria',
  imports: [FormsModule, Volver],
  templateUrl: './inventario-categoria.html',
  styleUrl: './inventario.css',
})
export class InventarioCategoria implements OnInit {
  private readonly inventarioService = inject(InventarioService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly opcion = signal<OpcionCategoria | null>(null);
  protected readonly categoria = signal<CategoriaInventario | null>(null);
  protected readonly puedoEditar = signal(false);
  protected readonly editando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly aviso = signal('');
  protected readonly borrador = signal<Borrador>({});

  // Modal "Añadir artículo".
  protected readonly NOMBRE_NUEVO = NOMBRE_NUEVO;
  protected readonly modalAbierto = signal(false);
  protected readonly creando = signal(false);
  protected readonly nuevoNombreSel = signal('');
  protected readonly nuevoNombreTexto = signal('');
  protected readonly nuevoTamano = signal('');
  protected readonly nuevaCantidad = signal('');

  // Modal "Enviar a evento". `enviarObjetivo` es un artículo concreto o 'todo'
  // (toda la categoría).
  protected readonly enviarAbierto = signal(false);
  protected readonly enviarObjetivo = signal<ArticuloInventario | 'todo' | null>(null);
  protected readonly eventosAbiertos = signal<EventoAbierto[]>([]);
  protected readonly eventoSel = signal<number | null>(null);
  protected readonly enviando = signal(false);

  ngOnInit(): void {
    const slug = this.ruta.snapshot.paramMap.get('categoria');
    const opcion = CATEGORIAS.find((c) => c.slug === slug) ?? null;
    this.opcion.set(opcion);
    if (!opcion) {
      this.estado.set('error');
      return;
    }
    this.cargar();
  }

  protected cargar(): void {
    const opcion = this.opcion();
    if (!opcion) return;
    this.estado.set('cargando');
    this.inventarioService.ver().subscribe({
      next: (datos) => {
        const cat = datos.categorias.find((c) => c.categoria === opcion.clave) ?? null;
        this.categoria.set(cat);
        this.puedoEditar.set(datos.puedoEditar);
        this.estado.set(cat ? 'listo' : 'error');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected editar(): void {
    const cat = this.categoria();
    if (!cat) return;
    const inicial: Borrador = {};
    for (const a of cat.articulos) {
      inicial[a.id] = { nombre: a.nombre, tamano: a.tamano, cantidad: a.cantidad };
    }
    this.borrador.set(inicial);
    this.aviso.set('');
    this.editando.set(true);
  }

  protected cancelar(): void {
    this.editando.set(false);
    this.borrador.set({});
  }

  protected cambiar(id: number, campo: 'nombre' | 'tamano' | 'cantidad', valor: string): void {
    const actual = this.borrador();
    const fila = { ...actual[id] };
    if (campo === 'cantidad') {
      fila.cantidad = Number(valor);
    } else {
      fila[campo] = valor;
    }
    this.borrador.set({ ...actual, [id]: fila });
  }

  private cambiadas(): ArticuloInventario[] {
    const cat = this.categoria();
    const b = this.borrador();
    if (!cat) return [];
    return cat.articulos.filter((a) => {
      const d = b[a.id];
      return d && (d.nombre !== a.nombre || d.tamano !== a.tamano || d.cantidad !== a.cantidad);
    });
  }

  protected guardar(): void {
    const b = this.borrador();
    const pendientes = this.cambiadas();
    if (pendientes.length === 0) {
      this.cancelar();
      return;
    }
    this.guardando.set(true);
    this.aviso.set('');
    let restantes = pendientes.length;
    let huboError = false;
    for (const a of pendientes) {
      this.inventarioService.actualizar(a.id, b[a.id]).subscribe({
        next: () => {
          restantes -= 1;
          if (restantes === 0) this.terminarGuardado(huboError);
        },
        error: () => {
          huboError = true;
          restantes -= 1;
          if (restantes === 0) this.terminarGuardado(huboError);
        },
      });
    }
  }

  private terminarGuardado(huboError: boolean): void {
    this.guardando.set(false);
    this.editando.set(false);
    this.borrador.set({});
    if (huboError) {
      this.aviso.set('Algún cambio no se pudo guardar. Recargo el listado.');
    }
    this.cargar();
  }

  /** Da de baja un artículo (papelera, solo en modo edición). Pide confirmación. */
  protected borrar(a: ArticuloInventario): void {
    if (!confirm(`¿Quitar "${a.nombre}" del inventario?`)) return;
    this.guardando.set(true);
    this.aviso.set('');
    this.inventarioService.borrar(a.id).subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(false);
        this.borrador.set({});
        this.cargar();
      },
      error: () => {
        this.guardando.set(false);
        this.aviso.set('No se pudo quitar el artículo.');
      },
    });
  }

  /** Abre el modal de "enviar a evento" para un artículo o para toda la categoría. */
  protected abrirEnviar(objetivo: ArticuloInventario | 'todo'): void {
    this.enviarObjetivo.set(objetivo);
    this.aviso.set('');
    this.eventoSel.set(null);
    this.enviarAbierto.set(true);
    if (this.eventosAbiertos().length === 0) {
      this.inventarioService.eventosAbiertos().subscribe({
        next: (evs) => this.eventosAbiertos.set(evs),
        error: () => this.aviso.set('No se pudieron cargar los eventos.'),
      });
    }
  }

  protected cerrarEnviar(): void {
    this.enviarAbierto.set(false);
    this.enviarObjetivo.set(null);
  }

  /** Texto del artículo objetivo (nombre · tamaño), vacío si el objetivo es 'todo'. */
  protected objetivoNombre(): string {
    const o = this.enviarObjetivo();
    return o && o !== 'todo' ? `${o.nombre} · ${o.tamano}` : '';
  }

  protected confirmarEnviar(): void {
    const eventoId = this.eventoSel();
    const objetivo = this.enviarObjetivo();
    const opcion = this.opcion();
    if (eventoId == null || objetivo == null || !opcion) return;
    this.enviando.set(true);
    const obs =
      objetivo === 'todo'
        ? this.inventarioService.enviarCategoria(opcion.clave, eventoId)
        : this.inventarioService.enviarAEvento(objetivo.id, eventoId);
    const nombreEvento = this.eventosAbiertos().find((e) => e.id === eventoId)?.nombre ?? 'el evento';
    obs.subscribe({
      next: () => {
        this.enviando.set(false);
        this.enviarAbierto.set(false);
        this.enviarObjetivo.set(null);
        this.aviso.set(`Enviado a «${nombreEvento}».`);
        this.cargar();
      },
      error: () => {
        this.enviando.set(false);
        this.aviso.set('No se pudo enviar.');
      },
    });
  }

  protected trackEvento = (_: number, e: EventoAbierto) => e.id;

  /** Nombres ya usados en esta categoría, sin repetir y ordenados, para el desplegable. */
  protected nombresExistentes(): string[] {
    const cat = this.categoria();
    if (!cat) return [];
    return [...new Set(cat.articulos.map((a) => a.nombre))].sort((x, y) => x.localeCompare(y));
  }

  protected abrirModal(): void {
    this.nuevoNombreSel.set('');
    this.nuevoNombreTexto.set('');
    this.nuevoTamano.set(this.categoria()?.tamanos[0] ?? '');
    this.nuevaCantidad.set('');
    this.aviso.set('');
    this.modalAbierto.set(true);
  }

  protected cerrarModal(): void {
    this.modalAbierto.set(false);
  }

  protected crearArticulo(): void {
    const opcion = this.opcion();
    if (!opcion) return;
    const nombre = (
      this.nuevoNombreSel() === NOMBRE_NUEVO ? this.nuevoNombreTexto() : this.nuevoNombreSel()
    ).trim();
    const tamano = this.nuevoTamano();
    const cantidad = Number(this.nuevaCantidad());
    if (!nombre || !tamano || this.nuevaCantidad() === '' || Number.isNaN(cantidad) || cantidad < 0) {
      this.aviso.set('Elige o escribe un nombre, un tamaño y una cantidad.');
      return;
    }
    this.creando.set(true);
    this.inventarioService
      .crear({ categoria: opcion.clave, nombre, tamano, cantidad })
      .subscribe({
        next: () => {
          this.creando.set(false);
          this.modalAbierto.set(false);
          this.cargar();
        },
        error: () => {
          this.creando.set(false);
          this.aviso.set('No se pudo añadir el artículo.');
        },
      });
  }

  protected trackArt = (_: number, a: ArticuloInventario) => a.id;
}
