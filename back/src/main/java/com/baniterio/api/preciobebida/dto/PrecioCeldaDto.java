package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

/** El precio de una marca, en un tamaño, en una tienda, de un evento. */
public record PrecioCeldaDto(Long bebidaId, String tamano, Long tiendaId, BigDecimal precio) {
}
