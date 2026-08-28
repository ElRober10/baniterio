package com.baniterio.api.auth;

/**
 * El teléfono no está en la lista de autorizados de la peña (o ya se usó para
 * registrarse). {@code ApiExceptionHandler} la traduce a un
 * {@code 403 {"codigo":"TELEFONO_NO_AUTORIZADO"}}, que el front reconoce para
 * mostrar el aviso de "pide que te autoricen".
 */
public class TelefonoNoAutorizadoException extends RuntimeException {
    public TelefonoNoAutorizadoException() {
        super("El teléfono no está autorizado para registrarse");
    }
}
