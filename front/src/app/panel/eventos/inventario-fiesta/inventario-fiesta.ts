import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { InventarioService } from '../../inventario/inventario.service';
import { ArticuloFiesta, CategoriaFiesta } from '../../inventario/inventario.types';

type DestinoDevolucion = 'inventario' | 'lista';

/**
 * Inventario que se ha enviado a un evento ("inventario de la fiesta"),
 * `/panel/eventos/:id/inventario`. Lo ve cualquier apuntado; los botones
 * "Devolver a inventario" / "Devolver a la lista" sólo salen con permiso
 * (`puedoEditar`) y piden confirmación en un modal.
 */
@Component({
  selector: 'app-inventario-fiesta',
  imports: [Volver],
  templateUrl: './inventario-fiesta.html',
  styleUrl: './inventario-fiesta.css',
})
export class InventarioFiesta implements OnInit {
  private readonly inventarioService = inject(InventarioService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly puedoEditar = signal(false);
  protected readonly categorias = signal<CategoriaFiesta[]>([]);
  protected readonly aviso = signal('');
  protected readonly devolviendo = signal(false);

  /** Artículo + destino pendiente de confirmar en el modal; `null` = modal cerrado. */
  protected readonly confirmacion = signal<{ articulo: ArticuloFiesta; destino: DestinoDevolucion } | null>(
    null,
  );

  protected readonly textoConfirmacion = computed(() => {
    const c = this.confirmacion();
    if (!c) return '';
    return c.destino === 'lista'
      ? `¿Devolver «${c.articulo.nombre}» a la lista de la compra?`
      : `¿Devolver «${c.articulo.nombre}» al inventario general?`;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.inventarioService.inventarioFiesta(this.eventoId).subscribe({
      next: (datos) => {
        this.puedoEditar.set(datos.puedoEditar);
        this.categorias.set(datos.categorias);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected pedirDevolver(articulo: ArticuloFiesta, destino: DestinoDevolucion): void {
    this.confirmacion.set({ articulo, destino });
  }

  protected cancelarConfirmacion(): void {
    this.confirmacion.set(null);
  }

  protected confirmarDevolucion(): void {
    const c = this.confirmacion();
    if (!c || this.devolviendo()) return;
    this.confirmacion.set(null);
    this.devolviendo.set(true);
    this.aviso.set('');
    const llamada =
      c.destino === 'lista'
        ? this.inventarioService.devolverALista(this.eventoId, c.articulo.id)
        : this.inventarioService.devolverAInventario(this.eventoId, c.articulo.id);
    llamada.subscribe({
      next: () => {
        this.devolviendo.set(false);
        this.cargar();
      },
      error: () => {
        this.devolviendo.set(false);
        this.aviso.set(
          c.destino === 'lista' ? 'No se pudo devolver a la lista.' : 'No se pudo devolver.',
        );
      },
    });
  }

  protected stockDe(a: ArticuloFiesta): number {
    return a.cantidad - a.cantidadComprada;
  }

  protected trackCat = (_: number, c: CategoriaFiesta) => c.categoria;
  protected trackArt = (_: number, a: ArticuloFiesta) => a.id;
}
