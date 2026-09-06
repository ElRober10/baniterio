import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { EventoResumen } from '../eventos.types';
import { EventosService } from '../eventos.service';

/**
 * Eventos "borrados" (ocultos, recuperables): solo llega quien tiene el enlace,
 * el backend re-comprueba que sea admin/superadmin (403 si no). Cada fila tiene
 * un botón "Recuperar"; al recuperarlo desaparece de esta lista.
 */
@Component({
  selector: 'app-eventos-ocultos',
  imports: [Volver],
  templateUrl: './eventos-ocultos.html',
})
export class EventosOcultos implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly eventos = signal<EventoResumen[]>([]);
  protected readonly aviso = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.eventosService.listarOcultos().subscribe({
      next: (r) => {
        this.eventos.set(r.eventos);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected abrir(e: EventoResumen): void {
    this.router.navigate(['/panel/eventos', e.id]);
  }

  protected recuperar(e: EventoResumen): void {
    this.aviso.set('');
    this.eventosService.recuperar(e.id).subscribe({
      next: () => {
        this.eventos.update((lista) => lista.filter((x) => x.id !== e.id));
        this.aviso.set(`«${e.nombre}» recuperado.`);
      },
      error: () => this.aviso.set('No se pudo recuperar el evento.'),
    });
  }
}
