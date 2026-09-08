import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { Volver } from '../../../shared/volver/volver';
import { CategoriaMovimiento, CuentaDetalle, PenistaCuota } from '../cuentas.types';
import { CuentasService } from '../cuentas.service';

const CATEGORIAS: { valor: CategoriaMovimiento; texto: string }[] = [
  { valor: 'REFRESCOS', texto: 'Refrescos' },
  { valor: 'CERVEZA_Y_TINTO', texto: 'Cerveza y tinto' },
  { valor: 'ALCOHOL', texto: 'Alcohol' },
  { valor: 'COMIDA', texto: 'Comida' },
  { valor: 'HIELOS', texto: 'Hielos' },
  { valor: 'MENAJE', texto: 'Menaje' },
  { valor: 'ROPA', texto: 'Ropa' },
  { valor: 'OTROS', texto: 'Otros' },
];

const ESTADO_TEXTO: Record<string, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  DECLARADO: 'Pagado, pendiente de confirmar',
  CONFIRMADO_PENDIENTE_ENVIO: 'Confirmado, pendiente de ingresar en la cuenta',
  CONFIRMADO_EN_CUENTA: 'Confirmado y en la cuenta',
};

/**
 * La hoja de una cuenta, al estilo del Excel del tesorero: cabecera con el saldo,
 * tabla de peñistas (quién ha pagado cuota / camiseta / sudadera), libro de
 * movimientos con saldo corriente y resumen de gastos por categoría. Un admin
 * añade gastos/ingresos con recibo y marca la ropa.
 */
@Component({
  selector: 'app-cuenta-detalle',
  imports: [Volver, DatePipe, FormsModule],
  templateUrl: './cuenta-detalle.html',
  styleUrl: './cuenta-detalle.css',
})
export class CuentaDetalleComponent implements OnInit {
  private readonly cuentasService = inject(CuentasService);
  private readonly route = inject(ActivatedRoute);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly cuenta = signal<CuentaDetalle | null>(null);
  protected readonly aviso = signal<string | null>(null);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  protected readonly categorias = CATEGORIAS;
  protected readonly estadoTexto = ESTADO_TEXTO;

  // Modal "He transferido al banco".
  protected readonly modalTransferir = signal(false);
  protected readonly enviandoTransfer = signal(false);

  // Formulario "+ Gasto / Ingreso".
  protected readonly formAbierto = signal(false);
  protected readonly guardandoMov = signal(false);
  protected form = this.formVacio();
  protected recibo: File | null = null;

  protected readonly pagados = computed(
    () => this.cuenta()?.penistas.filter((p) => this.confirmado(p)).length ?? 0,
  );

  /** Solo gastos/ingresos manuales: las cuotas cobradas van en la fila del peñista. */
  protected readonly gastos = computed(
    () => this.cuenta()?.movimientos.filter((m) => m.manual) ?? [],
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.cuentasService.detalle(this.id).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  /** Columnas de la hoja: concepto, cuota, estado, camiseta, sudadera, gasto, ingreso, recibo, saldo. */
  protected readonly COLUMNAS = 9;

  protected confirmado(p: PenistaCuota): boolean {
    return p.estadoPago === 'CONFIRMADO_EN_CUENTA' || p.estadoPago === 'CONFIRMADO_PENDIENTE_ENVIO';
  }

  protected urlRecibo(archivo: string): string {
    return `${environment.apiBaseUrl}/media/recibos/${archivo}`;
  }

  protected confirmarTransferencia(): void {
    this.enviandoTransfer.set(true);
    this.cuentasService.marcarTransferido(this.id).subscribe({
      next: (c) => this.trasCambio(c, 'Hecho, el dinero consta ingresado en el banco.'),
      error: () => {
        this.enviandoTransfer.set(false);
        this.aviso.set('No se ha podido registrar.');
      },
      complete: () => {
        this.enviandoTransfer.set(false);
        this.modalTransferir.set(false);
      },
    });
  }

  protected onRecibo(ev: Event): void {
    this.recibo = (ev.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected guardarMovimiento(): void {
    const datos = new FormData();
    datos.set('tipo', this.form.tipo);
    datos.set('concepto', this.form.concepto);
    datos.set('importe', String(this.form.importe));
    if (this.form.fecha) datos.set('fecha', this.form.fecha);
    if (this.form.categoria) datos.set('categoria', this.form.categoria);
    if (this.recibo) datos.set('recibo', this.recibo);

    this.guardandoMov.set(true);
    this.cuentasService.crearMovimiento(this.id, datos).subscribe({
      next: (c) => {
        this.trasCambio(c, 'Movimiento añadido.');
        this.formAbierto.set(false);
        this.form = this.formVacio();
        this.recibo = null;
      },
      error: () => this.aviso.set('No se ha podido guardar el movimiento.'),
      complete: () => this.guardandoMov.set(false),
    });
  }

  protected borrarMovimiento(movId: number): void {
    this.cuentasService.borrarMovimiento(movId).subscribe({
      next: (c) => this.trasCambio(c, 'Movimiento borrado.'),
      error: () => this.aviso.set('No se ha podido borrar.'),
    });
  }

  protected guardarRopa(
    p: PenistaCuota,
    cambio: {
      camisetaCantidad?: number;
      camisetaTalla?: string;
      sudaderaCantidad?: number;
      sudaderaTalla?: string;
    },
  ): void {
    this.cuentasService.marcarRopa(this.id, p.asistenciaId, cambio).subscribe({
      next: (c) => this.cuenta.set(c),
      error: () => this.aviso.set('No se ha podido cambiar.'),
    });
  }

  private trasCambio(c: CuentaDetalle, msg: string): void {
    this.cuenta.set(c);
    this.aviso.set(msg);
  }

  private formVacio() {
    return {
      tipo: 'GASTO' as 'GASTO' | 'INGRESO',
      concepto: '',
      importe: null as number | null,
      fecha: '',
      categoria: '' as CategoriaMovimiento | '',
    };
  }
}
