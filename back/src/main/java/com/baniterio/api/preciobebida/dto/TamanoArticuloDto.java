package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

/** El tamaño de botella (litros) apuntado para un artículo de un evento. */
public record TamanoArticuloDto(String nombreArticulo, BigDecimal litros) {
}
