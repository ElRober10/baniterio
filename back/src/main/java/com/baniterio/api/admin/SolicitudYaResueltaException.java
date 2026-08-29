package com.baniterio.api.admin;

/**
 * Se ha intentado resolver una solicitud que no está {@code PENDIENTE} (ya
 * aprobada/rechazada, o inexistente). {@code ApiExceptionHandler} la traduce a
 * {@code 409 {"codigo":"SOLICITUD_YA_RESUELTA"}}.
 */
public class SolicitudYaResueltaException extends RuntimeException {
    public SolicitudYaResueltaException() {
        super("Esa solicitud ya está resuelta");
    }
}
