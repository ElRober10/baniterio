package com.baniterio.api.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo (opcional) de {@code POST /api/v1/admin/solicitudes/{id}/rechazar}. Si
 * {@code motivo} viene vacío o no viene, se usa el texto de rechazo por defecto.
 */
public record RechazoRequest(
        @Size(max = 2000, message = "El motivo no puede superar los 2000 caracteres")
        String motivo) {
}
