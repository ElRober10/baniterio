package com.baniterio.api.admin.dto;

import com.baniterio.api.identidad.RolMembresia;
import jakarta.validation.constraints.NotNull;

/** Cuerpo JSON de {@code PUT /api/v1/admin/miembros/{id}/rol}. */
public record RolRequest(@NotNull RolMembresia rol) {
}
