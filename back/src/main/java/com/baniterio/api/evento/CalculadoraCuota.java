package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;

import com.baniterio.api.identidad.Alternativa;
import com.baniterio.api.identidad.Modalidad;

/**
 * Calcula la modalidad de peñista y el importe de la cuota a partir de la ficha
 * de bebida de San Miguel. Lógica pura (sin Spring) para poder probar toda la
 * matriz de casos con tests unitarios.
 *
 * <p>Precedencia (la primera que encaje manda):
 * <ol>
 *   <li>{@code embarazada} → {@link Modalidad#EMBARAZADA}, cuota {@code 5}.</li>
 *   <li>va exactamente 1 de los 2 días → {@link Modalidad#UN_DIA}, cuota {@code M/2 + 1}.</li>
 *   <li>no bebe alcohol y la alternativa es cerveza / tinto de verano →
 *       {@link Modalidad#SOLO_CERVEZA}, cuota {@code max(M - 10, 0)}.</li>
 *   <li>resto → {@link Modalidad#COMPLETA}, cuota {@code M}.</li>
 * </ol>
 * Si {@code cuotaMaxima} es {@code null} la cuota es {@code null} (el evento aún
 * no tiene cuota máxima), pero la modalidad se calcula igual.
 */
public final class CalculadoraCuota {

    private static final BigDecimal CUOTA_EMBARAZADA = new BigDecimal("5");
    private static final BigDecimal DESCUENTO_SOLO_CERVEZA = new BigDecimal("10");
    private static final BigDecimal RECARGO_UN_DIA = BigDecimal.ONE;
    private static final Set<Alternativa> ALTERNATIVAS_CERVEZA =
            EnumSet.of(Alternativa.CERVEZA, Alternativa.CERVEZA_ESPECIAL, Alternativa.TINTO_VERANO);

    private CalculadoraCuota() {
    }

    public record Resultado(Modalidad modalidad, BigDecimal cuota) {
    }

    public static Resultado calcular(BigDecimal cuotaMaxima, boolean embarazada,
                                     boolean asisteDia1, boolean asisteDia2,
                                     boolean bebeAlcohol, Alternativa alternativa) {
        Modalidad modalidad = modalidad(embarazada, asisteDia1, asisteDia2, bebeAlcohol, alternativa);
        BigDecimal cuota = cuotaMaxima == null ? null : importe(modalidad, cuotaMaxima);
        return new Resultado(modalidad, cuota);
    }

    private static Modalidad modalidad(boolean embarazada, boolean asisteDia1, boolean asisteDia2,
                                       boolean bebeAlcohol, Alternativa alternativa) {
        if (embarazada) {
            return Modalidad.EMBARAZADA;
        }
        if (asisteDia1 ^ asisteDia2) {
            return Modalidad.UN_DIA;
        }
        if (!bebeAlcohol && ALTERNATIVAS_CERVEZA.contains(alternativa)) {
            return Modalidad.SOLO_CERVEZA;
        }
        return Modalidad.COMPLETA;
    }

    private static BigDecimal importe(Modalidad modalidad, BigDecimal m) {
        BigDecimal bruto = switch (modalidad) {
            case EMBARAZADA -> CUOTA_EMBARAZADA;
            case UN_DIA -> m.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP).add(RECARGO_UN_DIA);
            case SOLO_CERVEZA -> m.subtract(DESCUENTO_SOLO_CERVEZA).max(BigDecimal.ZERO);
            case COMPLETA -> m;
        };
        return bruto.setScale(2, RoundingMode.HALF_UP);
    }
}
