package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una fila del listado de asistentes. {@code estado} es {@code APUNTADO} o
 * {@code EN_DUDA} (los {@code NO_VOY} no salen). {@code bebida} y {@code cuota}
 * son {@code null} si la asistencia no tiene ficha. {@code estadoPago} es uno de
 * {@code EstadoPagoCuota} ({@code null} si no hay ficha). Si el pago está
 * confirmado, van rellenos {@code metodoPago}, {@code pagadoPor} (nombre de quien
 * lo confirmó) y {@code pagadoAt}.
 */
public record AsistenteFila(
        String nombre,
        String estado,
        boolean esManual,
        BebidaFila bebida,
        BigDecimal cuota,
        String estadoPago,
        Long asistenciaId,
        String metodoPago,
        String pagadoPor,
        Instant pagadoAt) {
}
