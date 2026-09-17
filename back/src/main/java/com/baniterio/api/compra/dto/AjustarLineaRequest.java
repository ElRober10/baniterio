package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Ajuste manual de la cantidad final de una línea, con la lista bloqueada. */
public record AjustarLineaRequest(@NotNull @PositiveOrZero BigDecimal cantidad) {
}
