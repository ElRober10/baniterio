package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link SolicitudEvento}. */
public interface SolicitudEventoRepository extends JpaRepository<SolicitudEvento, Long> {

    boolean existsBySolicitanteIdAndTipoAndEstado(
            Long solicitanteId, TipoSolicitudEvento tipo, EstadoSolicitud estado);

    Optional<SolicitudEvento> findFirstBySolicitanteIdAndTipoAndEstadoAndEventoIsNullOrderByIdAsc(
            Long solicitanteId, TipoSolicitudEvento tipo, EstadoSolicitud estado);

    boolean existsByEventoIdAndTipoAndEstado(
            Long eventoId, TipoSolicitudEvento tipo, EstadoSolicitud estado);

    Optional<SolicitudEvento> findByEventoIdAndTipoAndEstado(
            Long eventoId, TipoSolicitudEvento tipo, EstadoSolicitud estado);

    List<SolicitudEvento> findByPenaIdAndEstadoOrderByCreatedAtAsc(
            Long penaId, EstadoSolicitud estado);
}
