package com.baniterio.api.perfil.dto;

import java.util.List;

/**
 * Una tarjeta de miembro para la sección Miembros ({@code GET /api/v1/miembros}).
 * Solo datos públicos dentro de la peña: <b>nunca</b> teléfono ni email.
 *
 * @param sobreMi      texto libre; {@code null} si está vacío.
 * @param imagenUrl    {@code /api/v1/media/avatares/{ref}.png} o
 *                     {@code /api/v1/media/fotos/{ref}} según el tipo.
 * @param parejaNombre nombre de la pareja si este miembro tiene un vínculo vivo;
 *                     {@code null} si no.
 * @param hijos        nombres de los hijos de este miembro (y de su vínculo
 *                     aceptado) marcados como visibles y sin cuenta propia.
 */
public record TarjetaMiembroResponse(
        Long id,
        String nombre,
        String apellidos,
        String mote,
        String sobreMi,
        String imagenUrl,
        String parejaNombre,
        List<String> hijos) {
}
