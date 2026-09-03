package com.baniterio.api.evento.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Ficha de una solicitud de evento para el panel de administración. */
public record SolicitudEventoResumen(Long id, String tipo, String estado, Solicitante solicitante,
                                     EventoRef evento, String mensaje, Instant createdAt) {

    public record Solicitante(Long id, String nombre, String apellidos) {
    }

    public record EventoRef(Long id, String nombre, LocalDate fecha) {
    }
}
