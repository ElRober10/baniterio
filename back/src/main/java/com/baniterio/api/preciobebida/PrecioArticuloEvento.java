package com.baniterio.api.preciobebida;

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
 * El precio de un artículo sin variantes de tamaño (refresco, cerveza/tinto
 * de verano, limpieza y utensilios, comida) en una tienda, para un evento
 * (fila de {@code precio_articulo_evento}, ver V54). A diferencia de
 * {@link PrecioBebidaEvento} no combina tamaños: un solo precio por artículo
 * y tienda. Solo existe fila cuando hay un precio metido; vaciar la celda
 * borra la fila.
 */
@Entity
@Table(name = "precio_articulo_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrecioArticuloEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaInventario categoria;

    @Column(name = "nombre_articulo", nullable = false, length = 120)
    private String nombreArticulo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal precio;

    /** Unidades que trae el pack al que corresponde el precio (1 = se compra suelto). */
    @Column(nullable = false)
    @Builder.Default
    private int cantidad = 1;
}
