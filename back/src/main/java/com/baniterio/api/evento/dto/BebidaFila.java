package com.baniterio.api.evento.dto;

/**
 * Lo que bebe un asistente, tal como está en su ficha. {@code alcohol} es
 * {@code null} si no bebe alcohol, {@code refresco} si no quiere ninguno.
 * {@code alternativa} y {@code modalidad} son los nombres de los enums
 * {@code Alternativa} y {@code Modalidad}.
 */
public record BebidaFila(String alcohol, String refresco, String alternativa, String modalidad) {
}
