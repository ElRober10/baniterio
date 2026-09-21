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
 * Una línea de la lista de la compra mostrada de un evento (tabla
 * {@code linea_compra_evento}, ver V39). Se sincroniza con el resultado del
 * cálculo en cada lectura mientras la lista no está bloqueada; una vez
 * {@code comprada}, queda congelada y enlazada a una fila de
 * {@code articulo_evento} por {@code articuloEventoId}.
 */
@Entity
@Table(name = "linea_compra_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineaCompraEvento {

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

    @Column(length = 120)
    private String tienda;

    @Column(name = "precio_unitario", precision = 10, scale = 4)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private boolean dinamica;

    @Column(name = "necesita_ficha", nullable = false)
    private boolean necesitaFicha;

    @Column(nullable = false)
    private boolean ajustada;

    @Column(nullable = false)
    private boolean comprada;

    /**
     * La necesidad de la fórmula (para su marca o artículo) cuando se modificó la línea a mano; null si
     * no está modificada. Mientras no cambie, la sincronización respeta la modificación.
     */
    @Column(name = "necesidad_base", precision = 10, scale = 2)
    private BigDecimal necesidadBase;

    /** Nota de la compra, p. ej. "2 × pack de 50" (solo si se compra en packs). */
    @Column(length = 80)
    private String detalle;

    @Column(name = "articulo_evento_id")
    private Long articuloEventoId;
}
