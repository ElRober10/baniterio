package com.baniterio.api.preciobebida.dto;

import java.time.LocalDate;

/** Ficha corta de un evento para la sección "Precio bebidas" (años → eventos). */
public record EventoPrecioBebidaDto(Long id, String nombre, LocalDate fecha, LocalDate fechaFin) {
}
