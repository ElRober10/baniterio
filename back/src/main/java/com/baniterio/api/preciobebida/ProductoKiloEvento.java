package com.baniterio.api.preciobebida;

import java.math.BigDecimal;

import com.baniterio.api.identidad.Evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Precio por kilo y peso estimado de un embutido comprado en Jamones Duriber,
 * para un evento (fila de {@code producto_kilo_evento}, ver V57).
 */
@Entity
@Table(name = "producto_kilo_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoKiloEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "nombre_articulo", nullable = false, length = 120)
    private String nombreArticulo;

    @Column(name = "precio_kilo", nullable = false, precision = 8, scale = 2)
    private BigDecimal precioKilo;

    @Column(name = "peso_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal pesoKg;
}
