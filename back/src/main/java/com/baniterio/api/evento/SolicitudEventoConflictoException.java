package com.baniterio.api.evento;

/**
 * Conflicto (409) en el flujo de solicitudes de evento. Lleva un {@code codigo}
 * estable para el front (patrón de {@code VinculoConflictoException}):
 * {@code SOLICITUD_EVENTO_YA_PENDIENTE}, {@code CREDITO_SIN_CONSUMIR},
 * {@code SOLICITUD_EVENTO_NO_APLICA}.
 */
public class SolicitudEventoConflictoException extends RuntimeException {

    private final String codigo;

    public SolicitudEventoConflictoException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
