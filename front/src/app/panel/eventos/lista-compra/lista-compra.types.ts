/**
 * Tipos del contrato con los endpoints de la lista de la compra
 * (`/api/v1/eventos/:id/lista-compra` y `/api/v1/admin/lista-compra/*`). Solo
 * "formas" de datos.
 */

export type CategoriaClave = 'ALCOHOL' | 'CERVEZA' | 'REFRESCOS' | 'LIMPIEZA' | 'COMIDA';

export type TipoFormula =
  | 'POR_PENISTA'
  | 'POR_PENISTA_DIA'
  | 'POR_DIA'
  | 'POR_EVENTO'
  | 'POR_CADA_N_PENISTAS'
  | 'POR_CADA_N_PENISTAS_DIA'
  | 'CERVEZA_ALTERNATIVA'
  | 'TINTO_ALTERNATIVA'
  | 'ALCOHOL_SELECCIONADO'
  | 'REFRESCO_SELECCIONADO'
  | 'CERVEZA_ESPECIAL_SELECCIONADA';

export interface LineaCompra {
  id: number;
  nombre: string;
  tamano: string;
  tienda?: string | null;
  precioUnitario?: number | null;
  cantidad: number;
  cantidadCalculada: number;
  ajustada: boolean;
  dinamica: boolean;
  necesitaFicha: boolean;
  comprada: boolean;
  /** Nota de la compra en packs, p. ej. "2 × pack de 50". */
  detalle?: string | null;
}

export interface CategoriaListaCompra {
  /** Una categoría, o "PARA_ALTERNAR" (cerveza, cervezas especiales y tinto de verano). */
  categoria: CategoriaClave | 'PARA_ALTERNAR';
  etiqueta: string;
  lineas: LineaCompra[];
}

export interface ListaCompraResponse {
  puedoEditar: boolean;
  llevaFicha: boolean;
  bloqueada: boolean;
  apuntados: number;
  diasFiesta: number;
  categorias: CategoriaListaCompra[];
  /** Saldo de la cuenta del evento en su año en curso (lo que hay para gastar). */
  presupuesto?: number | null;
}

export interface EventoListaCompra {
  id: number;
  nombre: string;
  fecha: string;
  fechaFin: string | null;
}

export interface ReglaCompraEvento {
  id: number;
  categoria: CategoriaClave;
  etiqueta: string;
  nombre: string;
  tamano: string;
  tipoFormula: TipoFormula;
  factor: number;
  porCada: number | null;
  origen: 'PLANTILLA' | 'MANUAL';
  cantidadCalculada: number;
  cantidadAjustada: number | null;
  cantidadFinal: number;
  activa: boolean;
}

export interface ListaCompraAdminResponse {
  evento: EventoListaCompra;
  bloqueada: boolean;
  apuntados: number;
  diasFiesta: number;
  reglas: ReglaCompraEvento[];
}

export interface AjustarRegla {
  cantidadAjustada: number | null;
  activa: boolean;
  factor: number;
  porCada: number | null;
}

export interface CrearRegla {
  categoria: CategoriaClave;
  nombre: string;
  tamano: string;
  tipoFormula: TipoFormula;
  factor: number;
  porCada?: number | null;
}
