import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../../../auth/auth.service';
import {
  CatalogoBebidas,
  EstadoAsistencia,
  EventoDetalle,
  FichaBebidaBody,
  PendienteRespuesta,
} from '../eventos.types';
import { EventosService } from '../eventos.service';
import { FichaBebida } from '../ficha-bebida/ficha-bebida';

const OPCIONES: { valor: EstadoAsistencia; texto: string }[] = [
  { valor: 'APUNTADO', texto: 'Me apunto' },
  { valor: 'NO_VOY', texto: 'No voy' },
  { valor: 'EN_DUDA', texto: 'En duda' },
];

/**
 * Modal bloqueante: mientras el usuario tenga convocatorias sin contestar, se
 * planta encima de toda la pantalla (sin botón de cerrar) con la primera y tres
 * botones grandes. Si responde "voy" o "en duda" y el evento pide ficha de
 * bebida, la pinta en el sitio de los botones. Al guardar recarga la lista;
 * cuando queda vacía, desaparece sola. Montado una vez en `panel.html`, igual
 * que `AvisoPendientes` — se dispara solo nada más entrar a `/panel` (login
 * recién hecho o recargar página), y como tapa la pantalla entera no hace
 * falta un guard de rutas aparte para impedir navegar mientras está abierto.
 */
@Component({
  selector: 'app-modal-respuesta-evento',
  imports: [FichaBebida],
  templateUrl: './modal-respuesta-evento.html',
})
export class ModalRespuestaEvento implements OnInit {
  private readonly eventosService = inject(EventosService);
  private readonly auth = inject(AuthService);

  protected readonly opciones = OPCIONES;
  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly pendientes = signal<PendienteRespuesta[]>([]);
  protected readonly enviando = signal(false);
  protected readonly aviso = signal('');
  /** Cuando el evento actual pide ficha, se guardan aquí su detalle (días, mi ficha) y el catálogo. */
  protected readonly detalleActual = signal<EventoDetalle | null>(null);
  protected readonly catalogo = signal<CatalogoBebidas | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  /** `true` si el pendiente actual es de otra persona (pareja/hijo), para el rótulo "Respondiendo por". */
  protected esDeOtro(p: PendienteRespuesta): boolean {
    return p.paraUsuario.id !== this.auth.usuarioActual()?.id;
  }

  private cargar(): void {
    this.detalleActual.set(null);
    this.catalogo.set(null);
    this.eventosService.pendientesRespuesta().subscribe({
      next: (r) => {
        this.pendientes.set(r.eventos);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected responder(estado: EstadoAsistencia): void {
    const p = this.pendientes()[0];
    if (!p) {
      return;
    }
    this.enviando.set(true);
    this.aviso.set('');
    this.eventosService.responder(p.evento.id, estado, p.paraUsuario.id).subscribe({
      next: (e) => {
        this.enviando.set(false);
        if (estado !== 'NO_VOY' && e.asistencia.ficha.llevaFicha) {
          this.detalleActual.set(e);
          this.eventosService.catalogoBebidas().subscribe({
            next: (c) => this.catalogo.set(c),
            error: () => this.cargar(),
          });
        } else {
          this.cargar();
        }
      },
      error: () => {
        this.enviando.set(false);
        this.aviso.set('No se pudo guardar tu respuesta.');
        this.cargar();
      },
    });
  }

  protected guardarFicha(body: FichaBebidaBody): void {
    const p = this.pendientes()[0];
    if (!p) {
      return;
    }
    this.eventosService
      .guardarFichaBebida(p.evento.id, { ...body, paraUsuarioId: p.paraUsuario.id })
      .subscribe({
        next: () => this.cargar(),
        error: () => this.aviso.set('No se pudo guardar la ficha.'),
      });
  }
}
