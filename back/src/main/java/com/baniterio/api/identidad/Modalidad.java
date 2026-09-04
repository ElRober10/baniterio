package com.baniterio.api.identidad;

/**
 * Modalidad de peñista en San Miguel, calculada a partir de la ficha de bebida.
 * Fija la cuota: EMBARAZADA=5 · UN_DIA=M/2+1 · SOLO_CERVEZA=max(M-10,0) · COMPLETA=M.
 */
public enum Modalidad {
    COMPLETA,
    SOLO_CERVEZA,
    UN_DIA,
    EMBARAZADA
}
