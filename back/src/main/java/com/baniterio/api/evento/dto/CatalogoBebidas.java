package com.baniterio.api.evento.dto;

import java.util.List;

/** Las dos listas de bebidas aceptadas para los desplegables de la ficha. */
public record CatalogoBebidas(List<BebidaRef> alcohol, List<BebidaRef> refresco) {
}
