package com.baniterio.api.auth;

public class TelefonoNoAutorizadoException extends RuntimeException {
    public TelefonoNoAutorizadoException() {
        super("El teléfono no está autorizado para registrarse");
    }
}
