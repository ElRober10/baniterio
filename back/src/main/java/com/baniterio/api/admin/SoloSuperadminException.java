package com.baniterio.api.admin;

/**
 * Solo el propio superadmin puede cambiar su rol o su estado; otro admin no.
 * {@code ApiExceptionHandler} la traduce a {@code 409 {"codigo":"SOLO_EL_SUPERADMIN"}}.
 */
public class SoloSuperadminException extends RuntimeException {
    public SoloSuperadminException() {
        super("Solo el superadmin puede modificarse a sí mismo");
    }
}
