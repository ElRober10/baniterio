import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { AsistenteFila, ListadoAsistentes } from '../eventos.types';
import { EventosService } from '../eventos.service';

const MODALIDAD: Record<string, string> = {
  COMPLETA: 'peña completa',
  SOLO_CERVEZA: 'solo cerveza',
  UN_DIA: 'un día',
  EMBARAZADA: 'embarazada',
};
const ALTERNATIVA: Record<string, string> = {
  CERVEZA: 'cerveza',
  TINTO_VERANO: 'tinto de verano',
  NADA: 'nada',
  CERVEZA_ESPECIAL: 'cerveza especial',
};

/**
 * Modal (overlay a pantalla completa, como `ModalRespuestaEvento`) con la lista
 * de quién va a un evento de San Miguel: nombre, estado, qué bebe, su cuota y si
 * ha pagado. Solo lectura; lo abre cualquier miembro desde el detalle del evento.
 */
@Component({
  selector: 'app-modal-asistentes',
  templateUrl: './modal-asistentes.html',
})
export class ModalAsistentes implements OnInit {
  private readonly eventosService = inject(EventosService);

  readonly eventoId = input.required<number>();
  readonly cerrar = output<void>();

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly datos = signal<ListadoAsistentes | null>(null);

  ngOnInit(): void {
    this.eventosService.asistentesEvento(this.eventoId()).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected bebidaTexto(a: AsistenteFila): string {
    if (!a.bebida) {
      return '—';
    }
    const alc = a.bebida.alcohol ?? 'sin alcohol';
    const alt = ALTERNATIVA[a.bebida.alternativa] ?? a.bebida.alternativa;
    const mod = MODALIDAD[a.bebida.modalidad] ?? a.bebida.modalidad;
    return `${alc} · ${a.bebida.refresco} · ${alt} (${mod})`;
  }
}
