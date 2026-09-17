/** Tipos del contrato con `/api/v1/precio-bebida/*`. Solo "formas" de datos. */

export interface EventoPrecioBebida {
  id: number;
  nombre: string;
  fecha: string;
  fechaFin: string | null;
}
