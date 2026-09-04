import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CatalogoBebidas,
  EstadoAsistencia,
  EventoDetalle,
  EventoResumen,
  FichaBebidaBody,
} from '../eventos/eventos.types';
import { EventosService } from '../eventos/eventos.service';
import { FichaBebida } from '../eventos/ficha-bebida/ficha-bebida';

/**
 * Pantalla bloqueante: mientras haya eventos con notificación sin contestar, el
 * usuario ve aquí el primero y no puede ir a otra parte (el
 * `respuestaPendienteGuard` lo devuelve). Responde uno a uno; en los eventos de
 * San Miguel, tras Me apunto / En duda rellena la ficha de bebida antes de
 * avanzar. Cuando no queda ninguno, va al panel.
 */
@Component({
  selector: 'app-responder',
  imports: [FichaBebida],
  templateUrl: './responder.html',
  styleUrl: './responder.css',
})
export class Responder implements OnInit {
  private readonly eventos = inject(EventosService);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly pendientes = signal<EventoResumen[]>([]);
  protected readonly enviando = signal(false);
  protected readonly aviso = signal('');

  /** `responder` = los 3 botones; `ficha` = el formulario de bebida (San Miguel). */
  protected readonly fase = signal<'responder' | 'ficha'>('responder');
  protected readonly detalle = signal<EventoDetalle | null>(null);
  protected readonly catalogo = signal<CatalogoBebidas | null>(null);

  protected readonly opciones: { valor: EstadoAsistencia; texto: string }[] = [
    { valor: 'APUNTADO', texto: 'Me apunto' },
    { valor: 'NO_VOY', texto: 'No voy' },
    { valor: 'EN_DUDA', texto: 'En duda' },
  ];

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.fase.set('responder');
    this.estado.set('cargando');
    this.eventos.pendientesRespuesta().subscribe({
      next: (r) => {
        this.pendientes.set(r.eventos);
        if (r.eventos.length === 0) {
          this.router.navigateByUrl('/panel');
          return;
        }
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected responder(estado: EstadoAsistencia): void {
    const actual = this.pendientes()[0];
    if (!actual || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.aviso.set('');
    this.eventos.responder(actual.id, estado).subscribe({
      next: (detalle) => {
        this.enviando.set(false);
        if (estado !== 'NO_VOY' && detalle.asistencia.ficha.llevaFicha) {
          this.detalle.set(detalle);
          this.eventos.catalogoBebidas().subscribe((c) => {
            this.catalogo.set(c);
            this.fase.set('ficha');
          });
        } else {
          this.cargar();
        }
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        const codigo = e.error?.codigo as string | undefined;
        this.aviso.set(
          codigo === 'EVENTO_YA_PASADO'
            ? 'Ese evento ya ha pasado.'
            : 'No se pudo guardar tu respuesta. Inténtalo otra vez.',
        );
        this.cargar();
      },
    });
  }

  protected guardarFicha(body: FichaBebidaBody): void {
    const actual = this.pendientes()[0];
    if (!actual) {
      return;
    }
    this.eventos.guardarFichaBebida(actual.id, body).subscribe({
      next: () => this.cargar(),
      error: () => this.aviso.set('No se pudo guardar la ficha. Inténtalo otra vez.'),
    });
  }
}
