package com.baniterio.api.preciobebida.dto;

import jakarta.validation.constraints.NotBlank;

/** Alta de una tienda nueva en el catálogo de la peña. */
public record CrearTiendaRequest(@NotBlank String nombre) {
}
