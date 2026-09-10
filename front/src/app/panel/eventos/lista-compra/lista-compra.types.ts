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
  | 'CERVEZA_ALTERNATIVA'
  | 'TINTO_ALTERNATIVA'
  | 'ALCOHOL_SELECCIONADO'
  | 'REFRESCO_SELECCIONADO';

export interface LineaCompra {
  id: number;
  nombre: string;
  tamano: string;
  cantidad: number;
  cantidadCalculada: number;
  ajustada: boolean;
  dinamica: boolean;
  necesitaFicha: boolean;
  comprada: boolean;
}

export interface CategoriaListaCompra {
  categoria: CategoriaClave;
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
}

export interface CrearRegla {
  categoria: CategoriaClave;
  nombre: string;
  tamano: string;
  tipoFormula: TipoFormula;
  factor: number;
  porCada?: number | null;
}
