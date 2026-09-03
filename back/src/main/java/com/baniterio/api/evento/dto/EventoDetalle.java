package com.baniterio.api.evento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ficha completa de un evento para su pantalla de detalle. {@code creadoPor}
 * es {@code null} en los eventos sembrados. {@code puedoEditar}/{@code puedoBorrar}
 * / {@code borradoPendiente} los calcula el servicio para el usuario que pregunta
 * (solo sirven para pintar botones; el permiso real se re-comprueba al actuar).
 */
public record EventoDetalle(Long id, String nombre, String descripcion, String lugar,
                            LocalDate fecha, LocalDate fechaFin, boolean pasado,
                            CuentaRef cuenta, BigDecimal cuotaMaxima, CreadoPor creadoPor,
                            boolean puedoEditar, boolean puedoBorrar, boolean borradoPendiente,
                            AsistenciaDetalle asistencia) {

    public record CreadoPor(Long id, String nombre) {
    }
}
