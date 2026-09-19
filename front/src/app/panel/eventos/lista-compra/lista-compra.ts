import { DecimalPipe } from '@angular/common';
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
  imports: [Volver, RouterLink, DecimalPipe],
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

  protected empezarEdicion(l: LineaCompra): void {
    this.editandoId.set(l.id);
    this.cantidadEditada.set(l.cantidad);
  }

  protected cancelarEdicion(): void {
    this.editandoId.set(null);
    this.cantidadEditada.set(null);
  }

  protected guardarEdicion(l: LineaCompra): void {
    const cantidad = this.cantidadEditada();
    if (this.ocupado() || cantidad === null || cantidad < 0) return;
    this.ocupado.set(true);
    this.service.ajustarLinea(this.eventoId, l.id, cantidad).subscribe({
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

  /** Suma cantidad × precio unitario de las líneas de la categoría que tengan precio. */
  protected totalEstimado(cat: CategoriaListaCompra): number {
    return cat.lineas.reduce(
      (total, l) => total + (l.precioUnitario != null ? l.cantidad * l.precioUnitario : 0),
      0,
    );
  }

  protected trackCat = (_: number, c: CategoriaListaCompra) => c.categoria;
}
