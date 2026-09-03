package com.baniterio.api.evento.dto;

import java.time.LocalDate;

/**
 * Ficha completa de un evento para su pantalla de detalle. {@code creadoPor}
 * es {@code null} en los eventos sembrados. {@code puedoEditar}/{@code puedoBorrar}
 * / {@code borradoPendiente} los calcula el servicio para el usuario que pregunta
 * (solo sirven para pintar botones; el permiso real se re-comprueba al actuar).
 */
public record EventoDetalle(Long id, String nombre, String descripcion, String lugar,
                            LocalDate fecha, LocalDate fechaFin, boolean pasado,
                            CreadoPor creadoPor, boolean puedoEditar, boolean puedoBorrar,
                            boolean borradoPendiente) {

    public record CreadoPor(Long id, String nombre) {
    }
}
