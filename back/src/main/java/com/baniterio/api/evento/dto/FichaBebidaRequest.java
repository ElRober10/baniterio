package com.baniterio.api.evento.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /eventos/{id}/ficha-bebida} y de la parte {@code ficha} de
 * "añadir a mano". Para cada bebida: o {@code *BebidaId} (una del catálogo) o
 * {@code *Otra} (texto nuevo → queda pendiente de aprobación), nunca las dos. El
 * refresco es obligatorio; el alcohol no (sin ninguno = "No bebo alcohol").
 * {@code asisteDia1/2} y {@code embarazada} son {@code Boolean} (wrapper) para
 * que Jackson no falle si el cliente los omite; {@code null} = valor por defecto.
 */
public record FichaBebidaRequest(
        @NotBlank @Pattern(regexp = "APUNTADO|EN_DUDA") String estado,
        Long alcoholBebidaId,
        @Size(max = 80) String alcoholOtra,
        Long refrescoBebidaId,
        @Size(max = 80) String refrescoOtra,
        @NotBlank @Pattern(regexp = "CERVEZA|TINTO_VERANO|NADA|CERVEZA_ESPECIAL") String alternativa,
        @Size(max = 80) String cervezaEspecial,
        Boolean embarazada,
        Boolean asisteDia1,
        Boolean asisteDia2) {

    public boolean esEmbarazada() {
        return Boolean.TRUE.equals(embarazada);
    }

    public boolean vaDia1() {
        return asisteDia1 == null || asisteDia1;
    }

    public boolean vaDia2() {
        return asisteDia2 == null || asisteDia2;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    @AssertTrue(message = "alcohol: elige una del catálogo o escribe una nueva, no las dos")
    public boolean isAlcoholValido() {
        return vacio(alcoholOtra) || alcoholBebidaId == null;
    }

    @AssertTrue(message = "refresco: elige uno del catálogo o escribe uno nuevo")
    public boolean isRefrescoValido() {
        return (refrescoBebidaId != null) ^ !vacio(refrescoOtra);
    }

    @AssertTrue(message = "la cerveza especial solo se rellena si la alternativa es CERVEZA_ESPECIAL")
    public boolean isCervezaEspecialValida() {
        if ("CERVEZA_ESPECIAL".equals(alternativa)) {
            return !vacio(cervezaEspecial);
        }
        return vacio(cervezaEspecial);
    }

    @AssertTrue(message = "hay que ir al menos un día")
    public boolean isAlgunDia() {
        return vaDia1() || vaDia2();
    }
}
