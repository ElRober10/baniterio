package com.baniterio.api.admin.dto;

import java.util.List;

/**
 * Vista de un miembro de la peña (un {@link com.baniterio.api.identidad.Usuario}
 * con {@link com.baniterio.api.identidad.Membresia}) para la pantalla de gestión
 * de permisos del panel.
 *
 * <p>{@code areas} son las concesiones explícitas de {@code permiso_area} de ese
 * usuario (nombres, ordenados). Un admin/superadmin las tiene todas de forma
 * implícita, así que su lista suele venir vacía aquí.
 */
public record MiembroResumen(
        Long id,
        String nombre,
        String apellidos,
        String mote,
        String telefono,
        String rol,
        boolean activo,
        boolean esSuperadmin,
        List<String> areas) {
}
