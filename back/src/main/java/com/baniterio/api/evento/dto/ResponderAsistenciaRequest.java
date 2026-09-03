package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.EstadoAsistencia;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PUT /api/v1/eventos/{id}/asistencia}. */
public record ResponderAsistenciaRequest(@NotNull EstadoAsistencia estado) {
}
