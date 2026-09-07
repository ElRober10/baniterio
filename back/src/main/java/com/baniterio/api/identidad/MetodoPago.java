package com.baniterio.api.identidad;

/** Cómo se ha pagado una cuota. Espejo del tipo `MetodoPago` de front y móvil. */
public enum MetodoPago {
    TRANSFERENCIA,
    BIZUM,
    EFECTIVO;

    /** Texto para mostrar al usuario. */
    public String legible() {
        return switch (this) {
            case TRANSFERENCIA -> "Transferencia";
            case BIZUM -> "Bizum";
            case EFECTIVO -> "Efectivo";
        };
    }
}
