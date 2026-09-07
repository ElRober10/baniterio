package com.baniterio.api.cuenta;

/** Se pide un movimiento del libro que no existe. La traduce {@code ApiExceptionHandler} a 404. */
public class MovimientoNoEncontradoException extends RuntimeException {
}
