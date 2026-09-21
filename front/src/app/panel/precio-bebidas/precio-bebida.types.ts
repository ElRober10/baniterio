/** Tipos del contrato con `/api/v1/precio-bebida/*`. Solo "formas" de datos. */

export interface EventoPrecioBebida {
  id: number;
  nombre: string;
  fecha: string;
  fechaFin: string | null;
}

export interface Tienda {
  id: number;
  nombre: string;
}

export interface BebidaRef {
  id: number;
  nombre: string;
}

export interface PrecioCelda {
  bebidaId: number;
  tamano: string;
  tiendaId: number;
  precio: number | null;
}

export interface GrillaAlcohol {
  puedoEditar: boolean;
  tiendas: Tienda[];
  tamanos: string[];
  bebidas: BebidaRef[];
  precios: PrecioCelda[];
}

export interface PrecioArticuloCelda {
  nombreArticulo: string;
  tiendaId: number;
  precio: number | null;
  /** Unidades que trae el pack al que corresponde el precio (1 = suelto). */
  cantidad: number;
}

export interface GrillaArticulo {
  puedoEditar: boolean;
  tiendas: Tienda[];
  articulos: string[];
  precios: PrecioArticuloCelda[];
  /** Solo los tamaños apuntados a mano; el resto se entiende de 2 litros. */
  tamanos: { nombreArticulo: string; litros: number }[];
  /** Solo en comida: los embutidos de Jamones Duriber (precio/kg y peso, nulos si no se ha apuntado). */
  porKilo: { nombreArticulo: string; precioKilo: number | null; pesoKg: number | null }[];
}
