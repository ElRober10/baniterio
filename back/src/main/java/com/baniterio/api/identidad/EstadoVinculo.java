package com.baniterio.api.identidad;

/**
 * Estado de un {@link VinculoPareja}. {@code SIN_CUENTA}: la pareja aún no está
 * registrada, se da por buena. {@code PENDIENTE}: la pareja tiene cuenta y debe
 * aceptar. {@code ACEPTADO}: vínculo mutuo confirmado. {@code RECHAZADO}:
 * terminal e histórico; el solicitante puede crear otro.
 */
public enum EstadoVinculo {
    SIN_CUENTA, PENDIENTE, ACEPTADO, RECHAZADO;

    /**
     * ¿Es válido pasar de este estado a {@code destino}? Máquina de estados del
     * vínculo de pareja (ver el spec, "Flujo: vínculo de pareja"):
     * <ul>
     *   <li>{@code SIN_CUENTA → PENDIENTE} (la pareja se registra o se detecta que ya era miembro).
     *   <li>{@code PENDIENTE → ACEPTADO} / {@code PENDIENTE → RECHAZADO}.
     *   <li>{@code ACEPTADO} y {@code RECHAZADO} son terminales: solo se sale de
     *       {@code ACEPTADO} borrando la fila (romper el vínculo), no con una transición.
     * </ul>
     */
    public boolean puedePasarA(EstadoVinculo destino) {
        return switch (this) {
            case SIN_CUENTA -> destino == PENDIENTE;
            case PENDIENTE -> destino == ACEPTADO || destino == RECHAZADO;
            case ACEPTADO, RECHAZADO -> false;
        };
    }
}
