package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Guarda (o borra, si {@code precio} es null) el precio de una celda de la rejilla de artículos.
 * {@code cantidad} son las unidades del pack; sin ella, 1.
 */
public record GuardarPrecioArticuloRequest(@NotBlank String nombreArticulo,
                                           @NotNull Long tiendaId, @PositiveOrZero BigDecimal precio,
                                           @Positive Integer cantidad) {
}
