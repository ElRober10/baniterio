package com.baniterio.api.identidad;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una peña (fila de la tabla {@code pena}). El {@code slug} es su identificador
 * "bonito" para URLs y para buscarla por texto ({@code baniterio}). Mismo patrón
 * JPA + Lombok que {@link Usuario}.
 */
@Entity
@Table(name = "pena")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 60, unique = true)
    private String slug;

    @Column(nullable = false)
    private boolean activa;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
