package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

/**
 * Una línea de la lista de la compra de un evento. {@code cantidad} es lo que hay
 * que comprar (fórmula redondeada arriba, o el ajuste manual del admin);
 * {@code cantidadCalculada} es siempre la fórmula sin ajuste (la usará el bloque 2
 * para restar el inventario de la fiesta).
 */
public record LineaCompraDto(long id, String nombre, String tamano, String tienda, BigDecimal precioUnitario,
                             BigDecimal cantidad, BigDecimal cantidadCalculada, boolean ajustada, boolean dinamica,
                             boolean necesitaFicha, boolean comprada, String detalle, InfoBebidaDto info) {
}
