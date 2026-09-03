package com.baniterio.api.identidad;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Evento}. El orden lo fija el {@link Pageable} que pasa el servicio. */
public interface EventoRepository extends JpaRepository<Evento, Long> {

    Page<Evento> findByPenaId(Long penaId, Pageable pageable);
}
