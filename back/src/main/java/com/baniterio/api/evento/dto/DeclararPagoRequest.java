package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.util.List;

import com.baniterio.api.identidad.MetodoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de {@code POST /eventos/{id}/pagos-declarados}. {@code cubreUsuarioIds}
 * son pareja/hijos con cuenta; {@code cubreAsistenciaIds} son invitados propios.
 * La asistencia del propio declarante la añade el servicio.
 */
public record DeclararPagoRequest(
        @NotNull @Positive BigDecimal importe,
        @NotNull MetodoPago metodo,
        List<Long> cubreUsuarioIds,
        List<Long> cubreAsistenciaIds) {
}
