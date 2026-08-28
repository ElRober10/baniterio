package com.baniterio.api.auth.dto;

import com.baniterio.api.identidad.Usuario;

/**
 * Vista pública de un usuario (lo que se devuelve en JSON). Nunca incluye el
 * hash de la contraseña ni el teléfono/email: solo lo que el front necesita
 * mostrar. {@link #de} convierte la entidad de BBDD en este DTO.
 */
public record UsuarioResponse(Long id, String nombre, String apellidos, String mote, boolean esSuperadmin) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(
            u.getId(), u.getNombre(), u.getApellidos(), u.getMote(), u.isEsSuperadmin());
    }
}
