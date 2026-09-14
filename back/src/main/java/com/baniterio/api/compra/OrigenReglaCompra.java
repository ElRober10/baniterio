package com.baniterio.api.compra;

/**
 * Procedencia de una regla de la lista de la compra de un evento.
 * {@link #PLANTILLA}: copiada de {@code regla_compra} al materializar; solo se
 * puede desactivar. {@link #MANUAL}: añadida por un admin en ese evento; se puede
 * borrar.
 */
public enum OrigenReglaCompra {
    PLANTILLA,
    MANUAL
}
