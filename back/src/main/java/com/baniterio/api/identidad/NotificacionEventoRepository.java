package com.baniterio.api.identidad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link NotificacionEvento}. */
public interface NotificacionEventoRepository extends JpaRepository<NotificacionEvento, Long> {

    Optional<NotificacionEvento> findFirstByEventoIdOrderByEnviadaAtDesc(Long eventoId);

    boolean existsByEventoId(Long eventoId);
}
