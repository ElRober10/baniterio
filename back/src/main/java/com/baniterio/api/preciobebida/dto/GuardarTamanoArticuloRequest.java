package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Apunta el tamaño de botella (1, 1.5 o 2 litros) de un artículo. */
public record GuardarTamanoArticuloRequest(@NotBlank String nombreArticulo, @NotNull BigDecimal litros) {
}
