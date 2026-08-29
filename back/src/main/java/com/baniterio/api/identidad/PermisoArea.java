package com.baniterio.api.identidad;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

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
 * Concesión explícita de acceso a un {@link AreaProtegida} para un usuario que
 * no es admin.
 *
 * <p>Tabla puente entre {@code usuario} y una de las áreas del panel. La
 * restricción única {@code (usuario_id, area)} impide conceder dos veces la
 * misma área al mismo usuario. {@code concedidoPor} es el admin que otorgó el
 * permiso (puede quedar {@code null} si ese usuario se borra).
 */
@Entity
@Table(name = "permiso_area")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermisoArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AreaProtegida area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concedido_por")
    private Usuario concedidoPor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
