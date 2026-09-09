/**
 * Tipos del contrato con `/api/v1/inventario`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.inventario.dto`). De momento la sección lista el
 * inventario y edita artículos que ya existen; el alta/baja llega después.
 */

export type CategoriaClave = 'ALCOHOL' | 'CERVEZA' | 'REFRESCOS' | 'LIMPIEZA' | 'COMIDA';

export interface ArticuloInventario {
  id: number;
  nombre: string;
  tamano: string;
  cantidad: number;
}

export interface CategoriaInventario {
  categoria: CategoriaClave;
  etiqueta: string;
  tamanos: string[];
  articulos: ArticuloInventario[];
}

export interface InventarioResponse {
  puedoEditar: boolean;
  categorias: CategoriaInventario[];
}

/** Cuerpo del PUT: los tres campos editables de un artículo. */
export interface CambioArticulo {
  nombre: string;
  tamano: string;
  cantidad: number;
}

/** Una categoría para la rejilla de botones y para resolver el slug de la URL. */
export interface OpcionCategoria {
  clave: CategoriaClave;
  etiqueta: string;
  slug: string;
}

/** Las cinco categorías, en el orden en que se pintan los botones. */
export const CATEGORIAS: OpcionCategoria[] = [
  { clave: 'ALCOHOL', etiqueta: 'Alcohol', slug: 'alcohol' },
  { clave: 'CERVEZA', etiqueta: 'Cerveza', slug: 'cerveza' },
  { clave: 'REFRESCOS', etiqueta: 'Refrescos', slug: 'refrescos' },
  { clave: 'LIMPIEZA', etiqueta: 'Limpieza', slug: 'limpieza' },
  { clave: 'COMIDA', etiqueta: 'Comida', slug: 'comida' },
];
