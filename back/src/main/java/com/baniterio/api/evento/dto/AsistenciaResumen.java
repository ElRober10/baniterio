package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.EstadoAsistencia;

/**
 * Fila de la lista de asistentes de un evento. {@code esManual} es {@code true}
 * cuando no hay usuario detrás (la añadió a mano un admin/organizador) y por
 * tanto se puede quitar; las respuestas de usuarios reales no se borran.
 */
public record AsistenciaResumen(Long id, String nombre, EstadoAsistencia estado, boolean esManual) {
}
