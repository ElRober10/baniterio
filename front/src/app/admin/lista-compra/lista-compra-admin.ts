import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Volver } from '../../shared/volver/volver';
import { ListaCompraService } from '../../panel/eventos/lista-compra/lista-compra.service';
import { EventoListaCompra } from '../../panel/eventos/lista-compra/lista-compra.types';

/**
 * "Cantidades para eventos": lista de eventos para entrar a ajustar su lista de
 * la compra. Ruta protegida por `areaGuard('INVENTARIO')`.
 */
@Component({
  selector: 'app-lista-compra-admin',
  imports: [RouterLink, Volver],
  templateUrl: './lista-compra-admin.html',
})
export class ListaCompraAdmin implements OnInit {
  private readonly service = inject(ListaCompraService);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly eventos = signal<EventoListaCompra[]>([]);

  ngOnInit(): void {
    this.service.adminEventos().subscribe({
      next: (e) => {
        this.eventos.set(e);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }
}
