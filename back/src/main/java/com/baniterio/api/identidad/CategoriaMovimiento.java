package com.baniterio.api.identidad;

/** Categoría de un gasto o ingreso manual del libro de una cuenta (V29). */
public enum CategoriaMovimiento {
    REFRESCOS,
    CERVEZA_Y_TINTO,
    ALCOHOL,
    COMIDA,
    HIELOS,
    MENAJE,
    ROPA,
    OTROS;

    public String legible() {
        return switch (this) {
            case REFRESCOS -> "Refrescos";
            case CERVEZA_Y_TINTO -> "Cerveza y tinto";
            case ALCOHOL -> "Alcohol";
            case COMIDA -> "Comida";
            case HIELOS -> "Hielos";
            case MENAJE -> "Menaje";
            case ROPA -> "Ropa";
            case OTROS -> "Otros";
        };
    }
}
