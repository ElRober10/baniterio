package com.baniterio.api.auth;

/**
 * El teléfono que intenta solicitar acceso en realidad SÍ está autorizado:
 * debería registrarse directamente, no pedir acceso.
 * {@code ApiExceptionHandler} → {@code 409 {"codigo":"TELEFONO_YA_AUTORIZADO"}}.
 */
public class TelefonoYaAutorizadoException extends RuntimeException {
    public TelefonoYaAutorizadoException() {
        super("Ese teléfono ya está autorizado; regístrate directamente");
    }
}
