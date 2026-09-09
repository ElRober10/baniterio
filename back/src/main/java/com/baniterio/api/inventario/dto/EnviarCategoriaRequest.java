package com.baniterio.api.inventario.dto;

import com.baniterio.api.inventario.CategoriaInventario;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/v1/inventario/enviar-categoria}. */
public record EnviarCategoriaRequest(@NotNull CategoriaInventario categoria, @NotNull Long eventoId) {
}
