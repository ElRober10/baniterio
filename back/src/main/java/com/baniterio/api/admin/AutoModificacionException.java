package com.baniterio.api.admin;

/**
 * El admin intenta degradarse o desactivarse a sí mismo. El {@code codigo}
 * distingue el caso ({@code NO_TE_PUEDES_DEGRADAR} / {@code NO_TE_PUEDES_DESACTIVAR}).
 */
public class AutoModificacionException extends RuntimeException {

    private final String codigo;

    public AutoModificacionException(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
