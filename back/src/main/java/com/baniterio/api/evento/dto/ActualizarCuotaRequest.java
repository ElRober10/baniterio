package com.baniterio.api.evento.dto;

import java.math.BigDecimal;

import com.baniterio.api.identidad.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de `PUT /eventos/{id}/asistencias/{asistenciaId}/cuota`. {@code metodo} es con
 * lo que se ha pagado la diferencia; solo es obligatorio si la cuota sube y el pago
 * ya estaba confirmado.
 */
public record ActualizarCuotaRequest(
        @NotNull @DecimalMin("0.00") BigDecimal cuota,
        MetodoPago metodo) {
}
