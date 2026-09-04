package com.baniterio.api.identidad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Bebida}. */
public interface BebidaRepository extends JpaRepository<Bebida, Long> {

    List<Bebida> findByTipoAndEstadoOrderByNombreAsc(TipoBebida tipo, EstadoBebida estado);

    List<Bebida> findByEstadoOrderByCreatedAtAsc(EstadoBebida estado);

    Optional<Bebida> findByTipoAndNombreIgnoreCase(TipoBebida tipo, String nombre);
}
