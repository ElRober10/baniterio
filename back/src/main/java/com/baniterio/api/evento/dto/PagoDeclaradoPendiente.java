package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Una fila de la cola de "Confirmar pagos" del panel de administración. */
public record PagoDeclaradoPendiente(
        Long id,
        Long eventoId,
        String eventoNombre,
        String declaradoPor,
        BigDecimal importe,
        String metodoPago,
        Instant createdAt,
        List<CubiertoPago> cubre) {
}
