package com.baniterio.api.evento.dto;

import java.util.List;

/**
 * Eventos que el usuario tiene pendientes de contestar: hay una notificación
 * enviada y él aún no ha respondido. Ordenados por fecha ascendente (primero el
 * más próximo). La lista vacía significa "nada que responder".
 */
public record PendientesRespuestaResponse(List<EventoResumen> eventos) {
}
