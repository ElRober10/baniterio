import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { EventosService } from '../eventos.service';
import { FichaBebida } from '../ficha-bebida/ficha-bebida';
import { AsistenciaResumen, CatalogoBebidas, FichaBebidaBody, MetodoPago } from '../eventos.types';

const METODOS: { valor: MetodoPago; texto: string }[] = [
  { valor: 'BIZUM', texto: 'Bizum' },
  { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
];

/**
 * Modal "Añadir asistente": un admin/organizador da de alta a alguien sin app
 * (nombre, teléfono y, si el evento lleva ficha de bebida, lo que bebe y los
 * días que va). El checkbox "Confirmar el pago ahora" hace que, tras el alta,
 * se confirme también el pago con el método elegido; si no se marca, la
 * persona queda igual que cualquier otro asistente con la cuota pendiente.
 */
@Component({
  selector: 'app-modal-anadir-asistente',
  imports: [FichaBebida],
  templateUrl: './modal-anadir-asistente.html',
})
export class ModalAnadirAsistente implements OnInit {
  private readonly eventosService = inject(EventosService);

  readonly eventoId = input.required<number>();
  readonly llevaFicha = input.required<boolean>();
  readonly diasEvento = input.required<string[]>();
  readonly cerrar = output<void>();
  readonly anadido = output<AsistenciaResumen>();

  protected readonly metodos = METODOS;
  protected readonly nombre = signal('');
  protected readonly telefono = signal('');
  protected readonly confirmarPago = signal(false);
  protected readonly metodoPago = signal<MetodoPago>('EFECTIVO');
  protected readonly catalogo = signal<CatalogoBebidas | null>(null);
  protected readonly guardando = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {
    if (this.llevaFicha()) {
      this.eventosService.catalogoBebidas().subscribe((c) => this.catalogo.set(c));
    }
  }

  protected cambiarNombre(e: Event): void {
    this.nombre.set((e.target as HTMLInputElement).value);
  }

  protected cambiarTelefono(e: Event): void {
    this.telefono.set((e.target as HTMLInputElement).value);
  }

  protected toggleConfirmarPago(e: Event): void {
    this.confirmarPago.set((e.target as HTMLInputElement).checked);
  }

  /** Sin ficha de bebida: alta directa con solo nombre y teléfono. */
  protected anadirSinFicha(): void {
    const nombre = this.nombre().trim();
    if (!nombre) {
      this.error.set('Escribe el nombre.');
      return;
    }
    this.error.set('');
    this.guardando.set(true);
    this.eventosService
      .anadirAsistente(this.eventoId(), nombre, 'APUNTADO', this.telefono().trim() || undefined)
      .subscribe({
        next: (r) => {
          this.guardando.set(false);
          this.anadido.emit(r);
        },
        error: () => {
          this.guardando.set(false);
          this.error.set('No se pudo añadir a esa persona.');
        },
      });
  }

  /** Con ficha de bebida: el botón de guardar de `app-ficha-bebida` dispara el alta completa. */
  protected anadirConFicha(ficha: FichaBebidaBody): void {
    const nombre = this.nombre().trim();
    if (!nombre) {
      this.error.set('Escribe el nombre antes de guardar la ficha.');
      return;
    }
    this.error.set('');
    this.guardando.set(true);
    this.eventosService
      .anadirAsistente(this.eventoId(), nombre, ficha.estado, this.telefono().trim() || undefined, ficha)
      .subscribe({
        next: (r) => this.trasAlta(r),
        error: () => {
          this.guardando.set(false);
          this.error.set('No se pudo añadir a esa persona.');
        },
      });
  }

  private trasAlta(r: AsistenciaResumen): void {
    if (!this.confirmarPago()) {
      this.guardando.set(false);
      this.anadido.emit(r);
      return;
    }
    this.eventosService.confirmarPago(this.eventoId(), r.id, this.metodoPago()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.anadido.emit(r);
      },
      error: () => {
        this.guardando.set(false);
        this.error.set('Se ha añadido, pero no se pudo confirmar el pago.');
      },
    });
  }
}
