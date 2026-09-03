package com.baniterio.api.evento;

/** Qué pasó al pedir borrar un evento: se borró (admin) o se creó una solicitud (creador). */
public enum ResultadoBorrado {
    BORRADO,
    SOLICITUD_CREADA
}
