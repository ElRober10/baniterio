package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

/** La opción más barata de un tamaño de botella según la rejilla de precios: tienda, precio y precio por litro. */
public record OpcionTamanoDto(String tamano, String tienda, BigDecimal precio, BigDecimal precioLitro) {
}
