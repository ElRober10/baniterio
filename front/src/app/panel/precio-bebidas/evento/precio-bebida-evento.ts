import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';

/**
 * Tercera pantalla de "Precio bebidas", `/panel/precio-bebidas/:anio/:id`.
 * Placeholder: el editor de precios llega en una tarea aparte.
 */
@Component({
  selector: 'app-precio-bebida-evento',
  imports: [Volver],
  templateUrl: './precio-bebida-evento.html',
})
export class PrecioBebidaEvento {
  private readonly ruta = inject(ActivatedRoute);

  protected readonly anio = Number(this.ruta.snapshot.paramMap.get('anio'));
}
