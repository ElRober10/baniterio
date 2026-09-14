package com.baniterio.api.inventario;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Evento;

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
 * Una línea del inventario de un evento (tabla {@code articulo_evento}, ver V37 y
 * V39). Una fila por producto {@code (evento, categoria, nombre, tamano)}. Puede
 * tener parte de stock ({@code cantidad}, con {@code articuloInventario} de
 * origen, al que se le devuelve al deshacer el envío) y parte comprada desde la
 * lista de la compra ({@code cantidadComprada}, sin origen, que se devuelve a la
 * lista). {@code categoria}, {@code nombre} y {@code tamano} son copia congelada.
 */
@Entity
@Table(name = "articulo_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticuloEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    /** Artículo de origen de la parte de stock; {@code null} si toda la fila viene de la lista de la compra. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_inventario_id")
    private ArticuloInventario articuloInventario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaInventario categoria;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tamano;

    /** Parte de stock: viene del inventario general y vuelve a él al devolver. */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidad;

    /** Parte comprada desde la lista de la compra; vuelve a la lista al devolver (V39). */
    @Column(name = "cantidad_comprada", nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidadComprada;

    @Column(nullable = false)
    private int orden;
}
