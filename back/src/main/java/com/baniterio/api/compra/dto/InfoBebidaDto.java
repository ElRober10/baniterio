package com.baniterio.api.compra.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contexto de una línea de bebida para ajustar la cantidad a mano: {@code personas}
 * son las personas que la beben contando cada una como fracción de los días de la fiesta
 * (dos días = 1, un día de dos = 0,5) y {@code stock} las botellas de esa marca que ya
 * hay en el inventario de la fiesta. {@code tamanos} son los tamaños con precio en la rejilla,
 * cada uno con su tienda más barata y lo que sale el litro.
 */
public record InfoBebidaDto(BigDecimal personas, BigDecimal stock, List<OpcionTamanoDto> tamanos) {
}
