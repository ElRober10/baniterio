package com.baniterio.api.perfil;

/**
 * {@code tienePareja=true} pero {@code parejaNombre} viene vacío → HTTP 400
 * {@code NOMBRE_PAREJA_REQUERIDO}. Distinto de {@link TelefonoParejaInvalidoException}:
 * un fallo de nombre no debe decirle al usuario que revise el teléfono.
 */
public class NombreParejaRequeridoException extends RuntimeException {
}
