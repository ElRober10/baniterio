package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bloque de ficha de bebida dentro de {@code EventoDetalle.asistencia}.
 * {@code llevaFicha} es {@code false} en los eventos que no son de San Miguel
 * (y entonces {@code diasEvento} vacío y {@code miFicha} null). {@code miFicha}
 * es null si el usuario aún no ha rellenado su ficha.
 */
public record FichaBebidaDetalle(boolean llevaFicha, List<LocalDate> diasEvento, MiFicha miFicha) {

    public record MiFicha(Long alcoholBebidaId, String alcohol, Long refrescoBebidaId, String refresco,
                          String alternativa, String cervezaEspecial, boolean embarazada,
                          boolean asisteDia1, boolean asisteDia2, String modalidad,
                          BigDecimal cuota, boolean cuotaPendiente, boolean bebidaPendiente) {
    }
}
