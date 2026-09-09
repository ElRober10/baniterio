package com.baniterio.api.compra.dto;

import java.math.BigDecimal;

import com.baniterio.api.compra.TipoFormulaCompra;
import com.baniterio.api.inventario.CategoriaInventario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Alta de una regla manual en un evento. La fórmula no puede ser dinámica. */
public record CrearReglaRequest(@NotNull CategoriaInventario categoria, @NotBlank String nombre,
                                @NotBlank String tamano, @NotNull TipoFormulaCompra tipoFormula,
                                @NotNull @PositiveOrZero BigDecimal factor, Integer porCada) {
}
