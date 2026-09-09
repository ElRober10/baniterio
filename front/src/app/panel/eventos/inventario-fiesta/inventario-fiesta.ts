import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { InventarioService } from '../../inventario/inventario.service';
import { ArticuloFiesta, CategoriaFiesta } from '../../inventario/inventario.types';

/**
 * Inventario que se ha enviado a un evento ("inventario de la fiesta"),
 * `/panel/eventos/:id/inventario`. Lo ve cualquier apuntado; el botón
 * "Devolver a inventario" de cada línea sólo sale con permiso (`puedoEditar`).
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

  protected devolver(a: ArticuloFiesta): void {
    if (!confirm(`¿Devolver "${a.nombre}" al inventario general?`)) return;
    this.devolviendo.set(true);
    this.aviso.set('');
    this.inventarioService.devolverAInventario(this.eventoId, a.id).subscribe({
      next: () => {
        this.devolviendo.set(false);
        this.cargar();
      },
      error: () => {
        this.devolviendo.set(false);
        this.aviso.set('No se pudo devolver.');
      },
    });
  }

  protected trackCat = (_: number, c: CategoriaFiesta) => c.categoria;
  protected trackArt = (_: number, a: ArticuloFiesta) => a.id;
}
