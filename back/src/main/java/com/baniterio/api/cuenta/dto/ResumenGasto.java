package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;

/** Total gastado en una categoría (para el resumen del detalle de la cuenta). */
public record ResumenGasto(String categoria, BigDecimal total) {
}
