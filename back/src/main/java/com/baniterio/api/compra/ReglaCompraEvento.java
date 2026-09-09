package com.baniterio.api.compra;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Evento;
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
 * Regla de compra de un evento concreto (fila de {@code regla_compra_evento}, ver
 * V38). Se materializa copiando {@link ReglaCompra} la primera vez que se abre la
 * lista del evento. {@code cantidadAjustada} es el override manual del admin
 * ({@code null} = usar la fórmula); {@code activa=false} = el admin la ha quitado
 * de ese evento.
 */
@Entity
@Table(name = "regla_compra_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReglaCompraEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenReglaCompra origen;

    @Column(name = "cantidad_ajustada", precision = 8, scale = 2)
    private BigDecimal cantidadAjustada;

    @Column(nullable = false)
    private boolean activa;
}
