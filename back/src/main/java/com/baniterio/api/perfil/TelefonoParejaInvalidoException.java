package com.baniterio.api.perfil;

/**
 * Los datos de pareja del editor no son válidos → HTTP 400
 * {@code TELEFONO_PAREJA_INVALIDO}: el teléfono no es un móvil español, o
 * apunta al propio usuario, o falta el nombre. El editor ya los valida antes;
 * esto es la red del backend (fuente de verdad).
 */
public class TelefonoParejaInvalidoException extends RuntimeException {
}
