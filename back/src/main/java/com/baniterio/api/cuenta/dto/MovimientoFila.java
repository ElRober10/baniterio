package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del libro de una cuenta, con el saldo acumulado tras aplicarla.
 * {@code importe} positivo = entra, negativo = sale. {@code manual} = lo apuntó
 * un admin ({@code GASTO}/{@code INGRESO}), se puede borrar. {@code categoria} y
 * {@code adelantadoPor} solo en los manuales. {@code origen} = el tipo de
 * movimiento ({@code SALDO_INICIAL}, {@code CUOTA}, {@code GASTO}, {@code INGRESO}…).
 */
public record MovimientoFila(
        Long id, String concepto, String categoria, BigDecimal importe, LocalDate fecha,
        BigDecimal saldoTras, String reciboArchivo, boolean manual, String origen, String adelantadoPor) {
}
