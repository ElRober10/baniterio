package com.baniterio.api.compra;

/**
 * Forma de la fórmula de una regla de la lista de la compra. El motor
 * ({@link CalculadoraListaCompra}) hace un {@code switch} sobre este valor.
 * {@link #ALCOHOL_SELECCIONADO} y {@link #REFRESCO_SELECCIONADO} son dinámicas:
 * una fila de regla se expande en varias líneas, una por marca elegida en la
 * ficha de bebida, y no se pueden crear a mano ni ajustar con cantidad fija.
 */
public enum TipoFormulaCompra {
    POR_PENISTA,
    POR_PENISTA_DIA,
    POR_DIA,
    POR_EVENTO,
    POR_CADA_N_PENISTAS,
    POR_CADA_N_PENISTAS_DIA,
    CERVEZA_ALTERNATIVA,
    TINTO_ALTERNATIVA,
    ALCOHOL_SELECCIONADO,
    REFRESCO_SELECCIONADO;

    public boolean esDinamica() {
        return this == ALCOHOL_SELECCIONADO || this == REFRESCO_SELECCIONADO;
    }
}
