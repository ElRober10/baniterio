package com.baniterio.api.identidad;

/** De dónde sale una fila del libro de movimientos (ver movimiento_cuenta, V28/V29). */
public enum OrigenMovimiento {
    SALDO_INICIAL,
    CUOTA,
    CAMISETA,
    SUDADERA,
    GASTO,
    INGRESO,
    AJUSTE;

    /** {@code GASTO} e {@code INGRESO} los apunta el admin a mano; el resto son automáticos. */
    public boolean esManual() {
        return this == GASTO || this == INGRESO;
    }
}
