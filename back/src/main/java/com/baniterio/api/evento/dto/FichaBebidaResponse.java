package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

/**
 * Respuesta al guardar la ficha: la modalidad y la cuota calculadas.
 * {@code cuotaPendiente} es {@code true} cuando el evento aún no tiene cuota
 * máxima ({@code cuota == null}).
 */
public record FichaBebidaResponse(String modalidad, BigDecimal cuota, boolean cuotaPendiente) {
}
