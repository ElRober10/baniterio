package com.baniterio.api.identidad;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
 * Declaración de pago (fila de {@code pago_declarado}, ver V27): alguien dice que
 * ha pagado su cuota de un evento de San Miguel. Queda {@code PENDIENTE} hasta que
 * un administrador la confirma (marca las {@link FichaBebida} de las asistencias
 * cubiertas como pagadas) o la rechaza. {@code cubre} son los id de
 * {@code asistencia_evento} que este pago paga: siempre la del propio declarante,
 * más pareja/hijos/invitados que haya marcado.
 */
@Entity
@Table(name = "pago_declarado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoDeclarado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "declarado_por", nullable = false)
    private Usuario declaradoPor;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 16)
    private MetodoPago metodoPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoPagoDeclarado estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por")
    private Usuario resueltoPor;

    @Column(name = "resuelto_at")
    private Instant resueltoAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "pago_declarado_cubre",
            joinColumns = @JoinColumn(name = "pago_declarado_id"))
    @Column(name = "asistencia_id", nullable = false)
    @Builder.Default
    private Set<Long> cubre = new LinkedHashSet<>();
}
