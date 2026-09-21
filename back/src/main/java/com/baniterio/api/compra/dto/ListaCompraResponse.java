package com.baniterio.api.compra.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * La lista de la compra calculada de un evento. {@code puedoEditar} =
 * quien pregunta tiene el área INVENTARIO (puede ir a "Cantidades para eventos").
 * {@code presupuesto} es el saldo que muestra la cuenta del evento en su año en curso.
 */
public record ListaCompraResponse(boolean puedoEditar, boolean llevaFicha, boolean bloqueada,
                                  int apuntados, int diasFiesta, List<CategoriaListaCompraDto> categorias,
                                  BigDecimal presupuesto) {
}
