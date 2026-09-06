package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.EstadoAsistencia;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /api/v1/eventos/{id}/asistencia}. {@code paraUsuarioId}
 * es {@code null} para responder por uno mismo; si no, es la pareja o un hijo
 * con cuenta propia por quien se responde (ver {@code VinculoFamiliarService}).
 */
public record ResponderAsistenciaRequest(@NotNull EstadoAsistencia estado, Long paraUsuarioId) {
}
