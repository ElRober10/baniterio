import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { MetodoPago, PersonaPagable } from '../eventos.types';
import { EventosService } from '../eventos.service';

/** El pago que se enviaría. En esta tanda NO se manda a ningún sitio. */
export interface PagoDeclarado {
  importe: number;
  metodo: MetodoPago;
  cubre: { yo: boolean; usuarioIds: number[]; asistenciaIds: number[] };
}

const METODOS: { valor: MetodoPago; texto: string }[] = [
  { valor: 'BIZUM', texto: 'Bizum al administrador' },
  { valor: 'TRANSFERENCIA', texto: 'Transferencia a la cuenta de la peña' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
];
const RELACION: Record<string, string> = { PAREJA: 'pareja', HIJO: 'hijo/a', INVITADO: 'invitado/a' };

/**
 * Modal para declarar un pago: importe, a quién cubre (uno mismo siempre, más
 * pareja / hijos mayores con cuenta / invitados propios) y método. En esta tanda
 * NO persiste: valida y emite `(enviado)` con el objeto; el detalle solo muestra
 * un aviso. La confirmación por el admin es una tanda posterior.
 */
@Component({
  selector: 'app-modal-he-pagado',
  templateUrl: './modal-he-pagado.html',
})
export class ModalHePagado implements OnInit {
  private readonly eventosService = inject(EventosService);

  readonly eventoId = input.required<number>();
  readonly cerrar = output<void>();
  readonly enviado = output<PagoDeclarado>();

  protected readonly metodos = METODOS;
  protected readonly relacionTexto = RELACION;
  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly miCuota = signal<number | null>(null);
  protected readonly personas = signal<PersonaPagable[]>([]);

  protected readonly importe = signal(0);
  protected readonly metodo = signal<MetodoPago>('BIZUM');
  protected readonly usuariosMarcados = signal<Set<number>>(new Set());
  protected readonly asistenciasMarcadas = signal<Set<number>>(new Set());

  /** Suma sugerida: mi cuota + la de cada persona marcada. */
  protected readonly sugerido = computed(() => {
    let total = this.miCuota() ?? 0;
    for (const p of this.personas()) {
      if (p.usuarioId != null && this.usuariosMarcados().has(p.usuarioId)) {
        total += p.cuota;
      }
      if (p.asistenciaId != null && this.asistenciasMarcadas().has(p.asistenciaId)) {
        total += p.cuota;
      }
    }
    return total;
  });

  protected readonly puedeEnviar = computed(() => this.importe() > 0 && this.metodo() != null);

  ngOnInit(): void {
    this.eventosService.asistentesEvento(this.eventoId()).subscribe({
      next: (d) => {
        this.miCuota.set(d.miCuota);
        this.personas.set(d.puedoPagarPor);
        this.importe.set(d.miCuota ?? 0);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected cambiarImporte(e: Event): void {
    this.importe.set(Number((e.target as HTMLInputElement).value) || 0);
  }

  /** ¿Está incluida en el pago esta persona? */
  protected estaMarcada(p: PersonaPagable): boolean {
    return p.usuarioId != null
      ? this.usuariosMarcados().has(p.usuarioId)
      : this.asistenciasMarcadas().has(p.asistenciaId!);
  }

  /** Añade o quita a esta persona del pago (unifica usuario e invitado). */
  protected alternar(p: PersonaPagable): void {
    if (p.usuarioId != null) {
      this.marcarUsuario(p.usuarioId);
    } else {
      this.marcarAsistencia(p.asistenciaId!);
    }
  }

  protected marcarUsuario(id: number): void {
    this.usuariosMarcados.update((s) => {
      const n = new Set(s);
      if (n.has(id)) {
        n.delete(id);
      } else {
        n.add(id);
      }
      return n;
    });
    this.importe.set(this.sugerido());
  }

  protected marcarAsistencia(id: number): void {
    this.asistenciasMarcadas.update((s) => {
      const n = new Set(s);
      if (n.has(id)) {
        n.delete(id);
      } else {
        n.add(id);
      }
      return n;
    });
    this.importe.set(this.sugerido());
  }

  protected enviar(): void {
    if (!this.puedeEnviar()) {
      return;
    }
    this.enviado.emit({
      importe: this.importe(),
      metodo: this.metodo()!,
      cubre: {
        yo: true,
        usuarioIds: [...this.usuariosMarcados()],
        asistenciaIds: [...this.asistenciasMarcadas()],
      },
    });
  }
}
