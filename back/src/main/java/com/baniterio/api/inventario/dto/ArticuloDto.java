package com.baniterio.api.inventario.dto;

import java.math.BigDecimal;

import com.baniterio.api.inventario.ArticuloInventario;

/** Un artículo del inventario en el JSON. */
public record ArticuloDto(Long id, String nombre, String tamano, BigDecimal cantidad) {

    public static ArticuloDto de(ArticuloInventario a) {
        return new ArticuloDto(a.getId(), a.getNombre(), a.getTamano(), a.getCantidad());
    }
}
