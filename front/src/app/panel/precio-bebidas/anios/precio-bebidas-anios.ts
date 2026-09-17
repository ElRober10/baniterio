import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PrecioBebidaService } from '../precio-bebida.service';
import { EventoPrecioBebida } from '../precio-bebida.types';

/**
 * Primera pantalla de "Precio bebidas", `/panel/precio-bebidas`: un botón por
 * año (derivado de la fecha de los eventos, no hay un año "creado" a mano).
 */
@Component({
  selector: 'app-precio-bebidas-anios',
  imports: [RouterLink],
  templateUrl: './precio-bebidas-anios.html',
})
export class PrecioBebidasAnios implements OnInit {
  private readonly service = inject(PrecioBebidaService);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  private readonly eventos = signal<EventoPrecioBebida[]>([]);

  protected readonly anios = computed(() => {
    const distintos = new Set(this.eventos().map((e) => new Date(e.fecha).getFullYear()));
    return [...distintos].sort((a, b) => b - a);
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.eventos().subscribe({
      next: (l) => {
        this.eventos.set(l);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }
}
