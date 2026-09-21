package com.baniterio.api.preciobebida;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * El tamaño de botella (litros) de cada refresco en un evento. Si el evento no
 * lo tiene apuntado, hereda el último que se apuntó en otro evento de la peña,
 * de modo que en eventos futuros el tamaño ya viene puesto. Sin nada apuntado
 * nunca, {@link #POR_DEFECTO} (2 litros).
 */
@Component
public class TamanosArticulo {

    public static final BigDecimal POR_DEFECTO = new BigDecimal("2");

    private final TamanoArticuloEventoRepository repo;

    public TamanosArticulo(TamanoArticuloEventoRepository repo) {
        this.repo = repo;
    }

    /** Litros por nombre de artículo, solo para los que tienen algo apuntado (en este evento o en uno anterior). */
    public Map<String, BigDecimal> apuntados(Long eventoId) {
        Map<String, BigDecimal> out = new HashMap<>();
        // De la peña entera, el id más alto de cada nombre gana; luego el propio evento manda.
        for (TamanoArticuloEvento t : repo.findDeLaPenaDelEvento(eventoId)) {
            out.put(t.getNombreArticulo(), t.getLitros());
        }
        for (TamanoArticuloEvento t : repo.findByEventoId(eventoId)) {
            out.put(t.getNombreArticulo(), t.getLitros());
        }
        return out;
    }

    public BigDecimal de(Map<String, BigDecimal> apuntados, String nombreArticulo) {
        return apuntados.getOrDefault(nombreArticulo, POR_DEFECTO);
    }
}
