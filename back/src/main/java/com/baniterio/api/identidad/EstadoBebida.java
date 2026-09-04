package com.baniterio.api.identidad;

/**
 * Estado de una bebida del catálogo. Solo las {@link #ACEPTADA} salen en los
 * desplegables; {@link #PENDIENTE} espera a que un admin la acepte o rechace.
 */
public enum EstadoBebida {
    ACEPTADA,
    PENDIENTE,
    RECHAZADA
}
