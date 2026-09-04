package com.baniterio.api.evento.dto;

import java.time.Instant;

/** Una bebida propuesta con "Otra…" que espera aprobación del admin. */
public record BebidaPendiente(Long id, String tipo, String nombre,
                              ProponenteBebida propuestaPor, Instant createdAt) {

    public record ProponenteBebida(Long id, String nombre) {
    }
}
