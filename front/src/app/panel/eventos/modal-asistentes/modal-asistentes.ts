import {
  Component,
  ElementRef,
  OnInit,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { AsistenteFila, ListadoAsistentes, MetodoPago } from '../eventos.types';
import { ESTADO_PAGO_TEXTO, EstadoPagoCuota } from '../../cuentas/cuentas.types';
import { EventosService } from '../eventos.service';

const METODOS: { valor: MetodoPago; texto: string }[] = [
  { valor: 'BIZUM', texto: 'Bizum' },
  { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
];

const METODO_TEXTO: Record<MetodoPago, string> = {
  BIZUM: 'Bizum',
  TRANSFERENCIA: 'Transferencia',
  EFECTIVO: 'Efectivo',
};

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

/** Máximo de columnas antes de rendirse y dejar que la lista tenga scroll. */
const MAX_COLUMNAS = 3;

/**
 * Modal (overlay a pantalla completa, como `ModalRespuestaEvento`) con la lista
 * de quién va a un evento de San Miguel: nombre, estado, qué bebe, su cuota y si
 * ha pagado. Solo lectura; lo abre cualquier miembro desde el detalle del evento.
 *
 * La lista intenta caber sin scroll: empieza en 1 columna y, mientras se
 * desborde y no pase de {@link MAX_COLUMNAS}, añade una columna más. Si a 3
 * columnas sigue sin caber, se queda con scroll.
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
  protected readonly columnas = signal(1);

  protected readonly metodos = METODOS;
  protected readonly metodoTexto = METODO_TEXTO;

  /** Texto corto del estado de pago para la fila. */
  protected estadoPagoTexto(e: EstadoPagoCuota | null): string {
    return e ? ESTADO_PAGO_TEXTO[e] : 'Pendiente de pago';
  }

  /** El pago ya está confirmado por un admin (en la cuenta o pendiente de ingresar). */
  protected confirmado(a: AsistenteFila): boolean {
    return a.estadoPago === 'CONFIRMADO_EN_CUENTA' || a.estadoPago === 'CONFIRMADO_PENDIENTE_ENVIO';
  }
  protected readonly filaConfirmando = signal<AsistenteFila | null>(null);
  protected readonly metodoElegido = signal<MetodoPago>('BIZUM');
  protected readonly guardandoPago = signal(false);
  protected readonly errorPago = signal('');

  private readonly lista = viewChild<ElementRef<HTMLElement>>('lista');

  ngOnInit(): void {
    this.eventosService.asistentesEvento(this.eventoId()).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.estado.set('listo');
        this.ajustarColumnas();
      },
      error: () => this.estado.set('error'),
    });
  }

  /** Sube columnas de una en una mientras la lista se desborde (hasta MAX_COLUMNAS). */
  private ajustarColumnas(): void {
    this.columnas.set(1);
    const paso = (): void => {
      const el = this.lista()?.nativeElement;
      if (!el) {
        return;
      }
      if (el.scrollHeight > el.clientHeight + 1 && this.columnas() < MAX_COLUMNAS) {
        this.columnas.update((c) => c + 1);
        requestAnimationFrame(paso);
      }
    };
    requestAnimationFrame(paso);
  }

  protected abrirConfirmar(a: AsistenteFila): void {
    this.metodoElegido.set('BIZUM');
    this.errorPago.set('');
    this.filaConfirmando.set(a);
  }

  protected cerrarConfirmar(): void {
    this.filaConfirmando.set(null);
  }

  protected confirmarPago(): void {
    const fila = this.filaConfirmando();
    if (!fila || this.guardandoPago()) {
      return;
    }
    this.guardandoPago.set(true);
    this.eventosService
      .confirmarPago(this.eventoId(), fila.asistenciaId, this.metodoElegido())
      .subscribe({
        next: (d) => {
          this.datos.set(d);
          this.guardandoPago.set(false);
          this.filaConfirmando.set(null);
          this.ajustarColumnas();
        },
        error: () => {
          this.guardandoPago.set(false);
          this.errorPago.set('No se pudo confirmar el pago.');
        },
      });
  }

  protected deshacerPago(a: AsistenteFila): void {
    this.eventosService.deshacerPago(this.eventoId(), a.asistenciaId).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.ajustarColumnas();
      },
      error: () => this.errorPago.set('No se pudo deshacer el pago.'),
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
