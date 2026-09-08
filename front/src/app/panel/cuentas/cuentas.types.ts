/**
 * Tipos del contrato con `/api/v1/cuentas*`. Reflejan uno a uno los DTOs del
 * backend (`com.baniterio.api.cuenta.dto`). De momento la sección solo lista y
 * ve el detalle; crear/editar/borrar y los movimientos llegan después.
 */

export interface CuentaResumen {
  id: number;
  nombre: string;
  descripcion: string | null;
}

/** Estado de pago de la cuota de una asistencia (ver ficha_bebida.estado_pago). */
export type EstadoPagoCuota =
  | 'PENDIENTE_PAGO'
  | 'DECLARADO'
  | 'CONFIRMADO_PENDIENTE_ENVIO'
  | 'CONFIRMADO_EN_CUENTA';

export const ESTADO_PAGO_TEXTO: Record<EstadoPagoCuota, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  DECLARADO: 'Pagado, pendiente de confirmar',
  CONFIRMADO_PENDIENTE_ENVIO: 'Confirmado, pendiente de ingresar en la cuenta',
  CONFIRMADO_EN_CUENTA: 'Confirmado y en la cuenta',
};

export type CategoriaMovimiento =
  | 'REFRESCOS'
  | 'CERVEZA_Y_TINTO'
  | 'ALCOHOL'
  | 'COMIDA'
  | 'HIELOS'
  | 'MENAJE'
  | 'ROPA'
  | 'OTROS';

export const CATEGORIA_TEXTO: Record<CategoriaMovimiento, string> = {
  REFRESCOS: 'Refrescos',
  CERVEZA_Y_TINTO: 'Cerveza y tinto',
  ALCOHOL: 'Alcohol',
  COMIDA: 'Comida',
  HIELOS: 'Hielos',
  MENAJE: 'Menaje',
  ROPA: 'Ropa',
  OTROS: 'Otros',
};

/** Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla. */
export interface MovimientoFila {
  id: number;
  concepto: string;
  categoria: string | null;
  importe: number;
  fecha: string;
  saldoTras: number;
  reciboArchivo: string | null;
  manual: boolean;
  /** SALDO_INICIAL | CUOTA | GASTO | INGRESO | … */
  origen: string;
  adelantadoPor: string | null;
}

/** Una fila de la tabla "Peñistas" del detalle de una cuenta. */
export interface PenistaCuota {
  asistenciaId: number;
  nombre: string;
  anio: number;
  cuota: number;
  estadoPago: EstadoPagoCuota | null;
  metodoPago: string | null;
  camisetaCantidad: number;
  camisetaTalla: string | null;
  camisetaConfirmada: boolean;
  sudaderaCantidad: number;
  sudaderaTalla: string | null;
  sudaderaConfirmada: boolean;
  /** Importe que entró y saldo tras él; `null` mientras la cuota no esté cobrada. */
  ingreso: number | null;
  saldoTras: number | null;
}

export interface ResumenGasto {
  categoria: string;
  total: number;
}

/** Detalle de una cuenta: la hoja completa. */
export interface CuentaDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  saldo: number;
  saldoInicial: number;
  estimacion: number;
  cobradoSinIngresar: number | null;
  puedoGestionar: boolean;
  precioCamiseta: number | null;
  precioSudadera: number | null;
  penistas: PenistaCuota[];
  totalCuotas: number;
  totalCobrado: number;
  movimientos: MovimientoFila[];
  resumenGastos: ResumenGasto[];
}

/** Códigos de error propios de cuentas (ver ApiExceptionHandler.java). */
export type CodigoErrorCuenta = 'CUENTA_NO_ENCONTRADA' | 'SIN_PERMISO' | 'VALIDACION';
