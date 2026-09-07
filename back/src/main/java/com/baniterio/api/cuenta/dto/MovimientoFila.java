package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla. */
public record MovimientoFila(String concepto, BigDecimal importe, LocalDate fecha, BigDecimal saldoTras) {
}
