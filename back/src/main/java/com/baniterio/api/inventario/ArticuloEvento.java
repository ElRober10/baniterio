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
 * Una línea del inventario de un evento (tabla {@code articulo_evento}, ver V37):
 * lo que se ha "enviado" del inventario general de la peña a esa fiesta. Una fila
 * por combinación evento + artículo de origen ({@code articuloInventario}), que
 * es al que se le devuelve la cantidad al deshacer el envío. {@code categoria},
 * {@code nombre} y {@code tamano} son copia congelada del momento del envío.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_inventario_id", nullable = false)
    private ArticuloInventario articuloInventario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaInventario categoria;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tamano;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private int orden;
}
