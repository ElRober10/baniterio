package com.baniterio.api.admin.dto;

import java.time.Instant;

/**
 * Vista de una {@link com.baniterio.api.identidad.SolicitudIngreso} para el panel
 * de administración. No expone el hash de contraseña: solo {@code traeContrasena}
 * indica si, al aprobarla, se creará el usuario ya con contraseña.
 */
public record SolicitudResumen(
        Long id,
        String nombre,
        String apellidos,
        String telefono,
        String email,
        String motivo,
        String relacion,
        String conocidos,
        boolean traeContrasena,
        String estado,
        Instant createdAt) {
}
