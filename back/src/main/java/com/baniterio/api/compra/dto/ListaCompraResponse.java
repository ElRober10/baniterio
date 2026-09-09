package com.baniterio.api.compra.dto;

import java.util.List;

/**
 * La lista de la compra calculada de un evento. {@code puedoEditar} =
 * quien pregunta tiene el área INVENTARIO (puede ir a "Cantidades para eventos").
 */
public record ListaCompraResponse(boolean puedoEditar, boolean llevaFicha, int apuntados, int diasFiesta,
                                  List<CategoriaListaCompraDto> categorias) {
}
