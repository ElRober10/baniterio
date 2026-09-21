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
 * El tamaño de botella (1, 1,5 o 2 litros) apuntado para un refresco en un
 * evento (fila de {@code tamano_articulo_evento}, ver V56). Sin fila, el
 * tamaño es el de por defecto: 2 litros.
 */
@Entity
@Table(name = "tamano_articulo_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TamanoArticuloEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "nombre_articulo", nullable = false, length = 120)
    private String nombreArticulo;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal litros;
}
