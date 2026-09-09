import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../shared/volver/volver';
import { InventarioService } from './inventario.service';
import {
  ArticuloInventario,
  CATEGORIAS,
  CategoriaInventario,
  OpcionCategoria,
} from './inventario.types';

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

  protected trackArt = (_: number, a: ArticuloInventario) => a.id;
}
