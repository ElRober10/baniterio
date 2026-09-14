package com.baniterio.api.compra.dto;

import java.util.List;

/** Las líneas de la lista de la compra de una categoría. */
public record CategoriaListaCompraDto(String categoria, String etiqueta, List<LineaCompraDto> lineas) {
}
