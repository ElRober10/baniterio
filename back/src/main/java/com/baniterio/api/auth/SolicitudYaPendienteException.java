package com.baniterio.api.auth;

/**
 * Ya hay una solicitud de ingreso PENDIENTE para ese teléfono.
 * {@code ApiExceptionHandler} → {@code 409 {"codigo":"SOLICITUD_YA_PENDIENTE"}}.
 */
public class SolicitudYaPendienteException extends RuntimeException {
    public SolicitudYaPendienteException() {
        super("Ya hay una solicitud pendiente para ese teléfono");
    }
}
