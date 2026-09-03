package com.baniterio.api.evento.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /eventos/solicitudes}: un mensaje opcional del solicitante. */
public record CrearSolicitudEventoRequest(@Size(max = 500) String mensaje) {
}
