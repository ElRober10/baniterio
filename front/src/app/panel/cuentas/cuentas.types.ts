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

/** Códigos de error propios de cuentas (ver ApiExceptionHandler.java). */
export type CodigoErrorCuenta = 'CUENTA_NO_ENCONTRADA' | 'SIN_PERMISO' | 'VALIDACION';
