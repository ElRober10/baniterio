package com.baniterio.api.preciobebida.dto;

import jakarta.validation.constraints.NotBlank;

/** Añade una pestaña de tamaño suelta a la rejilla de un evento. */
public record AnadirTamanoRequest(@NotBlank String tamano) {
}
