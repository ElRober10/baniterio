package com.baniterio.api.compra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor puro de "qué botellas comprar para una marca de alcohol": dado lo que
 * falta por cubrir (en cl, ya descontado el stock de la fiesta) y las opciones
 * de compra disponibles (tamaño + tienda + precio, de la rejilla de
 * {@code precio_bebida_evento}), busca la combinación de botellas de coste
 * mínimo que llegue o se pase de ese objetivo — pueden repetirse tamaños y
 * mezclar tiendas. Es la aplicación de la regla "compra siempre lo más
 * económico" para bebidas alcohólicas.
 */
public final class OptimizadorPrecioBebida {

    private OptimizadorPrecioBebida() {
    }

    /** Una opción de compra: tal tamaño, en tal tienda, a tal precio. */
    public record OpcionPrecio(String tamano, int cl, String tienda, BigDecimal precio) {
    }

    /** Una botella a comprar: tamaño + tienda, cuántas unidades, a qué precio cada una. */
    public record ItemCompra(String tamano, String tienda, int cantidad, BigDecimal precioUnitario) {
    }

    private static final Pattern TAMANO = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(cl|l)", Pattern.CASE_INSENSITIVE);

    /**
     * Convierte un tamaño de botella ("70 cl", "1 L", "1.75 L"...) a
     * centilitros. Vacío si el texto no tiene el formato "número + cl/L"
     * (p. ej. un tamaño puesto a mano con otro texto) — esas opciones se
     * ignoran en vez de romper el cálculo.
     */
    public static Optional<Integer> parseCl(String tamano) {
        if (tamano == null) {
            return Optional.empty();
        }
        Matcher m = TAMANO.matcher(tamano.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        BigDecimal numero = new BigDecimal(m.group(1).replace(',', '.'));
        BigDecimal cl = "l".equalsIgnoreCase(m.group(2)) ? numero.multiply(BigDecimal.valueOf(100)) : numero;
        return Optional.of(cl.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    /**
     * Combinación de botellas más barata que cubra al menos {@code restanteCl}
     * centilitros, a partir de {@code opciones}. {@code Optional.empty()} si no
     * hay ninguna opción usable (el llamante debe caer a otro criterio); lista
     * vacía si no hace falta comprar nada ({@code restanteCl <= 0}).
     */
    public static Optional<List<ItemCompra>> combinacionMasBarata(int restanteCl, List<OpcionPrecio> opciones) {
        List<OpcionPrecio> usables = opciones.stream().filter(o -> o.cl() > 0).toList();
        if (usables.isEmpty()) {
            return Optional.empty();
        }
        if (restanteCl <= 0) {
            return Optional.of(List.of());
        }

        // dp[j] = coste mínimo para cubrir al menos j cl; elegido[j] = última
        // opción usada para llegar a dp[j] (null en j = 0).
        BigDecimal[] dp = new BigDecimal[restanteCl + 1];
        OpcionPrecio[] elegido = new OpcionPrecio[restanteCl + 1];
        dp[0] = BigDecimal.ZERO;
        for (int j = 1; j <= restanteCl; j++) {
            for (OpcionPrecio o : usables) {
                int anterior = Math.max(0, j - o.cl());
                if (dp[anterior] == null) {
                    continue;
                }
                BigDecimal coste = dp[anterior].add(o.precio());
                if (dp[j] == null || coste.compareTo(dp[j]) < 0) {
                    dp[j] = coste;
                    elegido[j] = o;
                }
            }
        }

        Map<String, ItemCompra> agrupado = new LinkedHashMap<>();
        int j = restanteCl;
        while (j > 0) {
            OpcionPrecio o = elegido[j];
            String clave = o.tamano() + "|" + o.tienda();
            agrupado.merge(clave, new ItemCompra(o.tamano(), o.tienda(), 1, o.precio()),
                    (a, b) -> new ItemCompra(a.tamano(), a.tienda(), a.cantidad() + 1, a.precioUnitario()));
            j = Math.max(0, j - o.cl());
        }
        return Optional.of(new ArrayList<>(agrupado.values()));
    }
}
