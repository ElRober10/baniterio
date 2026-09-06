package com.baniterio.api.evento.dto;

/**
 * Un evento pendiente de contestar y por quién es: {@code paraUsuario} soy yo
 * mismo, o —si respondo en su nombre— mi pareja o un hijo con cuenta propia.
 * El front lo usa para etiquetar "respondiendo por X" cuando no soy yo.
 */
public record PendienteRespuesta(EventoResumen evento, ParaUsuario paraUsuario) {

    public record ParaUsuario(Long id, String nombre) {
    }
}
