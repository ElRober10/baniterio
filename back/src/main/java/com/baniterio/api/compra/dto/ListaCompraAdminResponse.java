package com.baniterio.api.compra.dto;

import java.util.List;

/** El editor de la lista de la compra de un evento: todas las reglas (activas e inactivas). */
public record ListaCompraAdminResponse(EventoListaCompraDto evento, boolean bloqueada, int apuntados,
                                       int diasFiesta, List<ReglaCompraEventoDto> reglas) {
}
