package com.baniterio.api.admin.dto;

import java.util.List;

import com.baniterio.api.identidad.AreaProtegida;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo JSON de {@code PUT /api/v1/admin/miembros/{id}/areas}. La lista es el
 * conjunto COMPLETO de áreas concedidas tras la operación (reemplaza lo anterior).
 */
public record AreasRequest(@NotNull List<@NotNull AreaProtegida> areas) {
}
