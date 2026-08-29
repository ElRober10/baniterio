package com.baniterio.api.auth.dto;

import java.util.Collection;
import java.util.List;

import com.baniterio.api.identidad.AreaProtegida;
import com.baniterio.api.identidad.RolMembresia;
import com.baniterio.api.identidad.Usuario;

/**
 * Vista pública de un usuario (JSON). Nunca incluye hash de contraseña ni
 * teléfono/email.
 *
 * <p>{@code rol} y {@code areas} solo se rellenan en las respuestas de
 * {@code /login} y {@code /yo} (con {@link #de(Usuario, RolMembresia,
 * Collection)}), que consultan permisos en vivo. El 201 de registro usa
 * {@link #de(Usuario)} y los deja vacíos (el cliente hace login a continuación).
 */
public record UsuarioResponse(
        Long id, String nombre, String apellidos, String mote, boolean esSuperadmin,
        String rol, List<String> areas) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getApellidos(), u.getMote(),
                u.isEsSuperadmin(), null, List.of());
    }

    public static UsuarioResponse de(Usuario u, RolMembresia rol, Collection<AreaProtegida> areas) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getApellidos(), u.getMote(),
                u.isEsSuperadmin(),
                rol == null ? null : rol.name(),
                areas.stream().map(Enum::name).sorted().toList());
    }
}
