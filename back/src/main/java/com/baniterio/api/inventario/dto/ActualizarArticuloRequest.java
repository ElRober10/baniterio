package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Cuerpo de {@code PUT /api/v1/inventario/{id}}. La categoría no se puede cambiar. */
public record ActualizarArticuloRequest(
        @NotBlank String nombre,
        @NotBlank String tamano,
        @NotNull @PositiveOrZero BigDecimal cantidad) {
}
