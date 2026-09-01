package com.baniterio.api.identidad;

import java.time.Instant;

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

/** Hijo declarado en el perfil de un miembro. Ver V12. */
@Entity
@Table(name = "hijo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hijo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creador_id", nullable = false)
    private Usuario creador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vinculo_pareja_id")
    private VinculoPareja vinculoPareja;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "mayor_de_edad", nullable = false)
    private boolean mayorDeEdad;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private boolean visible;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
