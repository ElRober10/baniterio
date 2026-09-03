package com.baniterio.api.evento.dto;

import java.time.LocalDate;

/** Ficha corta de un evento para el listado. {@code pasado} lo calcula el servicio. */
public record EventoResumen(Long id, String nombre, LocalDate fecha, LocalDate fechaFin,
                            String lugar, boolean pasado, CuentaRef cuenta) {
}
