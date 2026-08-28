package com.baniterio.api.auth;

/**
 * Login fallido: el teléfono no existe, la contraseña no coincide, o la cuenta
 * está inactiva. Se usa la MISMA excepción para los tres casos a propósito, para
 * no revelar cuál falló. {@code ApiExceptionHandler} → {@code 401
 * {"codigo":"CREDENCIALES_INVALIDAS"}}.
 */
public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException() {
        super("Teléfono o contraseña incorrectos");
    }
}
