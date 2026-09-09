import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InventarioService } from './inventario.service';
import { ArticuloInventario, CategoriaInventario, InventarioResponse } from './inventario.types';

type Borrador = Record<number, { nombre: string; tamano: string; cantidad: number }>;

/**
 * Sección Inventario: lo que la peña tiene almacenado, en cinco categorías.
 * Cualquier peñista lo ve; si el backend dice `puedoEditar`, aparece el botón
 * "Editar" que convierte las filas en campos (nombre, tamaño de la lista de la
 * categoría, cantidad). "Guardar" manda un PUT por cada fila que haya cambiado.
 *
 * Estado en signals:
 * - `estado`: 'cargando' | 'listo' | 'error' — controla el @switch de la plantilla.
 * - `datos`: la respuesta del backend tal cual (categorías + puedoEditar).
 * - `editando`: `true` mientras se está en modo edición.
 * - `borrador`: mapa id -> {nombre, tamano, cantidad} con los valores que se están tocando.
 * - `guardando`: bloquea el botón mientras van los PUT.
 * - `aviso`: banda de texto arriba (error de guardado, etc.).
 */
@Component({
  selector: 'app-inventario',
  imports: [FormsModule],
  templateUrl: './inventario.html',
  styleUrl: './inventario.css',
})
export class Inventario implements OnInit {
  private readonly inventarioService = inject(InventarioService);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly datos = signal<InventarioResponse | null>(null);
  protected readonly editando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly aviso = signal('');
  protected readonly borrador = signal<Borrador>({});

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.inventarioService.ver().subscribe({
      next: (datos) => {
        this.datos.set(datos);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected editar(): void {
    const datos = this.datos();
    if (!datos) return;
    const inicial: Borrador = {};
    for (const cat of datos.categorias) {
      for (const a of cat.articulos) {
        inicial[a.id] = { nombre: a.nombre, tamano: a.tamano, cantidad: a.cantidad };
      }
    }
    this.borrador.set(inicial);
    this.aviso.set('');
    this.editando.set(true);
  }

  protected cancelar(): void {
    this.editando.set(false);
    this.borrador.set({});
  }

  /** Actualiza un campo del borrador de una fila. */
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

  /** Filas cuyo borrador difiere del valor cargado. */
  private cambiadas(): ArticuloInventario[] {
    const datos = this.datos();
    const b = this.borrador();
    if (!datos) return [];
    const res: ArticuloInventario[] = [];
    for (const cat of datos.categorias) {
      for (const a of cat.articulos) {
        const d = b[a.id];
        if (d && (d.nombre !== a.nombre || d.tamano !== a.tamano || d.cantidad !== a.cantidad)) {
          res.push(a);
        }
      }
    }
    return res;
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
      this.aviso.set('Algún cambio no se pudo guardar. Recargo el inventario.');
    }
    this.cargar();
  }

  protected trackCat = (_: number, c: CategoriaInventario) => c.categoria;
  protected trackArt = (_: number, a: ArticuloInventario) => a.id;
}
