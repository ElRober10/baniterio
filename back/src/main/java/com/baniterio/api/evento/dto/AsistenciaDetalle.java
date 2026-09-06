package com.baniterio.api.evento.dto;

import java.time.Instant;

/**
 * Bloque de asistencia que se mete en {@link EventoDetalle}. {@code miAsistencia}
 * es el {@code name()} del estado del usuario que pregunta, o {@code null} si aún
 * no ha respondido. {@code puedeNotificar} pinta el botón "Mandar notificación"
 * (admin u organizador y evento no pasado). {@code notificacionReenviableAt} es
 * cuándo se podrá reenviar (último envío + 48 h), o {@code null} si nunca se ha
 * mandado. {@code notificacionMandada} es {@code true} en cuanto se ha mandado la
 * convocatoria alguna vez: hasta entonces la web y el móvil ocultan los
 * recuentos. {@code ficha} es el sub-bloque de la ficha de bebida (pieza 3b);
 * {@code ficha.llevaFicha=false} en los eventos que no son de San Miguel.
 */
public record AsistenciaDetalle(String miAsistencia, boolean puedeNotificar,
                                Instant notificacionReenviableAt, boolean notificacionMandada,
                                int apuntados, int noVoy, int enDuda, int sinContestar,
                                FichaBebidaDetalle ficha) {
}
