package com.baniterio.api.identidad;

/** Estado de pago de la cuota de una asistencia (ver ficha_bebida.estado_pago, V28). */
public enum EstadoPagoCuota {
    PENDIENTE_PAGO,
    DECLARADO,
    CONFIRMADO_PENDIENTE_ENVIO,
    CONFIRMADO_EN_CUENTA;

    public String legible() {
        return switch (this) {
            case PENDIENTE_PAGO -> "Pendiente de pago";
            case DECLARADO -> "Pagado, pendiente de confirmar";
            case CONFIRMADO_PENDIENTE_ENVIO -> "Confirmado, pendiente de ingresar en la cuenta";
            case CONFIRMADO_EN_CUENTA -> "Confirmado y en la cuenta";
        };
    }

    /** {@code true} si el dinero cuenta como recibido (en cuenta o pendiente de ingresar). */
    public boolean confirmado() {
        return this == CONFIRMADO_PENDIENTE_ENVIO || this == CONFIRMADO_EN_CUENTA;
    }
}
