package com.baniterio.api.inventario;

import java.util.List;

/**
 * Las cinco categorías del inventario de la peña. Cada una lleva su etiqueta
 * legible y la lista fija de tamaños que se pueden elegir para un artículo de
 * esa categoría (el desplegable de la interfaz y la validación del PUT salen de
 * aquí). Añadir una categoría = añadir un valor aquí y sembrar sus artículos.
 */
public enum CategoriaInventario {

    ALCOHOL("Alcohol", List.of("70 cl", "1 L", "1,5 L")),
    CERVEZA("Cerveza", List.of("lata", "botellín", "tercio")),
    REFRESCOS("Refrescos", List.of("botella", "lata", "garrafa", "brick")),
    LIMPIEZA("Limpieza y utensilios", List.of("unidad", "rollo", "paquete", "litro")),
    COMIDA("Comida", List.of("unidad", "paquete", "kg", "lata"));

    private final String etiqueta;
    private final List<String> tamanos;

    CategoriaInventario(String etiqueta, List<String> tamanos) {
        this.etiqueta = etiqueta;
        this.tamanos = tamanos;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public List<String> tamanos() {
        return tamanos;
    }

    /** {@code true} si {@code tamano} es uno de los tamaños permitidos en esta categoría. */
    public boolean permiteTamano(String tamano) {
        return tamanos.contains(tamano);
    }
}
