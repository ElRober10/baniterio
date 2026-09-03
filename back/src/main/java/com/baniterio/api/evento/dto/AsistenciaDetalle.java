package com.baniterio.api.evento.dto;

import java.time.Instant;

/**
 * Bloque de asistencia que se mete en {@link EventoDetalle}. {@code miAsistencia}
 * es el {@code name()} del estado del usuario que pregunta, o {@code null} si aún
 * no ha respondido. {@code puedeNotificar} pinta el botón "Mandar notificación"
 * (admin u organizador y evento no pasado). {@code notificacionReenviableAt} es
 * cuándo se podrá reenviar (último envío + 48 h), o {@code null} si nunca se ha
 * mandado. Los recuentos son para la cabecera del detalle.
 */
public record AsistenciaDetalle(String miAsistencia, boolean puedeNotificar,
                                Instant notificacionReenviableAt,
                                int apuntados, int noVoy, int enDuda, int sinContestar) {
}
