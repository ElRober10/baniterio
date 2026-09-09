package com.baniterio.api.compra.dto;

import java.time.LocalDate;

/** Ficha corta de un evento para la pantalla "Cantidades para eventos". */
public record EventoListaCompraDto(Long id, String nombre, LocalDate fecha, LocalDate fechaFin) {
}
