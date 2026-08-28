package com.baniterio.api.auth.dto;

import com.baniterio.api.identidad.Usuario;

public record UsuarioResponse(String id, String nombre, String apellidos, String mote, boolean esSuperadmin) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(
            u.getId().toString(), u.getNombre(), u.getApellidos(), u.getMote(), u.isEsSuperadmin());
    }
}
