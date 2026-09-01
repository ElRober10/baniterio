package com.baniterio.api.perfil.dto;

/** Respuesta de {@code POST /api/v1/perfil/foto}: la {@code imagen_ref} ({@code {uuid}.jpg}) a usar luego en el {@code PUT}. */
public record SubirFotoResponse(String imagenRef) {
}
