package com.baniterio.api.identidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link SolicitudIngreso}. Todavía sin usar (no hay
 *  endpoints de solicitud de acceso). */
public interface SolicitudIngresoRepository extends JpaRepository<SolicitudIngreso, Long> {

    List<SolicitudIngreso> findByPenaIdAndEstado(Long penaId, EstadoSolicitud estado);

    List<SolicitudIngreso> findByPenaId(Long penaId);
}
