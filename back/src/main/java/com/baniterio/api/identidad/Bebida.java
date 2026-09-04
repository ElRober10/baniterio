package com.baniterio.api.identidad;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
 * Una bebida del catálogo de la ficha de San Miguel (fila de {@code bebida}, ver
 * V22). Las {@code ACEPTADA} salen en los desplegables; las {@code PENDIENTE} las
 * ha propuesto alguien con "Otra…" y esperan a que un admin las acepte o rechace.
 * {@code propuestaPor} es {@code null} en las sembradas.
 */
@Entity
@Table(name = "bebida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bebida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TipoBebida tipo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoBebida estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propuesta_por_id")
    private Usuario propuestaPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
