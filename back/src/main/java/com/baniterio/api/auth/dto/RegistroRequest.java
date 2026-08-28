package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank
        @Pattern(regexp = "^[67]\\d{8}$", message = "El teléfono debe tener 9 dígitos y empezar por 6 o 7")
        String telefono,

        @NotBlank @Email
        String email,

        @NotBlank
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        @NotBlank @Size(max = 80)
        String nombre,

        @NotBlank @Size(max = 120)
        String apellidos,

        @Size(max = 60)
        String mote) {
}
