package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Apunta precio por kilo y peso estimado (kg) de un embutido. */
public record GuardarProductoKiloRequest(@NotBlank String nombreArticulo,
                                         @NotNull @PositiveOrZero BigDecimal precioKilo,
                                         @NotNull @PositiveOrZero BigDecimal pesoKg) {
}
