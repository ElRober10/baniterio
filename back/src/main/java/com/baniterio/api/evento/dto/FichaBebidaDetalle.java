package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bloque de ficha de bebida dentro de {@code EventoDetalle.asistencia}.
 * {@code llevaFicha} es {@code false} en los eventos que no son de San Miguel
 * (y entonces {@code diasEvento} vacío y {@code miFicha} null). {@code miFicha}
 * es null si el usuario aún no ha rellenado su ficha.
 */
public record FichaBebidaDetalle(boolean llevaFicha, List<LocalDate> diasEvento, MiFicha miFicha) {

    /**
     * {@code estadoPago} es uno de {@code EstadoPagoCuota}. Si está confirmado
     * ({@code CONFIRMADO_PENDIENTE_ENVIO} o {@code CONFIRMADO_EN_CUENTA}), van
     * rellenos {@code metodoPago}, {@code pagadoPor} (nombre de quien lo confirmó)
     * y {@code pagadoAt}.
     */
    public record MiFicha(Long alcoholBebidaId, String alcohol, Long refrescoBebidaId, String refresco,
                          String alternativa, String cervezaEspecial, boolean embarazada,
                          boolean asisteDia1, boolean asisteDia2, String modalidad,
                          BigDecimal cuota, boolean cuotaPendiente, boolean bebidaPendiente,
                          String estadoPago, String metodoPago, String pagadoPor, Instant pagadoAt,
                          MiPagoDeclarado miPagoDeclarado) {
    }
}
