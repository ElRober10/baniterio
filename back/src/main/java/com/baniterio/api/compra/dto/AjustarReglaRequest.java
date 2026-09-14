package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ajuste de una regla de un evento. {@code cantidadAjustada} null = volver a la
 * fórmula. {@code activa} false = quitar la regla de ese evento. {@code factor} y
 * {@code porCada} cambian la propia fórmula (p.ej. "0,5" → "0,6" por peñista y
 * día, o "1 cada 7" → "1 cada 5"); si la regla viene de la plantilla, el cambio
 * también se guarda ahí para que los próximos eventos nazcan ya con el nuevo valor.
 */
public record AjustarReglaRequest(@PositiveOrZero BigDecimal cantidadAjustada, boolean activa,
                                  @NotNull @PositiveOrZero BigDecimal factor, Integer porCada) {
}
