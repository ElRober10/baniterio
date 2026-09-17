import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { PrecioBebidaService } from '../precio-bebida.service';
import { EventoPrecioBebida } from '../precio-bebida.types';

/**
 * Segunda pantalla de "Precio bebidas", `/panel/precio-bebidas/:anio`: un
 * botón por evento de ese año.
 */
@Component({
  selector: 'app-precio-bebidas-eventos',
  imports: [Volver, RouterLink],
  templateUrl: './precio-bebidas-eventos.html',
})
export class PrecioBebidasEventos implements OnInit {
  private readonly service = inject(PrecioBebidaService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly anio = Number(this.ruta.snapshot.paramMap.get('anio'));
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  private readonly todos = signal<EventoPrecioBebida[]>([]);

  protected readonly eventosDelAnio = computed(() =>
    this.todos()
      .filter((e) => new Date(e.fecha).getFullYear() === this.anio)
      .sort((a, b) => a.fecha.localeCompare(b.fecha)),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.eventos().subscribe({
      next: (l) => {
        this.todos.set(l);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }
}
