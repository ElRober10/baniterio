package com.baniterio.api.preciobebida;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link PrecioBebidaEvento}. */
public interface PrecioBebidaEventoRepository extends JpaRepository<PrecioBebidaEvento, Long> {

    List<PrecioBebidaEvento> findByEventoId(Long eventoId);

    Optional<PrecioBebidaEvento> findByEventoIdAndBebidaIdAndTamanoAndTiendaId(
            Long eventoId, Long bebidaId, String tamano, Long tiendaId);
}
