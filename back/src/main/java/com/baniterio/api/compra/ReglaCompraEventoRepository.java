package com.baniterio.api.compra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ReglaCompraEvento} (reglas de un evento). */
public interface ReglaCompraEventoRepository extends JpaRepository<ReglaCompraEvento, Long> {

    List<ReglaCompraEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId);

    boolean existsByEventoId(Long eventoId);

    Optional<ReglaCompraEvento> findByIdAndEventoId(Long id, Long eventoId);
}
