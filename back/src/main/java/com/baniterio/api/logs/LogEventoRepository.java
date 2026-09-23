package com.baniterio.api.logs;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogEventoRepository extends JpaRepository<LogEvento, Long> {

    @Query("""
            SELECT l FROM LogEvento l
            WHERE l.pena.id = :penaId
              AND (:usuarioId IS NULL OR l.usuario.id = :usuarioId)
              AND (:origen IS NULL OR l.origen = :origen)
              AND (CAST(:desde AS Instant) IS NULL OR l.creadoEn >= :desde)
              AND (CAST(:hasta AS Instant) IS NULL OR l.creadoEn < :hasta)
            ORDER BY l.creadoEn DESC
            """)
    Page<LogEvento> buscar(@Param("penaId") Long penaId, @Param("usuarioId") Long usuarioId,
            @Param("origen") OrigenLog origen, @Param("desde") Instant desde, @Param("hasta") Instant hasta,
            Pageable pageable);
}
