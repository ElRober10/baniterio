package com.baniterio.api.evento.dto;

import java.time.LocalDate;

/** Ficha mínima de un evento "abierto" (no pasado, no oculto) para el selector de "enviar a evento". */
public record EventoAbiertoDto(Long id, String nombre, LocalDate fecha) {
}
