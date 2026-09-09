package com.baniterio.api.inventario.dto;

import java.util.List;

/** Raíz de {@code GET /api/v1/inventario/evento/{id}}. Sólo las categorías con algún artículo. */
public record InventarioEventoResponse(boolean puedoEditar, List<CategoriaEventoDto> categorias) {
}
