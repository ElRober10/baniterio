package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

import com.baniterio.api.identidad.EstadoAsistencia;

/**
 * Fila de la lista de asistentes de un evento. {@code esManual} es {@code true}
 * cuando no hay usuario detrás (la añadió a mano un admin/organizador) y por
 * tanto se puede quitar; las respuestas de usuarios reales no se borran.
 * {@code cuota} y {@code modalidad} solo vienen si el alta llevaba ficha de
 * bebida (San Miguel); si no, {@code null}.
 */
public record AsistenciaResumen(Long id, String nombre, EstadoAsistencia estado, boolean esManual,
                                BigDecimal cuota, String modalidad) {
}
