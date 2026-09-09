package com.baniterio.api.compra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a BBDD para {@link ReglaCompra} (plantilla global). */
public interface ReglaCompraRepository extends JpaRepository<ReglaCompra, Long> {

    List<ReglaCompra> findByPenaIdOrderByCategoriaAscOrdenAscNombreAsc(Long penaId);
}
