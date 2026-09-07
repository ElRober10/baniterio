package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoDeclaradoRepository extends JpaRepository<PagoDeclarado, Long> {

    Optional<PagoDeclarado> findByEventoIdAndDeclaradoPorIdAndEstado(
            Long eventoId, Long declaradoPorId, EstadoPagoDeclarado estado);

    /** La última declaración de ese usuario en ese evento, sea cual sea su estado. */
    Optional<PagoDeclarado> findFirstByEventoIdAndDeclaradoPorIdOrderByCreatedAtDesc(
            Long eventoId, Long declaradoPorId);

    List<PagoDeclarado> findByEstadoOrderByCreatedAtAsc(EstadoPagoDeclarado estado);

    List<PagoDeclarado> findByEventoIdAndEstado(Long eventoId, EstadoPagoDeclarado estado);
}
