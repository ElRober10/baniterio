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
 * siguientes son texto libre: por qué quiere entrar, qué relación tiene con la
 * peña y a quién conoce — se piden 10+ caracteres para evitar respuestas vacías.
 *
 * <p>{@code password} es opcional: si el solicitante la pone, se guarda hasheada
 * en la solicitud para crear el usuario ya con contraseña cuando se apruebe. Los
 * clientes envían {@code null} (no {@code ""}) cuando no hay contraseña.
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
        String conocidos,

        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String password) {
}
