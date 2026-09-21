package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

/**
 * Contexto de una línea de bebida para ajustar la cantidad a mano: {@code personas}
 * son las personas que la beben contando cada una como fracción de los días de la fiesta
 * (dos días = 1, un día de dos = 0,5) y {@code stock} las botellas de esa marca que ya
 * hay en el inventario de la fiesta.
 */
public record InfoBebidaDto(BigDecimal personas, BigDecimal stock) {
}
