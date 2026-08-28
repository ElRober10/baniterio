package com.baniterio.api.auth;

public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException() {
        super("Teléfono o contraseña incorrectos");
    }
}
