package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de una cuenta, la hoja completa: cabecera con el saldo, tabla de
 * peñistas (quién ha pagado), libro de movimientos con saldo corriente y resumen
 * de gastos por categoría. Los campos de admin ({@code cobradoSinIngresar}) van a
 * {@code null} si {@code !puedoGestionar}.
 */
public record CuentaDetalle(
        Long id, String nombre, String descripcion,
        int anio, List<Integer> anios, boolean esAnioActual,
        BigDecimal saldo, BigDecimal saldoInicial, BigDecimal estimacion, BigDecimal cobradoSinIngresar,
        boolean puedoGestionar,
        BigDecimal precioCamiseta, BigDecimal precioSudadera,
        List<PenistaCuota> penistas,
        BigDecimal totalCuotas, BigDecimal totalCobrado,
        List<MovimientoFila> movimientos,
        List<ResumenGasto> resumenGastos) {
}
