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

/** Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla. */
export interface MovimientoFila {
  concepto: string;
  importe: number;
  fecha: string;
  saldoTras: number;
}

/** Detalle de una cuenta: saldo, estimación y libro de movimientos. */
export interface CuentaDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  saldo: number;
  estimacion: number;
  movimientos: MovimientoFila[];
  puedoGestionar: boolean;
  porIngresar: number | null;
}

/** Códigos de error propios de cuentas (ver ApiExceptionHandler.java). */
export type CodigoErrorCuenta = 'CUENTA_NO_ENCONTRADA' | 'SIN_PERMISO' | 'VALIDACION';
