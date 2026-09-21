import { DecimalPipe, LowerCasePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { ListaCompraService } from './lista-compra.service';
import { CategoriaListaCompra, LineaCompra } from './lista-compra.types';

/**
 * Lista de la compra calculada de un evento, `/panel/eventos/:id/lista-compra`.
 * La ve cualquier peñista; el admin (área INVENTARIO) puede además marcar
 * "comprado" y, con la lista bloqueada, ajustar a mano la cantidad final de
 * una línea (p.ej. cuando ya sabe que el stock parcial que queda alcanza).
 */
@Component({
  selector: 'app-lista-compra',
  imports: [Volver, RouterLink, DecimalPipe, LowerCasePipe],
  templateUrl: './lista-compra.html',
  styleUrl: './lista-compra.css',
})
export class ListaCompra implements OnInit {
  private readonly service = inject(ListaCompraService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly apuntados = signal(0);
  protected readonly diasFiesta = signal(0);
  protected readonly categorias = signal<CategoriaListaCompra[]>([]);
  protected readonly puedoEditar = signal(false);
  protected readonly bloqueada = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly tamanoEditado = signal('');
  /** Qué se está ajustando de la línea abierta: el tamaño de la botella o la cantidad (dos botones distintos). */
  protected readonly modoEdicion = signal<'tamano' | 'cantidad' | 'tienda'>('cantidad');
  protected readonly tiendaEditada = signal('');
  /** Tamaños entre los que se puede cambiar una botella cuando la marca aún no tiene precios. */
  protected readonly TAMANOS_BASE = ['70 cl', '1 L'];
  protected readonly cantidadEditada = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.lista(this.eventoId).subscribe({
      next: (d) => {
        this.apuntados.set(d.apuntados);
        this.diasFiesta.set(d.diasFiesta);
        this.categorias.set(d.categorias);
        this.presupuesto.set(d.presupuesto ?? null);
        this.puedoEditar.set(d.puedoEditar);
        this.bloqueada.set(d.bloqueada);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected comprar(l: LineaCompra): void {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.service.comprado(this.eventoId, l.id).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cargar();
      },
      error: () => {
        this.ocupado.set(false);
        this.estado.set('error');
      },
    });
  }

  protected alternarBloqueo(): void {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.service.bloqueo(this.eventoId, !this.bloqueada()).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cargar();
      },
      error: () => {
        this.ocupado.set(false);
        this.estado.set('error');
      },
    });
  }

  /** Hay alguna línea modificada a mano (para ofrecer el restablecer general). */
  protected hayModificadas(): boolean {
    return this.categorias().some((c) => c.lineas.some((l) => l.modificada));
  }

  protected restablecerLinea(l: LineaCompra): void {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.service.restablecerLinea(this.eventoId, l.id).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cancelarEdicion();
        this.cargar();
      },
      error: () => {
        this.ocupado.set(false);
        this.estado.set('error');
      },
    });
  }

  protected restablecerTodo(): void {
    if (this.ocupado() || !confirm('¿Quitar todas tus modificaciones y volver al cálculo automático?')) return;
    this.ocupado.set(true);
    this.service.restablecerTodo(this.eventoId).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cancelarEdicion();
        this.cargar();
      },
      error: () => {
        this.ocupado.set(false);
        this.estado.set('error');
      },
    });
  }

  /** El menor precio por litro entre los tamaños de una línea de bebida (para marcar el más barato). */
  protected menorPrecioLitro(l: LineaCompra): number | null {
    const precios = l.info?.tamanos?.map((o) => o.precioLitro) ?? [];
    return precios.length > 0 ? Math.min(...precios) : null;
  }

  /** Con la lista bloqueada, el admin puede ajustar las líneas aún sin comprar. */
  protected puedeAjustar(l: LineaCompra): boolean {
    return this.bloqueada() && this.puedoEditar() && !l.comprada;
  }

  /** Pulsar la cantidad abre (o cierra) el desplegable de ajuste de esa línea. */
  protected alternarEdicion(l: LineaCompra, modo: 'tamano' | 'cantidad' | 'tienda'): void {
    if (this.editandoId() === l.id && this.modoEdicion() === modo) {
      this.cancelarEdicion();
    } else {
      this.empezarEdicion(l);
      this.modoEdicion.set(modo);
    }
  }

  protected empezarEdicion(l: LineaCompra): void {
    this.editandoId.set(l.id);
    this.cantidadEditada.set(l.cantidad);
    this.tamanoEditado.set(l.tamano);
    this.tiendaEditada.set(l.tienda ?? '');
  }

  /** Botones − / + del desplegable de cantidad. */
  protected sumarEdicion(delta: number): void {
    const actual = this.cantidadEditada() ?? 0;
    this.cantidadEditada.set(Math.max(0, actual + delta));
  }

  protected cancelarEdicion(): void {
    this.editandoId.set(null);
    this.cantidadEditada.set(null);
  }

  protected guardarEdicion(l: LineaCompra): void {
    if (this.modoEdicion() === 'tienda') {
      this.guardarTienda(l);
      return;
    }
    // Cada botón ajusta lo suyo: el de tamaño no toca la cantidad y el de cantidad no toca el tamaño.
    const soloTamano = this.modoEdicion() === 'tamano';
    const cantidad = soloTamano ? l.cantidad : this.cantidadEditada();
    if (this.ocupado() || cantidad === null || Number.isNaN(cantidad) || cantidad < 0) return;
    const tamano = soloTamano ? this.tamanoEditado() || l.tamano : l.tamano;
    if (cantidad === l.cantidad && tamano === l.tamano) {
      // No ha cambiado nada: se cierra sin marcar la línea como ajustada.
      this.cancelarEdicion();
      return;
    }
    this.ocupado.set(true);
    this.service.ajustarLinea(this.eventoId, l.id, cantidad, tamano !== l.tamano ? tamano : undefined).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cancelarEdicion();
        this.cargar();
      },
      error: () => {
        this.ocupado.set(false);
        this.estado.set('error');
      },
    });
  }

  /** Cambia dónde se compra la línea (el precio pasa a ser el de esa tienda). */
  private guardarTienda(l: LineaCompra): void {
    const tienda = this.tiendaEditada();
    if (this.ocupado()) return;
    if (!tienda || tienda === l.tienda) {
      this.cancelarEdicion();
      return;
    }
    this.ocupado.set(true);
    this.service.ajustarLinea(this.eventoId, l.id, l.cantidad, undefined, tienda).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cancelarEdicion();
        this.cargar();
      },
      error: () => {
        this.ocupado.set(false);
        this.estado.set('error');
      },
    });
  }

  /** Si alguna línea de la categoría tiene precio, se pintan las columnas de precio/total. */
  protected tienePrecios(cat: CategoriaListaCompra): boolean {
    return cat.lineas.some((l) => l.precioUnitario != null);
  }

  /** Suma cantidad × precio unitario de las líneas de la categoría que tengan precio. */
  protected totalEstimado(cat: CategoriaListaCompra): number {
    return cat.lineas.reduce(
      (total, l) => total + (l.precioUnitario != null ? l.cantidad * l.precioUnitario : 0),
      0,
    );
  }

  /** Suma del gasto estimado de todas las categorías. */
  protected totalGeneral(): number {
    return this.categorias().reduce((total, cat) => total + this.totalEstimado(cat), 0);
  }

  protected hayPrecios(): boolean {
    return this.categorias().some((cat) => this.tienePrecios(cat));
  }

  /** Líneas que hay que comprar pero aún no tienen precio (no entran en el total). */
  protected lineasSinPrecio(): number {
    return this.categorias().reduce(
      (n, cat) => n + cat.lineas.filter((l) => l.cantidad > 0 && l.precioUnitario == null && !l.necesitaFicha).length,
      0,
    );
  }

  protected readonly presupuesto = signal<number | null>(null);

  /** Lo que queda del presupuesto tras el gasto estimado (negativo = nos pasamos). */
  protected restante(): number {
    return (this.presupuesto() ?? 0) - this.totalGeneral();
  }

  protected trackCat = (_: number, c: CategoriaListaCompra) => c.categoria;
}
