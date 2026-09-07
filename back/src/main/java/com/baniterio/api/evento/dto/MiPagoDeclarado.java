package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * La declaración de pago propia dentro de {@code EventoDetalle.asistencia.ficha.miFicha}.
 * Solo se rellena si la última declaración del usuario en ese evento está
 * {@code PENDIENTE} o {@code RECHAZADA} (si está confirmada, {@code miFicha.pagado}
 * ya lo dice).
 */
public record MiPagoDeclarado(
        BigDecimal importe,
        String metodoPago,
        String estado,
        Instant createdAt,
        List<CubiertoPago> cubre) {
}
