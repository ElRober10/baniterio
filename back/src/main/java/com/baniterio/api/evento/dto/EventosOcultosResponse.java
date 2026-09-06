package com.baniterio.api.evento.dto;

import java.util.List;

/** {@code GET /api/v1/eventos/ocultos}: los eventos "borrados" (ocultos, recuperables). Solo admin/superadmin. */
public record EventosOcultosResponse(List<EventoResumen> eventos) {
}
