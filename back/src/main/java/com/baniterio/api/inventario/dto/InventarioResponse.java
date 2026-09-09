package com.baniterio.api.inventario.dto;

import java.util.List;

/**
 * Raíz de {@code GET /api/v1/inventario}: si quien pregunta puede editar
 * ({@code puedoEditar}) y las cinco categorías, cada una con sus artículos
 * (puede venir vacía, p. ej. Comida).
 */
public record InventarioResponse(boolean puedoEditar, List<CategoriaInventarioDto> categorias) {
}
