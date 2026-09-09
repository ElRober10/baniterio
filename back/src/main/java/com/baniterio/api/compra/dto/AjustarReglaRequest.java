package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ajuste de una regla de un evento. {@code cantidadAjustada} null = volver a la
 * fórmula. {@code activa} false = quitar la regla de ese evento.
 */
public record AjustarReglaRequest(@PositiveOrZero BigDecimal cantidadAjustada, boolean activa) {
}
