import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CodigoErrorEvento, EventoDetalle } from '../eventos.types';
import { EventosService } from '../eventos.service';

/**
 * Vista de un evento (solo lectura). Según los permisos que devuelve el backend
 * muestra "Gestionar" (`puedoEditar`) y "Borrar"/"Solicitar borrado"
 * (`puedoBorrar`). El texto del botón de borrado se decide de forma aproximada
 * por si el evento tiene `creadoPor`; el backend hace lo correcto igualmente
 * (204 = borrado real → vuelve a la lista; 202 = solicitud → aviso).
 */
@Component({
  selector: 'app-evento-detalle',
  imports: [Volver],
  templateUrl: './evento-detalle.html',
  styleUrl: './evento-detalle.css',
})
export class EventoDetalleComponent implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly evento = signal<EventoDetalle | null>(null);
  protected readonly aviso = signal('');
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.eventosService.detalle(this.id).subscribe({
      next: (e) => {
        this.evento.set(e);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected gestionar(): void {
    this.router.navigate(['/panel/eventos', this.id, 'editar']);
  }

  protected borrar(): void {
    this.eventosService.borrar(this.id).subscribe({
      next: (r) => {
        if (r && r.estado === 'PENDIENTE') {
          this.aviso.set('Solicitud de borrado enviada. Un administrador tiene que autorizarla.');
          this.cargar();
        } else {
          this.router.navigate(['/panel/eventos']);
        }
      },
      error: (e: HttpErrorResponse) => {
        const codigo = e.error?.codigo as CodigoErrorEvento | undefined;
        this.aviso.set(
          codigo === 'SOLICITUD_EVENTO_YA_PENDIENTE'
            ? 'Ya hay una solicitud de borrado pendiente para este evento.'
            : 'No se pudo borrar el evento.',
        );
        this.cargar();
      },
    });
  }
}
