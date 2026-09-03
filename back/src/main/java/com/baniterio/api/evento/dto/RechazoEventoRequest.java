package com.baniterio.api.evento.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo (opcional) de {@code POST /admin/solicitudes-evento/{id}/rechazar}. */
public record RechazoEventoRequest(@Size(max = 500) String motivo) {
}
