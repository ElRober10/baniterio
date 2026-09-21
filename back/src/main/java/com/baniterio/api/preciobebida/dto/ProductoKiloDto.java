package com.baniterio.api.preciobebida.dto;

import java.math.BigDecimal;

/** Precio por kilo y peso estimado de un embutido de Jamones Duriber (nulos si aún no se ha apuntado). */
public record ProductoKiloDto(String nombreArticulo, BigDecimal precioKilo, BigDecimal pesoKg) {
}
