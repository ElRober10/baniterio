package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

/**
 * Una regla de la lista de la compra de un evento, en el editor de "Cantidades
 * para eventos". {@code cantidadCalculada} es lo que da la fórmula ahora mismo
 * (suma de las líneas expandidas si es dinámica); {@code cantidadFinal} es
 * {@code cantidadAjustada} si no es null, y si no {@code ceil(cantidadCalculada)}.
 */
public record ReglaCompraEventoDto(Long id, String categoria, String etiqueta, String nombre, String tamano,
                                   String tipoFormula, BigDecimal factor, Integer porCada, String origen,
                                   BigDecimal cantidadCalculada, BigDecimal cantidadAjustada,
                                   BigDecimal cantidadFinal, boolean activa) {
}
