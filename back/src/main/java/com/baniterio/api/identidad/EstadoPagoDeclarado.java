package com.baniterio.api.identidad;

/** Estado de una {@link PagoDeclarado}: recién creada, o ya resuelta por un admin. */
public enum EstadoPagoDeclarado {
    PENDIENTE,
    CONFIRMADA,
    RECHAZADA
}
