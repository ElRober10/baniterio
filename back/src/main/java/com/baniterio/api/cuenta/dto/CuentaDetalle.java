package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de una cuenta. {@code saldo} = suma del libro; {@code estimacion} = lo
 * que habrá cuando todos paguen. {@code porIngresar} (lo que el admin ha cobrado
 * y aún no ha pasado a la cuenta) es {@code null} si {@code !puedoGestionar}.
 */
public record CuentaDetalle(
        Long id, String nombre, String descripcion,
        BigDecimal saldo, BigDecimal estimacion,
        List<MovimientoFila> movimientos,
        boolean puedoGestionar, BigDecimal porIngresar) {
}
