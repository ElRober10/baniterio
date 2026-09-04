package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.EstadoAsistencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta a mano de un asistente que no tiene la app (invitado, alguien sin cuenta).
 * Lo hace un admin u organizador desde el detalle del evento. {@code ficha} es
 * obligatoria si el evento es de San Miguel y {@code estado} es APUNTADO o
 * EN_DUDA; en otros eventos se ignora. El {@code estado} de dentro de la ficha
 * no se usa (manda el de fuera), pero debe pasar la validación igualmente.
 */
public record AnadirAsistenteRequest(@NotBlank @Size(max = 120) String nombre,
                                     @NotNull EstadoAsistencia estado,
                                     @Valid FichaBebidaRequest ficha) {
}
