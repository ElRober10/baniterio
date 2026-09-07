package com.baniterio.api.cuenta.dto;

import java.math.BigDecimal;

/** Una fila de la tabla "Peñistas" del detalle de una cuenta. */
public record PenistaCuota(
        Long asistenciaId, String nombre, BigDecimal cuota, String estadoPago, String metodoPago,
        boolean camisetaPagada, boolean sudaderaPagada) {
}
