package com.baniterio.api.perfil.dto;

import java.util.List;

/**
 * Mi perfil tal y como lo consume el editor y la tarjeta propia. Se devuelve
 * siempre en {@code GET /api/v1/perfil} (200), incluso si aún no hay fila en
 * {@code perfil}: en ese caso {@code completado=false}, la imagen va a
 * {@code null} y {@code nombre/apellidos/mote} salen de {@code usuario}.
 *
 * @param imagenUrl {@code /api/v1/media/avatares/{ref}.png} o
 *                  {@code /api/v1/media/fotos/{ref}} según el tipo; {@code null}
 *                  si todavía no hay imagen.
 * @param pareja    el vínculo donde soy el solicitante; {@code null} si no hay.
 * @param vinculoPendiente un vínculo {@code PENDIENTE} dirigido a mí (otra
 *                  persona dice que somos pareja y tengo que confirmar);
 *                  {@code null} si no hay.
 */
public record PerfilResponse(
        Long usuarioId,
        String nombre,
        String apellidos,
        String mote,
        String sobreMi,
        String imagenTipo,
        String imagenRef,
        String imagenUrl,
        boolean completado,
        ParejaEnPerfil pareja,
        List<HijoEnPerfil> hijos,
        VinculoPendiente vinculoPendiente) {

    /** El vínculo de pareja que yo declaré. {@code estado} es el nombre del enum {@code EstadoVinculo}. */
    public record ParejaEnPerfil(Long vinculoId, String nombre, String telefono, String estado) {
    }

    /** Un hijo mío (o de mi pareja aceptada). {@code registrado} = ya tiene cuenta enlazada. */
    public record HijoEnPerfil(
            Long id, String nombre, boolean mayorDeEdad, String telefono, boolean visible, boolean registrado) {
    }

    /** Aviso: otra persona ({@code solicitanteNombre}) declaró que somos pareja y espera mi respuesta. */
    public record VinculoPendiente(Long vinculoId, String solicitanteNombre) {
    }
}
