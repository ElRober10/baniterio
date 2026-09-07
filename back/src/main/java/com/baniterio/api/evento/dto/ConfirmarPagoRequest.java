package com.baniterio.api.evento.dto;

import com.baniterio.api.identidad.MetodoPago;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de `PUT /eventos/{id}/asistencias/{asistenciaId}/pago`. */
public record ConfirmarPagoRequest(@NotNull MetodoPago metodo) {
}
