package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo JSON de {@code POST /api/v1/auth/solicitudes}: lo que envía alguien
 * cuyo teléfono NO está en la lista de la peña para pedir que le den acceso.
 *
 * <p>Los 4 primeros campos son de identidad (como en el registro). Los 3
 * últimos son texto libre: por qué quiere entrar, qué relación tiene con la
 * peña y a quién conoce — se piden 10+ caracteres para evitar respuestas vacías.
 */
public record SolicitudIngresoRequest(
        @NotBlank
        @Pattern(regexp = "^[67]\\d{8}$", message = "El teléfono debe tener 9 dígitos y empezar por 6 o 7")
        String telefono,

        @NotBlank @Email
        @Size(max = 160, message = "El email no puede superar los 160 caracteres")
        String email,

        @NotBlank @Size(max = 80)
        String nombre,

        @NotBlank @Size(max = 120)
        String apellidos,

        @NotBlank @Size(min = 10, max = 2000, message = "Cuéntanos un poco más (mínimo 10 caracteres)")
        String motivo,

        @NotBlank @Size(min = 10, max = 2000, message = "Cuéntanos un poco más (mínimo 10 caracteres)")
        String relacion,

        @NotBlank @Size(min = 10, max = 2000, message = "Cuéntanos un poco más (mínimo 10 caracteres)")
        String conocidos) {
}
