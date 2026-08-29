package com.baniterio.api.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo JSON de {@code PUT /api/v1/admin/miembros/{id}/activo}. */
public record ActivoRequest(@NotNull Boolean activo) {
}
