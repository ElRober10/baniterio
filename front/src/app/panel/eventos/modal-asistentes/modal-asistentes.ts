import {
  Component,
  ElementRef,
  computed,
  OnInit,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { AsistenteFila, AsistenciaResumen, ListadoAsistentes, MetodoPago } from '../eventos.types';
import { ESTADO_PAGO_TEXTO, EstadoPagoCuota } from '../../cuentas/cuentas.types';
import { EventosService } from '../eventos.service';
import { ModalAnadirAsistente } from '../modal-anadir-asistente/modal-anadir-asistente';

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
const MAX_COLUMNAS = 4;

/**
 * Modal (overlay a pantalla completa, como `ModalRespuestaEvento`) con la lista
 * de quién va a un evento de San Miguel: nombre, estado, qué bebe, su cuota y si
 * ha pagado. Solo lectura; lo abre cualquier miembro desde el detalle del evento.
 *
 * La lista intenta caber sin scroll: empieza en 1 columna y, mientras se
 * desborde y no pase de {@link MAX_COLUMNAS}, añade una columna más. Si a 4
 * columnas sigue sin caber, se queda con scroll.
 */
@Component({
  selector: 'app-modal-asistentes',
  imports: [ModalAnadirAsistente],
  templateUrl: './modal-asistentes.html',
})
export class ModalAsistentes implements OnInit {
  private readonly eventosService = inject(EventosService);

  readonly eventoId = input.required<number>();
  readonly puedoEditar = input(false);
  readonly llevaFicha = input(false);
  readonly diasEvento = input<string[]>([]);
  readonly cerrar = output<void>();

  protected readonly modalAnadir = signal(false);

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

  protected readonly filaCuota = signal<AsistenteFila | null>(null);
  protected readonly cuotaElegida = signal<number | null>(null);
  protected readonly metodoCuota = signal<MetodoPago>('BIZUM');
  protected readonly errorCuota = signal('');
  protected readonly opciones = computed(() => this.datos()?.opcionesCuota ?? []);

  /** Lo que sube la cuota elegida respecto a la actual (negativo si baja). */
  protected readonly diferenciaCuota = computed(() => {
    const fila = this.filaCuota();
    const nueva = this.cuotaElegida();
    return fila && nueva != null ? Math.round((nueva - (fila.cuota ?? 0)) * 100) / 100 : 0;
  });

  /** Hay que decir cómo se ha pagado la diferencia: ya estaba confirmado y la cuota sube. */
  protected readonly pideMetodoCuota = computed(() => {
    const fila = this.filaCuota();
    return !!fila && this.confirmado(fila) && this.diferenciaCuota() > 0;
  });

  private readonly lista = viewChild<ElementRef<HTMLElement>>('lista');

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.eventosService.asistentesEvento(this.eventoId()).subscribe({
      next: (d) => {
        this.datos.set(d);
        this.estado.set('listo');
        this.ajustarColumnas();
      },
      error: () => this.estado.set('error'),
    });
  }

  protected onAsistenteAnadido(_: AsistenciaResumen): void {
    this.modalAnadir.set(false);
    this.cargar();
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

  protected abrirActualizarCuota(a: AsistenteFila): void {
    this.metodoCuota.set('BIZUM');
    this.errorCuota.set('');
    this.cuotaElegida.set(a.cuota);
    this.filaCuota.set(a);
  }

  protected cerrarActualizarCuota(): void {
    this.filaCuota.set(null);
  }

  protected actualizarCuota(): void {
    const fila = this.filaCuota();
    const nueva = this.cuotaElegida();
    if (!fila || nueva == null || this.guardandoPago()) {
      return;
    }
    this.guardandoPago.set(true);
    this.eventosService
      .actualizarCuota(
        this.eventoId(),
        fila.asistenciaId,
        nueva,
        this.pideMetodoCuota() ? this.metodoCuota() : null,
      )
      .subscribe({
        next: (d) => {
          this.datos.set(d);
          this.guardandoPago.set(false);
          this.filaCuota.set(null);
          this.ajustarColumnas();
        },
        error: () => {
          this.guardandoPago.set(false);
          this.errorCuota.set('No se pudo actualizar la cuota.');
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
    const refresco = a.bebida.refresco ?? 'sin refresco';
    const alt = ALTERNATIVA[a.bebida.alternativa] ?? a.bebida.alternativa;
    const mod = MODALIDAD[a.bebida.modalidad] ?? a.bebida.modalidad;
    return `${alc} · ${refresco} · ${alt} (${mod})`;
  }
}
