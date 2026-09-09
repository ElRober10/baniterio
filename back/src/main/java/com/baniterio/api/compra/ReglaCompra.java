package com.baniterio.api.compra;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.inventario.CategoriaInventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Plantilla global de reglas de compra de la peña (fila de {@code regla_compra},
 * ver V38). Una fila por artículo, con su fórmula tipada. Se copia a
 * {@link ReglaCompraEvento} la primera vez que se abre la lista de un evento.
 */
@Entity
@Table(name = "regla_compra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReglaCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pena_id", nullable = false)
    private Pena pena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaInventario categoria;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tamano;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_formula", nullable = false, length = 30)
    private TipoFormulaCompra tipoFormula;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal factor;

    @Column(name = "por_cada")
    private Integer porCada;

    @Column(nullable = false)
    private int orden;
}
