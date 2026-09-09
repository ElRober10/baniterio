package com.baniterio.api.inventario.dto;

import java.util.List;

/** Una categoría del inventario de un evento con sus artículos (aquí no se editan tamaños). */
public record CategoriaEventoDto(String categoria, String etiqueta, List<ArticuloDto> articulos) {
}
