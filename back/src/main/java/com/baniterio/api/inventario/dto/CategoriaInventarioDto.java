package com.baniterio.api.inventario.dto;

import java.util.List;

/** Una categoría del inventario con sus tamaños permitidos y sus artículos. */
public record CategoriaInventarioDto(String categoria, String etiqueta,
        List<String> tamanos, List<ArticuloDto> articulos) {
}
