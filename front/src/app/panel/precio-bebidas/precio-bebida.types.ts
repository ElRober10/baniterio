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
}

export interface GrillaArticulo {
  puedoEditar: boolean;
  tiendas: Tienda[];
  articulos: string[];
  precios: PrecioArticuloCelda[];
}
