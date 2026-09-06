package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
        LocalDate fechaFin,
        Long cuentaId,
        Boolean cuentaNueva,
        /** Única cuota que fija un administrador; las otras 4 se derivan (ver {@link com.baniterio.api.evento.CalculadoraCuota#derivar}). */
        @PositiveOrZero @Digits(integer = 5, fraction = 2) BigDecimal cuotaCubatas) {

    /** {@code true} si el request pide crear una cuenta nueva (campo opcional; ausente = no). */
    public boolean quiereCuentaNueva() {
        return Boolean.TRUE.equals(cuentaNueva);
    }

    @AssertTrue(message = "La fecha de fin no puede ser anterior a la de inicio")
    public boolean isRangoDeFechasValido() {
        return fechaFin == null || fecha == null || !fechaFin.isBefore(fecha);
    }

    /**
     * Hay que indicar la cuenta del evento: o una existente ({@code cuentaId}) o
     * marcar {@code cuentaNueva} para crear una con el nombre del evento. Una y
     * solo una de las dos.
     */
    @AssertTrue(message = "Indica una cuenta existente o crea una nueva, pero no ambas")
    public boolean isCuentaValida() {
        return quiereCuentaNueva() ^ (cuentaId != null);
    }
}
