package com.baniterio.api.identidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link SolicitudIngreso}. */
public interface SolicitudIngresoRepository extends JpaRepository<SolicitudIngreso, Long> {

    List<SolicitudIngreso> findByPenaIdAndEstado(Long penaId, EstadoSolicitud estado);

    List<SolicitudIngreso> findByPenaId(Long penaId);

    /** ¿Ese teléfono ya tiene una solicitud sin resolver? (para no duplicar). */
    boolean existsByTelefonoAndEstado(String telefono, EstadoSolicitud estado);

    /** Nº de solicitudes de una peña en un estado dado (para la campanita de pendientes). */
    long countByPenaIdAndEstado(Long penaId, EstadoSolicitud estado);
}
