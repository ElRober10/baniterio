import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { ListaCompraService } from './lista-compra.service';
import { CategoriaListaCompra, LineaCompra } from './lista-compra.types';

/**
 * Lista de la compra calculada de un evento, `/panel/eventos/:id/lista-compra`.
 * Solo lectura; la ve cualquier peñista. El ajuste de cantidades está en
 * "Cantidades para eventos" (administración).
 */
@Component({
  selector: 'app-lista-compra',
  imports: [Volver],
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

  protected trackCat = (_: number, c: CategoriaListaCompra) => c.categoria;
}
