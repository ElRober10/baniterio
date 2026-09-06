package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/**
 * Alguien a quien el usuario que pregunta puede incluir en su pago: su pareja,
 * un hijo mayor de edad con cuenta, o un invitado que él añadió a mano.
 * {@code relacion} ∈ {@code {PAREJA, HIJO, INVITADO}}. {@code usuarioId} viene
 * para pareja/hijo; {@code asistenciaId} para invitado.
 */
public record PersonaPagable(
        String nombre,
        BigDecimal cuota,
        String relacion,
        Long usuarioId,
        Long asistenciaId) {
}
