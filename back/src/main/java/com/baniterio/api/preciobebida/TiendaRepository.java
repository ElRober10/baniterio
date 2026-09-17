package com.baniterio.api.preciobebida;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link Tienda}. */
public interface TiendaRepository extends JpaRepository<Tienda, Long> {

    List<Tienda> findByPenaIdOrderByOrdenAscNombreAsc(Long penaId);

    boolean existsByPenaIdAndNombreIgnoreCase(Long penaId, String nombre);
}
