package com.baniterio.api.logs.dto;

/** Cuerpo de {@code POST /api/v1/logs/cliente}. Sin validación estricta a
 *  propósito: nada de lo que mande el cliente debe poder devolver un 4xx. */
public record LogClienteRequest(String origen, String pantalla, String mensaje) {
}
