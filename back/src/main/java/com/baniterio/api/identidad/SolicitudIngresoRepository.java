package com.baniterio.api.identidad;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudIngresoRepository extends JpaRepository<SolicitudIngreso, UUID> {

    List<SolicitudIngreso> findByPenaIdAndEstado(UUID penaId, EstadoSolicitud estado);

    List<SolicitudIngreso> findByPenaId(UUID penaId);
}
