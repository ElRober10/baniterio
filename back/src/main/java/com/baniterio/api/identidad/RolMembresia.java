package com.baniterio.api.identidad;

/** Rol de un usuario dentro de una peña. Se guarda como texto en la columna
 *  {@code membresia.rol}. El fundador entra como {@code ADMIN}; el resto,
 *  {@code MIEMBRO}. */
public enum RolMembresia {
    ADMIN,
    MIEMBRO
}
