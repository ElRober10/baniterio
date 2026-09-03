package com.baniterio.api.cuenta;

/**
 * Se intenta crear una cuenta con un nombre que ya usa otra de la misma peña.
 * La traduce {@code ApiExceptionHandler} a 409 {@code CUENTA_YA_EXISTE}.
 */
public class CuentaConflictoException extends RuntimeException {
}
