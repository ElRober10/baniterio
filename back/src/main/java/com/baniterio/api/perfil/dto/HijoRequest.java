package com.baniterio.api.perfil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Un hijo tal y como lo manda el editor de perfil. {@code id} nulo = alta;
 * {@code id} presente = edición de una fila existente. La reconciliación (qué
 * hijo es nuevo, cuál se editó, cuál se borró) la hace {@code HijosReconciliador}
 * (Task 8) comparando esta lista con lo que había.
 */
public record HijoRequest(
        Long id,
        @NotBlank @Size(max = 80) String nombre,
        boolean mayorDeEdad,
        String telefono,
        boolean visible) {
}
