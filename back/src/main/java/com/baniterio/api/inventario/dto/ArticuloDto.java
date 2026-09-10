package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import com.baniterio.api.inventario.ArticuloEvento;
import com.baniterio.api.inventario.ArticuloInventario;

/**
 * Un artículo en el JSON. En el inventario de la fiesta {@code cantidad} es el
 * total (stock + comprado) y {@code cantidadComprada} la parte venida de la lista
 * de la compra (V39). En el inventario general {@code cantidadComprada} es 0.
 */
public record ArticuloDto(Long id, String nombre, String tamano, BigDecimal cantidad,
                          BigDecimal cantidadComprada) {

    public static ArticuloDto de(ArticuloInventario a) {
        return new ArticuloDto(a.getId(), a.getNombre(), a.getTamano(), a.getCantidad(), BigDecimal.ZERO);
    }

    public static ArticuloDto deEvento(ArticuloEvento f) {
        return new ArticuloDto(f.getId(), f.getNombre(), f.getTamano(),
                f.getCantidad().add(f.getCantidadComprada()), f.getCantidadComprada());
    }
}
