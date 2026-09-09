import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { ListaCompraService } from './lista-compra.service';
import { CategoriaListaCompra } from './lista-compra.types';

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
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected trackCat = (_: number, c: CategoriaListaCompra) => c.categoria;
}
