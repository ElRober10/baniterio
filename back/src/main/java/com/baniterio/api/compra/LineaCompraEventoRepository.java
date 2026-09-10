package com.baniterio.api.compra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link LineaCompraEvento} (la lista de la compra mostrada). */
public interface LineaCompraEventoRepository extends JpaRepository<LineaCompraEvento, Long> {

    List<LineaCompraEvento> findByEventoIdOrderByCategoriaAscOrdenAscNombreAsc(Long eventoId);

    Optional<LineaCompraEvento> findByIdAndEventoId(Long id, Long eventoId);

    Optional<LineaCompraEvento> findByEventoIdAndArticuloEventoId(Long eventoId, Long articuloEventoId);
}
