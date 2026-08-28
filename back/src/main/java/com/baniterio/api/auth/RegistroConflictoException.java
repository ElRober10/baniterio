package com.baniterio.api.auth;

/**
 * Ya existe un usuario con ese teléfono o ese email. {@code ApiExceptionHandler}
 * la traduce a {@code 409 {"codigo":"YA_REGISTRADO"}}.
 */
public class RegistroConflictoException extends RuntimeException {
    public RegistroConflictoException() {
        super("Ese teléfono o email ya está registrado");
    }
}
