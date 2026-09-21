import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';

/**
 * Tercera pantalla de "Precio bebidas", `/panel/precio-bebidas/:anio/:id`:
 * un botón por tipo de bebida. De momento solo "Bebidas alcohólicas".
 */
@Component({
  selector: 'app-precio-bebida-evento',
  imports: [Volver, RouterLink],
  templateUrl: './precio-bebida-evento.html',
})
export class PrecioBebidaEvento {
  private readonly ruta = inject(ActivatedRoute);

  protected readonly anio = Number(this.ruta.snapshot.paramMap.get('anio'));
  protected readonly id = Number(this.ruta.snapshot.paramMap.get('id'));

  protected readonly categorias = [
    { etiqueta: 'Bebidas alcohólicas', ruta: ['/panel/precio-bebidas', this.anio, this.id, 'alcohol'] },
    ...[
      ['Refrescos', 'refrescos'],
      ['Para alternar', 'cerveza'],
      ['Limpieza y utensilios', 'limpieza'],
      ['Comida', 'comida'],
    ].map(([etiqueta, slug]) => ({
      etiqueta,
      ruta: ['/panel/precio-bebidas', this.anio, this.id, 'articulos', slug],
    })),
  ];
}
