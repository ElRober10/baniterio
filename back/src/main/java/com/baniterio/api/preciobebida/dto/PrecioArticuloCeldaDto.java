package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

/** El precio de un artículo (de un pack de {@code cantidad} unidades), en una tienda, de un evento. */
public record PrecioArticuloCeldaDto(String nombreArticulo, Long tiendaId, BigDecimal precio,
                                   int cantidad) {
}
