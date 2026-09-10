package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
 * Un evento de la peña (fila de la tabla {@code evento}, ver V13). {@code fecha}
 * es el día de inicio: es lo que se muestra y por lo que se ordena el listado.
 * {@code fechaFin} es opcional. {@code creadoPor} puede ser {@code null} (eventos
 * sembrados). Mismo patrón JPA + Lombok que {@link Perfil}.
 */
@Entity
@Table(name = "evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pena_id", nullable = false)
    private Pena pena;

    /** Cuenta a la que va el dinero de este evento (ver V18). Obligatoria. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 2000)
    private String descripcion;

    @Column(length = 160)
    private String lugar;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    /**
     * Las 5 cuotas del evento en euros, las fija un administrador. {@code null} =
     * esa cuota aún no está puesta. {@code embarazada} no varía por días.
     */
    @Column(name = "cuota_cubatas", precision = 7, scale = 2)
    private BigDecimal cuotaCubatas;

    @Column(name = "cuota_cervezas", precision = 7, scale = 2)
    private BigDecimal cuotaCervezas;

    @Column(name = "cuota_cubatas_1dia", precision = 7, scale = 2)
    private BigDecimal cuotaCubatas1Dia;

    @Column(name = "cuota_cervezas_1dia", precision = 7, scale = 2)
    private BigDecimal cuotaCervezas1Dia;

    @Column(name = "cuota_embarazada", precision = 7, scale = 2)
    private BigDecimal cuotaEmbarazada;

    /** Precio de la camiseta / sudadera de la peña para este evento (San Miguel); {@code null} si no se venden o no se ha puesto (V29). */
    @Column(name = "precio_camiseta", precision = 7, scale = 2)
    private BigDecimal precioCamiseta;

    @Column(name = "precio_sudadera", precision = 7, scale = 2)
    private BigDecimal precioSudadera;

    /** "Borrar" un evento lo oculta en vez de borrarlo de verdad; recuperable (V25). */
    @Column(nullable = false)
    private boolean oculto;

    /**
     * Al activarlo, la lista de la compra deja de recalcularse cuando se apunta
     * más gente; el administrador sigue pudiendo ajustar reglas a mano (V39).
     */
    @Column(name = "lista_compra_bloqueada", nullable = false)
    private boolean listaCompraBloqueada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
