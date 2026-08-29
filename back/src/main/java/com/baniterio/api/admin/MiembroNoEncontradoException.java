package com.baniterio.api.admin;

/**
 * No existe ninguna membresía en la peña piloto para el {@code id} de miembro
 * que llega en la ruta ({@code /api/v1/admin/miembros/{id}/...}).
 *
 * <p>Se lanza al resolver la membresía en {@link AdminService#cambiarRol},
 * {@link AdminService#cambiarActivo} y {@link AdminService#reemplazarAreas}.
 * {@code ApiExceptionHandler} la traduce a
 * {@code 404 {"codigo":"MIEMBRO_NO_ENCONTRADO"}} (antes, un id inexistente
 * reventaba con un 500).
 */
public class MiembroNoEncontradoException extends RuntimeException {
    public MiembroNoEncontradoException() {
        super("No se ha encontrado ese miembro en la peña");
    }
}
