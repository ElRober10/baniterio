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
}
