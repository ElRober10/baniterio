package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Listado de asistentes de un evento de San Miguel (pieza 4): quién va, qué bebe,
 * su cuota y —de momento siempre {@code false}— si ha pagado. {@code puedoPagarPor}
 * es a quién puede cubrir en un pago el usuario que pregunta; {@code miCuota} es
 * su propia cuota ({@code null} si no tiene ficha o el evento no tiene cuotas).
 * {@code puedoConfirmarPagos} es {@code true} si quien pregunta puede confirmar
 * pagos (administrador u organizador del evento). {@code opcionesCuota} son las
 * cuotas del evento, para actualizar la de un asistente (vacío si no puede).
 */
public record ListadoAsistentesResponse(
        List<AsistenteFila> asistentes,
        BigDecimal totalCuotas,
        BigDecimal totalPagado,
        List<PersonaPagable> puedoPagarPor,
        BigDecimal miCuota,
        boolean puedoConfirmarPagos,
        List<OpcionCuota> opcionesCuota) {
}
