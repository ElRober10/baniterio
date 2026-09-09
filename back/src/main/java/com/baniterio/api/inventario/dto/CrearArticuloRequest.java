package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import com.baniterio.api.inventario.CategoriaInventario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Cuerpo de {@code POST /api/v1/inventario}: alta de un artículo en una categoría. */
public record CrearArticuloRequest(
        @NotNull CategoriaInventario categoria,
        @NotBlank String nombre,
        @NotBlank String tamano,
        @NotNull @PositiveOrZero BigDecimal cantidad) {
}
