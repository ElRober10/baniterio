package com.baniterio.api.identidad;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudIngresoRepository extends JpaRepository<SolicitudIngreso, Long> {

    List<SolicitudIngreso> findByPenaIdAndEstado(Long penaId, EstadoSolicitud estado);

    List<SolicitudIngreso> findByPenaId(Long penaId);
}
