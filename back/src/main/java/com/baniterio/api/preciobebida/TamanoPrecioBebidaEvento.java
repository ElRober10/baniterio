package com.baniterio.api.preciobebida;

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
 * Una pestaña de tamaño de la rejilla de precio de bebidas de un evento
 * (fila de {@code tamano_precio_bebida_evento}, ver V50). Compartida por
 * todas las marcas de ese evento; se materializa con "70 cl" y "1 L" la
 * primera vez que se abre la rejilla.
 */
@Entity
@Table(name = "tamano_precio_bebida_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TamanoPrecioBebidaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(nullable = false, length = 20)
    private String tamano;

    @Column(nullable = false)
    private int orden;
}
