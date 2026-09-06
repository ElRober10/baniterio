package com.baniterio.api.evento.dto;

import java.util.List;

/**
 * Eventos pendientes de contestar: los propios y los de quien el usuario puede
 * responder en su nombre (pareja, hijos con cuenta propia). Ordenados por fecha
 * ascendente (primero el más próximo). La lista vacía significa "nada que
 * responder".
 */
public record PendientesRespuestaResponse(List<PendienteRespuesta> eventos) {
}
