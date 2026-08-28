package com.baniterio.api.auth;

public class RegistroConflictoException extends RuntimeException {
    public RegistroConflictoException() {
        super("Ese teléfono o email ya está registrado");
    }
}
