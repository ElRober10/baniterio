package com.baniterio.api.evento.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /eventos} y {@code PUT /eventos/{id}}. La regla "la fecha
 * de fin no puede ser anterior a la de inicio" se valida con {@link #isRangoDeFechasValido()}:
 * si falla, {@code ApiExceptionHandler} ya devuelve 400 {@code VALIDACION} sin
 * código nuevo.
 */
public record GuardarEventoRequest(
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 2000) String descripcion,
        @Size(max = 160) String lugar,
        @NotNull LocalDate fecha,
        LocalDate fechaFin) {

    @AssertTrue(message = "La fecha de fin no puede ser anterior a la de inicio")
    public boolean isRangoDeFechasValido() {
        return fechaFin == null || fecha == null || !fechaFin.isBefore(fecha);
    }
}
