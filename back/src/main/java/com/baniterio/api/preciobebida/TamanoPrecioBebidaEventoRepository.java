package com.baniterio.api.preciobebida;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link TamanoPrecioBebidaEvento}. */
public interface TamanoPrecioBebidaEventoRepository extends JpaRepository<TamanoPrecioBebidaEvento, Long> {

    List<TamanoPrecioBebidaEvento> findByEventoIdOrderByOrdenAscIdAsc(Long eventoId);

    boolean existsByEventoId(Long eventoId);

    boolean existsByEventoIdAndTamanoIgnoreCase(Long eventoId, String tamano);
}
