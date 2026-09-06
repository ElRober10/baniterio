package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/**
 * Una fila del listado de asistentes. {@code estado} es {@code APUNTADO} o
 * {@code EN_DUDA} (los {@code NO_VOY} no salen). {@code bebida} y {@code cuota}
 * son {@code null} si la asistencia no tiene ficha.
 */
public record AsistenteFila(
        String nombre,
        String estado,
        boolean esManual,
        BebidaFila bebida,
        BigDecimal cuota,
        boolean pagado) {
}
