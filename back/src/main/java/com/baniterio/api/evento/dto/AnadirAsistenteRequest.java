package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.EstadoAsistencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta a mano de un asistente que no tiene la app (invitado, alguien sin cuenta).
 * Lo hace un admin u organizador desde el detalle del evento.
 */
public record AnadirAsistenteRequest(@NotBlank @Size(max = 120) String nombre,
                                     @NotNull EstadoAsistencia estado) {
}
