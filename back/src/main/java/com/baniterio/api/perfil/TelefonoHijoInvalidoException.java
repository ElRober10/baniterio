package com.baniterio.api.perfil;

/**
 * El teléfono de un hijo en el editor no es un móvil español válido → HTTP 400
 * {@code TELEFONO_HIJO_INVALIDO}. Como en {@link TelefonoParejaInvalidoException},
 * el editor ya lo valida antes; esto es la red del backend (fuente de verdad).
 */
public class TelefonoHijoInvalidoException extends RuntimeException {
}
