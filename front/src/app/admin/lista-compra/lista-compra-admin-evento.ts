import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../shared/volver/volver';
import { ListaCompraService } from '../../panel/eventos/lista-compra/lista-compra.service';
import {
  CategoriaClave,
  CrearRegla,
  ReglaCompraEvento,
  TipoFormula,
} from '../../panel/eventos/lista-compra/lista-compra.types';

const CATEGORIAS: { clave: CategoriaClave; etiqueta: string }[] = [
  { clave: 'ALCOHOL', etiqueta: 'Alcohol' },
  { clave: 'CERVEZA', etiqueta: 'Cerveza' },
  { clave: 'REFRESCOS', etiqueta: 'Refrescos' },
  { clave: 'LIMPIEZA', etiqueta: 'Limpieza y utensilios' },
  { clave: 'COMIDA', etiqueta: 'Comida' },
];

const FORMULAS_CREABLES: { clave: TipoFormula; etiqueta: string }[] = [
  { clave: 'POR_PENISTA', etiqueta: 'Por peñista' },
  { clave: 'POR_PENISTA_DIA', etiqueta: 'Por peñista y día' },
  { clave: 'POR_DIA', etiqueta: 'Por día de fiesta' },
  { clave: 'POR_EVENTO', etiqueta: 'Por evento (cantidad fija)' },
  { clave: 'POR_CADA_N_PENISTAS', etiqueta: 'Por cada N peñistas' },
  { clave: 'CERVEZA_ALTERNATIVA', etiqueta: 'Por peñista y día (bebe cerveza)' },
  { clave: 'TINTO_ALTERNATIVA', etiqueta: 'Por peñista y día (bebe tinto)' },
];

function nuevaVacia(): CrearRegla {
  return { categoria: 'COMIDA', nombre: '', tamano: '', tipoFormula: 'POR_EVENTO', factor: 1, porCada: null };
}

/**
 * Editor de la lista de la compra de un evento: ajustar cantidades,
 * activar/desactivar reglas y añadir/quitar artículos. Ruta protegida por
 * `areaGuard('INVENTARIO')`.
 */
@Component({
  selector: 'app-lista-compra-admin-evento',
  imports: [Volver, FormsModule],
  templateUrl: './lista-compra-admin-evento.html',
})
export class ListaCompraAdminEvento implements OnInit {
  private readonly service = inject(ListaCompraService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly categorias = CATEGORIAS;
  protected readonly formulas = FORMULAS_CREABLES;
  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly nombre = signal('');
  protected readonly reglas = signal<ReglaCompraEvento[]>([]);
  protected readonly aviso = signal('');
  protected readonly guardando = signal(false);
  protected readonly formAbierto = signal(false);
  protected nueva: CrearRegla = nuevaVacia();

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.adminEvento(this.eventoId).subscribe({
      next: (d) => {
        this.nombre.set(d.evento.nombre);
        this.reglas.set(d.reglas);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected formulaLegible(r: ReglaCompraEvento): string {
    switch (r.tipoFormula) {
      case 'POR_PENISTA':
        return `${r.factor} por peñista`;
      case 'POR_PENISTA_DIA':
        return `${r.factor} por peñista y día`;
      case 'POR_DIA':
        return `${r.factor} por día de fiesta`;
      case 'POR_EVENTO':
        return `${r.factor} por evento`;
      case 'POR_CADA_N_PENISTAS':
        return `${r.factor} por cada ${r.porCada} peñistas`;
      case 'POR_CADA_N_PENISTAS_DIA':
        return `${r.factor} por cada ${r.porCada} peñistas y día`;
      case 'CERVEZA_ALTERNATIVA':
        return `${r.factor} por peñista y día (bebe cerveza)`;
      case 'TINTO_ALTERNATIVA':
        return `${r.factor} por peñista y día (bebe tinto de verano)`;
      case 'ALCOHOL_SELECCIONADO':
        return '0,5 botellas por peñista y día, por marca elegida';
      case 'REFRESCO_SELECCIONADO':
        return '2 litros por peñista y día, por refresco elegido';
      case 'CERVEZA_ESPECIAL_SELECCIONADA':
        return `${r.factor} por peñista y día, por cerveza especial escrita en la ficha`;
    }
  }

  protected tienePorCada(r: ReglaCompraEvento): boolean {
    return r.tipoFormula === 'POR_CADA_N_PENISTAS' || r.tipoFormula === 'POR_CADA_N_PENISTAS_DIA';
  }

  protected guardar(r: ReglaCompraEvento): void {
    this.guardando.set(true);
    this.aviso.set('');
    this.service
      .ajustarRegla(this.eventoId, r.id, {
        cantidadAjustada: null,
        activa: r.activa,
        factor: r.factor,
        porCada: this.tienePorCada(r) ? r.porCada : null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cargar();
        },
        error: () => {
          this.guardando.set(false);
          this.aviso.set('No se pudo guardar.');
        },
      });
  }

  protected anadir(): void {
    this.guardando.set(true);
    this.aviso.set('');
    const body: CrearRegla = {
      ...this.nueva,
      porCada: this.nueva.tipoFormula === 'POR_CADA_N_PENISTAS' ? this.nueva.porCada : null,
    };
    this.service.crearRegla(this.eventoId, body).subscribe({
      next: () => {
        this.guardando.set(false);
        this.formAbierto.set(false);
        this.nueva = nuevaVacia();
        this.cargar();
      },
      error: () => {
        this.guardando.set(false);
        this.aviso.set('No se pudo añadir el artículo.');
      },
    });
  }

  protected quitar(r: ReglaCompraEvento): void {
    if (!confirm(`¿Quitar "${r.nombre}" de este evento?`)) return;
    this.service.borrarRegla(this.eventoId, r.id).subscribe({
      next: () => this.cargar(),
      error: () => this.aviso.set('No se pudo quitar.'),
    });
  }
}
