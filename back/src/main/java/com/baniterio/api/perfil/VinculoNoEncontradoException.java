package com.baniterio.api.perfil;

/**
 * No hay ningún vínculo de pareja sobre el que actuar (aceptar / rechazar /
 * romper) para este usuario → HTTP 404 {@code VINCULO_NO_ENCONTRADO}.
 */
public class VinculoNoEncontradoException extends RuntimeException {
}
