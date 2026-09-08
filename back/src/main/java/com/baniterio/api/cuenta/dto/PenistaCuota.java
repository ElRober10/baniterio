package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;

/**
 * Una fila de la tabla "Peñistas" del detalle de una cuenta. {@code ingreso} y
 * {@code saldoTras} van a {@code null} mientras la cuota no esté cobrada; cuando
 * lo está, son el importe que entró y el saldo de la cuenta justo después.
 */
public record PenistaCuota(
        Long asistenciaId, String nombre, BigDecimal cuota, String estadoPago, String metodoPago,
        int camisetaCantidad, String camisetaTalla, int sudaderaCantidad, String sudaderaTalla,
        BigDecimal ingreso, BigDecimal saldoTras) {
}
