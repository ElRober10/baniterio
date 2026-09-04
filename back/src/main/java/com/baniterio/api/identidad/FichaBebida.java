package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La ficha de bebida de una asistencia a un evento de San Miguel (fila de
 * {@code ficha_bebida}, ver V23). 1:1 con {@link AsistenciaEvento}: la PK es la
 * FK ({@code @MapsId}). {@code alcohol} es {@code null} si no bebe alcohol.
 * {@code modalidad} y {@code cuota} las calcula el servicio; {@code cuota} es
 * {@code null} si el evento aún no tiene cuota máxima.
 */
@Entity
@Table(name = "ficha_bebida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FichaBebida {

    @Id
    @Column(name = "asistencia_id")
    private Long asistenciaId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "asistencia_id")
    private AsistenciaEvento asistencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alcohol_bebida_id")
    private Bebida alcohol;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refresco_bebida_id", nullable = false)
    private Bebida refresco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Alternativa alternativa;

    @Column(name = "cerveza_especial", length = 80)
    private String cervezaEspecial;

    @Column(nullable = false)
    private boolean embarazada;

    @Column(name = "asiste_dia_1", nullable = false)
    private boolean asisteDia1;

    @Column(name = "asiste_dia_2", nullable = false)
    private boolean asisteDia2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Modalidad modalidad;

    @Column(precision = 7, scale = 2)
    private BigDecimal cuota;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
