package com.baniterio.api.admin;

/**
 * El usuario autenticado no tiene acceso al área del panel que pide.
 * {@code ApiExceptionHandler} la traduce a {@code 403 {"codigo":"SIN_PERMISO"}}.
 */
public class SinPermisoException extends RuntimeException {
    public SinPermisoException() {
        super("No tienes acceso a esta sección del panel");
    }
}
