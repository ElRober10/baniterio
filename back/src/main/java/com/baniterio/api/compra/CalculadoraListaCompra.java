package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.baniterio.api.identidad.Alternativa;

/**
 * Motor puro de la lista de la compra: aplica la fórmula tipada de una regla
 * sobre los datos de un evento y devuelve una o varias líneas con la cantidad
 * bruta (antes de restar el inventario de la fiesta, que es el bloque 2). El
 * redondeo hacia arriba lo hace {@link #ceil} y lo aplica el servicio; las líneas
 * dinámicas ya vienen redondeadas porque la regla de "1 L si es 1 botella" lo
 * necesita.
 */
public final class CalculadoraListaCompra {

    private CalculadoraListaCompra() {
    }

    /** Una persona apuntada al evento, con lo que hace falta para las fórmulas. */
    public record PersonaCompra(int diasQueVa, boolean tieneFicha, String alcohol, String refresco,
                                Alternativa alternativa) {
    }

    /** Datos agregados del evento para una tanda de cálculo. */
    public record DatosEvento(int apuntados, int diasFiesta, boolean llevaFicha, List<PersonaCompra> personas) {
    }

    /** Una línea de resultado de una regla. */
    public record LineaCalculada(String categoria, String nombre, String tamano, BigDecimal bruto,
                                 boolean dinamica, boolean necesitaFicha, int orden) {
    }

    public static BigDecimal ceil(BigDecimal v) {
        return v.setScale(0, RoundingMode.CEILING);
    }

    public static List<LineaCalculada> lineasDe(ReglaCompraEvento r, DatosEvento d) {
        String cat = r.getCategoria().name();
        BigDecimal f = r.getFactor();

        return switch (r.getTipoFormula()) {
            case POR_PENISTA -> uno(r, f.multiply(BigDecimal.valueOf(d.apuntados())), false);
            case POR_PENISTA_DIA ->
                    uno(r, f.multiply(BigDecimal.valueOf(sumaDias(d.personas(), p -> true))), false);
            case POR_DIA -> uno(r, f.multiply(BigDecimal.valueOf(d.diasFiesta())), false);
            case POR_EVENTO -> uno(r, f, false);
            case POR_CADA_N_PENISTAS -> {
                int grupos = (int) Math.ceil((double) d.apuntados() / r.getPorCada());
                yield uno(r, f.multiply(BigDecimal.valueOf(grupos)), false);
            }
            case POR_CADA_N_PENISTAS_DIA -> {
                int personasDia = sumaDias(d.personas(), p -> true);
                int grupos = (int) Math.ceil((double) personasDia / r.getPorCada());
                yield uno(r, f.multiply(BigDecimal.valueOf(grupos)), false);
            }
            case CERVEZA_ALTERNATIVA -> reglaBebida(r, d, f.multiply(BigDecimal.valueOf(
                    sumaDias(d.personas(), p -> p.tieneFicha() && p.alternativa() == Alternativa.CERVEZA))));
            case TINTO_ALTERNATIVA -> reglaBebida(r, d, f.multiply(BigDecimal.valueOf(
                    sumaDias(d.personas(), p -> p.tieneFicha() && p.alternativa() == Alternativa.TINTO_VERANO))));
            case ALCOHOL_SELECCIONADO -> dinamica(r, d, cat, true);
            case REFRESCO_SELECCIONADO -> dinamica(r, d, cat, false);
        };
    }

    private static List<LineaCalculada> uno(ReglaCompraEvento r, BigDecimal bruto, boolean necesitaFicha) {
        return List.of(new LineaCalculada(r.getCategoria().name(), r.getNombre(), r.getTamano(),
                bruto, false, necesitaFicha, r.getOrden()));
    }

    /** Regla de cerveza / tinto: si el evento no lleva ficha, línea a 0 con necesitaFicha. */
    private static List<LineaCalculada> reglaBebida(ReglaCompraEvento r, DatosEvento d, BigDecimal bruto) {
        if (!d.llevaFicha()) {
            return uno(r, BigDecimal.ZERO, true);
        }
        return uno(r, bruto, false);
    }

    private static int sumaDias(List<PersonaCompra> personas, Predicate<PersonaCompra> filtro) {
        return personas.stream().filter(filtro).mapToInt(PersonaCompra::diasQueVa).sum();
    }

    /**
     * Reglas ALCOHOL/REFRESCO_SELECCIONADO: una línea por marca elegida, orden
     * alfabético. Sin ficha en el evento, una única línea a 0 con necesitaFicha y
     * nombre "Alcohol" / "Refrescos".
     */
    private static List<LineaCalculada> dinamica(ReglaCompraEvento r, DatosEvento d, String cat, boolean esAlcohol) {
        if (!d.llevaFicha()) {
            String etiqueta = esAlcohol ? "Alcohol" : "Refrescos";
            return List.of(new LineaCalculada(cat, etiqueta, r.getTamano(), BigDecimal.ZERO, true, true, r.getOrden()));
        }
        Map<String, Integer> diasPorMarca = new LinkedHashMap<>();
        for (PersonaCompra p : d.personas()) {
            if (!p.tieneFicha()) {
                continue;
            }
            String marca = esAlcohol ? p.alcohol() : p.refresco();
            if (marca == null) {
                continue;
            }
            diasPorMarca.merge(marca, p.diasQueVa(), Integer::sum);
        }
        List<LineaCalculada> out = new ArrayList<>();
        diasPorMarca.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(e -> {
                    BigDecimal n = ceil(r.getFactor().multiply(BigDecimal.valueOf(e.getValue())));
                    String tamano = r.getTamano();
                    if (esAlcohol) {
                        tamano = n.compareTo(BigDecimal.ONE) == 0 ? "1 L" : "70 cl";
                    }
                    out.add(new LineaCalculada(cat, e.getKey(), tamano, n, true, false, r.getOrden()));
                });
        return out;
    }
}
