package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

/**
 * Lo que cuesta una línea en una tienda según la rejilla de precios: el precio de la botella
 * (o del pack, con {@code unidades} > 1) y, en bebidas, lo que sale el litro.
 */
public record OpcionTiendaDto(String tienda, BigDecimal precio, int unidades, BigDecimal precioLitro) {
}
