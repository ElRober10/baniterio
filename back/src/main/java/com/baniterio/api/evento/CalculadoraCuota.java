package com.baniterio.api.evento;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.baniterio.api.identidad.Modalidad;

/**
 * Calcula la modalidad de peñista y el importe de la cuota a partir de la ficha
 * de bebida de San Miguel. Lógica pura (sin Spring) para poder probar toda la
 * matriz de casos con tests unitarios.
 *
 * <p>Un administrador solo fija el precio de cubatas; las otras 4 cuotas
 * ({@link Cuotas}) salen solas con {@link #derivar}. Elegir cuál de las 5 toca
 * según la modalidad es cosa de {@link #calcular}. Precedencia (la primera que
 * encaje manda):
 * <ol>
 *   <li>{@code embarazada} → {@link Modalidad#EMBARAZADA}, {@link Cuotas#embarazada()}
 *       (no varía por días).</li>
 *   <li>va exactamente 1 de los 2 días → {@link Modalidad#UN_DIA}, {@link Cuotas#cervezas1Dia()}
 *       si no bebe alcohol, si no {@link Cuotas#cubatas1Dia()}.</li>
 *   <li>no bebe alcohol → {@link Modalidad#SOLO_CERVEZA}, {@link Cuotas#cervezas()} (da igual
 *       la alternativa que elija —nada, cerveza, cerveza especial o tinto de verano—: sin
 *       alcohol siempre paga esa cuota, la misma que solo beber cerveza).</li>
 *   <li>resto → {@link Modalidad#COMPLETA}, {@link Cuotas#cubatas()}.</li>
 * </ol>
 * Si la cuota que toca es {@code null} (el administrador aún no la ha puesto),
 * la cuota del resultado es {@code null}, pero la modalidad se calcula igual.
 */
public final class CalculadoraCuota {

    private CalculadoraCuota() {
    }

    /** Las 5 cuotas del evento, tal como las fija un administrador. */
    public record Cuotas(BigDecimal cubatas, BigDecimal cervezas,
                         BigDecimal cubatas1Dia, BigDecimal cervezas1Dia,
                         BigDecimal embarazada) {
    }

    public record Resultado(Modalidad modalidad, BigDecimal cuota) {
    }

    private static final BigDecimal DIEZ = BigDecimal.valueOf(10);
    private static final BigDecimal DOS = BigDecimal.valueOf(2);
    private static final BigDecimal EMBARAZADA_FIJA = BigDecimal.valueOf(5);

    /**
     * Deriva las 5 cuotas a partir de la única que fija un administrador
     * (cubatas): es la única editable, las demás salen solas con la fórmula
     * histórica de San Miguel. {@code cubatas == null} → sin cuotas puestas
     * (las 5 salen {@code null}).
     * <ul>
     *   <li>cubatas = M (el precio puesto)</li>
     *   <li>cervezas = M − 10 (mínimo 0)</li>
     *   <li>cubatas 1 día = M/2 + 1</li>
     *   <li>cervezas 1 día = cervezas/2 + 1</li>
     *   <li>embarazada = 5 € fijo</li>
     * </ul>
     */
    public static Cuotas derivar(BigDecimal cubatas) {
        if (cubatas == null) {
            return new Cuotas(null, null, null, null, null);
        }
        BigDecimal cervezas = cubatas.subtract(DIEZ).max(BigDecimal.ZERO);
        BigDecimal cubatas1Dia = cubatas.divide(DOS, 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
        BigDecimal cervezas1Dia = cervezas.divide(DOS, 4, RoundingMode.HALF_UP).add(BigDecimal.ONE);
        return new Cuotas(
                cubatas.setScale(2, RoundingMode.HALF_UP),
                cervezas.setScale(2, RoundingMode.HALF_UP),
                cubatas1Dia.setScale(2, RoundingMode.HALF_UP),
                cervezas1Dia.setScale(2, RoundingMode.HALF_UP),
                EMBARAZADA_FIJA.setScale(2, RoundingMode.HALF_UP));
    }

    public static Resultado calcular(Cuotas cuotas, boolean embarazada,
                                     boolean asisteDia1, boolean asisteDia2,
                                     boolean bebeAlcohol) {
        Modalidad modalidad = modalidad(embarazada, asisteDia1, asisteDia2, bebeAlcohol);
        BigDecimal cuota = importe(modalidad, !bebeAlcohol, cuotas);
        return new Resultado(modalidad, cuota);
    }

    private static Modalidad modalidad(boolean embarazada, boolean asisteDia1, boolean asisteDia2,
                                       boolean bebeAlcohol) {
        if (embarazada) {
            return Modalidad.EMBARAZADA;
        }
        if (asisteDia1 ^ asisteDia2) {
            return Modalidad.UN_DIA;
        }
        if (!bebeAlcohol) {
            return Modalidad.SOLO_CERVEZA;
        }
        return Modalidad.COMPLETA;
    }

    private static BigDecimal importe(Modalidad modalidad, boolean cervezas, Cuotas cuotas) {
        BigDecimal bruto = switch (modalidad) {
            case EMBARAZADA -> cuotas.embarazada();
            case UN_DIA -> cervezas ? cuotas.cervezas1Dia() : cuotas.cubatas1Dia();
            case SOLO_CERVEZA -> cuotas.cervezas();
            case COMPLETA -> cuotas.cubatas();
        };
        return bruto == null ? null : bruto.setScale(2, RoundingMode.HALF_UP);
    }
}
