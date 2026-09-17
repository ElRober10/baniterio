package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Guarda (o borra, si {@code precio} es null) el precio de una celda de la rejilla. */
public record GuardarPrecioRequest(@NotNull Long bebidaId, @NotBlank String tamano,
                                   @NotNull Long tiendaId, @PositiveOrZero BigDecimal precio) {
}
