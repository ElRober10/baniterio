import { DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import type * as PdfjsLib from 'pdfjs-dist';
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

  // Modal "Cerrar el año".
  protected readonly modalCerrarAnio = signal(false);
  protected readonly cerrandoAnio = signal(false);

  // Confirmación de "borrar movimiento".
  protected readonly movABorrar = signal<number | null>(null);
  protected readonly borrandoMov = signal(false);

  // Visor de recibo: modal embebido en vez de pestaña nueva, con el nombre del archivo abierto.
  protected readonly reciboAbierto = signal<string | null>(null);
  protected readonly reciboEsPdf = computed(() => (this.reciboAbierto() ?? '').toLowerCase().endsWith('.pdf'));
  @ViewChild('pdfContenedor') private pdfContenedor?: ElementRef<HTMLDivElement>;

  constructor() {
    // Se dispara al abrir el modal con un PDF; `pdfContenedor` puede no existir
    // aún en el primer tick (el `@if` del modal lo crea), de ahí el microtask.
    effect(() => {
      const archivo = this.reciboAbierto();
      if (archivo && this.reciboEsPdf()) {
        queueMicrotask(() => this.renderizarPdf(this.urlRecibo(archivo)));
      }
    });
  }

  // Selector de año.
  private readonly anioSel = signal<number | null>(null);
  protected readonly mostrarAniosViejos = signal(false);
  /** Botones de año: los 5 más nuevos, o todos si se han desplegado. */
  protected readonly aniosVisibles = computed(() => {
    const anios = this.cuenta()?.anios ?? [];
    return this.mostrarAniosViejos() ? anios : anios.slice(0, 5);
  });
  protected readonly hayAniosViejos = computed(() => (this.cuenta()?.anios.length ?? 0) > 5);

  // Formulario "+ Gasto / Ingreso".
  protected readonly formAbierto = signal(false);
  protected readonly guardandoMov = signal(false);
  protected form = this.formVacio();
  protected recibo: File | null = null;

  protected readonly pagados = computed(
    () => this.cuenta()?.penistas.filter((p) => this.confirmado(p)).length ?? 0,
  );

  /** Gastos/ingresos manuales + ropa confirmada; la cuota cobrada va en la fila del peñista. */
  protected readonly gastos = computed(
    () =>
      this.cuenta()?.movimientos.filter(
        (m) => m.manual || m.origen === 'CAMISETA' || m.origen === 'SUDADERA',
      ) ?? [],
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.cuentasService.detalle(this.id, this.anioSel() ?? undefined).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected verAnio(anio: number): void {
    if (anio === this.cuenta()?.anio) {
      return;
    }
    this.anioSel.set(anio);
    this.aviso.set(null);
    this.cargar();
  }

  /** Columnas de la hoja: concepto, estado, camiseta, sudadera, gasto, ingreso, recibo, saldo. */
  protected readonly COLUMNAS = 8;

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

  protected cerrarAnio(): void {
    this.cerrandoAnio.set(true);
    this.cuentasService.cerrarAnio(this.id).subscribe({
      next: (c) => {
        this.anioSel.set(null);
        this.mostrarAniosViejos.set(false);
        this.trasCambio(c, `Año cerrado. Ahora estás en ${c.anio}.`);
      },
      error: () => this.aviso.set('No se ha podido cerrar el año.'),
      complete: () => {
        this.cerrandoAnio.set(false);
        this.modalCerrarAnio.set(false);
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

  protected pedirBorrarMovimiento(movId: number): void {
    this.movABorrar.set(movId);
  }

  protected confirmarBorrarMovimiento(): void {
    const movId = this.movABorrar();
    if (movId == null) return;
    this.borrandoMov.set(true);
    this.cuentasService.borrarMovimiento(movId).subscribe({
      next: (c) => this.trasCambio(c, 'Movimiento borrado.'),
      error: () => this.aviso.set('No se ha podido borrar.'),
      complete: () => {
        this.borrandoMov.set(false);
        this.movABorrar.set(null);
      },
    });
  }

  private trasCambio(c: CuentaDetalle, msg: string): void {
    this.cuenta.set(c);
    this.aviso.set(msg);
  }

  private pdfjsLib?: typeof PdfjsLib;

  /**
   * Dibuja cada página del PDF en un `<canvas>`, una debajo de otra. `<embed
   * type="application/pdf">` no funciona en navegadores móviles (Chrome/Brave Android no lo
   * renderizan inline, y el botón "Abrir" que dejan en su lugar no hacía nada dentro del
   * modal): pdfjs-dist sí pinta igual en escritorio y móvil.
   *
   * Se carga con `import()` dinámico (no en cabecera) para que no engorde el bundle inicial
   * de toda la sección de Cuentas con algo que solo hace falta al abrir un recibo en PDF. El
   * worker se sirve como asset aparte (`pdf.worker.min.mjs`, ver angular.json).
   */
  private async renderizarPdf(url: string): Promise<void> {
    const contenedor = this.pdfContenedor?.nativeElement;
    if (!contenedor) return;
    contenedor.innerHTML = '';
    try {
      if (!this.pdfjsLib) {
        this.pdfjsLib = await import('pdfjs-dist');
        // Absoluta, no relativa: esta pantalla vive en una ruta anidada
        // (/panel/cuentas/...) y una relativa resolvía mal (404).
        this.pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs';
      }
      const pdf = await this.pdfjsLib.getDocument({ url }).promise;
      const anchoDisponible = contenedor.clientWidth || 600;
      for (let i = 1; i <= pdf.numPages; i++) {
        const pagina = await pdf.getPage(i);
        // Se ajusta al ancho del modal y se renderiza a 2x para que no se vea borroso.
        const escala = (anchoDisponible / pagina.getViewport({ scale: 1 }).width) * 2;
        const viewport = pagina.getViewport({ scale: escala });
        const canvas = document.createElement('canvas');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.width = '100%';
        canvas.style.display = 'block';
        canvas.style.marginBottom = '8px';
        await pagina.render({ canvas, viewport }).promise;
        contenedor.appendChild(canvas);
      }
    } catch (e) {
      console.error('No se pudo renderizar el PDF del recibo:', e);
      // Temporal: el mensaje real en pantalla para depurar sin acceso a la consola del
      // móvil. Se quita en cuanto se sepa la causa (ver conversación 2026-09-22).
      const detalle = e instanceof Error ? `${e.name}: ${e.message}` : String(e);
      contenedor.innerHTML =
        `<p class="p-4 text-sm text-muted">No se ha podido mostrar el PDF.<br><span class="text-xs opacity-70">${detalle}</span></p>`;
    }
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
