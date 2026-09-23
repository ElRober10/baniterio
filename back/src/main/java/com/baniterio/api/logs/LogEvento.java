package com.baniterio.api.logs;

import java.time.Instant;

import com.baniterio.api.identidad.Pena;
import com.baniterio.api.identidad.Usuario;

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
 * Una fila del registro de eventos: una petición de escritura de la API
 * (éxito o error) o un error reportado por el cliente que nunca llegó a
 * golpear el backend. Ver spec
 * {@code docs/superpowers/specs/2026-09-23-logs-eventos-design.md}.
 */
@Entity
@Table(name = "log_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pena_id", nullable = false)
    private Pena pena;

    /** {@code null} si no había sesión (login/registro nunca lo hay; un error de cliente puede no tenerla). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenLog origen;

    /** {@code null} en un log de cliente (no hubo petición HTTP de verdad, o no aplica). */
    @Column(length = 10)
    private String metodo;

    /** La ruta de la API en un log de backend; la pantalla/repositorio de origen en uno de cliente. */
    @Column(length = 300)
    private String ruta;

    /** Código HTTP de la respuesta; {@code null} en un log de cliente (nunca hubo respuesta). */
    private Integer estado;

    @Column(name = "codigo_error", length = 60)
    private String codigoError;

    @Column(columnDefinition = "text")
    private String mensaje;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
