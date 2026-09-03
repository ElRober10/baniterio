package com.baniterio.api.identidad;

import java.time.Instant;

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
 * Un envío de "Mandar notificación" para un evento (fila de
 * {@code notificacion_evento}, ver V21). Se guarda una fila por envío; la más
 * reciente marca cuándo se puede reenviar (>= 48 h después).
 *
 * <p>{@code enviadaAt} es una columna normal (no {@code @CreationTimestamp}): el
 * servicio la fija al enviar y los tests pueden antedatarla para probar la
 * ventana de reenvío.
 */
@Entity
@Table(name = "notificacion_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacionEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(length = 500)
    private String texto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enviada_por_id", nullable = false)
    private Usuario enviadaPor;

    @Builder.Default
    @Column(name = "enviada_at", nullable = false)
    private Instant enviadaAt = Instant.now();
}
