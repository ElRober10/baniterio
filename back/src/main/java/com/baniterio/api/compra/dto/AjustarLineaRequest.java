package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ajuste manual de la cantidad final de una línea, con la lista bloqueada. {@code tamano}
 * (opcional, solo bebidas alcohólicas) cambia el tamaño de la botella de la línea y
 * {@code tienda} (opcional) cambia la tienda donde se compra.
 */
public record AjustarLineaRequest(@NotNull @PositiveOrZero BigDecimal cantidad, String tamano, String tienda) {
}
