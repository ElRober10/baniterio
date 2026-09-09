package com.baniterio.api.inventario.dto;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/inventario/{id}/enviar}. */
public record EnviarAEventoRequest(@NotNull Long eventoId) {
}
