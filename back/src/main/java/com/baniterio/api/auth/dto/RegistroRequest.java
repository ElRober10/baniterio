package com.baniterio.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo JSON que espera {@code POST /api/v1/auth/registro}. Es un DTO
 * (objeto de transporte): solo datos, sin lógica.
 *
 * <p>Las anotaciones {@code @NotBlank}, {@code @Pattern}, {@code @Size},
 * {@code @Email} son "Bean Validation": cuando el controlador marca el
 * parámetro con {@code @Valid}, Spring comprueba estas reglas ANTES de entrar
 * al método. Si alguna falla, lanza {@code MethodArgumentNotValidException},
 * que {@code ApiExceptionHandler} convierte en un 400 con el detalle por campo.
 */
public record RegistroRequest(
        @NotBlank
        @Pattern(regexp = "^[67]\\d{8}$", message = "El teléfono debe tener 9 dígitos y empezar por 6 o 7")
        String telefono,

        @NotBlank @Email
        @Size(max = 160, message = "El email no puede superar los 160 caracteres")
        String email,

        // El máximo de 72 no es cosmético: BCrypt trunca en silencio a partir de 72 bytes.
        @NotBlank
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String password,

        @NotBlank @Size(max = 80)
        String nombre,

        @NotBlank @Size(max = 120)
        String apellidos,

        @Size(max = 60)
        String mote) {
}
