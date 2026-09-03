package com.baniterio.api.evento.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/eventos/{id}/notificacion}. Texto libre opcional. */
public record MandarNotificacionRequest(@Size(max = 500) String texto) {
}
