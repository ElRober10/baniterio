package com.baniterio.api.admin;

/**
 * Degradar o desactivar a este admin dejaría a la peña sin ningún admin activo.
 * {@code ApiExceptionHandler} la traduce a {@code 409 {"codigo":"ULTIMO_ADMIN"}}.
 */
public class UltimoAdminException extends RuntimeException {
    public UltimoAdminException() {
        super("No puedes dejar la peña sin ningún administrador activo");
    }
}
