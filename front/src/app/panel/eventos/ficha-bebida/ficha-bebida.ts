import { Component, EventEmitter, Input, OnInit, Output, computed, signal } from '@angular/core';
import { Alternativa, CatalogoBebidas, FichaBebidaBody, FichaBebidaMia } from '../eventos.types';

const OTRA = '__otra__';

/**
 * Formulario de la ficha de bebida de San Miguel. Reutilizable: el detalle del
 * evento, la pantalla de convocatoria y el alta manual lo montan igual. No hace
 * ninguna llamada: al pulsar "Guardar" emite `(guardar)` con el cuerpo listo
 * para `EventosService.guardarFichaBebida` / `anadirAsistente`.
 */
@Component({
  selector: 'app-ficha-bebida',
  templateUrl: './ficha-bebida.html',
})
export class FichaBebida implements OnInit {
  @Input({ required: true }) catalogo!: CatalogoBebidas;
  /** 1 o 2 fechas ISO del evento. Con 2 aparece el selector de día. */
  @Input({ required: true }) dias!: string[];
  @Input() fichaActual: FichaBebidaMia | null = null;
  @Input() enDuda = false;
  @Input() textoBoton = 'Guardar ficha';
  @Output() guardar = new EventEmitter<FichaBebidaBody>();

  protected readonly OTRA = OTRA;
  protected readonly alcoholSel = signal('');
  protected readonly alcoholOtra = signal('');
  protected readonly refrescoSel = signal('');
  protected readonly refrescoOtra = signal('');
  protected readonly alternativa = signal<Alternativa>('NADA');
  protected readonly cervezaEspecial = signal('');
  protected readonly embarazada = signal(false);
  protected readonly dia1 = signal(true);
  protected readonly dia2 = signal(true);
  protected readonly error = signal('');

  protected readonly dosDias = computed(() => this.dias.length === 2);

  ngOnInit(): void {
    const f = this.fichaActual;
    if (!f) {
      return;
    }
    this.embarazada.set(f.embarazada);
    this.alcoholSel.set(f.alcoholBebidaId != null ? String(f.alcoholBebidaId) : '');
    this.refrescoSel.set(f.refrescoBebidaId != null ? String(f.refrescoBebidaId) : '');
    this.alternativa.set(f.alternativa);
    this.cervezaEspecial.set(f.cervezaEspecial ?? '');
    this.dia1.set(f.asisteDia1);
    this.dia2.set(f.asisteDia2);
  }

  protected set(sig: { set: (v: string) => void }, e: Event): void {
    sig.set((e.target as HTMLInputElement | HTMLSelectElement).value);
  }

  /**
   * `[selected]` de cada `<option>` generada por `@for`, en vez de `[value]`
   * en el `<select>`: el `<select>` se pinta antes de que existan sus
   * opciones (`@for` es una vista aparte), así que fijar su valor ahí llega
   * demasiado pronto y no selecciona nada la primera vez que hay una ficha
   * ya rellenada. Marcando cada `<option>` no depende de ese orden.
   */
  protected esId(valorSel: string, id: number): boolean {
    return valorSel === String(id);
  }

  /**
   * Lo que muestra el desplegable "Para alternar". "Sin alcohol" y "sin gluten" no son
   * alternativas propias en el backend: se guardan como cerveza especial con ese texto
   * (así entran en la lista de la compra y toman el precio de la rejilla).
   */
  protected readonly eleccion = computed<string>(() => {
    if (this.alternativa() !== 'CERVEZA_ESPECIAL') {
      return this.alternativa();
    }
    const texto = this.cervezaEspecial().trim().toLowerCase().replace(/^cerveza\s+/, '');
    return texto === 'sin alcohol' ? 'SIN_ALCOHOL' : texto === 'sin gluten' ? 'SIN_GLUTEN' : 'CERVEZA_ESPECIAL';
  });

  protected setAlternativa(e: Event): void {
    const valor = (e.target as HTMLSelectElement).value;
    if (valor === 'SIN_ALCOHOL' || valor === 'SIN_GLUTEN') {
      this.alternativa.set('CERVEZA_ESPECIAL');
      this.cervezaEspecial.set(valor === 'SIN_ALCOHOL' ? 'sin alcohol' : 'sin gluten');
      return;
    }
    if (valor === 'CERVEZA_ESPECIAL' && this.eleccion() !== 'CERVEZA_ESPECIAL') {
      this.cervezaEspecial.set('');
    }
    this.alternativa.set(valor as Alternativa);
  }

  protected toggleEmbarazada(e: Event): void {
    this.embarazada.set((e.target as HTMLInputElement).checked);
  }

  protected toggleDia(cual: 1 | 2, e: Event): void {
    const v = (e.target as HTMLInputElement).checked;
    (cual === 1 ? this.dia1 : this.dia2).set(v);
  }

  protected enviar(): void {
    this.error.set('');
    const emb = this.embarazada();
    const refrescoOtra = this.refrescoSel() === OTRA ? this.refrescoOtra().trim() : '';
    const refrescoId = this.refrescoSel() !== OTRA && this.refrescoSel() ? Number(this.refrescoSel()) : null;
    const alt: Alternativa = emb ? 'NADA' : this.alternativa();
    if (!emb && alt === 'CERVEZA_ESPECIAL' && !this.cervezaEspecial().trim()) {
      this.error.set('Escribe cuál es tu cerveza especial.');
      return;
    }
    const va1 = this.dosDias() ? this.dia1() : true;
    const va2 = this.dosDias() ? this.dia2() : true;
    if (!va1 && !va2) {
      this.error.set('Marca al menos un día.');
      return;
    }
    const alcoholOtra =
      !emb && this.alcoholSel() === OTRA ? this.alcoholOtra().trim() : '';
    const alcoholId =
      !emb && this.alcoholSel() !== OTRA && this.alcoholSel() ? Number(this.alcoholSel()) : null;

    const body: FichaBebidaBody = {
      estado: this.enDuda ? 'EN_DUDA' : 'APUNTADO',
      alcoholBebidaId: alcoholId,
      alcoholOtra: alcoholOtra || null,
      refrescoBebidaId: refrescoId,
      refrescoOtra: refrescoOtra || null,
      alternativa: alt,
      cervezaEspecial: !emb && alt === 'CERVEZA_ESPECIAL' ? this.cervezaEspecial().trim() : null,
      embarazada: emb,
      asisteDia1: va1,
      asisteDia2: va2,
    };
    this.guardar.emit(body);
  }
}
