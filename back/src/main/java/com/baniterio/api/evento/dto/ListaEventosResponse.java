package com.baniterio.api.evento.dto;

import java.util.List;

/**
 * Página del listado de eventos. {@code puedeCrear} = el usuario puede crear ya
 * (admin o crédito sin consumir). {@code puedeSolicitar} = no puede crear pero
 * puede pedir un crédito (siempre {@code false} para administradores).
 */
public record ListaEventosResponse(List<EventoResumen> eventos, int pagina, int totalPaginas,
                                   boolean puedeCrear, boolean puedeSolicitar) {
}
