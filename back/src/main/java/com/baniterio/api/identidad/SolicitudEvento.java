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
 * Petición de un miembro para crear o borrar un evento (ver V14). Espejo de
 * {@link SolicitudIngreso}. En {@code CREAR}, {@code evento} es {@code null}
 * hasta que el crédito se consume al crear el evento; en {@code BORRAR},
 * {@code evento} es el que se quiere borrar.
 */
@Entity
@Table(name = "solicitud_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pena_id", nullable = false)
    private Pena pena;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TipoSolicitudEvento tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    @Column(length = 500)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoSolicitud estado;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelta_por")
    private Usuario resueltaPor;

    @Column(name = "resuelta_at")
    private Instant resueltaAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
