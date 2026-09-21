package com.baniterio.api.preciobebida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Los embutidos que se compran siempre en {@link #PROVEEDOR}: no se comparan
 * tiendas, se apunta precio por kilo y peso estimado de la pieza. Como con
 * {@link TamanosArticulo}, lo apuntado en un evento pasa por defecto a los
 * siguientes.
 */
@Component
public class ProductosPorKilo {

    public static final String PROVEEDOR = "Jamones Duriber";
    public static final Set<String> NOMBRES = Set.of("Paletilla ibérica", "Salchichón ibérico", "Chorizo ibérico");

    /** Precio por kilo y peso estimado; el precio de la pieza es su producto. */
    public record Kilo(BigDecimal precioKilo, BigDecimal pesoKg) {
        public BigDecimal precioPieza() {
            return precioKilo.multiply(pesoKg).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private final ProductoKiloEventoRepository repo;

    public ProductosPorKilo(ProductoKiloEventoRepository repo) {
        this.repo = repo;
    }

    public Map<String, Kilo> apuntados(Long eventoId) {
        Map<String, Kilo> out = new LinkedHashMap<>();
        for (ProductoKiloEvento p : repo.findDeLaPenaDelEvento(eventoId)) {
            out.put(p.getNombreArticulo(), new Kilo(p.getPrecioKilo(), p.getPesoKg()));
        }
        for (ProductoKiloEvento p : repo.findByEventoId(eventoId)) {
            out.put(p.getNombreArticulo(), new Kilo(p.getPrecioKilo(), p.getPesoKg()));
        }
        return out;
    }
}
